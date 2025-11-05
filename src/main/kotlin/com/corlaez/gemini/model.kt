package com.corlaez.gemini

import java.io.InputStream
import java.io.OutputStream
import java.net.URI

/**
 * Gemini Protocol Handler
 * Pure protocol logic - no TLS/network concerns
 */
internal class GeminiProtocolHandler {

    /** Read a Gemini request from an input stream Returns null if the request is invalid */
    fun readRequest(input: InputStream, remoteAddress: String): Pair<GeminiRequest?, ProtocolError?> {
        val requestLineBytes = mutableListOf<Byte>()
        var byte: Int
        var foundCR = false

        // Read until we get CRLF
        while (true) {
            byte = input.read()
            if (byte == -1) {
                return null to ProtocolError.InvalidRequest// Connection closed
            }

            if (byte == '\r'.code) {
                foundCR = true
            } else if (byte == '\n'.code && foundCR) {
                break
            } else {
                if (foundCR) {
                    requestLineBytes.add('\r'.code.toByte())
                    foundCR = false
                }
                requestLineBytes.add(byte.toByte())
            }

            if (requestLineBytes.size > 1024) {
                return null to ProtocolError.UrlTooLong // URL too long
            }
        }

        val requestLine = String(requestLineBytes.toByteArray(), Charsets.UTF_8)

        val geminiURI = GeminiURI(requestLine)
        return if (geminiURI.uri == null) null to ProtocolError.InvalidUrl
            else GeminiRequest(url = geminiURI.uri, remoteAddress = remoteAddress) to null
    }

    /** Write a Gemini response to an output stream */
    fun writeResponse(output: OutputStream, response: GeminiResponse) {
        val responseHeader = "${response.statusCode} ${response.meta}\r\n"
        val responseBytes = if (response.body != null) {
            (responseHeader + response.body).toByteArray(Charsets.UTF_8)
        } else {
            responseHeader.toByteArray(Charsets.UTF_8)
        }
        output.write(responseBytes)
        output.flush()
    }

    /** Create error responses for protocol violations */
    fun createErrorResponse(error: ProtocolError): GeminiResponse {
        return when (error) {
            is ProtocolError.UrlTooLong -> GeminiResponse.BadRequest("URL too long")
            is ProtocolError.InvalidUrl -> GeminiResponse.BadRequest("Invalid URL")
            is ProtocolError.InvalidRequest -> GeminiResponse.BadRequest("Invalid request received")
            is ProtocolError.NoRequest -> GeminiResponse.BadRequest("No request received")
        }
    }
}

/**
 * Protocol-level errors
 */
internal sealed class ProtocolError {
    object UrlTooLong : ProtocolError()
    object InvalidUrl : ProtocolError()
    object InvalidRequest : ProtocolError()
    object NoRequest : ProtocolError()
}

/**
 * Gemini request representation
 */
public data class GeminiRequest(
    val url: URI,
    val remoteAddress: String
)

/**
 * Gemini response representation
 */
public sealed class GeminiResponse(
    public val statusCode: Int,
    public val meta: String,
    public val body: String? = null
) {
    public class Success(mimeType: String = "text/gemini", body: String) :
        GeminiResponse(20, mimeType, body)

    public class Input(prompt: String) :
        GeminiResponse(10, prompt)

    public class Redirect(newUrl: String, permanent: Boolean = false) :
        GeminiResponse(if (permanent) 31 else 30, newUrl)

    public class TemporaryFailure(message: String = "Temporary failure") :
        GeminiResponse(40, message)

    public class ServerUnavailable(message: String = "Server unavailable") :
        GeminiResponse(41, message)

    public class BadRequest(message: String = "Bad request") :
        GeminiResponse(59, message)

    public class NotFound(message: String = "Not found") :
        GeminiResponse(51, message)

    public class ProxyRequestRefused(message: String = "Proxy request refused") :
        GeminiResponse(53, message)
}