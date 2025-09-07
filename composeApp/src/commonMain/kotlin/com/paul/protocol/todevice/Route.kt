package com.paul.protocol.todevice

import com.paul.domain.DirectionInfo
import com.paul.infrastructure.repositories.RouteRepository
import kotlinx.serialization.Serializable
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
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

/**
 * Calculates the dominant bearing of a path segment.
 * @param segment The list of coordinates representing the path segment.
 * @param analysisDistance The distance (in meters) from the end of the segment to analyze.
 * @param fromEnd If true, the analysis is performed on the last part of the segment (for incoming paths).
 *                If false, it's on the first part (for outgoing paths).
 * @return The dominant bearing in degrees, or null if it cannot be determined.
 */
private fun calculateDominantBearing(
    segment: List<Point>,
    analysisDistance: Double,
    fromEnd: Boolean
): Double? {
    if (segment.size < 2) {
        return null // Not enough points to determine a bearing
    }

    val relevantBearings = mutableListOf<Double>()
    var accumulatedDistance = 0.0

    if (fromEnd) {
        // Analyze the end of the segment (approaching a turn)
        for (i in segment.size - 2 downTo 0) {
            val point1 = segment[i]
            val point2 = segment[i + 1]
            val distance = calculateDistanceInMeters(point1, point2)
            if (accumulatedDistance < analysisDistance) {
                relevantBearings.add(calculateBearing(point1, point2))
                accumulatedDistance += distance
            } else {
                break
            }
        }
    } else {
        // Analyze the start of the segment (leaving a turn)
        for (i in 0 until segment.size - 1) {
            val point1 = segment[i]
            val point2 = segment[i + 1]
            val distance = calculateDistanceInMeters(point1, point2)
            if (accumulatedDistance < analysisDistance) {
                relevantBearings.add(calculateBearing(point1, point2))
                accumulatedDistance += distance
            } else {
                break
            }
        }
    }

    return if (relevantBearings.isNotEmpty()) relevantBearings.average() else null
}

/**
 * Calculates the initial bearing (in degrees) from a start coordinate to an end coordinate.
 */
private fun calculateBearing(start: Point, end: Point): Double {
    val lat1 = Math.toRadians(start.latitude.toDouble())
    val lon1 = Math.toRadians(start.longitude.toDouble())
    val lat2 = Math.toRadians(end.latitude.toDouble())
    val lon2 = Math.toRadians(end.longitude.toDouble())

    val deltaLon = lon2 - lon1

    val y = sin(deltaLon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)

    val initialBearing = Math.toDegrees(atan2(y, x))
    return (initialBearing + 360) % 360 // Normalize to a 0-360 degree range
}

/**
 * Calculates the great-circle distance between two points on Earth using the Haversine formula.
 * @return The distance in meters.
 */
private fun calculateDistanceInMeters(
    start: Point,
    end: Point
): Double {
    val earthRadius = 6371e3 // in meters

    val lat1Rad = Math.toRadians(start.latitude.toDouble())
    val lat2Rad = Math.toRadians(end.latitude.toDouble())
    val deltaLat = Math.toRadians((end.latitude - start.latitude).toDouble())
    val deltaLon = Math.toRadians((end.longitude - start.longitude).toDouble())

    val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1Rad) * cos(lat2Rad) *
            sin(deltaLon / 2) * sin(deltaLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return earthRadius * c
}

// The distance (in meters) to look ahead and behind from a turn point
// to identify the path segment for analysis.
private const val BEARING_LOOKAROUND_METERS = 100.0

// The final portion of the path segment (in meters) to analyze for a stable bearing.
private const val BEARING_ANALYSIS_SEGMENT_METERS = 30.0

// The distance from the start or end of the route within which to ignore direction points.
private const val IGNORE_EDGE_METERS = 30.0

fun processDirectionInfo(
    coordinates: List<Point>,
    directions: List<DirectionInfo>
): List<DirectionPoint> {
    if (coordinates.size < 2) return emptyList()

    val startPoint = coordinates.first()
    val endPoint = coordinates.last()

    // Filter out directions that are too close to the absolute start or end of the route,
    // as these are often just initial orientation or arrival points that don't require a turn notification.
    val filteredDirections = directions.filter { direction ->
        // Ensure the index is valid to avoid crashes
        if (direction.index >= coordinates.size) return@filter false

        val point = coordinates[direction.index]
        val distanceFromStart = calculateDistanceInMeters(startPoint, point)
        val distanceFromEnd = calculateDistanceInMeters(endPoint, point)

        // Keep the point only if it's sufficiently far from both the start and the end.
        distanceFromStart > IGNORE_EDGE_METERS && distanceFromEnd > IGNORE_EDGE_METERS
    }

    // Map the filtered direction indices to DirectionPoints with calculated turn angles
    return filteredDirections.map { direction ->
        val turnIndex = direction.index
        val currentPoint = coordinates[turnIndex]

        var turnAngle = 0.0

        // 1. Identify the incoming and outgoing path segments around the turn.
        val incomingSegment = mutableListOf<Point>()
        for (i in turnIndex - 1 downTo 0) {
            if (calculateDistanceInMeters(
                    coordinates[i],
                    currentPoint
                ) > BEARING_LOOKAROUND_METERS
            ) {
                break
            }
            incomingSegment.add(0, coordinates[i])
        }
        incomingSegment.add(currentPoint) // Add the turn point to the end of the incoming segment

        val outgoingSegment = mutableListOf<Point>()
        outgoingSegment.add(currentPoint) // Add the turn point to the start of the outgoing segment
        for (i in turnIndex + 1 until coordinates.size) {
            if (calculateDistanceInMeters(
                    coordinates[i],
                    currentPoint
                ) > BEARING_LOOKAROUND_METERS
            ) {
                break
            }
            outgoingSegment.add(coordinates[i])
        }

        // 2. Calculate the dominant bearing for both segments.
        val bearingIn =
            calculateDominantBearing(incomingSegment, BEARING_ANALYSIS_SEGMENT_METERS, true)
        val bearingOut =
            calculateDominantBearing(outgoingSegment, BEARING_ANALYSIS_SEGMENT_METERS, false)


        // Ensure we have valid bearings to calculate the turn angle
        if (bearingIn != null && bearingOut != null) {
            turnAngle = bearingOut - bearingIn

            // Normalize angle to be between -180 (left) and 180 (right)
            if (turnAngle <= -180) {
                turnAngle += 360
            }
            if (turnAngle > 180) {
                turnAngle -= 360
            }
        }

        DirectionPoint(turnAngle.toFloat(), turnIndex)
    }
}

class Route(val name: String, var route: List<Point>, directionsIn: List<DirectionInfo>) :
    Protocol {
    private var directions = processDirectionInfo(route, directionsIn)

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