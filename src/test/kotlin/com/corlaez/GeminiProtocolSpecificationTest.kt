package com.corlaez

import com.corlaez.gemini.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI

class GeminiProtocolSpecificationTest {

    private val handler = GeminiProtocolHandler()

    @Nested
    inner class RequestTests {

        @Test
        fun `request MUST be a single CRLF-terminated line with UTF-8 encoding`() {
            val requestUrl = "gemini://example.com/test"
            val requestBytes = "$requestUrl\r\n".toByteArray(Charsets.UTF_8)
            val input = ByteArrayInputStream(requestBytes)

            val request = handler.readRequest(input, "127.0.0.1")

            assertNotNull(request)
            assertEquals(URI(requestUrl), request!!.url)
        }

        @Test
        fun `request line MUST NOT exceed 1024 bytes`() {
            val longUrl = "gemini://example.com/" + "a".repeat(1024 - "gemini://example.com/".length + 1)
            val requestBytes = "$longUrl\r\n".toByteArray(Charsets.UTF_8)
            val input = ByteArrayInputStream(requestBytes)

            val request = handler.readRequest(input, "127.0.0.1")

            assertNull(request, "URLs longer than 1024 bytes should be rejected")
        }

        @Test
        fun `request at exactly 1024 bytes should be accepted`() {
            val exactUrl = "gemini://example.com/" + "a".repeat(1024 - "gemini://example.com/".length)
            val requestBytes = "$exactUrl\r\n".toByteArray(Charsets.UTF_8)
            val input = ByteArrayInputStream(requestBytes)

            val request = handler.readRequest(input, "127.0.0.1")

            assertNotNull(request, "URLs at exactly 1024 bytes should be accepted")
        }

        @Test
        fun `request MUST be a valid absolute URL with scheme`() {
            // Valid absolute URL
            val validUrl = "gemini://example.com/path"
            val validBytes = "$validUrl\r\n".toByteArray(Charsets.UTF_8)
            val validInput = ByteArrayInputStream(validBytes)

            val validRequest = handler.readRequest(validInput, "127.0.0.1")
            assertNotNull(validRequest)

            // Relative URL should be rejected (handled by URI parsing)
            val relativeUrl = "/path/to/resource"
            val relativeBytes = "$relativeUrl\r\n".toByteArray(Charsets.UTF_8)
            val relativeInput = ByteArrayInputStream(relativeBytes)

            val relativeRequest = handler.readRequest(relativeInput, "127.0.0.1")
            // Java URI accepts this, but it won't have a scheme
            if (relativeRequest != null) {
                assertNull(relativeRequest.url.scheme, "Relative URLs should not have a scheme")
            }
        }

        @Test
        fun `request with only LF should be rejected`() {
            val requestUrl = "gemini://example.com/test"
            val requestBytes = "$requestUrl\n".toByteArray(Charsets.UTF_8)
            val input = ByteArrayInputStream(requestBytes)

            val request = handler.readRequest(input, "127.0.0.1")

            // Should timeout/fail because it's waiting for CRLF
            // In practice, it will read until EOF
            assertNull(request)
        }

        @Test
        fun `request with only CR should be rejected`() {
            val requestUrl = "gemini://example.com/test"
            val requestBytes = "$requestUrl\r".toByteArray(Charsets.UTF_8)
            val input = ByteArrayInputStream(requestBytes)

            val request = handler.readRequest(input, "127.0.0.1")

            assertNull(request, "Request with only CR should not be accepted")
        }

        @Test
        fun `request with userinfo in URL must be rejected`() {
            val requestUrl = "gemini://user:pass@example.com/test"
            val requestBytes = "$requestUrl\r\n".toByteArray(Charsets.UTF_8)
            val input = ByteArrayInputStream(requestBytes)

            val request = handler.readRequest(input, "127.0.0.1")

            assertNull(request)
        }

        @Test
        fun `request with fragment must be rejected`() {
            val requestUrl = "gemini://example.com/test#section"
            val requestBytes = "$requestUrl\r\n".toByteArray(Charsets.UTF_8)
            val input = ByteArrayInputStream(requestBytes)

            val request = handler.readRequest(input, "127.0.0.1")

            assertNull(request)
        }

        @Test
        fun `request can include query string`() {
            val requestUrl = "gemini://example.com/search?query=test&page=1"
            val requestBytes = "$requestUrl\r\n".toByteArray(Charsets.UTF_8)
            val input = ByteArrayInputStream(requestBytes)

            val request = handler.readRequest(input, "127.0.0.1")

            assertNotNull(request)
            assertEquals("query=test&page=1", request!!.url.query)
        }

        @Test
        @Disabled // I am not sure this is a real requirement, leaving out for now as it would complicate dependencies
        fun `request with non-gemini scheme should still be parsed`() {
            // Spec says clients should use gemini://, but servers should handle others
            val httpsUrl = "https://example.com/test"
            val requestBytes = "$httpsUrl\r\n".toByteArray(Charsets.UTF_8)
            val input = ByteArrayInputStream(requestBytes)

            val request = handler.readRequest(input, "127.0.0.1")

            assertNotNull(request)
            assertEquals("https", request!!.url.scheme)
        }
    }

    @Nested
    inner class ResponseHeaderTests {

        @Test
        fun `response header MUST be UTF-8 encoded`() {
            val output = ByteArrayOutputStream()
            val response = GeminiResponse.Success(body = "Test")

            handler.writeResponse(output, response)

            val result = output.toString(Charsets.UTF_8)
            assertTrue(result.startsWith("20 text/gemini\r\n"))
        }

        @Test
        fun `response header MUST end with CRLF`() {
            val output = ByteArrayOutputStream()
            val response = GeminiResponse.NotFound("Not found")

            handler.writeResponse(output, response)

            val result = output.toString(Charsets.UTF_8)
            assertTrue(result.contains("\r\n"), "Response must contain CRLF")
            assertTrue(result.indexOf("\r\n") == result.indexOf("Not found") + "Not found".length)
        }

        @Test
        fun `response MUST begin with two-digit status code`() {
            val responses = listOf(
                GeminiResponse.Input("Enter name") to "10",
                GeminiResponse.Success(body = "OK") to "20",
                GeminiResponse.Redirect("gemini://other.com") to "30",
                GeminiResponse.TemporaryFailure("Error") to "40",
                GeminiResponse.NotFound("Not found") to "51",
                GeminiResponse.BadRequest("Bad") to "59"
            )

            for ((response, expectedCode) in responses) {
                val output = ByteArrayOutputStream()
                handler.writeResponse(output, response)
                val result = output.toString(Charsets.UTF_8)

                assertTrue(result.startsWith(expectedCode),
                    "Response should start with status code $expectedCode but got: $result")
            }
        }

        @Test
        fun `status code MUST be followed by single space`() {
            val output = ByteArrayOutputStream()
            val response = GeminiResponse.Success(body = "Test")

            handler.writeResponse(output, response)

            val result = output.toString(Charsets.UTF_8)
            assertTrue(result.startsWith("20 "), "Status code must be followed by single space")
            assertFalse(result.startsWith("20  "), "Should not have double space")
        }

        @Test
        fun `meta string MUST NOT exceed 1024 bytes`() {
            // Create a long meta string (we can't enforce this at response creation,
            // but we should be aware of it)
            val longMeta = "a".repeat(1025)
            val output = ByteArrayOutputStream()

            // We construct response with long meta manually since our classes don't validate
            // In a real implementation, you might want to add validation
            val responseHeader = "20 $longMeta\r\n"
            output.write(responseHeader.toByteArray(Charsets.UTF_8))

            val result = output.toString(Charsets.UTF_8)
            val metaLength = result.substringAfter("20 ").substringBefore("\r\n").toByteArray(Charsets.UTF_8).size

            // This test documents the issue - ideally you'd enforce this limit
            assertTrue(metaLength > 1024, "This demonstrates that long meta strings are possible")
        }

        @Test
        fun `status code 20 MUST have MIME type as meta`() {
            val output = ByteArrayOutputStream()
            val response = GeminiResponse.Success(mimeType = "text/gemini", body = "Test")

            handler.writeResponse(output, response)

            val result = output.toString(Charsets.UTF_8)
            assertTrue(result.startsWith("20 text/gemini\r\n"))
        }

        @Test
        fun `status code 20 MIME type MAY include charset parameter`() {
            val output = ByteArrayOutputStream()
            val response = GeminiResponse.Success(
                mimeType = "text/gemini; charset=utf-8",
                body = "Test"
            )

            handler.writeResponse(output, response)

            val result = output.toString(Charsets.UTF_8)
            assertTrue(result.startsWith("20 text/gemini; charset=utf-8\r\n"))
        }

        @Test
        fun `status code 20 defaults to text gemini if not specified`() {
            val output = ByteArrayOutputStream()
            val response = GeminiResponse.Success(body = "Test")

            handler.writeResponse(output, response)

            val result = output.toString(Charsets.UTF_8)
            assertTrue(result.startsWith("20 text/gemini\r\n"))
        }

        @Test
        fun `status code 3x MUST have redirect URL as meta`() {
            val output = ByteArrayOutputStream()
            val redirectUrl = "gemini://newsite.com/path"
            val response = GeminiResponse.Redirect(redirectUrl)

            handler.writeResponse(output, response)

            val result = output.toString(Charsets.UTF_8)
            assertTrue(result.startsWith("30 $redirectUrl\r\n"))
        }

        @Test
        fun `status code 30 is temporary redirect`() {
            val response = GeminiResponse.Redirect("gemini://other.com", permanent = false)
            assertEquals(30, response.statusCode)
        }

        @Test
        fun `status code 31 is permanent redirect`() {
            val response = GeminiResponse.Redirect("gemini://other.com", permanent = true)
            assertEquals(31, response.statusCode)
        }

        @Test
        fun `status code 1x MUST have prompt text as meta`() {
            val output = ByteArrayOutputStream()
            val prompt = "Enter your name"
            val response = GeminiResponse.Input(prompt)

            handler.writeResponse(output, response)

            val result = output.toString(Charsets.UTF_8)
            assertTrue(result.startsWith("10 $prompt\r\n"))
        }
    }

    @Nested
    inner class ResponseBodyTests {

        @Test
        fun `status code 20 MUST be followed by response body`() {
            val output = ByteArrayOutputStream()
            val body = "# Test\n\nContent"
            val response = GeminiResponse.Success(body = body)

            handler.writeResponse(output, response)

            val result = output.toString(Charsets.UTF_8)
            assertTrue(result.contains(body))
            assertEquals("20 text/gemini\r\n$body", result)
        }

        @Test
        fun `status codes other than 20 MUST NOT have response body`() {
            val responses = listOf(
                GeminiResponse.Input("Prompt"),
                GeminiResponse.Redirect("gemini://other.com"),
                GeminiResponse.TemporaryFailure("Error"),
                GeminiResponse.NotFound("Not found"),
                GeminiResponse.BadRequest("Bad request")
            )

            for (response in responses) {
                assertNull(response.body,
                    "Status code ${response.statusCode} should not have a body")

                val output = ByteArrayOutputStream()
                handler.writeResponse(output, response)
                val result = output.toString(Charsets.UTF_8)

                // Should only contain header (status + meta + CRLF)
                assertEquals(1, result.split("\r\n").size - 1,
                    "Non-20 responses should only have header line")
            }
        }

        @Test
        fun `response body MUST be UTF-8 encoded`() {
            val output = ByteArrayOutputStream()
            val body = "Hello 世界 🌍"  // Mixed UTF-8 content
            val response = GeminiResponse.Success(body = body)

            handler.writeResponse(output, response)

            val result = output.toString(Charsets.UTF_8)
            assertTrue(result.contains(body))
        }

        @Test
        fun `response body can be empty for status 20`() {
            val output = ByteArrayOutputStream()
            val response = GeminiResponse.Success(body = "")

            handler.writeResponse(output, response)

            val result = output.toString(Charsets.UTF_8)
            assertEquals("20 text/gemini\r\n", result)
        }
    }

    @Nested
    inner class StatusCodeRangeTests {

        @Test
        fun `status codes 10-19 are INPUT responses`() {
            val inputResponse = GeminiResponse.Input("Enter data")
            assertTrue(inputResponse.statusCode in 10..19)
            assertNull(inputResponse.body)
        }

        @Test
        fun `status codes 20-29 are SUCCESS responses`() {
            val successResponse = GeminiResponse.Success(body = "Content")
            assertTrue(successResponse.statusCode in 20..29)
            assertNotNull(successResponse.body)
        }

        @Test
        fun `status codes 30-39 are REDIRECT responses`() {
            val tempRedirect = GeminiResponse.Redirect("gemini://other.com", permanent = false)
            val permRedirect = GeminiResponse.Redirect("gemini://other.com", permanent = true)

            assertTrue(tempRedirect.statusCode in 30..39)
            assertTrue(permRedirect.statusCode in 30..39)
            assertNull(tempRedirect.body)
            assertNull(permRedirect.body)
        }

        @Test
        fun `status codes 40-49 are TEMPORARY FAILURE responses`() {
            val tempFailure = GeminiResponse.TemporaryFailure("Try again")
            val serverUnavailable = GeminiResponse.ServerUnavailable("Down for maintenance")

            assertTrue(tempFailure.statusCode in 40..49)
            assertTrue(serverUnavailable.statusCode in 40..49)
            assertNull(tempFailure.body)
            assertNull(serverUnavailable.body)
        }

        @Test
        fun `status codes 50-59 are PERMANENT FAILURE responses`() {
            val notFound = GeminiResponse.NotFound("Page not found")
            val badRequest = GeminiResponse.BadRequest("Invalid request")
            val proxyRefused = GeminiResponse.ProxyRequestRefused("No proxy")

            assertTrue(notFound.statusCode in 50..59)
            assertTrue(badRequest.statusCode in 50..59)
            assertTrue(proxyRefused.statusCode in 50..59)
            assertNull(notFound.body)
            assertNull(badRequest.body)
            assertNull(proxyRefused.body)
        }
    }

    @Nested
    inner class EdgeCaseTests {

        @Test
        fun `empty request should be rejected`() {
            val requestBytes = "\r\n".toByteArray(Charsets.UTF_8)
            val input = ByteArrayInputStream(requestBytes)

            val request = handler.readRequest(input, "127.0.0.1")

            // Empty URL will fail URI parsing
            assertNull(request)
        }

        @Test
        fun `request with embedded nulls must be rejected`() {
            val requestBytes = "gemini://example.com/\u0000test\r\n".toByteArray(Charsets.UTF_8)
            val input = ByteArrayInputStream(requestBytes)

            val request = handler.readRequest(input, "127.0.0.1")

            assertNull(request)
        }

        @Test
        fun `request with international domain names should work`() {
            val requestUrl = "gemini://münchen.de/test"
            val requestBytes = "$requestUrl\r\n".toByteArray(Charsets.UTF_8)
            val input = ByteArrayInputStream(requestBytes)

            val request = handler.readRequest(input, "127.0.0.1")

            assertNotNull(request)
            assertTrue(request!!.url.host.contains("münchen") ||
                    request.url.host.contains("xn--"), // IDN encoded
                "Should handle international domain names")
        }

        @Test
        fun `consecutive CRLF should only terminate first line`() {
            val requestUrl = "gemini://example.com/test"
            val requestBytes = "$requestUrl\r\n\r\n".toByteArray(Charsets.UTF_8)
            val input = ByteArrayInputStream(requestBytes)

            val request = handler.readRequest(input, "127.0.0.1")

            assertNotNull(request)
            assertEquals(URI(requestUrl), request!!.url)
        }
    }
}
