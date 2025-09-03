package com.paul.protocol.todevice

import com.garmin.monkeybrains.serialization.MonkeyChar
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

data class RectPoint(val x: Float, val y: Float, val altitude: Float) {
    fun valid(): Boolean {
        return !x.isNaN() && !y.isNaN() && !altitude.isNaN()
    }
}

data class RectDirectionPoint(
    val x: Float,
    val y: Float,
    val angleDeg: Float,
    val routeIndex: Float
) {
    fun valid(): Boolean {
        return !x.isNaN() && !y.isNaN() && !angleDeg.isNaN()
    }
}

val _pi360 = PI / 360.0f
val _lonConversion = 20037508.34f / 180.0f
val _pi180 = PI / 180.0f;

@Serializable
data class Point(val latitude: Float, val longitude: Float, val altitude: Float) {
    // this function needs to exactly match RectangularPoint.latLon2xy on the watch app
    fun convert2XY(): RectPoint? {
        val latRect = ((ln(tan((90 + latitude) * _pi360)) / _pi180) * _lonConversion)
        val lonRect = longitude * _lonConversion;

        val point = RectPoint(lonRect, latRect.toFloat(), altitude);
        if (!point.valid()) {
            return null
        }

        return point;
    }
}

@Serializable
data class DirectionPoint(
    val latitude: Float,
    val longitude: Float,
    val angleDeg: Float,
    val routeIndex: Float
) {
    // this function needs to exactly match RectangularPoint.latLon2xy on the watch app
    fun convert2XY(): RectDirectionPoint? {
        val latRect = ((ln(tan((90 + latitude) * _pi360)) / _pi180) * _lonConversion)
        val lonRect = longitude * _lonConversion;

        val point = RectDirectionPoint(lonRect, latRect.toFloat(), angleDeg, routeIndex);
        if (!point.valid()) {
            return null
        }

        return point;
    }
}

class Route(val name: String, var route: List<Point>, var directions: List<DirectionPoint>) :
    Protocol {
    init {
        // truncate our points so we can send them to the device without it crashing/taking too long
        // the watch has this same cap on points already
        val truncatedPoints = mutableListOf<Point>()
        var nthPoint = Math.ceil(route.size / 400.0).toInt()
        if (nthPoint == 0) {
            // get all if less than 1000
            // should never happen now we are doing ceil()
            nthPoint = 1
        }
        for (i in 0 until route.size step nthPoint) {
            truncatedPoints.add(route[i])
        }
        route = truncatedPoints
        // hack for perf/memory testing, every point is a direction
        directions =
            route.mapIndexed { index, it -> DirectionPoint(it.latitude, it.longitude, 0f, index.toFloat()) }


        // assume directions are very minimal, we will still truncate them the same, this could skip important directions though
        val truncatedDirections = mutableListOf<DirectionPoint>()
        // only allow much fewer directions so we do not trip watchdog errors or run out of memory
        var nthDirectionPoint = Math.ceil(directions.size / 100.0).toInt()
        if (nthDirectionPoint == 0) {
            // get all if less than 1000
            // should never happen now we are doing ceil()
            nthDirectionPoint = 1
        }
        for (i in 0 until directions.size step nthDirectionPoint) {
            truncatedDirections.add(directions[i])
        }
        directions = truncatedDirections
        directions = directions.map { it.copy(routeIndex = it.routeIndex / nthPoint) }
    }

    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_ROUTE_DATA
    }

    override fun payload(): List<Any> {
        val data = mutableListOf<Any>(name)

        for (point in route) {
            data.add(point.latitude)
            data.add(point.longitude)
            data.add(point.altitude)
        }
        // v1 route protocol did not accept directions, so don't add them

        return data
    }

    fun toV2(): Route2 {
        return Route2(
            name,
            route.map { it.convert2XY() }.mapNotNull { it },
            directions.map { it.convert2XY() }.mapNotNull { it })
    }
}

class Route2(
    private val name: String,
    private val route: List<RectPoint>,
    private val directions: List<RectDirectionPoint>
) : Protocol {
    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_ROUTE_DATA2
    }

    override fun payload(): List<Any> {
        val data = mutableListOf<Any>(name)

        val routeData = mutableListOf<Any>()
        for (point in route) {
            routeData.add(point.x)
            routeData.add(point.y)
            routeData.add(point.altitude)
        }
        data.add(routeData)
        val directionData = mutableListOf<Any>()
        for (point in directions) {
            directionData.add(point.x)
            directionData.add(point.y)
            // char can only store -128 to 127
            // so convert our -180 to 180 range down by 2
            // this save 3 bytes on the device per direction, significant
            directionData.add(MonkeyChar(point.angleDeg.toInt() / 2))
            directionData.add(point.routeIndex)
        }
        data.add(directionData)

        return data
    }
}