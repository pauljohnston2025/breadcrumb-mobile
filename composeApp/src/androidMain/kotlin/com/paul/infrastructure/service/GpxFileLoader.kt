package com.paul.infrastructure.service

import io.github.aakira.napier.Napier
import androidx.compose.material.SnackbarHostState
import com.paul.domain.DirectionInfo
import com.paul.domain.GpxRoute
import com.paul.protocol.todevice.Point
import com.paul.protocol.todevice.Route
import io.ticofab.androidgpxparser.parser.GPXParser
import io.ticofab.androidgpxparser.parser.domain.Gpx
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import io.ticofab.androidgpxparser.parser.domain.Point as GpxPoint


data class GpxFile(
    val gpx: Gpx,
    val _rawBytes: ByteArray,
) : GpxRoute() {
    private var _name: String? = null

    override suspend fun toRoute(snackbarHostState: SnackbarHostState): Route? {
        var baseGpxPoints = emptyList<GpxPoint>()

        // Prioritize tracks, then routes for the base points of the route.
        if (gpx.tracks.isNotEmpty()) {
            val track = gpx.tracks[0]
            Napier.d("loading track points for ${name()}")
            if (track.trackSegments.isEmpty()) {
                snackbarHostState.showSnackbar("failed to get segments")
                return null
            }
            baseGpxPoints = track.trackSegments[0].trackPoints
        } else if (gpx.routes.isNotEmpty()) {
            val route = gpx.routes[0]
            Napier.d("loading route points for ${name()}")
            baseGpxPoints = route.routePoints
        } else if (gpx.wayPoints.isNotEmpty()) {
            // No track or route, the waypoints themselves form the route.
            Napier.d("loading waypoints as the main route for ${name()}")
            baseGpxPoints = gpx.wayPoints
        }

        if (baseGpxPoints.isEmpty()) {
            Napier.d("failed to get track, route, or waypoints")
            return null
        }

        // Convert GpxPoints to a mutable list of our domain's Point objects.
        val finalPoints = baseGpxPoints.map { gpxPoint ->
            Point(
                gpxPoint.latitude?.toFloat() ?: 0.0f,
                gpxPoint.longitude?.toFloat() ?: 0.0f,
                gpxPoint.elevation?.toFloat() ?: 0.0f
            )
        }.toMutableList()

        val directionsIn = mutableListOf<DirectionInfo>()

        // If the route was built from waypoints, every point is a direction.
        if (gpx.tracks.isEmpty() && gpx.routes.isEmpty() && gpx.wayPoints.isNotEmpty()) {
            finalPoints.indices.forEach { index ->
                directionsIn.add(DirectionInfo(index))
            }
        }
        // Otherwise, merge waypoints into the track/route.
        else if (gpx.wayPoints.isNotEmpty()) {
            gpx.wayPoints.forEach { waypoint ->
                val pointToAdd = Point(
                    waypoint.latitude?.toFloat() ?: 0.0f,
                    waypoint.longitude?.toFloat() ?: 0.0f,
                    waypoint.elevation?.toFloat() ?: 0.0f
                )

                // Check if the waypoint already exists in the track.
                val existingIndex = finalPoints.indexOf(pointToAdd)
                if (existingIndex == -1) {
                    // *** START of updated logic ***
                    // The waypoint is not in the track, so find the best location to insert it.
                    var bestInsertionIndex = finalPoints.size // Default to the end
                    if (finalPoints.size >= 2) {
                        var minDistance = Double.MAX_VALUE
                        // Iterate through each segment of the route to find the closest one.
                        for (i in 0 until finalPoints.size - 1) {
                            val p1 = finalPoints[i]
                            val p2 = finalPoints[i + 1]

                            // The best segment is the one where the sum of distances from the
                            // waypoint to the segment's start and end points is minimal.
                            val distance = haversineDistance(pointToAdd, p1) + haversineDistance(
                                pointToAdd,
                                p2
                            )

                            if (distance < minDistance) {
                                minDistance = distance
                                bestInsertionIndex =
                                    i + 1 // Insert after the first point of the segment.
                            }
                        }
                    }
                    // Insert the new waypoint at the calculated best position.
                    finalPoints.add(bestInsertionIndex, pointToAdd)
                    // *** END of updated logic ***
                }
            }

            // After all insertions are complete, build the directions list.
            // This is more reliable than trying to adjust indices during insertion.
            val finalPointsSet = finalPoints.toSet()
            gpx.wayPoints.forEach { waypoint ->
                val waypointPoint = Point(
                    waypoint.latitude?.toFloat() ?: 0.0f,
                    waypoint.longitude?.toFloat() ?: 0.0f,
                    waypoint.elevation?.toFloat() ?: 0.0f
                )
                if (finalPointsSet.contains(waypointPoint)) {
                    // Find all occurrences in case of duplicate points
                    finalPoints.forEachIndexed { index, point ->
                        if (point == waypointPoint) {
                            directionsIn.add(DirectionInfo(index))
                        }
                    }
                }
            }
        }

        return Route(
            name(),
            finalPoints,
            directionsIn.distinct()
                .sortedBy { it.index } // Ensure directions are unique and sorted.
        )
    }

    /**
     * Calculates the distance between two points in meters using the Haversine formula.
     */
    private fun haversineDistance(p1: Point, p2: Point): Double {
        val earthRadius = 6371e3 // in meters
        val lat1Rad = Math.toRadians(p1.latitude.toDouble())
        val lat2Rad = Math.toRadians(p2.latitude.toDouble())
        val dLat = Math.toRadians((p2.latitude - p1.latitude).toDouble())
        val dLon = Math.toRadians((p2.longitude - p1.longitude).toDouble())

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    override fun name(): String {
        if (_name != null) {
            return _name!!
        }
        if (gpx.tracks.size != 0) {
            val track = gpx.tracks[0]
            if (track.trackName != null && track.trackName != "") {
                return track.trackName
            }
        } else if (gpx.routes.size != 0) {
            val route = gpx.routes[0]
            if (route.routeName !== null && route.routeName != "") {
                return route.routeName
            }
        }

        return "<unknown>"
    }

    override fun setName(name: String) {
        _name = name
    }

    override fun hasDirectionInfo(): Boolean {
        // wayPoints appear to be used as the track directions in sites like plotaroute.com
        // we might have to let users specify where to find the directions for each route in the future, but this is fine for now
        // If they are present we can combine them into the track/route we find
        // because there is no guarantee that the lat/long exists in the track/route
        return gpx.wayPoints.size != 0
    }

    override fun rawBytes(): ByteArray {
        return _rawBytes
    }
}

class GpxFileLoader() : IGpxFileLoader {
    private var parser = GPXParser()

    override suspend fun loadGpxFromBytes(stream: ByteArray): GpxFile {
        val fileContents = loadFileContents(stream)
        return GpxFile(
            fileContents.first,
            fileContents.second.encodeToByteArray()
        )
    }

    private fun loadFileContents(stream: ByteArray): Pair<Gpx, String> {
        var streamContents = stream.decodeToString()
        // world topo map app track creation includes <time></time> sections with no value, this breaks the
        // parse, as it expects a time
        // there are also a few others for waypoints that seem to have empty bodies
        streamContents = streamContents.replace("<time></time>", "")
            .replace("<ele></ele>", "")
            .replace("<desc></desc>", "")
        val streamContentsAsStream: InputStream =
            ByteArrayInputStream(streamContents.toByteArray(StandardCharsets.UTF_8))
        return Pair(parser.parse(streamContentsAsStream), streamContents)
    }
}
