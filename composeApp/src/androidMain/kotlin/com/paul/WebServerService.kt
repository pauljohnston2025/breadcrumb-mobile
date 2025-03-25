package com.paul

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.paul.infrastructure.utils.ImageProcessor
import com.paul.infrastructure.utils.TileGetter
import com.paul.infrastructure.web.LoadTileRequest
import com.paul.infrastructure.web.LoadTileResponse
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.resources.Resources
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.response.responseType
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json
import org.slf4j.event.Level
import io.ktor.server.resources.get as resourcesGet

val startTimeKey = AttributeKey<Long>("StartTime")
val responseKey = AttributeKey<Any>("Response")

val RequestLogging = createApplicationPlugin(name = "RequestLogging") {
    onCall { call ->
        val request = call.request
        val path = request.path()
        val method = request.httpMethod.value
        val headers = request.headers.entries().joinToString(separator = "\n    ") { (name, values) ->
            "$name: ${values.joinToString()}"
        }

        val parameters = request.queryParameters.entries().joinToString(separator = "\n    ") { (name, values) ->
            "$name: ${values.joinToString()}"
        }

//        Log.d("WebserverService","Incoming request - Method: $method, Path: $path\n  Headers:\n    $headers\n  Parameters:\n    $parameters")

        // Store the start time in the call attributes
        call.attributes.put(startTimeKey, System.currentTimeMillis())
    }

    // status is not set at this point, but we need it to capture the response
    onCallRespond { call, response ->
        call.attributes.put(responseKey, response)
    }

    on(ResponseSent) { call ->
        val request = call.request
        val path = request.path()
        val method = request.httpMethod.value
        val startTime = call.attributes[startTimeKey]
        val responseObject = call.attributes[responseKey]
        val duration = System.currentTimeMillis() - startTime
        val statusCode = call.response.status()
        val headers = call.response.headers.allValues().entries().joinToString(separator = "\n    ") { (name, values) ->
            "$name: ${values.joinToString()}"
        }
        val responseType = call.response.responseType
//        Log.d("WebserverService", "Outgoing response - Method: $method, Path: $path\n  Status: $statusCode\n  Headers:\n" +
//                "    $headers\n  Duration: $duration ms\n  ResponseType: $responseType\n  Response: $responseObject")
    }
}

class WebServerService : Service() {
    // service has its own impl of tileGetter, since it runs outside of the main activity
    private val tileGetter = TileGetter(ImageProcessor(this), this)

    private val serverPort = 8080
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startKtorServer()
        }
        catch (t: Throwable)
        {
            // todo handle shutting down the old service somehow
            // then start a new one
            Log.e("stdout", "failed to start service, probably already bound: $t")
        }
        return START_STICKY
    }

    private fun startKtorServer() {
        // emulator exposed externally
//         val host = if(isEmulator()) "0.0.0.0" else "127.0.0.1";
        // to make this work on the emulator you ned to run
        // adb forward tcp:8080 tcp:8080
        // need to listen externally so that we can still connect over wifi
        // or hotspot
        server = embeddedServer(Netty, port = serverPort, host = "0.0.0.0") {
            module()
        }.start(wait = false)
    }


    override fun onDestroy() {
        super.onDestroy()
        server?.stop(1000, 5000)
    }


    fun Application.module() {
        install(Resources)
        install(CallLogging) {
            level = Level.INFO
            filter { call -> call.request.path().startsWith("/") }
        }
        install(RequestLogging)

        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }

        routing {
            resourcesGet<LoadTileRequest>  { params ->
                call.respond(tileGetter.getTile(params))
            }

            get("/") {
                Log.d("WebserverService", "got request for root")
                call.respondText("helloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworldhelloworld")
            }
        }
    }
}