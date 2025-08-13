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
    private var _name: String? = null

    override suspend fun toRoute(snackbarHostState: SnackbarHostState): Route? {
        // prefer track, then routes (world topo app creation is a route, most other gpx's i've seen are a single track)
        var points = emptyList<GpxPoint>()
        if (gpx.tracks.size != 0) {
            val track = gpx.tracks[0]
            Napier.d("loading track points for ${name()}")
            if (track.trackSegments.size < 1) {
                snackbarHostState.showSnackbar("failed to get segments")
                return null
            }

            val segment = track.trackSegments[0]
            points = segment.trackPoints
        } else if (gpx.routes.size != 0) {
            val route = gpx.routes[0]
            Napier.d("loading route points for ${name()}")
            points = route.routePoints
        } else {
            Napier.d("failed to get track or route")
            return null
        }

        return Route(name(), points.map { trackPoint ->
            Point(
                trackPoint.latitude?.toFloat().let { it ?: 0.0f },
                trackPoint.longitude?.toFloat().let { it ?: 0.0f },
                trackPoint.elevation?.toFloat().let { it ?: 0.0f }
            )
        })
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
        // there are also a few others for waypoints that seem to have empty bodies
        streamContents = streamContents.replace("<time></time>", "")
            .replace("<ele></ele>", "")
            .replace("<desc></desc>", "")
        val streamContentsAsStream: InputStream =
            ByteArrayInputStream(streamContents.toByteArray(StandardCharsets.UTF_8))
        return Pair(parser.parse(streamContentsAsStream), streamContents)
    }
}
