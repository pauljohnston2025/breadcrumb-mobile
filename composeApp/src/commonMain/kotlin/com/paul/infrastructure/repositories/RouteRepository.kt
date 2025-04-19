package com.paul.infrastructure.repositories

import androidx.compose.runtime.mutableStateListOf
import com.paul.domain.CoordinatesRoute
import com.paul.domain.GpxRoute
import com.paul.domain.IRoute
import com.paul.domain.RouteEntry
import com.paul.domain.RouteType
import com.paul.infrastructure.service.IFileHelper
import com.paul.infrastructure.service.IGpxFileLoader
import com.russhwolf.settings.Settings
import io.github.aakira.napier.Napier
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class RouteRepository(
    private val fileHelper: IFileHelper,
    private val gpxLoader: IGpxFileLoader,
) {
    val ROUTES_KEY = "ROUTES"
    val settings: Settings = Settings()
    val routes = mutableStateListOf<RouteEntry>()

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

    fun deleteRoute(routeId: String) {
        // todo: delete the file too
        routes.removeIf { it.id == routeId }
        saveRoutes()
    }

    fun updateRoute(routeId: String, newName: String) {
        val current = getRouteEntry(routeId)
        if (current == null) {
            return
        }
        deleteRoute(routeId) // we need to replace it with a new object, or we will not be able to render
        routes.add(RouteEntry(routeId, newName, current.type, current.createdAt))
        saveRoutes()
        saveRoutes()
    }

    suspend fun saveRoute(route: IRoute) {
        fileHelper.writeLocalFile("routes/${route.id}", route.rawBytes())
        val type = when (route) {
            is GpxRoute -> RouteType.GPX
            is CoordinatesRoute -> RouteType.COORDINATES
            else -> throw RuntimeException("unknown route type")
        }
        routes.add(RouteEntry(route.id, route.name(), type, Clock.System.now()))
        saveRoutes()
    }

    private fun saveRoutes() {
        settings.putString(ROUTES_KEY, Json.encodeToString(routes.toList().takeLast(100)))
    }
}