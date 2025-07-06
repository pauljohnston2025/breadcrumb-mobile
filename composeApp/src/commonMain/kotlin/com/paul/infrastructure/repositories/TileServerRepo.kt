package com.paul.infrastructure.repositories

import com.paul.domain.ServerType
import com.paul.domain.TileServerInfo
import com.paul.infrastructure.web.ChangeAuthToken
import com.paul.infrastructure.web.ChangeTileServer
import com.paul.infrastructure.web.ChangeTileType
import com.paul.infrastructure.web.KtorClient
import com.paul.infrastructure.web.TileType
import com.paul.infrastructure.web.WebServerController
import com.russhwolf.settings.Settings
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class TileServerRepo(
    private val webServerController: WebServerController,
    private val tileRepo: ITileRepository, // the one running for the maps page (we update the webserver one through web calls)
) {
    companion object {
        val TILE_SERVER_KEY = "TILE_SERVER"
        val TILE_TYPE_KEY = "TILE_TYPE"
        val AUTH_TOKEN_KEY = "AUTH_TOKEN"
        val CUSTOM_SERVERS_KEY = "CUSTOM_SERVERS_KEY"
        val TILE_SERVER_ENABLED_KEY = "TILE_SERVER_ENABLED_KEY"
        val settings: Settings = Settings()

        val defaultTileType = TileType.TILE_DATA_TYPE_64_COLOUR
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

        fun getTileTypeOnStart(): TileType {
            val tileType = settings.getStringOrNull(TILE_TYPE_KEY)

            if (tileType == null) {
                return defaultTileType
            }

            return try {
                Json.decodeFromString<TileType>(tileType)
            } catch (t: Throwable) {
                // bad encoding, maybe we changed it
                return defaultTileType
            }
        }

        fun getAuthTokenOnStart(): String {
            val authToken = settings.getStringOrNull(AUTH_TOKEN_KEY)
            if (authToken == null) {
                return ""
            }

            return authToken
        }
    }

    private val client = KtorClient.client // Get the singleton client instance
    private val currentTileServer: MutableStateFlow<TileServerInfo> = MutableStateFlow(
        defaultTileServer
    )
    private val currentTileType: MutableStateFlow<TileType> = MutableStateFlow(
        defaultTileType
    )
    private val tileServerEnabled: MutableStateFlow<Boolean> = MutableStateFlow(true)
    private val currentAuthToken: MutableStateFlow<String> = MutableStateFlow("")

    fun currentServerFlow(): StateFlow<TileServerInfo> {
        return currentTileServer.asStateFlow()
    }

    fun currentTileTypeFlow(): StateFlow<TileType> {
        return currentTileType.asStateFlow()
    }

    fun currentTokenFlow(): StateFlow<String> {
        return currentAuthToken.asStateFlow()
    }

    fun tileServerEnabledFlow(): Flow<Boolean> {
        return tileServerEnabled
    }

    fun authTokenFlow(): Flow<String> {
        return currentAuthToken
    }

    fun availableServersFlow(): Flow<List<TileServerInfo>> {
        return availableServers
    }

    fun availableTileTypesFlow(): Flow<List<TileType>> {
        return availableTileTypes
    }

    // @formatter:off
    val availableTileTypes = MutableStateFlow(TileType.entries)
    val availableServers = MutableStateFlow(listOf(
        // opentopo map is weird, docs say up to layer 17, but their own website fails to get layer 16/17 sometimes
        // I have managed to get some layer 16/17 tiles but it almost always times out or errors
        // might be some rate limit thing on lower levels
        TileServerInfo(ServerType.OPENTOPOMAP, "Open Topo Map", "https://a.tile.opentopomap.org/{z}/{x}/{y}.png", 0, 15),
        TileServerInfo(ServerType.GOOGLE, "Google - Hybrid", "https://mt1.google.com/vt/lyrs=y&x={x}&y={y}&z={z}", 0, 20),
        TileServerInfo(ServerType.GOOGLE, "Google - Satellite", "https://mt1.google.com/vt/lyrs=s&x={x}&y={y}&z={z}", 0, 20),
        TileServerInfo(ServerType.GOOGLE, "Google - Road", "https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}", 0, 20),
        TileServerInfo(ServerType.GOOGLE, "Google - Terain", "https://mt1.google.com/vt/lyrs=p&x={x}&y={y}&z={z}", 0, 20),
        TileServerInfo(ServerType.ESRI, "Esri - World Imagery", "https://server.arcgisonline.com/arcgis/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}?blankTile=false", 0, 20),
        TileServerInfo(ServerType.ESRI, "Esri - World Street Map", "https://server.arcgisonline.com/arcgis/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}?blankTile=false", 0, 19),
        TileServerInfo(ServerType.ESRI, "Esri - World Topo Map", "https://server.arcgisonline.com/arcgis/rest/services/World_Topo_Map/MapServer/tile/{z}/{y}/{x}?blankTile=false", 0, 19),
        TileServerInfo(ServerType.ESRI, "Esri - World Transportation", "https://server.arcgisonline.com/arcgis/rest/services/Reference/World_Transportation/MapServer/tile/{z}/{y}/{x}?blankTile=false", 0, 15),
        TileServerInfo(ServerType.ESRI, "Esri - World Dark Gray Base", "https://server.arcgisonline.com/arcgis/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}?blankTile=false", 0, 16),
        TileServerInfo(ServerType.ESRI, "Esri - World Hillshade", "https://server.arcgisonline.com/arcgis/rest/services/Elevation/World_Hillshade/MapServer/tile/{z}/{y}/{x}?blankTile=false", 0, 16),
        TileServerInfo(ServerType.ESRI, "Esri - World Hillshade Dark", "https://server.arcgisonline.com/arcgis/rest/services/Elevation/World_Hillshade_Dark/MapServer/tile/{z}/{y}/{x}?blankTile=false", 0, 16),
        TileServerInfo(ServerType.ESRI, "Esri - World Light Gray Base", "https://server.arcgisonline.com/arcgis/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}?blankTile=false", 0, 16),
        TileServerInfo(ServerType.ESRI, "Esri - USA Topo Maps", "https://server.arcgisonline.com/arcgis/rest/services/USA_Topo_Maps/MapServer/tile/{z}/{y}/{x}?blankTile=false", 0, 15),
        TileServerInfo(ServerType.ESRI, "Esri - World Ocean Base", "https://server.arcgisonline.com/arcgis/rest/services/Ocean/World_Ocean_Base/MapServer/tile/{z}/{y}/{x}?blankTile=false", 0, 13),
        TileServerInfo(ServerType.ESRI, "Esri - World Shaded Relief", "https://server.arcgisonline.com/arcgis/rest/services/World_Shaded_Relief/MapServer/tile/{z}/{y}/{x}?blankTile=false", 0, 13),
        TileServerInfo(ServerType.ESRI, "Esri - NatGeo World Map", "https://server.arcgisonline.com/arcgis/rest/services/NatGeo_World_Map/MapServer/tile/{z}/{y}/{x}?blankTile=false", 0, 12),
        TileServerInfo(ServerType.ESRI, "Esri - World Navigation Charts", "https://server.arcgisonline.com/arcgis/rest/services/Specialty/World_Navigation_Charts/MapServer/tile/{z}/{y}/{x}?blankTile=false", 0, 10),
        TileServerInfo(ServerType.ESRI, "Esri - World Physical Map", "https://server.arcgisonline.com/arcgis/rest/services/World_Physical_Map/MapServer/tile/{z}/{y}/{x}?blankTile=false", 0, 8),
        TileServerInfo(ServerType.OPENSTREETMAP, "Open Street Map - Cyclosm", "https://a.tile-cyclosm.openstreetmap.fr/cyclosm/{z}/{x}/{y}.png", 0, 20),
        TileServerInfo(ServerType.OPENSTREETMAP, "Open Street Map", "https://tile.openstreetmap.org/{z}/{x}/{y}.png", 0, 19),
        TileServerInfo(ServerType.STADIA, "Stadia - Alidade Smooth (auth required)", "https://tiles.stadiamaps.com/tiles/alidade_smooth/{z}/{x}/{y}.png?api_key={authToken}", 0, 20),
        TileServerInfo(ServerType.STADIA, "Stadia - Alidade Smooth Dark (auth required)", "https://tiles.stadiamaps.com/tiles/alidade_smooth_dark/{z}/{x}/{y}.png?api_key={authToken}", 0, 20),
        TileServerInfo(ServerType.STADIA, "Stadia - Outdoors (auth required)", "https://tiles.stadiamaps.com/tiles/outdoors/{z}/{x}/{y}.png?api_key={authToken}", 0, 20),
        TileServerInfo(ServerType.STADIA, "Stadia - Stamen Toner (auth required)", "https://tiles.stadiamaps.com/tiles/stamen_toner/{z}/{x}/{y}.png?api_key={authToken}", 0, 20),
        TileServerInfo(ServerType.STADIA, "Stadia - Stamen Toner Lite (auth required)", "https://tiles.stadiamaps.com/tiles/stamen_toner_lite/{z}/{x}/{y}.png?api_key={authToken}", 0, 20),
        TileServerInfo(ServerType.STADIA, "Stadia - Stamen Terrain (auth required)", "https://tiles.stadiamaps.com/tiles/stamen_terrain/{z}/{x}/{y}.png?api_key={authToken}", 0, 20),
        TileServerInfo(ServerType.STADIA, "Stadia - Stamen Watercolor (auth required)", "https://tiles.stadiamaps.com/tiles/stamen_watercolor/{z}/{x}/{y}.jpg?api_key={authToken}", 0, 16),
        TileServerInfo(ServerType.STADIA, "Stadia - OSM Bright (auth required)", "https://tiles.stadiamaps.com/tiles/osm_bright/{z}/{x}/{y}.png?api_key={authToken}", 0, 20),
        TileServerInfo(ServerType.CARTO, "Carto - Voyager", "https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png", 0, 20),
        TileServerInfo(ServerType.CARTO, "Carto - Dark Matter", "https://a.basemaps.cartocdn.com/rastertiles/dark_all/{z}/{x}/{y}.png", 0, 20),
        TileServerInfo(ServerType.CARTO, "Carto - Light All", "https://a.basemaps.cartocdn.com/rastertiles/light_all/{z}/{x}/{y}.png", 0, 20),
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
        availableServers.value = newList.toList()
        currentTileServer.value = getTileServerOnStart()
        currentTileType.value = getTileTypeOnStart()
        currentAuthToken.value = getAuthTokenOnStart()

        val enabled = settings.getBooleanOrNull(TILE_SERVER_ENABLED_KEY)
        // null check for anyone who has never set the setting, defaults to enabled
        tileServerEnabled.value = (enabled == null || enabled)
    }

    suspend fun updateCurrentTileServer(tileServer: TileServerInfo) {
        tileRepo.setTileServer(tileServer) // app map view

        if (currentlyEnabled()) {
            // webserver
            // should probably be driven from event from currentServerFlow, but oh well
            val req = ChangeTileServer(tileServer = tileServer)
            val response = client.post(req) {
                contentType(ContentType.Application.Json) // Set content type
                setBody(req)
            }
            response.status.isSuccess()
        }


        currentTileServer.emit(tileServer)
        settings.putString(TILE_SERVER_KEY, Json.encodeToString(tileServer))
    }

    suspend fun updateCurrentTileType(tileType: TileType) {
        tileRepo.updateTileType(tileType) // app map view

        if (currentlyEnabled()) {
            // webserver
            // should probably be driven from event from currentTypeFlow, but oh well
            val req = ChangeTileType(tileType = tileType)
            val response = client.post(req) {
                contentType(ContentType.Application.Json) // Set content type
                setBody(req)
            }
            response.status.isSuccess()
        }


        currentTileType.emit(tileType)
        settings.putString(TILE_TYPE_KEY, Json.encodeToString(tileType))
    }

    suspend fun updateAuthToken(authToken: String) {
        tileRepo.setAuthToken(authToken) // app map view

        if (currentlyEnabled()) {
            // webserver
            // should probably be driven from event from currentServerFlow, but oh well
            val req = ChangeAuthToken(authToken = authToken)
            val response = client.post(req) {
                contentType(ContentType.Application.Json) // Set content type
                setBody(req)
            }
            response.status.isSuccess()
        }

        currentAuthToken.emit(authToken)
        settings.putString(AUTH_TOKEN_KEY, authToken)
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

    fun nameFromId(id: String): String? {
        val tileServer = availableServers.value.find { it.id == id }
        if (tileServer == null) {
            return null
        }

        return tileServer.title
    }

    fun get(id: String): TileServerInfo? {
        return availableServers.value.find { it.id == id }
    }
}