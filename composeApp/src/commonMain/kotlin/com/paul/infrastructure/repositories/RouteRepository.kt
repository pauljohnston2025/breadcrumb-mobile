package com.paul.infrastructure.repositories

import androidx.compose.runtime.mutableStateListOf
import com.paul.domain.CoordinatesRoute
import com.paul.domain.GpxRoute
import com.paul.domain.IRoute
import com.paul.domain.RouteEntry
import com.paul.domain.RouteSettings
import com.paul.domain.RouteType
import com.paul.domain.TileServerInfo
import com.paul.infrastructure.repositories.TileServerRepo.Companion.defaultTileServer
import com.paul.infrastructure.repositories.TileServerRepo.Companion.settings
import com.paul.infrastructure.service.IFileHelper
import com.paul.infrastructure.service.IGpxFileLoader
import com.russhwolf.settings.Settings
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class RouteRepository(
    private val fileHelper: IFileHelper,
    private val gpxLoader: IGpxFileLoader,
) {
    val ROUTES_KEY = "ROUTES"
    val settings: Settings = Settings()
    val routes = mutableStateListOf<RouteEntry>()
    private val currentSettings: MutableStateFlow<RouteSettings> = MutableStateFlow(
        getSettings()
    )

    companion object {
        private val SETTINGS_KEY = "ROUTE_SETTINGS"

        fun getSettings(): RouteSettings {
            val routeSettings = settings.getStringOrNull(SETTINGS_KEY)

            if (routeSettings == null) {
                return RouteSettings.default
            }

            return try {
                Json.decodeFromString<RouteSettings>(routeSettings)
            } catch (t: Throwable) {
                // bad encoding, maybe we changed it
                return RouteSettings.default
            }
        }
    }

    init {
        val routesJson = settings.getStringOrNull(ROUTES_KEY)
        if (routesJson != null) {
            try {
                Json.decodeFromString<List<RouteEntry>>(routesJson).forEach {
                    routes.add(it)
                }
            } catch (t: Throwable) {
                Napier.d("failed to hydrate routes items $t")
            }
        }
    }

    fun currentSettingsFlow(): StateFlow<RouteSettings> {
        return currentSettings.asStateFlow()
    }

    suspend fun saveSettings(routeSettings: RouteSettings) {
        var toSave = routeSettings
        if (routeSettings.directionsPointLimit > routeSettings.coordinatesPointLimit) {
            // we must have at least as many coordinates kept, otherwise we cannot send the directions (direction is just an index to an existing coordinate)
            toSave = RouteSettings(
                routeSettings.directionsPointLimit,
                routeSettings.directionsPointLimit
            )
        }
        currentSettings.emit(toSave)
        settings.putString(SETTINGS_KEY, Json.encodeToString(toSave))
    }

    suspend fun getGpxRoute(id: String): GpxRoute? {
        val file = fileHelper.readLocalFile("routes/$id")
        if (file == null) {
            return null
        }

        val route = gpxLoader.loadGpxFromBytes(file)
        // gpxLoader sets a new id each time, should make it persist the id to file like coordinates route does
        route.id = id
        // overlay the name we have (users can edit it)
        var routeEntry = getRouteEntry(id)
        if (routeEntry == null) {
            return null
        }
        route.setName(routeEntry.name)
        return route
    }

    suspend fun getCoordinatesRoute(id: String): CoordinatesRoute? {
        val file = fileHelper.readLocalFile("routes/$id")
        if (file == null) {
            return null
        }

        val route = CoordinatesRoute.fromBytes(file)
        // overlay the name we have (users can edit it)
        var routeEntry = getRouteEntry(id)
        if (routeEntry == null) {
            return null
        }
        route.setName(routeEntry.name)
        return route
    }

    fun getRouteEntry(id: String): RouteEntry? {
        return routes.find { it.id == id }
    }

    suspend fun getRouteI(id: String): IRoute? {
        val routeEntry = getRouteEntry(id)
        if (routeEntry == null) {
            return null
        }

        return when (routeEntry.type) {
            RouteType.GPX -> getGpxRoute(routeEntry.id)
            RouteType.COORDINATES -> getCoordinatesRoute(routeEntry.id)
        }
    }

    suspend fun deleteRoute(routeId: String) {
        fileHelper.delete("routes/${routeId}")
        routes.removeIf { it.id == routeId }
        saveRoutes()
    }

    fun updateRoute(routeId: String, newName: String) {
        val current = getRouteEntry(routeId)
        if (current == null) {
            return
        }
        routes.removeIf { it.id == routeId }
        routes.add(RouteEntry(routeId, newName, current.type, current.createdAt, current.sizeBytes))
        saveRoutes()
    }

    suspend fun saveRoute(route: IRoute) {
        fileHelper.writeLocalFile("routes/${route.id}", route.rawBytes())
        val type = when (route) {
            is GpxRoute -> RouteType.GPX
            is CoordinatesRoute -> RouteType.COORDINATES
            else -> throw RuntimeException("unknown route type")
        }
        routes.add(
            RouteEntry(
                route.id,
                route.name(),
                type,
                Clock.System.now(),
                route.rawBytes().size.toLong()
            )
        )
        saveRoutes()
    }

    private fun saveRoutes() {
        settings.putString(ROUTES_KEY, Json.encodeToString(routes.toList().takeLast(100)))
    }

    suspend fun deleteAll() {
        fileHelper.deleteDir("routes")
        routes.clear()
        saveRoutes()
    }
}