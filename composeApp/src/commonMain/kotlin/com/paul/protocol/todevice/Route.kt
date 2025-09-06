package com.paul.protocol.todevice

import com.paul.infrastructure.repositories.RouteRepository
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

fun Float.toByteArrayBigEndian(): ByteArray {
    val bits = this.toBits()
    return byteArrayOf(
        (bits shr 24).toByte(),
        (bits shr 16).toByte(),
        (bits shr 8).toByte(),
        bits.toByte()
    )
}

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
        val routeSettings = RouteRepository.getSettings()

        // truncate our points so we can send them to the device without it crashing/taking too long
        // the watch has this same cap on points already
        val truncatedPoints = mutableListOf<Point>()
        if (routeSettings.coordinatesPointLimit != 0) {
            var nthPoint =
                Math.ceil(route.size.toDouble() / routeSettings.coordinatesPointLimit).toInt()
            if (nthPoint == 0) {
                // get all if less than 1000
                // should never happen now we are doing ceil()
                nthPoint = 1
            }
            for (i in 0 until route.size step nthPoint) {
                truncatedPoints.add(route[i])
            }
        }
        route = truncatedPoints
        // hack for perf/memory testing, every point is a direction
        directions =
            route.mapIndexed { index, it ->
                DirectionPoint(
                    it.latitude,
                    it.longitude,
                    0f,
                    index.toFloat()
                )
            }


        // assume directions are very minimal, we will still truncate them the same, this could skip important directions though
        val truncatedDirections = mutableListOf<DirectionPoint>()
        if (routeSettings.directionsPointLimit != 0) {
            // only allow much fewer directions so we do not trip watchdog errors or run out of memory
            var nthDirectionPoint =
                Math.ceil(directions.size.toDouble() / routeSettings.directionsPointLimit).toInt()
            if (nthDirectionPoint == 0) {
                // get all if less than 1000
                // should never happen now we are doing ceil()
                nthDirectionPoint = 1
            }
            for (i in 0 until directions.size step nthDirectionPoint) {
                val direction = directions[i]
                truncatedDirections.add(direction.copy(routeIndex = direction.routeIndex / nthDirectionPoint))
            }
        }
        directions = truncatedDirections
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

    fun toV3(): Route3 {
        return Route3(
            name,
            route,
            directions
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun payload(): List<Any> {
        val data = mutableListOf<Any>(name)

        val routeData = mutableListOf<Any>()
        for (point in route) {
            routeData.add(point.x)
            routeData.add(point.y)
            routeData.add(point.altitude)
        }
        data.add(routeData)

        return data
    }
}

// identical to routev2 but adds compressed byte packing for route data
// directions could have been added n a back compat way to v2, but had to break compat for
// compressed byte format
class Route3(
    private val name: String,
    private val route: List<RectPoint>,
    private val directions: List<RectDirectionPoint>
) : Protocol {
    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_ROUTE_DATA3
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun payload(): List<Any> {
        val data = mutableListOf<Any>(name)

        val routeDataByteList = mutableListOf<Byte>()
        for (point in route) {
            routeDataByteList.addAll(point.x.toByteArrayBigEndian().asIterable())
            routeDataByteList.addAll(point.y.toByteArrayBigEndian().asIterable())
            routeDataByteList.addAll(point.altitude.toByteArrayBigEndian().asIterable())
        }
        val routeDataCombinedData = routeDataByteList.toByteArray()
        val routeDataBase64EncodedString = Base64.Default.encode(routeDataCombinedData)
        data.add(routeDataBase64EncodedString)


        val byteList = mutableListOf<Byte>()
        for (point in directions) {
            byteList.addAll(point.x.toByteArrayBigEndian().asIterable())
            byteList.addAll(point.y.toByteArrayBigEndian().asIterable())
            byteList.add((point.angleDeg.toInt() / 2).toByte())
            byteList.addAll(point.routeIndex.toByteArrayBigEndian().asIterable())
        }

        val combinedData = byteList.toByteArray()
        val base64EncodedString = Base64.Default.encode(combinedData)

        data.add(base64EncodedString)

        return data
    }
}




