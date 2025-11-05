package com.corlaez.server

import com.corlaez.gemini.CertificateConfig
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.*

/** A generic TLSServer using Java's SSLServerSocket and kotlin coroutines
 * Supports only TLSv1.2 and TLSv1.3 because this server is intended to be a Gemini Server*/
internal class TLSServer(
    private val host: String = "0.0.0.0",
    private val port: Int = 1965,
    private val certificateConfig: CertificateConfig
) {
    private val isRunning = AtomicBoolean(false)
    private var scope: CoroutineScope? = null
    private var serverJob: Job? = null
    private var serverSocket: SSLServerSocket? = null

    fun start(handler: suspend (InputStream, String) -> (OutputStream) -> Unit) {
        // If already running, stop first
        if (isRunning.get()) {
            println("Server already running, stopping first...")
            stop()
        } else {
            Runtime.getRuntime().addShutdownHook(Thread(::stop))
        }
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        // Create an SSL server socket using Java's built-in TLS
        val sslContext = certificateConfig.createSSLContext()
        val serverSocketFactory = sslContext.serverSocketFactory
        serverSocket = serverSocketFactory.createServerSocket(
            port,
            50,
            InetAddress.getByName(host)
        ) as SSLServerSocket

        // Configure TLS versions on the server socket
        serverSocket!!.enabledProtocols = arrayOf("TLSv1.2", "TLSv1.3")
        serverSocket!!.needClientAuth = false

        println("Gemini server started on gemini://$host:$port")
//        println("TLS versions: ${serverSocket!!.enabledProtocols.joinToString(", ")}")
//        println("Cipher suites: ${serverSocket!!.enabledCipherSuites.size} enabled")
//        println("Cipher suites: ${serverSocket!!.enabledCipherSuites.joinToString(", ")} enabled")

        isRunning.set(true)
        serverJob = scope?.launch {
            while (isActive) {
                try {
                    val socket = serverSocket?.accept() as? SSLSocket ?: break
                    launch {
                        handleTLSConnection(socket, handler)
                    }
                } catch (e: Exception) {
                    if (isRunning.get())
                        println("Error accepting connection: ${e}")
                }
            }
            println("Server stopped")
        }
    }

    suspend fun awaitTermination() {
        serverJob?.join()
    }

    fun stop() {
        if (!isRunning.get()) {
            return
        }

        isRunning.set(false)
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            println("Error closing server socket: ${e.message}")
        }

        scope?.cancel() // Cancel all coroutines
        serverJob = null
        runBlocking {
            awaitTermination()
        }
    }

    private suspend fun handleTLSConnection(
        sslSocket: SSLSocket,
        handler: suspend (InputStream, String) -> (OutputStream) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            // Configure and establish TLS
            sslSocket.enabledProtocols = arrayOf("TLSv1.2", "TLSv1.3")
            sslSocket.startHandshake()

            println("Connection from ${sslSocket.remoteSocketAddress}, protocol: ${sslSocket.session.protocol}")

            val input = sslSocket.inputStream
            val output = sslSocket.outputStream

            try {
                handler(input, sslSocket.remoteSocketAddress.toString()).invoke(output)
            } catch (e: Exception) {
                println("Error in secure server handler: ${e.message}")
                e.printStackTrace()
            } finally {
                // Graceful TLS shutdown
                closeGracefully(sslSocket, input, output)
            }
        } catch (e: Exception) {
            println("Error handling TLS connection: ${e.message}")
            e.printStackTrace()
            try {
                sslSocket.close()
            } catch (closeException: Exception) {
                closeException.printStackTrace()
            }
        }
    }

    private fun closeGracefully(sslSocket: SSLSocket, input: InputStream, output: OutputStream) {
        try {
            //("Shutting down output...")
            sslSocket.shutdownOutput()
            //("Waiting for client to close...")
            val buffer = ByteArray(1024)
            while (input.read(buffer) != -1) {}
            println("Client closed connection gracefully")
        } catch (e: Exception) {
            println("Error during graceful shutdown: ${e.message}")
        } finally {
            try {
                output.close()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                input.close()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                sslSocket.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
