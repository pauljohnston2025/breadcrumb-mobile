package com.paul.infrastructure.repositories

import com.paul.domain.CoordinatesRoute
import com.paul.infrastructure.web.KtorClient
import com.paul.protocol.todevice.DirectionPoint
import com.paul.protocol.todevice.Point
import io.github.aakira.napier.Napier
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// there are other properties, we are just getting the ones we care about
@Serializable
data class KomootSetPropsRoot(val page: KomootPage)

@Serializable
data class KomootPage(val _embedded: KomootPageEmbedded)

@Serializable
data class KomootPageEmbedded(val tour: KomootTour)

@Serializable
data class KomootTour(
    val name: String,
    val _embedded: KomootTourEmbedded
)

@Serializable
data class KomootTourEmbedded(val coordinates: KomootTourCoordinates, val directions: KomootTourDirections)
// there is also a directions array, but cannot see any way to link to coordinates (other than distance)
// unless the index corresponds to the coordinate index?
//"directions": {
//						"items": [
//							{
//								"index": 0,
//								"complex": false,
//								"distance": 469,
//								"type": "S",
//								"cardinal_direction": "E",
//								"change_way": false,
//								"last_similar": 0,
//								"street_name": "",
//								"way_type": "wt#hike_d2"
//							},
//							{
//								"index": 10,
//								"complex": false,
//								"distance": 262,
//								"type": "TR",
//								"cardinal_direction": "E",
//								"change_way": true,
//								"last_similar": 4,
//								"street_name": "Trail",
//								"way_type": "wt#hike_d2"
//							},
//							{
//								"index": 15,
//								"complex": false,
//								"distance": 74,
//								"type": "TSR",
//								"cardinal_direction": "SE",
//								"change_way": false,
//								"last_similar": 10,
//								"street_name": "Trail",
//								"way_type": "wt#way"
//							},

@Serializable
data class KomootTourCoordinates(val items: List<KomootTourCoordinate>)

@Serializable
data class KomootTourDirections(val items: List<KomootTourDirection>)

@Serializable
data class KomootTourCoordinate(
    val lat: Float,
    val lng: Float,
    val alt: Float,
    val t: Int,
)

// we do not really need the point, we just need the angle between the 2 segments
// turn right etc. This is mainly for offline hiking, not `turn left at street x`
// it's more just `turn left at next intersection`
// it there is a fork though, maybe we ned to say take the left most fork?
@Serializable
data class KomootTourDirection(
    val index: Int,
)

class KomootRepository {
    private val client = KtorClient.client

    companion object {
        // The distance (in meters) to look ahead and behind from a turn point
        // to identify the path segment for analysis.
        private const val BEARING_LOOKAROUND_METERS = 100.0
        // The final portion of the path segment (in meters) to analyze for a stable bearing.
        private const val BEARING_ANALYSIS_SEGMENT_METERS = 30.0
    }

    suspend fun getRoute(url: String): CoordinatesRoute? {
        // inspired from https://github.com/DreiDe/komootGPXport
        val komootRoot = parseGpxRouteFromKomoot(url)
        if (komootRoot == null) {
            return null
        }

        val name = komootRoot.page._embedded.tour.name
        val coordinates = komootRoot.page._embedded.tour._embedded.coordinates.items
        val directions = komootRoot.page._embedded.tour._embedded.directions.items
        // Map all coordinates to Point objects
        val points = coordinates.map { Point(it.lat, it.lng, it.alt) }

        // Map direction indices to DirectionPoints with calculated turn angles
        val directionPoints = directions.map { direction ->
            val turnIndex = direction.index
            val currentPoint = coordinates[turnIndex]

            var turnAngle = 0.0

            // ### MODIFICATION START ###

            // 1. Identify the incoming and outgoing path segments around the turn.
            val incomingSegment = mutableListOf<KomootTourCoordinate>()
            for (i in turnIndex - 1 downTo 0) {
                if (calculateDistanceInMeters(coordinates[i], currentPoint) > BEARING_LOOKAROUND_METERS) {
                    break
                }
                incomingSegment.add(0, coordinates[i])
            }
            incomingSegment.add(currentPoint) // Add the turn point to the end of the incoming segment

            val outgoingSegment = mutableListOf<KomootTourCoordinate>()
            outgoingSegment.add(currentPoint) // Add the turn point to the start of the outgoing segment
            for (i in turnIndex + 1 until coordinates.size) {
                if (calculateDistanceInMeters(coordinates[i], currentPoint) > BEARING_LOOKAROUND_METERS) {
                    break
                }
                outgoingSegment.add(coordinates[i])
            }

            // 2. Calculate the dominant bearing for both segments.
            val bearingIn = calculateDominantBearing(incomingSegment, BEARING_ANALYSIS_SEGMENT_METERS, true)
            val bearingOut = calculateDominantBearing(outgoingSegment, BEARING_ANALYSIS_SEGMENT_METERS, false)


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
            // ### MODIFICATION END ###

            DirectionPoint(turnAngle.toFloat(), turnIndex)
        }

        // todo this should store a komoot route, not a coordinates route. then we can process it differently in the future
        return CoordinatesRoute(name, points, directionPoints)
    }

    /**
     * Calculates the dominant bearing of a path segment.
     * @param segment The list of coordinates representing the path segment.
     * @param analysisDistance The distance (in meters) from the end of the segment to analyze.
     * @param fromEnd If true, the analysis is performed on the last part of the segment (for incoming paths).
     *                If false, it's on the first part (for outgoing paths).
     * @return The dominant bearing in degrees, or null if it cannot be determined.
     */
    private fun calculateDominantBearing(segment: List<KomootTourCoordinate>, analysisDistance: Double, fromEnd: Boolean): Double? {
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
    private fun calculateBearing(start: KomootTourCoordinate, end: KomootTourCoordinate): Double {
        val lat1 = Math.toRadians(start.lat.toDouble())
        val lon1 = Math.toRadians(start.lng.toDouble())
        val lat2 = Math.toRadians(end.lat.toDouble())
        val lon2 = Math.toRadians(end.lng.toDouble())

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
    private fun calculateDistanceInMeters(start: KomootTourCoordinate, end: KomootTourCoordinate): Double {
        val earthRadius = 6371e3 // in meters

        val lat1Rad = Math.toRadians(start.lat.toDouble())
        val lat2Rad = Math.toRadians(end.lat.toDouble())
        val deltaLat = Math.toRadians((end.lat - start.lat).toDouble())
        val deltaLon = Math.toRadians((end.lng - start.lng).toDouble())

        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLon / 2) * sin(deltaLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    private suspend fun parseGpxRouteFromKomoot(komootUrl: String): KomootSetPropsRoot? {
        val response = client.get(komootUrl)
        if (response.status.isSuccess()) {
            val htmlString = response.bodyAsChannel().toByteArray().decodeToString()

            // this is incredibly flaky, but should work most of the time
            // the html page has a script tag containing kmtBoot.setProps("json with coordinates");
            // should parse using xml/html parser and find the whole string, but regex will work for now
            val regex = Regex("""kmtBoot\.setProps\((.*?)\);""", RegexOption.DOT_MATCHES_ALL)
            val matchResult = regex.find(htmlString)
            val jsonStringEscaped = matchResult?.groupValues?.getOrNull(1)
            if (jsonStringEscaped == null) {
                throw RuntimeException("could find json in komoot webpage")
            }
            val jsonString = Json {
                isLenient = true
            }.decodeFromString<String>(jsonStringEscaped)

            val root = Json { ignoreUnknownKeys = true }.decodeFromString<KomootSetPropsRoot>(
                jsonString
            )
//            Napier.d("$root")
            return root
        }

        return null
    }
}