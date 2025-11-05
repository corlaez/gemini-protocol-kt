package com.corlaez

import com.corlaez.gemini.*
import com.corlaez.util.*
import kotlinx.coroutines.runBlocking

public fun main() {
    val generatedSelfSignedConfig = CertificateConfig.GeneratedSelfSigned()
    val server1 = GeminiServer(generatedSelfSignedConfig, 1965)
        .start { GeminiResponse.Success(body = "# Hello world from server 1!") }

    val fileSelfSignedConfig = CertificateConfig.FileSelfSigned(
        certPemPath = "./src/main/resources/gitignored/cert.pem",
        privateKeyPath = "./src/main/resources/gitignored/private.key",
        privateKeyPassword = getEnvAsCharArray("PRIVATE_KEY_PASSWORD") ?: CharArray(0)
    )
    val server2 = GeminiServer(fileSelfSignedConfig, 1966)
        .start { GeminiResponse.Success(body = "# Hello world from server 2!") }

    val letsEncryptConfig = CertificateConfig.LetsEncrypt.fromDomain("corlaez.com")
    val server3 = GeminiServer(letsEncryptConfig, 1967)
        .start { GeminiResponse.Success(body = "# Hello world from server 3!") }

    runBlocking {
        server1.awaitTermination()
        server2.awaitTermination()
        server3.awaitTermination()
    }
}


//
//val certConfig = if (System.getenv("GEMINI_ENV") == "production") {
//    CertificateConfig.LetsEncrypt.fromDomain("corlaez.com")
//} else {
//    CertificateConfig.FileSelfSigned(
//        certPemPath = "./src/main/resources/gitignored/cert.pem",
//        privateKeyPath = "./src/main/resources/gitignored/private.key",
//        privateKeyPassword = getEnvAsCharArray("PRIVATE_KEY_PASSWORD")!!
//    )
//}
//val server = GeminiServer("0.0.0.0", 1965, certConfig)
//server.startWithRoutes {
//    get("/") {
//        GeminiResponse.Success(body = """
//                # Welcome to Gemini Server
//
//                This is a Gemini protocol server built with Ktor Network.
//
//                ## Features
//                * TLS 1.2 and 1.3 support
//                * Let's Encrypt certificates
//                * Self-signed certificates for local testing
//
//                ## Links
//                => /about About this server
//                => /test Test page
//                => /users/123 Example user
//                => /users/1243 Example user
//            """.trimIndent())
//    }
//    get("/about") {
//        GeminiResponse.Success(body = """
//                # About This Server
//
//                This is a Gemini protocol server implementation.
//
//                => / Back to home
//            """.trimIndent())
//    }
//    get("/test") {
//        GeminiResponse.Success(body = """
//                # Test Page
//
//                You successfully reached the test page!
//
//                Remote address: $remoteAddress
//                Path: $path
//
//                => / Back to home
//            """.trimIndent())
//    }
//    // Route with path parameter
//    get("/users/{id}") {
//        val userId = pathParam("id")
//        GeminiResponse.Success(body = """
//                # User Profile
//
//                User ID: $userId
//
//                => / Back to home
//            """.trimIndent())
//    }
//    staticFiles(
//        urlPath = "/static",
//        localPath = File("./public"),
//        listDirectories = true
//    )
//    notFound {
//        GeminiResponse.NotFound("Page not found: $path")
//    }
//}
//runBlocking {
//    server.start { GeminiResponse.Success(body ="# Success!") }
//    delay(5.seconds)
//    server.startWithRoutes {  }
//    delay(5.seconds)
//    server.stop()
//    launch {
//        server.awaitTermination()
//    }
//}