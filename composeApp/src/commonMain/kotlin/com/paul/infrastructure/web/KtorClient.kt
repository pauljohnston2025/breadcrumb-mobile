package com.paul.infrastructure.web

import android.util.Log
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.resources.*
import io.ktor.client.plugins.* // Import for defaultRequest
import io.ktor.client.request.header
import io.ktor.http.* // Needed for URLProtocol, HttpHeaders

expect fun createHttpClientEngine(): HttpClientEngineFactory<*>
expect fun platformInfo(): String

object KtorClient {

    private const val API_HOST = "127.0.0.1"
    private const val API_PORT = 8080
    private val API_PROTOCOL = URLProtocol.HTTP

    val client: HttpClient by lazy {
        HttpClient(createHttpClientEngine()) { // Ktor will try to find an engine

            // Configure JSON serialization
            install(Resources)
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true // Optional: Make JSON output easier to read
                    isLenient = true // Optional: Be more tolerant of malformed JSON
                    ignoreUnknownKeys = true // Optional: Ignore properties not defined in data class
                })
            }

            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d("HTTP call", message)
                    }
                }
                level = LogLevel.ALL
            }

            install(DefaultRequest) {
                host = API_HOST
                port = API_PORT
                url {
                    protocol = API_PROTOCOL
                }
            }

            // Handle non-2xx responses (optional: default throws exception)
            expectSuccess = true // If true, non-2xx responses throw ResponseException
            // If false, you need to check response.status manually
        }
    }
}