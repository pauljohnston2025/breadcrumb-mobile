package com.paul.viewmodels

import android.util.Log
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.web.ChangeTileServer
import com.paul.infrastructure.web.KtorClient
import com.paul.protocol.todevice.DropTileCache
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ServerType {
    CUSTOM,
    ESRI,
    GOOGLE,
    OPENTOPOMAP,
    OPENSTREETMAP,
}

@Serializable
data class TileServerInfo(
    val serverType: ServerType,
    val title: String,
    val url: String,
    val isCustom: Boolean = false // Flag to identify user-added servers
)

class Settings(
    private val deviceSelector: DeviceSelector,
    private val connection: IConnection,
    private val snackbarHostState: SnackbarHostState
) : ViewModel() {
    companion object {
        val TILE_SERVER_KEY = "TILE_SERVER"
        val CUSTOM_SERVERS_KEY = "CUSTOM_SERVERS_KEY"
        val settings: Settings = Settings()

        fun getTileServerOnStart(): TileServerInfo {
            val default = TileServerInfo(
                ServerType.OPENTOPOMAP,
                "Open Topo Map",
                "https://a.tile.opentopomap.org/{z}/{x}/{y}.png"
            )
            val tileServer = settings.getStringOrNull(TILE_SERVER_KEY)

            if (tileServer == null)
            {
                return default
            }

            return try {
                Json.decodeFromString<TileServerInfo>(tileServer)
            }
            catch (t: Throwable)
            {
                // bad encoding, maybe we changed it
                return default
            }
        }
    }

    private val client = KtorClient.client // Get the singleton client instance

    val sendingMessage: MutableState<String> = mutableStateOf("")

    val currentTileServer: MutableState<String> = mutableStateOf("")

    val availableServers = mutableStateListOf(
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

    fun getCustomServers(): List<TileServerInfo> {
        val customServers = settings.getStringOrNull(CUSTOM_SERVERS_KEY)
        return when (customServers) {
            null -> listOf()
            else -> Json.decodeFromString<List<TileServerInfo>>(customServers)
        }
    }

    init {
        val servers = getCustomServers()
        servers.forEach { availableServers.add(it) }
        currentTileServer.value = getTileServerOnStart().title
    }

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
                    settings.putString(TILE_SERVER_KEY, tileServer.url)

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

    fun onAddCustomServer(tileServer: TileServerInfo) {
        val mutableServers = getCustomServers().toMutableList()
        mutableServers.add(tileServer)

        settings.putString(CUSTOM_SERVERS_KEY, Json.encodeToString(mutableServers))
        availableServers.add(tileServer)
    }

    fun onRemoveCustomServer(tileServer: TileServerInfo) {
        val newServers = getCustomServers().filter { it.isCustom && it.title != tileServer.title }
        settings.putString(CUSTOM_SERVERS_KEY, Json.encodeToString(newServers))
        availableServers.removeIf { it.isCustom && it.title == tileServer.title }
    }
}
