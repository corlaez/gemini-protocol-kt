package com.corlaez.gemini

import com.corlaez.server.TLSServer

/**
 * Gemini TLS Server
 * Handles TLS connections and delegates protocol handling
 */
public class GeminiServer(
    private val host: String = "0.0.0.0",
    private val port: Int = 1965,
    private val certificateConfig: CertificateConfig
) {
    private val protocolHandler = GeminiProtocolHandler()
    private var server: TLSServer? = null

    public fun start(geminiHandler: suspend (GeminiRequest) -> GeminiResponse) {
        if (server == null) {
            server = TLSServer(host, port, certificateConfig)
        }
        server!!.start { input, remoteAddress ->
            val request = protocolHandler.readRequest(input, remoteAddress)

            val response = if (request != null) {
                // Valid request - call application handler
                try {
                    geminiHandler(request)
                } catch (e: Exception) {
                    println("Error in application handler: ${e.message}")
                    e.printStackTrace()
                    GeminiResponse.TemporaryFailure("Internal server error")
                }
            } else {
                // Invalid request - return error
                protocolHandler.createErrorResponse(ProtocolError.InvalidUrl)
            }

            // Write response using protocol handler
            println("Response sent: ${response.statusCode} ${response.meta}")
            return@start { output ->
                protocolHandler.writeResponse(output, response)
            }
        }
    }

    public suspend fun awaitTermination() {
        server?.awaitTermination()
    }

    /** Gracefully stop the server */
    public fun stop() {
        server?.stop()
    }
}
