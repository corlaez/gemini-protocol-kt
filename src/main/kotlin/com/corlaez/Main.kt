package com.corlaez

public suspend fun main() {
    val gemServer = startGeminiServer()
    gemServer.awaitTermination()
}
