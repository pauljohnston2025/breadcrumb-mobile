package com.paul.protocol.todevice

import com.paul.infrastructure.repositories.RouteRepository
import kotlinx.serialization.Serializable
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

data class RectPoint(val x: Float, val y: Float, val altitude: Float) {
    fun valid(): Boolean {
        return !x.isNaN() && !y.isNaN() && !altitude.isNaN()
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
    val angleDeg: Float,
    val routeIndex: Int
)

class Route(val name: String, var route: List<Point>, var directions: List<DirectionPoint>) :
    Protocol {
    init {
        val routeSettings = RouteRepository.getSettings()

        // hack for perf/memory testing, every point is a direction
//        directions =
//            route.mapIndexed { index, it ->
//                DirectionPoint(
//                    it.latitude,
//                    it.longitude,
//                    0f,
//                    index.toFloat()
//                )
//            }

        // truncate the directions first, we must keep every coordinate that matches the directions index
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
                truncatedDirections.add(direction.copy(routeIndex = direction.routeIndex))
            }
        }
        directions = truncatedDirections


        // truncate our points so we can send them to the device without it crashing/taking too long
        // 1. Create a set of indices that must be kept.
        val directionIndices = directions.map { it.routeIndex }.toSet()

        // 2. Identify indices for points that are not mandatory.
        val otherIndices = route.indices.filter { it !in directionIndices }

        // 3. Calculate how many additional points we can afford to keep.
        val remainingCapacity = routeSettings.coordinatesPointLimit - directionIndices.size

        val finalIndicesToKeep = mutableSetOf<Int>()
        finalIndicesToKeep.addAll(directionIndices) // Add all mandatory direction indices.

        // 4. If there's capacity, sample from the other non-essential points.
        if (remainingCapacity > 0 && otherIndices.isNotEmpty()) {
            val nthPoint =
                Math.ceil(otherIndices.size.toDouble() / remainingCapacity).toInt().coerceAtLeast(1)
            for (i in otherIndices.indices step nthPoint) {
                finalIndicesToKeep.add(otherIndices[i])
            }
        }

        // 5. Build the new route from the selected indices, ensuring the final list is sorted by the original index.
        val sortedIndicesToKeep = finalIndicesToKeep.sorted()
        route = sortedIndicesToKeep.mapNotNull { index -> route.getOrNull(index) }

        // 6. After truncating the route, the `routeIndex` in the directions is now incorrect.
        //    We need to create a map from the old indices to the new indices.
        val oldToNewIndexMap = sortedIndicesToKeep
            .withIndex()
            .associate { (newIndex, oldIndex) -> oldIndex to newIndex }

        // 7. Update the `directions` list with the new, correct `routeIndex`.
        //    Since all `directionIndices` were preserved, every `routeIndex` will exist in our map.
        directions = directions.map { direction ->
            val newIndex = oldToNewIndexMap[direction.routeIndex]
            direction.copy(routeIndex = newIndex!!)
        }
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
            route.mapNotNull { it.convert2XY() },
            directions
        )
    }
}

class Route2(
    private val name: String,
    private val route: List<RectPoint>,
    private val directions: List<DirectionPoint>
) : Protocol {
    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_ROUTE_DATA2
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

        // old watch app ignores the extra data correctly
        val directionData = mutableListOf<Any>()
        for (point in directions) {
            val angleOffset = point.angleDeg.toInt() + 180;
            val packedData = ((
                    angleOffset and 0xFFFF) shl 16) or (point.routeIndex and 0xFFFF)
            directionData.add(packedData)
        }
        data.add(directionData)


        return data
    }
}