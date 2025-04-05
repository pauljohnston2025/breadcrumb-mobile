package com.paul.viewmodels

import android.util.Log
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.web.ChangeTileServer
import com.paul.infrastructure.web.KtorClient
import com.paul.protocol.todevice.DropTileCache
import com.paul.protocol.todevice.Route
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.uuid.Uuid

enum class ServerType {
    ESRI,
    GOOGLE,
    OPENTOPOMAP,
    OPENSTREETMAP,
}

data class TileServerInfo(
    val serverType: ServerType,
    val title: String,
    val url: String,
)

class Settings(
    private val deviceSelector: DeviceSelector,
    private val connection: IConnection,
    private val snackbarHostState: SnackbarHostState
) : ViewModel() {
    private val client = KtorClient.client // Get the singleton client instance

    val sendingMessage: MutableState<String> = mutableStateOf("")

    // todo load from storage at startup
    val currentTileServer: MutableState<String> = mutableStateOf("")

    // todo: load user generated ones too
    val availableServers = listOf(
        TileServerInfo(
            ServerType.OPENTOPOMAP,
            "Open Topo Map",
            "https://a.tile.opentopomap.org/{z}/{x}/{y}.png"
        ),
        TileServerInfo(
            ServerType.OPENSTREETMAP,
            "Open Street Map",
            "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        ),
        TileServerInfo(
            ServerType.GOOGLE,
            "Google - Hybrid",
            "https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}"
        ),
        TileServerInfo(
            ServerType.GOOGLE,
            "Google - Satellite",
            "https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}"
        ),
        TileServerInfo(
            ServerType.GOOGLE,
            "Google - Road",
            "https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}"
        ),
        // View available tiles at https://server.arcgisonline.com/arcgis/rest/services/
        // could load these on startup?
        TileServerInfo(
            ServerType.ESRI,
            "Esri - World Imagery (Satellite)",
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
        ),
        TileServerInfo(
            ServerType.ESRI,
            "Esri - World Street Map",
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}"
        ),
        TileServerInfo(
            ServerType.ESRI,
            "Esri - World Topo Map",
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}"
        ),
        TileServerInfo(
            ServerType.ESRI,
            "Esri - World Hillshade Base",
            "https://server.arcgisonline.com/arcgis/rest/services/Elevation/World_Hillshade/MapServer/tile/{z}/{y}/{x}"
        ),
        TileServerInfo(
            ServerType.ESRI,
            "Esri - Voyager",
            "https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png"
        ),
        TileServerInfo(
            ServerType.ESRI,
            "Esri - Dark Matter",
            "https://a.basemaps.cartocdn.com/rastertiles/dark_all/{z}/{x}/{y}.png"
        ),
        TileServerInfo(
            ServerType.ESRI,
            "Esri - Light All",
            "https://a.basemaps.cartocdn.com/rastertiles/light_all/{z}/{x}/{y}.png"
        ),
    )

    fun onServerSelected(tileServer: TileServerInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val device = deviceSelector.currentDevice()
            if (device == null) {
                // todo make this a toast or something better for the user
                snackbarHostState.showSnackbar("no devices selected")
                return@launch
            }

            sendingMessage("Setting tile server") {
                try {
                    currentTileServer.value = tileServer.title

                    try {
                        val req = ChangeTileServer(tileServer = tileServer.url)
                        val response = client.post(req) {
                            contentType(ContentType.Application.Json) // Set content type
                            setBody(req)
                        }
                        response.status.isSuccess()
                    } catch (e: Exception) {
                        println("POST request failed: ${e.message}")
                        snackbarHostState.showSnackbar("Failed to update tile server")
                        return@sendingMessage
                    }

                    connection.send(device, DropTileCache())

                    // todo: tell watch to dump tile cache
                } catch (t: TimeoutCancellationException) {
                    snackbarHostState.showSnackbar("Timed out clearing tile cache")
                    return@sendingMessage
                } catch (t: Throwable) {
                    snackbarHostState.showSnackbar("Failed to send to selected device")
                    return@sendingMessage
                }
                snackbarHostState.showSnackbar("Tile server updated")
            }
        }
    }

    private suspend fun sendingMessage(msg: String, cb: suspend () -> Unit) {
        try {
            viewModelScope.launch(Dispatchers.Main) {
                sendingMessage.value = msg
            }
            cb()
        } catch (t: Throwable) {
            Log.d("stdout", "Failed to do operation: $msg $t")
        } finally {
            viewModelScope.launch(Dispatchers.Main) {
                sendingMessage.value = ""
            }
        }
    }
}
