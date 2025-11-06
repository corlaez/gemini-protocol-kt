package com.corlaez.gemini

import com.corlaez.server.TLSServer

/** GeminiServer = TLSServer + GeminiProtocolHandler */
public class GeminiServer(
    private val certificateConfig: CertificateConfig,
    private val port: Int = 1965,
    private val host: String = "0.0.0.0",
) {
    private val protocolHandler = GeminiProtocolHandler()
    private var server: TLSServer? = null

    internal fun start(geminiHandler: suspend (GeminiRequest) -> GeminiResponse) {
        if (server == null) {
            server = TLSServer(host, port, certificateConfig)
        }
        server!!.start { input, remoteAddress ->
            val (request, error) = protocolHandler.readRequest(input, remoteAddress)

            val response = if (request != null) {
                try {
                    geminiHandler(request)
                } catch (e: Exception) {
                    println("Error in application handler: ${e.message}")
                    e.printStackTrace()
                    GeminiResponse.TemporaryFailure("Internal server error")
                }
            } else {
                protocolHandler.createErrorResponse(error!!)
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

    public fun stop() {
        server?.stop()
    }
}
