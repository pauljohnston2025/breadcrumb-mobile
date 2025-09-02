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
abstract class IRoute(var id: String = uuid4().toString()) {
    abstract suspend fun toRoute(snackbarHostState: SnackbarHostState): Route?
    abstract fun name(): String
    abstract fun rawBytes(): ByteArray
    abstract fun setName(name: String)
}

abstract class GpxRoute : IRoute()

@Serializable
data class CoordinatesRoute(
    private var _name: String,
    private val _coordinates: List<Point>,
    private val _directions: List<DirectionPoint> = emptyList() // need default for back compat fromBytes
) :
    IRoute() {
    companion object {
        fun fromBytes(bytes: ByteArray): CoordinatesRoute {
            return Json.decodeFromString<CoordinatesRoute>(bytes.decodeToString())
        }
    }

    override suspend fun toRoute(snackbarHostState: SnackbarHostState): Route? {
        return Route(_name, _coordinates, _directions)
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
)