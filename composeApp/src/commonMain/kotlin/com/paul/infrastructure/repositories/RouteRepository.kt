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

        return gpxLoader.loadGpxFromBytes(file)
    }

    suspend fun getCoordinatesRoute(id: String): CoordinatesRoute? {
        val file = fileHelper.readLocalFile("routes/$id")
        if (file == null) {
            return null
        }

        return CoordinatesRoute.fromBytes(file)
    }

    fun getRoute(id: String): RouteEntry? {
        return routes.find { it.id == id }
    }

    suspend fun deleteRoute(route: IRoute) {
        TODO("delete route")
    }

    suspend fun saveRoute(route: IRoute) {
        fileHelper.writeLocalFile("routes/${route.id}", route.rawBytes())
        val type = when (route) {
            is GpxRoute -> RouteType.GPX
            is CoordinatesRoute -> RouteType.COORDINATES
            else -> throw RuntimeException("unknown route type")
        }
        routes.add(RouteEntry(route.id, route.name(), type))
    }
}