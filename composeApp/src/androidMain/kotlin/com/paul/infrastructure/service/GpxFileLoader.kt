package com.paul.infrastructure.service

import io.github.aakira.napier.Napier
import androidx.compose.material.SnackbarHostState
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
    override suspend fun toRoute(snackbarHostState: SnackbarHostState): Route? {
        // prefer track, then routes (world topo app creation is a route, most other gpx's i've seen are a single track)
        var points = emptyList<GpxPoint>()
        if (gpx.tracks.size != 0) {
            val track = gpx.tracks[0]
            Napier.d("loading points for ${track.trackName}")
            if (track.trackSegments.size < 1) {
                snackbarHostState.showSnackbar("failed to get segments")
                return null
            }

            val segment = track.trackSegments[0]
            points = segment.trackPoints
        } else if (gpx.routes.size != 0) {
            val route = gpx.routes[0]
            Napier.d("loading points for ${route.routeName}")
            points = route.routePoints
        } else {
            Napier.d("failed to get track or route")
            return null
        }

        val routePoints = mutableListOf<Point>()
        // too figure out the max size we can have
        // for now use 1000
        // from connectIq internals ConnectIQ.sendMessage if (data.length > 16384 ...
        // MonkeyDouble is 9 bytes, MonkeyFloat is 5 bytes , though if its small enough error they send as float
        // so 1000 is probably fine (since each point is double|double|float (15 to 23 bytes each))
        // should probably condense this down (possibly send the rectangular coordinate)
        var nthPoint = Math.ceil(points.size / 400.0).toInt()
        if (nthPoint == 0) {
            // get all if less than 1000
            // should never happen now we are doing ceil()
            nthPoint = 1
        }
        for (i in 1 until points.size step nthPoint) {
            val trackPoint = points[i]
            routePoints.add(
                Point(
                    trackPoint.latitude.toFloat(),
                    trackPoint.longitude.toFloat(),
                    trackPoint.elevation?.toFloat().let { it ?: 0.0f }
                )
            )
        }

        return Route(name(), routePoints)
    }

    override fun name(): String {
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

    override fun rawBytes(): ByteArray {
        return _rawBytes;
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
        streamContents = streamContents.replace("<time></time>", "");
        val streamContentsAsStream: InputStream =
            ByteArrayInputStream(streamContents.toByteArray(StandardCharsets.UTF_8))
        return Pair(parser.parse(streamContentsAsStream), streamContents)
    }
}
