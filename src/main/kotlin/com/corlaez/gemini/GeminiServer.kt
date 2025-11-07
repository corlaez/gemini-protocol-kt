package com.corlaez.gemini

import com.corlaez.server.TLSServer

/** Allows the creation of a gemini server with a given configuration
 * @param certificateConfig specifies the configuration for the TLS certificate.
 * @param port specifies the port the server will use.
 * @param host specifies the host the server will use.
 *      - "0.0.0.0" as host means that it will accept requests from anywhere
 *      - "127.0.0.1" as host means that it will accept requests only from the local machine
 *      - "::1"       as host means that it will accept requests only from the local machine
 *      - "localhost" as host means that it will accept requests only from localhost (should map to 127.0.0.1 or ::1)
 *      - Any valid IP as host means that it will accept requests only from the machine with that IP
 * */
public class GeminiServer(
    private val certificateConfig: CertificateConfig,
    private val port: Int = 1965,
    private val host: String = "0.0.0.0",
) {
    /* GeminiServer = TLSServer + GeminiProtocolHandler */
    private var tlsServer: TLSServer? = null
    private val protocolHandler = GeminiProtocolHandler()

    /**
     * Starts the server
     * @param wait if true, this function does not exit until the server is destroyed
     * @param configureRouting a block of code that configures the application's routes
     */
    public fun startWithRoutes(wait: Boolean = true, configureRouting: GeminiRouting.() -> Unit) {
        val geminiRouting = GeminiRouting()
        configureRouting(geminiRouting)
        start(wait) { request -> geminiRouting.handle(request) }
    }

    /** Starts the server in a new coroutine with the given [block] as its context.
     * @param wait if true, this function does not exit until the server is destroyed
     * */
    internal fun start(wait: Boolean, geminiHandler: suspend (GeminiRequest) -> GeminiResponse) {
        if (tlsServer == null) {
            tlsServer = TLSServer(host, port, certificateConfig)
        }
        tlsServer!!.start(wait) { input, remoteAddress ->
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

    /** Stops the server. NOOP if the server is not running */
    public fun stop() {
        tlsServer?.stop()
    }
}
