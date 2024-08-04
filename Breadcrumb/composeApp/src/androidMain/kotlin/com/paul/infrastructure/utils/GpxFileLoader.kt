package com.paul.infrastructure.utils

import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.material.SnackbarHostState
import com.paul.infrastructure.protocol.Point
import com.paul.infrastructure.protocol.Route
import io.ticofab.androidgpxparser.parser.GPXParser
import io.ticofab.androidgpxparser.parser.domain.Gpx
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.InputStream
import kotlin.coroutines.resumeWithException

data class GpxFile(
    val name: String,
    val gpx: Gpx
) {
    suspend fun toRoute(snackbarHostState: SnackbarHostState): Route? {
        // will need to figure out best way to parse different files
        // for example it looks like its a track
        if (gpx.tracks.size < 1) {
            println("failed to get track")
            return null
        }

        val track = gpx.tracks[0]
        println("loading points for ${track.trackName}")
        if (track.trackSegments.size < 1) {
            snackbarHostState.showSnackbar("failed to get segments")
            return null
        }

        val segment = track.trackSegments[0]

        val routePoints = mutableListOf<Point>()
        // too figure out the max size we can have
        // for now use 1000
        // from connectIq internals ConnectIQ.sendMessage if (data.length > 16384 ...
        // MonkeyDouble is 9 bytes, MonkeyFloat is 5 bytes , though if its small enough error they send as float
        // so 1000 is probably fine (since each point is double|double|float (15 to 23 bytes each))
        // should probably condense this down (possibly send the rectangular coordinate)
        var nthPoint = Math.ceil(segment.trackPoints.size / 500.0).toInt()
        if (nthPoint == 0) {
            // get all if less than 1000
            // should never happen now we are doing ceil()
            nthPoint = 1
        }
        for (i in 1 until segment.trackPoints.size step nthPoint) {
            val trackPoint = segment.trackPoints[i]
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
        val uri = searchForGpxFileUri()
        return GpxFile(uri.toString(), loadFileContents(uri))
    }

    suspend fun loadGpxFile(uri: Uri): GpxFile {
        return GpxFile(uri.toString(), loadFileContents(uri))
    }

    suspend fun loadGpxFromInputStream(stream: InputStream): GpxFile {
        return GpxFile(stream.toString(), parser.parse(stream))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun searchForGpxFileUri(): Uri = suspendCancellableCoroutine { continuation ->
        require(searchCompletion == null)
        searchCompletion = { uri ->
            if (uri == null) {
                continuation.resumeWithException(Exception("failed to load file: $uri"))
            } else {
                println("Load file: " + uri.toString())
                continuation.resume(uri) {
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
        val stream = context.contentResolver.openInputStream(uri)
        // need to close stream?
        return parser.parse(stream)
    }

    fun fileLoaded(uri: Uri?) {
        require(searchCompletion != null)
        searchCompletion!!(uri)
    }
}


//class GpxFileLoader(private val context: Context, private val requestCode: Int) {
//
//    var searchCompletion: ((Int, Intent?) -> Unit)? = null
//
//    suspend fun searchForGpxFile(): GpxFile = suspendCancellableCoroutine { continuation ->
//        require(searchCompletion == null)
//        searchCompletion = { resultCode, data ->
//            if (resultCode != RESULT_OK || data == null)
//            {
//                continuation.resumeWithException(Exception("failed to load file: $resultCode"))
//            }
//            else {
//                val uri = data.data
//                println("Load file: " + uri.toString())
//                continuation.resume(GpxFile(uri.toString(), ubyteArrayOf())) {
//                    println("failed to resume")
//                }
//            }
//
//            searchCompletion = null
//        }
//
//        // BEGIN_INCLUDE (use_open_document_intent)
//        // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file browser.
//        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
//
//
//        // Filter to only show results that can be "opened", such as a file (as opposed to a list
//        // of contacts or timezones)
//        intent.addCategory(Intent.CATEGORY_OPENABLE)
//
//
//        // Filter to show only images, using the image MIME data type.
//        // If one wanted to search for ogg vorbis files, the type would be "audio/ogg".
//        // To search for all documents available via installed storage providers, it would be
//        // "*/*".
//        intent.setType("*/*")
//
////        if (initialUri != null) intent.putExtra(
////            DocumentsContract.EXTRA_INITIAL_URI,
////            Data.lastImportedExportedUri
////        )
//        context.startActivity(intent, requestCode)
//    }
//
//}