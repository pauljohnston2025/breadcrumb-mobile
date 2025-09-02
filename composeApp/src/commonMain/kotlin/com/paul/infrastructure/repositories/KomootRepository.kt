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
        // The target distance (in meters) to look ahead and behind from a turn point
        // to get a stable bearing. This avoids issues with dense points at intersections.
        private const val BEARING_LOOKAROUND_METERS = 60.0
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

            // Find a point sufficiently far behind the turn to establish the incoming bearing.
            // We iterate backward from the turn to find the first point that is further
            // than our target lookaround distance.
            var previousIndex = 0 // Default to the start of the route
            for (i in turnIndex - 1 downTo 0) {
                if (calculateDistanceInMeters(coordinates[i], currentPoint) > BEARING_LOOKAROUND_METERS) {
                    previousIndex = i
                    break // Found a suitable point
                }
            }

            // Find a point sufficiently far ahead of the turn to establish the outgoing bearing.
            // We iterate forward to find a point further than our target distance.
            var nextIndex = coordinates.size - 1 // Default to the end of the route
            for (i in turnIndex + 1 until coordinates.size) {
                if (calculateDistanceInMeters(coordinates[i], currentPoint) > BEARING_LOOKAROUND_METERS) {
                    nextIndex = i
                    break // Found a suitable point
                }
            }

            val previousPoint = coordinates[previousIndex]
            val nextPoint = coordinates[nextIndex]

            // Ensure points are not identical to avoid calculation errors
            if (previousPoint != currentPoint && currentPoint != nextPoint) {
                val bearingIn = calculateBearing(previousPoint, currentPoint)
                val bearingOut = calculateBearing(currentPoint, nextPoint)

                turnAngle = bearingOut - bearingIn

                // Normalize angle to be between -180 (left) and 180 (right)
                if (turnAngle <= -180) {
                    turnAngle += 360
                }
                if (turnAngle > 180) {
                    turnAngle -= 360
                }
            }

            DirectionPoint(currentPoint.lat, currentPoint.lng, turnAngle.toFloat(), turnIndex.toFloat())
        }

        return CoordinatesRoute(name, points, directionPoints)
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