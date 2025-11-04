package com.corlaez.gemini

import java.net.URI
import java.net.URL
import java.net.IDN

internal class GeminiURI(val uri: URI?) {
    private companion object {
        private fun parseAndValidateGeminiUri(requestLine: String): URI? {
            val trimmedRequest = requestLine.trim()
            if (trimmedRequest.contains("\u0000")) return null
            val geminiScheme = "gemini://"

            // 1. Check for the correct scheme first
            if (!trimmedRequest.startsWith(geminiScheme, ignoreCase = true)) {
                println("Error: Must use the gemini:// scheme.")
                return null
            }
            // 2. Temporarily replace 'gemini' with 'http' to trick java.net.URL parsing
            val shimmedRequest = trimmedRequest.replaceFirst("gemini://", "http://", ignoreCase = true)

            // 3. Use URL to robustly extract the host and path components
            val tempUrl: URL = try {
                // NOTE: We temporarily use the more lenient URL class for parsing
                URL(shimmedRequest)
            } catch (e: Exception) {
                println("Error: Malformed URL for initial parsing: ${e.message}")
                return null
            }

            val tempUri = tempUrl.toURI()
            if (tempUri.fragment != null) {
                println("URI must not have a fragment")
                return null
            }
            if (tempUri.userInfo != null) {
                println("URI must not have user info")
                return null
            }
//          if (it.isOpaque) error("URI must be hierarchical")
//          if (it.path.isNullOrEmpty()) error("URI must have a non-empty path")
//          if (it.query != null) error("URI must not have a query")

            // 4. Safely convert the extracted host to Punycode (IDN.toASCII is idempotent/safe)
            val punycodeHost: String = try {
                IDN.toASCII(tempUrl.host)
            } catch (e: Exception) {
                println("Error: Invalid hostname for Punycode conversion.")
                return null
            }

            // 5. Reconstruct the URI string with the valid Punycode host
            // (Path and query components from the URL object are automatically percent-encoded)
            val finalUriString = StringBuilder()
                .append(geminiScheme)
                .append(punycodeHost) // Corrected host
                .append(if (tempUrl.port > 0) ":${tempUrl.port}" else "")
                .append(tempUrl.file) // Includes path and query

            // 6. Create the final, valid URI object for your application logic
            val finalUri = try {
                URI(finalUriString.toString()).also {
                    // Final validation checks
                    if (it.scheme.lowercase() != "gemini") error("URI must use the gemini:// scheme")
                    if (it.host == null) error("URI must have a hostname")
                }
            } catch (e: Exception) {
                println("Error validating final URI: ${e.message}")
                return null
            }

            if (!finalUri.isAbsolute) error("URI must be absolute")

            return finalUri
        }
    }
    constructor(requestLine: String) : this(parseAndValidateGeminiUri(requestLine)) {}
}
