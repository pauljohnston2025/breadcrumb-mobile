package com.paul.viewmodels

import android.net.Uri
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paul.infrastructure.connectiq.Connection
import com.paul.infrastructure.protocol.Colour
import com.paul.infrastructure.protocol.MapTile
import com.paul.infrastructure.protocol.Route
import com.paul.infrastructure.utils.GpxFile
import com.paul.infrastructure.utils.GpxFileLoader
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset

@Serializable
data class HistoryItem(val name: String, val uri: String)

class StartViewModel(
    private val connection: Connection,
    private val deviceSelector: DeviceSelector,
    private val gpxFileLoader: GpxFileLoader,
    private val snackbarHostState: SnackbarHostState,
    fileLoad: Uri?,
    shortGoogleUrl: String?,
    initialErrorMessage: String?
) : ViewModel() {

    val HISTORY_KEY = "HISTORY"
    val settings: Settings = Settings()

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
                uri = gpxFileLoader.searchForGpxFileUri()
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Failed to find file (invalid or no selection)")
                return@launch
            }

            loadFile(uri, true)
        }
    }

    fun loadFile(fileName: String, firstTimeSeenFile: Boolean) {
        loadFile(Uri.parse(fileName), firstTimeSeenFile)
    }

    private fun loadFile(fileName: Uri, firstTimeSeenFile: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            sendingMessage("Parsing gpx input stream...") {
                try {
                    val file = gpxFileLoader.loadGpxFile(fileName, firstTimeSeenFile)
                    sendFile(file)
                } catch (e: SecurityException) {
                    snackbarHostState.showSnackbar("Failed to load gpx file (you might not have permissions, please restart app to grant)")
                    println(e)
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to load gpx file (possibly invalid format)")
                    println(e)
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

        val (contentType, gpxInputStream) = loaded
        if (!contentType.contains("application/gpx+xml")) {
            snackbarHostState.showSnackbar("Bad content type: $contentType")
            viewModelScope.launch(Dispatchers.IO) {
                val message = gpxInputStream.readAllBytes().decodeToString()
                viewModelScope.launch(Dispatchers.Main) {
                    if (contentType.contains("text/html")) {
                        htmlErrorMessage.value = message
                    }
                    else {
                        errorMessage.value = message
                    }
                }
            }
            return
        }

        sendingMessage("Parsing gpx input stream...") {
            try {
                val file = gpxFileLoader.loadGpxFromInputStream(gpxInputStream)
                sendFile(file)
            } catch (e: SecurityException) {
                snackbarHostState.showSnackbar("Failed to load gpx file (you might not have permissions, please restart app to grant)")
                println(e)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Failed to load gpx file (possibly invalid format)")
                println(e)
            }
        }
    }

    private suspend fun sendFile(file: GpxFile) {
        val device = deviceSelector.currentDevice()
        if (device == null) {
            // todo make this a toast or something better for the user
            snackbarHostState.showSnackbar("no devices selected")
            return
        }

        var route: Route? = null
        sendingMessage("Loading Route") {
            try {
                val historyItem = HistoryItem(file.name(), file.uri)
                history.add(historyItem)
                saveHistory()
                route = file.toRoute(snackbarHostState)
            } catch (e: Exception) {
                println("Failed to parse route: ${e.message}")
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
        }
        finally {
            viewModelScope.launch(Dispatchers.Main) {
                sendingFile.value = ""
            }
        }
    }

    private fun saveHistory() {
        // keep only the last few items, we do not want to overflow out internal storage
        settings.putString(HISTORY_KEY, Json.encodeToString(history.toList().takeLast(100)))
    }

    private fun loadFromMapsToGpx(googleShortUrl: String): Pair<String, InputStream>? {
        val url =
            "https://mapstogpx.com/load.php?d=default&lang=en&elev=off&tmode=off&pttype=fixed&o=gpx&cmt=off&desc=off&descasname=off&w=on&dtstr=20240804_092634&gdata=" + URLEncoder.encode(
                googleShortUrl.replace("https://", ""),
                Charset.defaultCharset()
            )
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
            return Pair(connection.contentType, connection.inputStream)

        } catch (e: Throwable) {
            println("Problem while expanding {}$address$e")
        }

        return null
    }

    fun clearHistory() {
        history.clear()
        saveHistory()
    }

    fun sendMockTile(x: Int, y: Int, colour: Colour) {
        viewModelScope.launch(Dispatchers.IO) {
            sendMockTileInner(x,y,colour)
        }
    }

    suspend fun sendMockTileInner(x: Int, y: Int, colour: Colour) {
        val tilesize = 64
        val data = List(tilesize * tilesize) { colour };
        val tile = MapTile(x, y, data);

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

    fun sendAllTiles(colour: Colour){
        viewModelScope.launch(Dispatchers.IO) {
            val device = deviceSelector.currentDevice()
            if (device == null) {
                // todo make this a toast or something better for the user
                snackbarHostState.showSnackbar("no devices selected")
                return@launch
            }
            val size = 6;
            for (x in 0..size) {
                for (y in 0..size) {
                    sendMockTileInner(x,y,colour)
                }
            }
        }
    }

}
