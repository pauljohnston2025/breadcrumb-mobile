package com.paul.viewmodels

import android.util.Log
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paul.domain.GpxRoute
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.service.IFileHelper
import com.paul.infrastructure.service.IGpxFileLoader
import com.paul.protocol.fromdevice.ProtocolResponse
import com.paul.protocol.todevice.CancelLocationRequest
import com.paul.protocol.todevice.Colour
import com.paul.protocol.todevice.MapTile
import com.paul.protocol.todevice.RequestLocationLoad
import com.paul.protocol.todevice.RequestSettings
import com.paul.protocol.todevice.Route
import com.russhwolf.settings.Settings
import com.paul.protocol.fromdevice.Settings as DeviceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset

@Serializable
data class HistoryItem(val name: String, val uri: String)

class StartViewModel(
    private val connection: IConnection,
    private val deviceSelector: DeviceSelector,
    private val gpxFileLoader: IGpxFileLoader,
    private val fileHelper: IFileHelper,
    private val snackbarHostState: SnackbarHostState,
    fileLoad: String?,
    shortGoogleUrl: String?,
    initialErrorMessage: String?
) : ViewModel() {
    val HISTORY_KEY = "HISTORY"
    val settings: Settings = Settings()

    var lat by mutableStateOf("-27.472077")
    var long by mutableStateOf("153.023551")
    val sendingFile: MutableState<String> = mutableStateOf("")
    val errorMessage: MutableState<String> = mutableStateOf(initialErrorMessage ?: "")
    val htmlErrorMessage: MutableState<String> = mutableStateOf(initialErrorMessage ?: "")
    val history = mutableStateListOf<HistoryItem>()

    init {
        val historyJson = settings.getStringOrNull(HISTORY_KEY)
        if (historyJson != null) {
            val json = Json.parseToJsonElement(historyJson)
            json.jsonArray.forEach {
                history.add(Json.decodeFromJsonElement<HistoryItem>(it))
            }
        }

        if (fileLoad != null) {
            loadFile(fileLoad, true)
        }

        if (shortGoogleUrl != null) {
            loadFromGoogle(shortGoogleUrl)
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

    fun loadFile(fileName: String, firstTimeSeenFile: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            sendingMessage("Parsing gpx input stream...") {
                try {
                    val lastFileNamePart = fileHelper.getFileName(fileName)
                        ?: fileHelper.generateRandomFilename(".gpx")
                    val fileContents = fileHelper.readFile(fileName)!!
                    val gpxRoute = gpxFileLoader.loadGpxFromBytes(fileContents)
                    if (firstTimeSeenFile) {
                        fileHelper.writeLocalFile(lastFileNamePart, gpxRoute.rawBytes())
                    }
                    sendRoute(gpxRoute, lastFileNamePart)
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

    private suspend fun sendRoute(file: GpxRoute, localFilePath: String) {
        val device = deviceSelector.currentDevice()
        if (device == null) {
            // todo make this a toast or something better for the user
            snackbarHostState.showSnackbar("no devices selected")
            return
        }

        var route: Route? = null
        sendingMessage("Loading Route") {
            try {
                val historyItem = HistoryItem(file.name(), localFilePath)
                history.add(historyItem)
                saveHistory()
                route = file.toRoute(snackbarHostState)
            } catch (e: Exception) {
                Log.d("stdout", "Failed to parse route: ${e.message}")
            }
        }

        if (route == null) {
            snackbarHostState.showSnackbar("Failed to convert to route")
            return
        }

        sendingMessage("Sending file") {
            connection.send(device, route!!)
            snackbarHostState.showSnackbar("Route sent")
        }
    }

    private suspend fun sendingMessage(msg: String, cb: suspend () -> Unit) {
        try {
            viewModelScope.launch(Dispatchers.Main) {
                sendingFile.value = msg
            }
            cb()
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
            withContext(Dispatchers.IO) {
                return@withContext Pair(
                    connection.contentType,
                    connection.inputStream.readAllBytes()
                )
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

    suspend fun sendMockTileInner(x: Int, y: Int, z: Int, colour: Colour) {
        val TILE_SIZE = 64;
        val data = List(TILE_SIZE * TILE_SIZE) { colour };
        // random colour tiles for now
//        data = data.map {
//            Colour.random()
//        }
        val tile = MapTile(x, y, z, data);

        val device = deviceSelector.currentDevice()
        if (device == null) {
            // todo make this a toast or something better for the user
            snackbarHostState.showSnackbar("no devices selected")
            return
        }

        sendingMessage("Sending tile $x $y") {
            connection.send(device, tile)
            snackbarHostState.showSnackbar("Tile sent $x $y")
        }
    }

    fun loadLocation(lat: Float, long: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("stdout", "requesting load location")
            val device = deviceSelector.currentDevice()
            if (device == null) {
                // todo make this a toast or something better for the user
                snackbarHostState.showSnackbar("no devices selected")
                return@launch
            }

            sendingMessage("Requesting load location") {
                connection.send(device, RequestLocationLoad(lat, long))
                snackbarHostState.showSnackbar("Requesting load location sent")
            }
        }
    }

    fun clearLocation() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("stdout", "requesting clear location")
            val device = deviceSelector.currentDevice()
            if (device == null) {
                // todo make this a toast or something better for the user
                snackbarHostState.showSnackbar("no devices selected")
                return@launch
            }

            sendingMessage("Requesting clear location") {
                connection.send(device, CancelLocationRequest())
                snackbarHostState.showSnackbar("Requesting clear location sent")
            }
        }
    }

    fun requestSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("stdout", "requesting settings")
            val device = deviceSelector.currentDevice()
            if (device == null) {
                // todo make this a toast or something better for the user
                snackbarHostState.showSnackbar("no devices selected")
                return@launch
            }

            sendingMessage("Requesting settings") {
                val settings = connection.query<DeviceSettings>(device, RequestSettings(), ProtocolResponse.PROTOCOL_SEND_SETTINGS)
                Log.d("stdout", "got settings $settings")
                snackbarHostState.showSnackbar("Requesting settings sent")
            }
        }
    }
}
