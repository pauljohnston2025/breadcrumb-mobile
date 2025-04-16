package com.paul.viewmodels

import android.util.Log
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paul.domain.GpxRoute
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.service.IFileHelper
import com.paul.infrastructure.service.IGpxFileLoader
import com.paul.infrastructure.service.InputStreamHelpers
import com.paul.infrastructure.web.KtorClient
import com.paul.protocol.todevice.Point
import com.paul.protocol.todevice.Route
import com.russhwolf.settings.Settings
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class KomootGpxRoute : GpxRoute {
    var name: String = ""
    var coordinates: List<KomootTourCoordinate> = listOf()

    constructor(komootRoot: KomootSetPropsRoot) {
        name = komootRoot.page._embedded.tour.name
        coordinates = komootRoot.page._embedded.tour._embedded.coordinates.items
    }

    constructor(historyItem: HistoryItem) {
        name = historyItem.name
        coordinates = historyItem.coords
    }

    override suspend fun toRoute(snackbarHostState: SnackbarHostState): Route? {
        return Route(name, coordinates.map { Point(it.lat, it.lng, it.alt) })
    }

    override fun name(): String {
        return name
    }

    override fun rawBytes(): ByteArray {
        return byteArrayOf() // not used for komoot routes - dirty hack
    }

}

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
data class KomootTourEmbedded(val coordinates: KomootTourCoordinates)

@Serializable
data class KomootTourCoordinates(val items: List<KomootTourCoordinate>)

@Serializable
data class KomootTourCoordinate(
    val lat: Float,
    val lng: Float,
    val alt: Float,
    val t: Int,
)

@Serializable
data class HistoryItem(
    val id: String,
    val name: String,
    val uri: String,
    val timestamp: Instant,
    val coords: List<KomootTourCoordinate>
)

class StartViewModel(
    private val connection: IConnection,
    private val deviceSelector: DeviceSelector,
    private val gpxFileLoader: IGpxFileLoader,
    private val fileHelper: IFileHelper,
    private val snackbarHostState: SnackbarHostState
) : ViewModel() {
    val HISTORY_KEY = "HISTORY"
    val settings: Settings = Settings()

    val sendingFile: MutableState<String> = mutableStateOf("")
    val errorMessage: MutableState<String> = mutableStateOf("")
    val htmlErrorMessage: MutableState<String> = mutableStateOf("")
    val history = mutableStateListOf<HistoryItem>()
    private val client = KtorClient.client // Get the singleton client instance

    fun load(
        fileLoad: String?,
        shortGoogleUrl: String?,
        komootUrl: String?,
        initialErrorMessage: String?
    ) {
        if (initialErrorMessage != null) {
            errorMessage.value = initialErrorMessage
            htmlErrorMessage.value = initialErrorMessage
        }

        if (fileLoad != null) {
            loadFile(fileLoad, true)
        }

        if (shortGoogleUrl != null) {
            loadFromGoogle(shortGoogleUrl)
        }

        if (komootUrl != null) {
            loadFromKomoot(komootUrl)
        }
    }

    init {
        val historyJson = settings.getStringOrNull(HISTORY_KEY)
        if (historyJson != null) {
            try {
                Json.decodeFromString<List<HistoryItem>>(historyJson).forEach {
                    history.add(it)
                }
            } catch (t: Throwable) {
                Log.d("stdout", "failed to hydrate history items $t")
            }
        }
    }

    fun pickRoute() {
        viewModelScope.launch(Dispatchers.IO) {
            val uri: String
            try {
                uri = fileHelper.findFile()
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Failed to find file (invalid or no selection)")
                return@launch
            }

            loadFile(uri, true)
        }
    }

    fun loadFileFromHistory(historyItem: HistoryItem) {
        if (historyItem.coords.size != 0) {
            val gpxRoute = KomootGpxRoute(historyItem)
            viewModelScope.launch(Dispatchers.IO) {
                sendRoute(gpxRoute, "unused", historyItem.coords)
            }
            return
        }

        loadFile(historyItem.uri, false)
    }

    fun loadFile(fileName: String, externalFile: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            sendingMessage("Parsing gpx input stream...") {
                try {
                    val fileContents = when (externalFile) {
                        true -> {
                            fileHelper.readFile(fileName)!!
                        }

                        false -> {
                            fileHelper.readLocalFile(fileName)!!
                        }
                    }

                    val gpxRoute = gpxFileLoader.loadGpxFromBytes(fileContents)
                    val historyFileName = when (externalFile) {
                        true -> fileHelper.getFileName(fileName)
                            ?: fileHelper.generateRandomFilename(".gpx")

                        false -> fileName
                    }
                    if (externalFile) {
                        fileHelper.writeLocalFile(historyFileName, gpxRoute.rawBytes())
                    }
                    sendRoute(gpxRoute, historyFileName)
                } catch (e: SecurityException) {
                    snackbarHostState.showSnackbar("Failed to load gpx file (you might not have permissions, please restart app to grant)")
                    Log.d("stdout", e.toString())
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to load gpx file (possibly invalid format)")
                    Log.d("stdout", e.toString())
                }
            }
        }
    }

    private fun loadFromGoogle(shortGoogleUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            sendingMessage("Loading google route from mapstogpx...") {
                loadFromGoogleInner(shortGoogleUrl)
            }
        }
    }

    private fun loadFromKomoot(komootUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            sendingMessage("Loading route from komoot...") {
                viewModelScope.launch(Dispatchers.IO) {
                    // inspired from https://github.com/DreiDe/komootGPXport
                    val gpxRoute = parseGpxRouteFromKomoot(komootUrl)
                    if (gpxRoute != null) {
                        sendRoute(gpxRoute, "unused", gpxRoute.coordinates)
                    }
                }
            }
        }
    }

    private suspend fun parseGpxRouteFromKomoot(komootUrl: String): KomootGpxRoute? {
        return try {
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
                Log.d("stdout", "$root")
                return KomootGpxRoute(root)
            }

            return null
        } catch (e: Exception) {
            Log.d("stdout", "failed to load komoot url $e")
            snackbarHostState.showSnackbar("Failed to load komoot url")
            return null
        }
    }

    private suspend fun loadFromGoogleInner(shortGoogleUrl: String) {
        val loaded = loadFromMapsToGpx(shortGoogleUrl)

        if (loaded == null) {
            snackbarHostState.showSnackbar("Failed to load from mapstogpx, consider using webpage directly")
            return
        }

        val (contentType, gpxBytes) = loaded
        if (!contentType.contains("application/gpx+xml")) {
            snackbarHostState.showSnackbar("Bad content type: $contentType")
            viewModelScope.launch(Dispatchers.IO) {
                val message = gpxBytes.decodeToString()
                viewModelScope.launch(Dispatchers.Main) {
                    if (contentType.contains("text/html")) {
                        htmlErrorMessage.value = message
                    } else {
                        errorMessage.value = message
                    }
                }
            }
            return
        }

        sendingMessage("Parsing gpx input stream...") {
            try {
                val gpxRoute = gpxFileLoader.loadGpxFromBytes(gpxBytes)
                val filename = fileHelper.generateRandomFilename(".gpx");
                fileHelper.writeLocalFile(filename, gpxRoute.rawBytes())
                sendRoute(gpxRoute, filename)
            } catch (e: SecurityException) {
                snackbarHostState.showSnackbar("Failed to load gpx file (you might not have permissions, please restart app to grant)")
                Log.d("stdout", e.toString())
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Failed to load gpx file (possibly invalid format)")
                Log.d("stdout", e.toString())
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun sendRoute(
        gpxRoute: GpxRoute,
        localFilePath: String,
        coords: List<KomootTourCoordinate> = listOf()
    ) {
        val device = deviceSelector.currentDevice()
        if (device == null) {
            // todo make this a toast or something better for the user
            snackbarHostState.showSnackbar("no devices selected")
            return
        }

        var route: Route? = null
        sendingMessage("Loading Route") {
            try {
                val historyItem = HistoryItem(
                    Uuid.random().toString(),
                    gpxRoute.name(),
                    localFilePath,
                    Clock.System.now(),
                    coords
                )
                history.add(historyItem)
                saveHistory()
                route = gpxRoute.toRoute(snackbarHostState)
            } catch (e: Exception) {
                Log.d("stdout", "Failed to parse route: ${e.message}")
            }
        }

        if (route == null) {
            snackbarHostState.showSnackbar("Failed to convert to route")
            return
        }

        sendingMessage("Sending file") {
            try {
                val version = connection.appInfo(device).version
                if (version > 9 || version == 0 /** simulator */) {
                    connection.send(device, route!!.toV2())
                } else {
                    connection.send(device, route!!)
                }
            } catch (t: TimeoutCancellationException) {
                snackbarHostState.showSnackbar("Timed out sending file")
                return@sendingMessage
            } catch (t: Throwable) {
                snackbarHostState.showSnackbar("Failed to send to selected device")
                return@sendingMessage
            }
            snackbarHostState.showSnackbar("Route sent")
        }
    }

    private suspend fun sendingMessage(msg: String, cb: suspend () -> Unit) {
        try {
            viewModelScope.launch(Dispatchers.Main) {
                sendingFile.value = msg
            }
            cb()
        } catch (t: Throwable) {
            Log.d("stdout", "Failed to do operation: $msg $t")
        } finally {
            viewModelScope.launch(Dispatchers.Main) {
                sendingFile.value = ""
            }
        }
    }

    private fun saveHistory() {
        // keep only the last few items, we do not want to overflow out internal storage
        settings.putString(HISTORY_KEY, Json.encodeToString(history.toList().takeLast(100)))
    }

    private suspend fun loadFromMapsToGpx(googleShortUrl: String): Pair<String, ByteArray>? {
        val url =
            "https://mapstogpx.com/load.php?d=default&lang=en&elev=off&tmode=off&pttype=fixed&o=gpx&cmt=off&desc=off&descasname=off&w=on&dtstr=20240804_092634&gdata=" + URLEncoder.encode(
                googleShortUrl.replace("https://", ""),
                Charset.defaultCharset()
            )
        // todo switch out the url call to kmp compatible
        val address = URL(url)

        //Connect & check for the location field
        try {
            val connection = address.openConnection(Proxy.NO_PROXY) as HttpURLConnection
            connection.setRequestProperty("Referer", "https://mapstogpx.com/")
            connection.instanceFollowRedirects = false
            connection.connect()
            if (connection.responseCode != 200) {
                return null
            }
            return withContext(Dispatchers.IO) {
                val res = Pair(
                    connection.contentType,
                    InputStreamHelpers.readAllBytes(connection.inputStream)
                )

                return@withContext res
            }
        } catch (e: Throwable) {
            Log.d("stdout", "Problem while expanding {}$address$e")
        }

        return null
    }

    fun clearHistory() {
        history.clear()
        saveHistory()
    }

    fun openDeviceSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val device = deviceSelector.currentDevice()
            if (device == null) {
                snackbarHostState.showSnackbar("no devices selected")
                return@launch
            }
            sendingMessage("Loading Settings From Device.\nEnsure an activity with the datafield is running (or at least open) or this will fail.") {
                deviceSelector.openDeviceSettingsSuspend(device)
            }
        }
    }
}
