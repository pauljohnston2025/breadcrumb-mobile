package com.paul.infrastructure.repositories

import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.mutableStateListOf
import com.paul.domain.CoordinatesRoute
import com.paul.domain.FitRoute
import com.paul.domain.GpxRoute
import com.paul.domain.IRoute
import com.paul.domain.RouteEntry
import com.paul.domain.RouteSettings
import com.paul.domain.RouteType
import com.paul.infrastructure.repositories.TileServerRepo.Companion.settings
import com.paul.domain.SegmentType
import com.paul.infrastructure.service.IFileHelper
import com.paul.infrastructure.service.IFitFileLoader
import com.paul.infrastructure.service.IGpxFileLoader
import com.paul.protocol.todevice.Point
import com.paul.protocol.todevice.Route
import com.paul.protocol.todevice.Route.Companion.ROUTE_SUMMARY_VERSION
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
    private val fitLoader: IFitFileLoader,
    private val spatialIndexRepository: SpatialIndexRepository,
) {
    companion object {
        private const val TAG = "RouteRepository"
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
                Napier.w("Failed to decode RouteSettings, using defaults", t, tag = TAG)
                return RouteSettings.default
            }
        }
    }

    val ROUTES_KEY = "ROUTES"
    val settings: Settings = Settings()
    val routes = mutableStateListOf<RouteEntry>()
    private val currentSettings: MutableStateFlow<RouteSettings> = MutableStateFlow(
        getSettings()
    )

    init {
        val routesJson = settings.getStringOrNull(ROUTES_KEY)
        if (routesJson != null) {
            try {
                Json.decodeFromString<List<RouteEntry>>(routesJson).forEach {
                    routes.add(it)
                }
            } catch (t: Throwable) {
                Napier.e("failed to hydrate routes items", t, tag = TAG)
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
            toSave = routeSettings.copy(coordinatesPointLimit = routeSettings.directionsPointLimit)
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

    suspend fun getFitRoute(id: String): FitRoute? {
        val file = fileHelper.readLocalFile("routes/$id")
        if (file == null) {
            return null
        }

        val route = fitLoader.loadFitFromBytes(file)
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
            RouteType.FIT -> getFitRoute(routeEntry.id)
        }
    }

    suspend fun deleteRoute(routeId: String) {
        fileHelper.delete("routes/${routeId}")
        routes.removeIf { it.id == routeId }
        saveRoutes()
        spatialIndexRepository.deleteRoute(routeId)
    }

    fun updateRoute(routeId: String, newName: String) {
        val current = getRouteEntry(routeId)
        if (current == null) {
            return
        }
        routes.removeIf { it.id == routeId }
        routes.add(current.copy(name = newName))
        saveRoutes()
    }

    suspend fun saveRoute(route: IRoute) {
        fileHelper.writeLocalFile("routes/${route.id}", route.rawBytes())
        val type = when (route) {
            is GpxRoute -> RouteType.GPX
            is CoordinatesRoute -> RouteType.COORDINATES
            is FitRoute -> RouteType.FIT
            else -> {
                val error = "unknown route type: ${route::class.simpleName}"
                Napier.e(error, tag = TAG)
                throw RuntimeException(error)
            }
        }
        val points = when (route) {
            is CoordinatesRoute -> route.coordinates()
            is GpxRoute -> route.getPoints() ?: emptyList()
            is FitRoute -> route.getPoints()
            else -> emptyList()
        }
        val distance = Route.calculateTotalDistance(points)

        routes.add(
            RouteEntry(
                route.id,
                route.name(),
                type,
                Clock.System.now(),
                route.rawBytes().size.toLong(),
                route.hasDirectionInfo(),
                distanceMeters = distance
            )
        )
        saveRoutes()
        spatialIndexRepository.indexRoute(route.id, points)
    }

    private fun saveRoutes() {
        settings.putString(ROUTES_KEY, Json.encodeToString(routes.toList().takeLast(100)))
    }

    suspend fun deleteAll() {
        fileHelper.deleteDir("routes")
        routes.clear()
        saveRoutes()
        spatialIndexRepository.clear(SegmentType.ROUTE)
    }

    suspend fun updateRouteSummary(id: String, summary: List<Point>, distanceMeters: Float) {
        val current = getRouteEntry(id)
        if (current == null) {
            return
        }

        routes.removeIf { it.id == id }
        routes.add(current.copy(summary = summary, summaryVersion = ROUTE_SUMMARY_VERSION, distanceMeters = distanceMeters))
        saveRoutes()
        spatialIndexRepository.indexRoute(id, summary)
    }

    suspend fun getRouteEntrySummary(route: RouteEntry?, snackbarHostState: SnackbarHostState): Route?
    {
        if (route != null) {
            var summary = route.summaryToRoute()
            if (summary == null || route.summaryVersion != ROUTE_SUMMARY_VERSION) {
                // first time write the summary back and persist it
                val iRoute = getRouteI(route.id)
                if (iRoute != null) {
                    val summaryLine = iRoute.toSummary(snackbarHostState)
                    val points = if (iRoute is CoordinatesRoute) {
                        iRoute.coordinates()
                    } else if (iRoute is GpxRoute) {
                        iRoute.getPoints(snackbarHostState) ?: emptyList()
                    } else if (iRoute is FitRoute) {
                        iRoute.getPoints()
                    } else {
                        emptyList()
                    }
                    val distance = Route.calculateTotalDistance(points)

                    updateRouteSummary(route.id, summaryLine, distance)
                    summary = Route(route.name, summaryLine, emptyList())
                }
            }

            return summary
        }

        return null
    }
}