package com.paul.domain

import androidx.compose.material.SnackbarHostState
import com.benasher44.uuid.uuid4
import com.paul.protocol.todevice.DirectionPoint
import com.paul.protocol.todevice.Point
import com.paul.protocol.todevice.Route
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RouteSettings(
    val coordinatesPointLimit: Int,
    val directionsPointLimit: Int,
    val mockDirections: Boolean,
    val showRoutePoints: Boolean = false,
    val useReumannWitkam: Boolean = false,
) {

    companion object {
        val default = RouteSettings(400, 100, false)
    }
}

@Serializable
abstract class IRoute(var id: String = uuid4().toString()) {
    abstract suspend fun toRouteForDevice(snackbarHostState: SnackbarHostState): Route?
    abstract suspend fun toSummary(snackbarHostState: SnackbarHostState): List<Point>
    abstract fun name(): String
    abstract fun rawBytes(): ByteArray
    abstract fun setName(name: String)
    abstract fun hasDirectionInfo(): Boolean

    // dirty dirty hacks
    fun isStrava(): Boolean {
        return id.startsWith("strava:")
    }
}

abstract class GpxRoute : IRoute()

@Serializable
data class DirectionInfo(val index: Int)

@Serializable
data class CoordinatesRoute(
    private var _name: String,
    private val _coordinates: List<Point>,
    private val _directions: List<DirectionInfo> = emptyList() // need default for back compat fromBytes
) :
    IRoute() {
    companion object {
        fun fromBytes(bytes: ByteArray): CoordinatesRoute {
            return Json.decodeFromString<CoordinatesRoute>(bytes.decodeToString())
        }
    }

    override suspend fun toRouteForDevice(snackbarHostState: SnackbarHostState): Route? {
        return Route.prepareForDevice(_name, _coordinates, _directions)
    }

    override suspend fun toSummary(snackbarHostState: SnackbarHostState): List<Point> {
        return Route.summary(_coordinates)
    }

    override fun rawBytes(): ByteArray {
        // easiest way is to write json to the file
        var string = Json.encodeToString(this)
        return string.toByteArray()
    }

    override fun name(): String {
        return _name
    }

    override fun setName(name: String) {
        _name = name
    }

    override fun hasDirectionInfo(): Boolean {
        return _directions.isNotEmpty()
    }

    fun coordinates(): List<Point> {
        return _coordinates
    }
}

@Serializable
enum class RouteType {
    COORDINATES,
    GPX,
}

@Serializable
data class RouteEntry(
    val id: String,
    val name: String,
    val type: RouteType,
    val createdAt: Instant,
    val sizeBytes: Long,
    val hasDirectionInfo: Boolean = false,
    val summary: List<Point>? = null,
) {
    fun summaryToRoute(): Route? {
        if (summary == null) return null

        return Route(name, summary, emptyList())
    }
}