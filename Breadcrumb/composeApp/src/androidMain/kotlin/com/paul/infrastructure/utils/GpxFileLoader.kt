package com.paul.infrastructure.utils

import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.material.SnackbarHostState
import com.paul.infrastructure.protocol.Point
import com.paul.infrastructure.protocol.Route
import io.ticofab.androidgpxparser.parser.GPXParser
import io.ticofab.androidgpxparser.parser.domain.Gpx
import io.ticofab.androidgpxparser.parser.domain.TrackPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resumeWithException
import io.ticofab.androidgpxparser.parser.domain.Point as GpxPoint;


data class GpxFile(
    val uri: String,
    val gpx: Gpx
) {
    suspend fun toRoute(snackbarHostState: SnackbarHostState): Route? {
        // prefer track, then routes (world topo app creation is a route, most other gpx's i've seen are a single track)
        var points = emptyList<GpxPoint>()
        if (gpx.tracks.size != 0) {
            val track = gpx.tracks[0]
            println("loading points for ${track.trackName}")
            if (track.trackSegments.size < 1) {
                snackbarHostState.showSnackbar("failed to get segments")
                return null
            }

            val segment = track.trackSegments[0]
            points = segment.trackPoints
        }
        else if (gpx.routes.size != 0)
        {
            val route = gpx.routes[0]
            println("loading points for ${route.routeName}")
            points = route.routePoints
        }
        else {
            println("failed to get track or route")
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

        return Route(routePoints)
    }

    fun name() : String {
        if (gpx.tracks.size != 0) {
            val track = gpx.tracks[0]
            if (track.trackName != null && track.trackName != "")
            {
                return track.trackName
            }
        }
        else if (gpx.routes.size != 0)
        {
            val route = gpx.routes[0]
            if (route.routeName !== null && route.routeName != "")
            {
                return route.routeName
            }
        }

        return uri
    }
}


class GpxFileLoader(private val context: Context) {

    private var parser = GPXParser()

    private var getContentLauncher: ActivityResultLauncher<Array<String>>? =
        null
    private var searchCompletion: ((Uri?) -> Unit)? = null

    fun setLauncher(_getContentLauncher: ActivityResultLauncher<Array<String>>) {
        require(getContentLauncher == null)
        getContentLauncher = _getContentLauncher
    }

    suspend fun searchForGpxFile(): GpxFile {
        val uri_str = searchForGpxFileUri()
        return GpxFile(uri_str, loadFileContents(Uri.parse(uri_str)))
    }

    suspend fun loadGpxFile(uri: Uri): GpxFile {
        return GpxFile(uri.toString(), loadFileContents(uri))
    }

    suspend fun loadGpxFile(uri: String): GpxFile {
        return loadGpxFile(Uri.parse(uri))
    }

    suspend fun loadGpxFromInputStream(stream: InputStream): GpxFile {
        return GpxFile(stream.toString(), parser.parse(stream))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun searchForGpxFileUri(): String = suspendCancellableCoroutine { continuation ->
        require(searchCompletion == null)
        searchCompletion = { uri ->
            if (uri == null) {
                continuation.resumeWithException(Exception("failed to load file: $uri"))
            } else {
                println("Load file: " + uri.toString())
                continuation.resume(uri.toString()) {
                    println("failed to resume")
                }
            }

            searchCompletion = null
        }

        require(getContentLauncher != null)
        getContentLauncher!!.launch(arrayOf("*"))
    }

    private suspend fun loadFileContents(uri: Uri): Gpx {
        // see TracksBrowserActivity.displayImportTracksDialog in wormnav
        var streamContents = withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(uri)
            val ret = stream!!.readAllBytes().decodeToString()
            stream.close()
            ret
        }
        // world topo map app track creation includes <time></time> sections with no value, this breaks the
        // parse, as it expects a time
        streamContents = streamContents.replace("<time></time>", "");
        val streamContentsAsStream: InputStream =
            ByteArrayInputStream(streamContents.toByteArray(StandardCharsets.UTF_8))
        return parser.parse(streamContentsAsStream)
    }

    fun fileLoaded(uri: Uri?) {
        require(searchCompletion != null)
        searchCompletion!!(uri)
    }
}
