package com.paul.domain

import androidx.compose.material.SnackbarHostState
import com.benasher44.uuid.uuid4
import com.paul.protocol.todevice.Point
import com.paul.protocol.todevice.Route
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
abstract class IRoute(val id: String = uuid4().toString()) {
    abstract suspend fun toRoute(snackbarHostState: SnackbarHostState): Route?
    abstract fun name(): String
    abstract fun rawBytes(): ByteArray
}

abstract class GpxRoute : IRoute() {
}

@Serializable
data class CoordinatesRoute(private val _name: String, private val _coordinates: List<Point>) : IRoute() {
    companion object {
        fun fromBytes(bytes: ByteArray): CoordinatesRoute
        {
            return Json.decodeFromString<CoordinatesRoute>(bytes.decodeToString())
        }
    }

    override suspend fun toRoute(snackbarHostState: SnackbarHostState): Route? {
        return Route(_name, _coordinates)
    }

    override fun rawBytes(): ByteArray
    {
        // easiest way is to write json to the file
        var string = Json.encodeToString(this)
        return string.toByteArray()
    }

    override fun name(): String {
        return _name
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
)