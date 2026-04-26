package com.paul.protocol.todevice

import com.paul.domain.DirectionInfo
import com.paul.domain.RouteSettings
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
 * Calculates the dominant bearing of a path segment by determining the direction
 * of a single vector over a specified distance. This method is more robust against
 * GPS noise and curvature than averaging bearings of smaller segments.
 *
 * @param segment The list of coordinates representing the path segment.
 * @param analysisDistance The distance (in meters) from the start or end of the segment to analyze.
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

    if (fromEnd) {
        // Analyze the end of the segment (approaching a turn).
        val endPoint = segment.last()
        var accumulatedDistance = 0.0
        var startOfAnalysis: Point? = null
        var analysisIndex = -1

        // Find a point that is approximately analysisDistance away from the end.
        for (i in segment.size - 2 downTo 0) {
            accumulatedDistance += calculateDistanceInMeters(segment[i], segment[i + 1])
            if (accumulatedDistance >= analysisDistance) {
                startOfAnalysis = segment[i]
                analysisIndex = i
                break
            }
        }

        // To improve stability with sparse coordinates, if the analysis only used the point
        // immediately adjacent to the turn, and a point further back exists, use it instead.
        if (analysisIndex == segment.size - 2 && segment.size > 2) {
            startOfAnalysis = segment[segment.size - 3]
        }

        // If the entire segment is shorter than the analysis distance, use its first point.
        val effectiveStartPoint = startOfAnalysis ?: segment.first()

        return if (effectiveStartPoint != endPoint) {
            calculateBearing(effectiveStartPoint, endPoint)
        } else {
            null
        }

    } else {
        // Analyze the start of the segment (leaving a turn).
        val startPoint = segment.first()
        var accumulatedDistance = 0.0
        var endOfAnalysis: Point? = null
        var analysisIndex = -1

        // Find a point that is approximately analysisDistance away from the start.
        for (i in 0 until segment.size - 1) {
            accumulatedDistance += calculateDistanceInMeters(segment[i], segment[i + 1])
            if (accumulatedDistance >= analysisDistance) {
                endOfAnalysis = segment[i + 1]
                analysisIndex = i + 1
                break
            }
        }

        // To improve stability with sparse coordinates, if the analysis only used the point
        // immediately after the turn, and a point further ahead exists, use it instead.
        if (analysisIndex == 1 && segment.size > 2) {
            endOfAnalysis = segment[2]
        }

        // If the entire segment is shorter than the analysis distance, use its last point.
        val effectiveEndPoint = endOfAnalysis ?: segment.last()

        return if (startPoint != effectiveEndPoint) {
            calculateBearing(startPoint, effectiveEndPoint)
        } else {
            null
        }
    }
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

// The minimum angle change (in degrees) to be considered a turn for mock directions.
private const val ANGLE_CHANGE_THRESHOLD_DEGREES = 45.0


fun processDirectionInfo(
    routeSettings: RouteSettings,
    coordinates: List<Point>,
    directions: List<DirectionInfo>
): List<DirectionPoint> {
    if (coordinates.size < 2) return emptyList()

    var effectiveDirections = directions

    // If directions are empty, generate mock directions by analyzing the track's geometry.
    if (routeSettings.mockDirections) {
        if (directions.isEmpty() && coordinates.size > 2) {
            val mockDirections = mutableListOf<DirectionInfo>()
            // The loop condition `i < coordinates.size - 1` already ensures we don't process
            // the very first or last point as a turn.
            var i = 1
            while (i < coordinates.size - 1) {
                val turnIndex = i
                val currentPoint = coordinates[turnIndex]

                // 1. Identify incoming and outgoing path segments around the potential turn point.
                val incomingSegment = mutableListOf<Point>()
                // SAFE ACCESS: The loop condition guarantees turnIndex > 0.
                incomingSegment.add(coordinates[turnIndex - 1])
                for (j in turnIndex - 2 downTo 0) {
                    if (calculateDistanceInMeters(
                            coordinates[j],
                            currentPoint
                        ) > BEARING_LOOKAROUND_METERS
                    ) {
                        break
                    }
                    incomingSegment.add(0, coordinates[j]) // Prepending to keep order
                }
                incomingSegment.add(currentPoint)

                val outgoingSegment = mutableListOf<Point>()
                outgoingSegment.add(currentPoint)
                // SAFE ACCESS: The loop condition guarantees turnIndex < coordinates.size - 1.
                outgoingSegment.add(coordinates[turnIndex + 1])
                for (j in turnIndex + 2 until coordinates.size) {
                    if (calculateDistanceInMeters(
                            coordinates[j],
                            currentPoint
                        ) > BEARING_LOOKAROUND_METERS
                    ) {
                        break
                    }
                    outgoingSegment.add(coordinates[j])
                }

                // 2. Calculate the dominant bearing for both segments.
                val bearingIn =
                    calculateDominantBearing(incomingSegment, BEARING_ANALYSIS_SEGMENT_METERS, true)
                val bearingOut =
                    calculateDominantBearing(
                        outgoingSegment,
                        BEARING_ANALYSIS_SEGMENT_METERS,
                        false
                    )

                if (bearingIn != null && bearingOut != null) {
                    var turnAngle = bearingOut - bearingIn
                    // Normalize angle between -180 and 180
                    if (turnAngle <= -180) turnAngle += 360
                    if (turnAngle > 180) turnAngle -= 360

                    if (kotlin.math.abs(turnAngle) > ANGLE_CHANGE_THRESHOLD_DEGREES) {
                        mockDirections.add(DirectionInfo(index = turnIndex))

                        // Advance index past this turn to avoid detecting it multiple times.
                        val turnPoint = coordinates[turnIndex]
                        var nextIndex = turnIndex + 1
                        while (nextIndex < coordinates.size - 1) {
                            if (calculateDistanceInMeters(
                                    turnPoint,
                                    coordinates[nextIndex]
                                ) > BEARING_ANALYSIS_SEGMENT_METERS
                            ) {
                                break
                            }
                            nextIndex++
                        }
                        i = nextIndex
                        continue // Continue to the next iteration of the while loop
                    }
                }
                i++ // Move to the next point if no significant turn was found
            }
            effectiveDirections = mockDirections
        }
    }

    val startPoint = coordinates.first()
    val endPoint = coordinates.last()

    // Filter out directions that are too close to the absolute start or end of the route.
    val filteredDirections = effectiveDirections.filter { direction ->
        if (direction.index >= coordinates.size) return@filter false

        val point = coordinates[direction.index]
        val distanceFromStart = calculateDistanceInMeters(startPoint, point)
        val distanceFromEnd = calculateDistanceInMeters(endPoint, point)

        distanceFromStart > IGNORE_EDGE_METERS && distanceFromEnd > IGNORE_EDGE_METERS
    }

    // Map the filtered direction indices to DirectionPoints with calculated turn angles.
    return filteredDirections.map { direction ->
        val turnIndex = direction.index

        // SAFETY GUARD: Explicitly check if the index is at the start or end of the array.
        // A turn angle cannot be calculated for the first or last point.
        if (turnIndex <= 0 || turnIndex >= coordinates.size - 1) {
            return@map DirectionPoint(0.0f, turnIndex)
        }

        val currentPoint = coordinates[turnIndex]
        var turnAngle = 0.0

        // 1. Identify the incoming and outgoing path segments around the turn.
        val incomingSegment = mutableListOf<Point>()
        // SAFE ACCESS: The guard above ensures turnIndex > 0.
        incomingSegment.add(coordinates[turnIndex - 1])
        for (i in turnIndex - 2 downTo 0) {
            if (calculateDistanceInMeters(
                    coordinates[i],
                    currentPoint
                ) > BEARING_LOOKAROUND_METERS
            ) {
                break
            }
            incomingSegment.add(0, coordinates[i]) // Prepending to keep order
        }
        incomingSegment.add(currentPoint)

        val outgoingSegment = mutableListOf<Point>()
        outgoingSegment.add(currentPoint)
        // SAFE ACCESS: The guard above ensures turnIndex < coordinates.size - 1.
        outgoingSegment.add(coordinates[turnIndex + 1])
        for (i in turnIndex + 2 until coordinates.size) {
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

// todo make this just a dto and do all the complex init logic in some other place so that we can load them faster for RouteMiniMap
// maybe a companion object method?
class Route(
    val name: String,
    var route: List<Point>,
    directionsIn: List<DirectionInfo>,
    routeSettings: RouteSettings
) :
    Protocol {

    constructor(name: String, route: List<Point>, directionsIn: List<DirectionInfo>) : this(
        name,
        route,
        directionsIn,
        RouteRepository.getSettings()
    )

    companion object {
        fun summary(route: List<Point>): List<Point> {
            // hack should lift out to better methods
            return Route(
                "ignored",
                route,
                listOf(),
                RouteSettings(
                    coordinatesPointLimit = 100,
                    directionsPointLimit = 0,
                    mockDirections = false,
                    showRoutePoints = false,
                    useReumannWitkam = true,
                )
            ).route
        }
    }

    var directions = processDirectionInfo(routeSettings, route, directionsIn)

    // cached values for rectangular points
    val projectedPoints: List<RectPoint> by lazy {
        route.mapNotNull { it.convert2XY() }
    }

    init {
        if (route.isNotEmpty()) {
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
                    Math.ceil(directions.size.toDouble() / routeSettings.directionsPointLimit)
                        .toInt()
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
            // 1. Mandatory indices that must be kept
            val directionIndices = directions.map { it.routeIndex }.toSet()
            val finalIndicesToKeep = mutableSetOf<Int>()
            finalIndicesToKeep.addAll(directionIndices)
            finalIndicesToKeep.add(0)
            finalIndicesToKeep.add(route.size - 1)

            // 2. Setup budget tracking
            // How many "other" points are we allowed to have in total?
            val maxOtherPointsAllowed =
                (routeSettings.coordinatesPointLimit - finalIndicesToKeep.size).coerceAtLeast(0)
            val reducedOtherIndices = mutableListOf<Int>()

            // Identify all potential points we could remove
            val otherIndices = route.indices.filter { it !in finalIndicesToKeep }

            if (routeSettings.useReumannWitkam) {
                if (maxOtherPointsAllowed > 0 && otherIndices.isNotEmpty()) {
                    val toleranceMeters = 50.0
                    val anchors = finalIndicesToKeep.toList().sorted()

                    for (i in 0 until anchors.size - 1) {
                        val startIdx = anchors[i]
                        val endIdx = anchors[i + 1]

                        if (endIdx - startIdx <= 1) continue

                        var keyIdx = startIdx
                        var currIdx = startIdx + 1

                        while (currIdx < endIdx) {
                            // The "Corridor" is defined by the line passing through
                            // keyIdx and keyIdx + 1.
                            val lineStart = route[keyIdx]
                            val lineEnd = route[keyIdx + 1]

                            // We check if the point at currIdx deviates from the corridor
                            val distance = calculatePerpendicularDistanceToInfiniteLine(
                                route[currIdx],
                                lineStart,
                                lineEnd
                            )

                            if (distance > toleranceMeters) {
                                // This point is outside the corridor.
                                // We keep it and it becomes the NEW anchor.
                                reducedOtherIndices.add(currIdx)
                                keyIdx = currIdx
                                // The next point to test is the one after the new anchor
                                currIdx = keyIdx + 1
                            } else {
                                // Point is within tolerance, skip it and check the next one
                                // against the SAME corridor.
                                currIdx++
                            }
                        }
                    }
                }
            } else {
                reducedOtherIndices.addAll(otherIndices)
            }


            // 4. Fallback: nthPoint sampling
            // This only runs if the algorithm processed the whole route and the point count is STILL too high.
            if (reducedOtherIndices.size > maxOtherPointsAllowed && maxOtherPointsAllowed > 0) {
                val nthPoint =
                    Math.ceil(reducedOtherIndices.size.toDouble() / maxOtherPointsAllowed).toInt()
                        .coerceAtLeast(1)
                for (i in reducedOtherIndices.indices step nthPoint) {
                    if (finalIndicesToKeep.size < routeSettings.coordinatesPointLimit) {
                        finalIndicesToKeep.add(reducedOtherIndices[i])
                    }
                }
            } else {
                finalIndicesToKeep.addAll(reducedOtherIndices)
            }

            // 5. Rebuild route and update mappings (Same logic as before)
            val sortedIndicesToKeep = finalIndicesToKeep.sorted()
            val oldToNewIndexMap = sortedIndicesToKeep
                .withIndex()
                .associate { (newIndex, oldIndex) -> oldIndex to newIndex }

            route = sortedIndicesToKeep.map { route[it] }

            directions = directions.map { direction ->
                val newIndex = oldToNewIndexMap[direction.routeIndex]
                direction.copy(routeIndex = newIndex ?: 0)
            }
        }
    }

    private fun calculatePerpendicularDistanceToInfiniteLine(
        p: Point,
        start: Point,
        end: Point
    ): Double {
        // 1. Get projected XY coordinates (meters)
        val pXY = p.convert2XY() ?: return 0.0
        val sXY = start.convert2XY() ?: return 0.0
        val eXY = end.convert2XY() ?: return 0.0

        val x0 = pXY.x.toDouble()
        val y0 = pXY.y.toDouble()
        val x1 = sXY.x.toDouble()
        val y1 = sXY.y.toDouble()
        val x2 = eXY.x.toDouble()
        val y2 = eXY.y.toDouble()

        // 2. Line equation: Ax + By + C = 0
        // (y1 – y2)x + (x2 – x1)y + (x1y2 – x2y1) = 0
        val A = y1 - y2
        val B = x2 - x1
        val C = x1 * y2 - x2 * y1

        val denominator = Math.sqrt(A * A + B * B)
        if (denominator == 0.0) return calculateDistanceInMeters(p, start)

        // 3. Perpendicular distance formula
        return Math.abs(A * x0 + B * y0 + C) / denominator
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

    fun toUl(): RouteUL {
        return RouteUL(
            route.mapNotNull { it.convert2XY() },
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

class RouteUL(
    private val route: List<RectPoint>,
) : Protocol {
    override fun type(): ProtocolType {
        // keep the same protocol, ultralight app just handles it differently
        return ProtocolType.PROTOCOL_ROUTE_DATA_UL
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun payload(): List<Any> {
        val data = mutableListOf<Any>()

        // just x and y the UL app does not support elevation
        val routeData = mutableListOf<Any>()
        for (point in route) {
            routeData.add(point.x)
            routeData.add(point.y)
        }
        data.add(routeData)
        return data
    }
}