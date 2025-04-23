package com.paul.infrastructure.web

import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.HttpClientCall
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.resources.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json

expect fun createHttpClientEngine(): HttpClientEngineFactory<*>
expect fun platformInfo(): String

object KtorClient {

    private val REQUEST_URL_KEY = AttributeKey<String>("RequestUrlKey")
    private const val API_HOST = "127.0.0.1"
    private const val API_PORT = 8080
    private val API_PROTOCOL = URLProtocol.HTTP

    val conciseHttpLogger = createClientPlugin("conciseHttpLogger") {
        // This lambda block is the 'install' part of the plugin.
        // 'this' refers to the HttpClient instance (scope) the plugin is installed on.

        // Intercept the 'Send' phase of the request pipeline.
        // This phase is where the client executes the request using the underlying engine.
        // Intercepting here allows us to wrap the actual execution.
        this.client.sendPipeline.intercept(HttpSendPipeline.Engine) { subject -> // subject is usually Unit in Send phase

            // ---> Before Execution <---
            // Note: RequestBuilder might still be mutable here depending on prior phases.
            // context refers to the HttpRequestBuilder here.
            val url = context.url.toString() // Get URL before proceeding

            // ---> Execute the request by calling proceed() <---
            // proceed() continues the pipeline, eventually hitting the engine and returning the call result.
            val call = proceed() as HttpClientCall // proceed() returns 'Any' in Send phase, cast is needed.

            // ---> After Execution <---
            // Now 'call' holds the completed HttpClientCall containing request and response.
            val status = call.response.status.value

            // Log the concise message
//            Napier.d("Fetching $url, got response $status", tag="HTTP client")

            // Return the result of the execution (the HttpClientCall) to the pipeline
            call // Ensure the interceptor returns the call object
        }
    }

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

            install(conciseHttpLogger)

//            install(Logging) {
//                logger = object : Logger {
//                    override fun log(message: String) {
//                        Napier.d(message, tag="HTTP call")
//                    }
//                }
//                level = LogLevel.ALL
//
//            }

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