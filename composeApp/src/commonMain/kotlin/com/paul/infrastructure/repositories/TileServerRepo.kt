package com.paul.infrastructure.repositories

import com.paul.domain.ServerType
import com.paul.domain.TileServerInfo
import com.paul.infrastructure.web.ChangeTileServer
import com.paul.infrastructure.web.KtorClient
import com.paul.infrastructure.web.WebServerController
import com.russhwolf.settings.Settings
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json

class TileServerRepo(private val webServerController: WebServerController) {
    companion object {
        val TILE_SERVER_KEY = "TILE_SERVER"
        val CUSTOM_SERVERS_KEY = "CUSTOM_SERVERS_KEY"
        val TILE_SERVER_ENABLED_KEY = "TILE_SERVER_ENABLED_KEY"
        val settings: Settings = Settings()

        val defaultTileServer = TileServerInfo(
            ServerType.OPENTOPOMAP,
            "Open Topo Map",
            "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
            0,
            15
        )

        fun getTileServerOnStart(): TileServerInfo {
            val tileServer = settings.getStringOrNull(TILE_SERVER_KEY)

            if (tileServer == null) {
                return defaultTileServer
            }

            return try {
                Json.decodeFromString<TileServerInfo>(tileServer)
            } catch (t: Throwable) {
                // bad encoding, maybe we changed it
                return defaultTileServer
            }
        }
    }

    private val client = KtorClient.client // Get the singleton client instance
    private val currentTileServer: MutableStateFlow<TileServerInfo> = MutableStateFlow(
        defaultTileServer
    )
    private val tileServerEnabled: MutableStateFlow<Boolean> = MutableStateFlow(true)

    fun currentServerFlow(): Flow<TileServerInfo> {
        return currentTileServer
    }

    fun tileServerEnabledFlow(): Flow<Boolean> {
        return tileServerEnabled
    }

    fun availableServersFlow(): Flow<List<TileServerInfo>> {
        return availableServers
    }

    // @formatter:off
    val availableServers = MutableStateFlow(listOf(
        TileServerInfo(ServerType.OPENTOPOMAP, "Open Topo Map", "https://a.tile.opentopomap.org/{z}/{x}/{y}.png", 0, 15),
        TileServerInfo(ServerType.GOOGLE, "Google - Hybrid", "https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}", 0, 20),
        TileServerInfo(ServerType.GOOGLE, "Google - Satellite", "https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}", 0, 20),
        TileServerInfo(ServerType.GOOGLE, "Google - Road", "https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}", 0, 20),
        TileServerInfo(ServerType.GOOGLE, "Google - Terain", "https://mt1.google.com/vt/lyrs=p&x={x}&y={y}&z={z}", 0, 20),
        TileServerInfo(ServerType.ESRI, "Esri - World Imagery", "https://server.arcgisonline.com/arcgis/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}", 0, 20),
        TileServerInfo(ServerType.ESRI, "Esri - World Street Map", "https://server.arcgisonline.com/arcgis/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}", 0, 19),
        TileServerInfo(ServerType.ESRI, "Esri - World Topo Map", "https://server.arcgisonline.com/arcgis/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}", 0, 19),
        TileServerInfo(ServerType.ESRI, "Esri - World Transportation", "https://server.arcgisonline.com/arcgis/rest/services/Reference/World_Transportation/MapServer/tile/{z}/{y}/{x}", 0, 15),
        TileServerInfo(ServerType.ESRI, "Esri - World Dark Gray Base", "https://server.arcgisonline.com/arcgis/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}", 0, 16),
        TileServerInfo(ServerType.ESRI, "Esri - World Hillshade", "https://server.arcgisonline.com/arcgis/rest/services/Elevation/World_Hillshade/MapServer/tile/{z}/{y}/{x}", 0, 16),
        TileServerInfo(ServerType.ESRI, "Esri - World Hillshade Dark", "https://server.arcgisonline.com/arcgis/rest/services/Elevation/World_Hillshade_Dark/MapServer/tile/{z}/{y}/{x}", 0, 16),
        TileServerInfo(ServerType.ESRI, "Esri - World Light Gray Base", "https://server.arcgisonline.com/arcgis/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}", 0, 16),
        TileServerInfo(ServerType.ESRI, "Esri - USA Topo Maps", "https://server.arcgisonline.com/arcgis/rest/services/USA_Topo_Maps/MapServer/tile/{z}/{y}/{x}", 0, 15),
        TileServerInfo(ServerType.ESRI, "Esri - World Ocean Base", "https://server.arcgisonline.com/arcgis/rest/services/Ocean/World_Ocean_Base/MapServer/tile/{z}/{y}/{x}", 0, 13),
        TileServerInfo(ServerType.ESRI, "Esri - World Shaded Relief", "https://server.arcgisonline.com/arcgis/rest/services/World_Shaded_Relief/MapServer/tile/{z}/{y}/{x}", 0, 13),
        TileServerInfo(ServerType.ESRI, "Esri - NatGeo World Map", "https://server.arcgisonline.com/arcgis/rest/services/NatGeo_World_Map/MapServer/tile/{z}/{y}/{x}", 0, 12),
        TileServerInfo(ServerType.ESRI, "Esri - World Navigation Charts", "https://server.arcgisonline.com/arcgis/rest/services/Specialty/World_Navigation_Charts/MapServer/tile/{z}/{y}/{x}", 0, 10),
        TileServerInfo(ServerType.ESRI, "Esri - World Physical Map", "https://server.arcgisonline.com/arcgis/rest/services/World_Physical_Map/MapServer/tile/{z}/{y}/{x}", 0, 8),
        TileServerInfo(ServerType.OPENSTREETMAP, "Open Street Map - Cyclosm", "https://a.tile-cyclosm.openstreetmap.fr/cyclosm/{z}/{x}/{y}.png", 0, 12),
        TileServerInfo(ServerType.OPENSTREETMAP, "Open Street Map", "https://tile.openstreetmap.org/{z}/{x}/{y}.png", 0, 12),
    ))
    // @formatter:on

    fun getCustomServers(): List<TileServerInfo> {
        val customServers = settings.getStringOrNull(CUSTOM_SERVERS_KEY)
        return when (customServers) {
            null -> listOf()
            else -> Json.decodeFromString<List<TileServerInfo>>(customServers)
        }
    }

    init {
        val servers = getCustomServers()
        val newList = availableServers.value.toMutableList()
        servers.forEach { newList.add(it) }
        availableServers.tryEmit(newList.toList())
        currentTileServer.tryEmit(getTileServerOnStart())

        val enabled = settings.getBooleanOrNull(TILE_SERVER_ENABLED_KEY)
        // null check for anyone who has never set the setting, defaults to enabled
        tileServerEnabled.tryEmit(enabled == null || enabled)
    }

    suspend fun updateCurrentTileServer(tileServer: TileServerInfo) {
        currentTileServer.emit(tileServer)
        settings.putString(TILE_SERVER_KEY, tileServer.url)

        // should probably be driven from event from currentServerFlow, but oh well
        val req = ChangeTileServer(tileServer = tileServer.url)
        val response = client.post(req) {
            contentType(ContentType.Application.Json) // Set content type
            setBody(req)
        }
        response.status.isSuccess()
    }

    suspend fun onAddCustomServer(tileServer: TileServerInfo) {
        val mutableServers = getCustomServers().toMutableList()
        mutableServers.add(tileServer)

        settings.putString(CUSTOM_SERVERS_KEY, Json.encodeToString(mutableServers))
        val newList = availableServers.value.toMutableList()
        newList.add(tileServer)
        availableServers.emit(newList.toList())
    }

    suspend fun onTileServerEnabledChange(newVal: Boolean) {
        tileServerEnabled.emit(newVal)
        settings.putBoolean(TILE_SERVER_ENABLED_KEY, newVal)
        webServerController.changeTileServerEnabled(newVal)
    }

    fun currentlyEnabled(): Boolean {
        return tileServerEnabled.value
    }

    suspend fun rollBackEnabled(oldVal: Boolean) {
        tileServerEnabled.emit(oldVal)
    }

    suspend fun onRemoveCustomServer(tileServer: TileServerInfo) {
        val newServers = getCustomServers().filter { it.isCustom && it.title != tileServer.title }
        settings.putString(CUSTOM_SERVERS_KEY, Json.encodeToString(newServers))
        var newList = availableServers.value.toMutableList()
        newList.removeIf { it.isCustom && it.title == tileServer.title }
        availableServers.emit(newList.toList())
    }
}