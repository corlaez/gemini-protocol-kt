package com.corlaez

import com.corlaez.gemini.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI

class GeminiProtocolHandlerTest {

    private val handler = GeminiProtocolHandler()

    @Test
    fun `should read valid gemini request`() {
        // Arrange
        val requestUrl = "gemini://example.com/test"
        val requestBytes = "$requestUrl\r\n".toByteArray(Charsets.UTF_8)
        val input = ByteArrayInputStream(requestBytes)
        val remoteAddress = "127.0.0.1:12345"

        // Act
        val request = handler.readRequest(input, remoteAddress)

        // Assert
        assertNotNull(request)
        assertEquals(URI(requestUrl), request!!.url)
        assertEquals(remoteAddress, request.remoteAddress)
        assertEquals("/test", request.url.path)
        assertEquals("example.com", request.url.host)
        assertEquals("gemini", request.url.scheme)
    }

    @Test
    fun `should write success response with body`() {
        // Arrange
        val output = ByteArrayOutputStream()
        val body = "# Hello Gemini\n\nWelcome to my capsule!"
        val response = GeminiResponse.Success(body = body)

        // Act
        handler.writeResponse(output, response)

        // Assert
        val result = output.toString(Charsets.UTF_8)
        assertTrue(result.startsWith("20 text/gemini\r\n"))
        assertTrue(result.contains(body))
        assertEquals("20 text/gemini\r\n$body", result)
    }

    @Test
    fun `should write success response with custom mime type`() {
        // Arrange
        val output = ByteArrayOutputStream()
        val body = "Plain text content"
        val response = GeminiResponse.Success(mimeType = "text/plain", body = body)

        // Act
        handler.writeResponse(output, response)

        // Assert
        val result = output.toString(Charsets.UTF_8)
        assertEquals("20 text/plain\r\n$body", result)
    }

    @Test
    fun `should write error response without body`() {
        // Arrange
        val output = ByteArrayOutputStream()
        val response = GeminiResponse.NotFound("Page not found")

        // Act
        handler.writeResponse(output, response)

        // Assert
        val result = output.toString(Charsets.UTF_8)
        assertEquals("51 Page not found\r\n", result)
    }

    @Test
    fun `should handle request with query parameters`() {
        // Arrange
        val requestUrl = "gemini://example.com/search?q=test&page=2"
        val requestBytes = "$requestUrl\r\n".toByteArray(Charsets.UTF_8)
        val input = ByteArrayInputStream(requestBytes)

        // Act
        val request = handler.readRequest(input, "127.0.0.1")

        // Assert
        assertNotNull(request)
        assertEquals("/search", request!!.url.path)
        assertEquals("q=test&page=2", request.url.query)
    }

    @Test
    fun `should return null for request without CRLF`() {
        // Arrange
        val requestBytes = "gemini://example.com/test".toByteArray(Charsets.UTF_8)
        val input = ByteArrayInputStream(requestBytes)

        // Act
        val request = handler.readRequest(input, "127.0.0.1")

        // Assert
        assertNull(request)
    }

    @Test
    fun `should return null for too long URL`() {
        // Arrange
        val longUrl = "gemini://example.com/" + "a".repeat(1025)
        val requestBytes = "$longUrl\r\n".toByteArray(Charsets.UTF_8)
        val input = ByteArrayInputStream(requestBytes)

        // Act
        val request = handler.readRequest(input, "127.0.0.1")

        // Assert
        assertNull(request, "Should return null for URL longer than 1024 bytes")
    }

    @Test
    fun `should return null for invalid URI`() {
        // Arrange
        val invalidUrl = "not a valid uri ://[]"
        val requestBytes = "$invalidUrl\r\n".toByteArray(Charsets.UTF_8)
        val input = ByteArrayInputStream(requestBytes)

        // Act
        val request = handler.readRequest(input, "127.0.0.1")

        // Assert
        assertNull(request)
    }

    @Test
    fun `should handle empty path as root`() {
        // Arrange
        val requestUrl = "gemini://example.com"
        val requestBytes = "$requestUrl\r\n".toByteArray(Charsets.UTF_8)
        val input = ByteArrayInputStream(requestBytes)

        // Act
        val request = handler.readRequest(input, "127.0.0.1")

        // Assert
        assertNotNull(request)
        assertEquals("", request!!.url.path)
    }

    @Test
    fun `should create error response for invalid URL`() {
        // Arrange & Act
        val response1 = handler.createErrorResponse(ProtocolError.InvalidUrl)
        val response2 = handler.createErrorResponse(ProtocolError.InvalidUrl)
        val response3 = handler.createErrorResponse(ProtocolError.InvalidUrl)

        // Assert
        assertEquals(59, response1.statusCode)
        assertNull(response1.body)
        assertEquals(59, response2.statusCode)
        assertNull(response2.body)
        assertEquals(59, response3.statusCode)
        assertNull(response3.body)
    }

    @Test
    fun `should create error response for URL too long`() {
        // Arrange & Act
        val response = handler.createErrorResponse(ProtocolError.UrlTooLong)

        // Assert
        assertEquals(59, response.statusCode)
        assertEquals("URL too long", response.meta)
    }

    @Test
    fun `should write all gemini response types correctly`() {
        // Test Input response
        val inputResponse = GeminiResponse.Input("Enter your name")
        val inputOutput = ByteArrayOutputStream()
        handler.writeResponse(inputOutput, inputResponse)
        assertEquals("10 Enter your name\r\n", inputOutput.toString(Charsets.UTF_8))

        // Test Redirect response
        val redirectResponse = GeminiResponse.Redirect("gemini://newsite.com/")
        val redirectOutput = ByteArrayOutputStream()
        handler.writeResponse(redirectOutput, redirectResponse)
        assertEquals("30 gemini://newsite.com/\r\n", redirectOutput.toString(Charsets.UTF_8))

        // Test TemporaryFailure response
        val tempFailResponse = GeminiResponse.TemporaryFailure("Server busy")
        val tempFailOutput = ByteArrayOutputStream()
        handler.writeResponse(tempFailOutput, tempFailResponse)
        assertEquals("40 Server busy\r\n", tempFailOutput.toString(Charsets.UTF_8))

        // Test BadRequest response
        val badReqResponse = GeminiResponse.BadRequest("Invalid request")
        val badReqOutput = ByteArrayOutputStream()
        handler.writeResponse(badReqOutput, badReqResponse)
        assertEquals("59 Invalid request\r\n", badReqOutput.toString(Charsets.UTF_8))
    }

    @Test
    fun `should handle request with port number`() {
        // Arrange
        val requestUrl = "gemini://example.com:1965/test"
        val requestBytes = "$requestUrl\r\n".toByteArray(Charsets.UTF_8)
        val input = ByteArrayInputStream(requestBytes)

        // Act
        val request = handler.readRequest(input, "127.0.0.1")

        // Assert
        assertNotNull(request)
        assertEquals("example.com", request!!.url.host)
        assertEquals(1965, request.url.port)
        assertEquals("/test", request.url.path)
    }
}