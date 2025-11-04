package com.corlaez

import com.corlaez.gemini.*
import com.corlaez.gemini.CertificateConfig
import com.corlaez.util.staticFiles
import java.io.File

public fun startGeminiServer(): GeminiServer {
    // Select config based on an env variable
    val certConfig = if (System.getenv("GEMINI_ENV") == "production") {
        CertificateConfig.LetsEncrypt.fromDomain("corlaez.com")
    } else {
        CertificateConfig.SelfSigned(hostname = "localhost")
    }
    val server = GeminiServer("0.0.0.0", 1965, certConfig)
    server.startWithRoutes {
        get("/") {
            GeminiResponse.Success(body = """
                # Welcome to Gemini Server
                
                This is a Gemini protocol server built with Ktor Network.
                
                ## Features
                * TLS 1.2 and 1.3 support
                * Let's Encrypt certificates
                * Self-signed certificates for local testing
                
                ## Links
                => /about About this server
                => /test Test page
                => /users/123 Example user
                => /users/1243 Example user
            """.trimIndent())
        }
        get("/about") {
            GeminiResponse.Success(body = """
                # About This Server
                
                This is a Gemini protocol server implementation.
                
                => / Back to home
            """.trimIndent())
        }
        get("/test") {
            GeminiResponse.Success(body = """
                # Test Page
                
                You successfully reached the test page!
                
                Remote address: $remoteAddress
                Path: $path
                
                => / Back to home
            """.trimIndent())
        }
        // Route with path parameter
        get("/users/{id}") {
            val userId = pathParam("id")
            GeminiResponse.Success(body = """
                # User Profile
                
                User ID: $userId
                
                => / Back to home
            """.trimIndent())
        }
        staticFiles(
            urlPath = "/static",
            localPath = File("./public"),
            listDirectories = true
        )
        notFound {
            GeminiResponse.NotFound("Page not found: $path")
        }
    }
    return server
}
