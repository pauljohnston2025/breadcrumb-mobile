package com.paul.viewmodels

import android.net.Uri
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paul.infrastructure.connectiq.Connection
import com.paul.infrastructure.protocol.Route
import com.paul.infrastructure.utils.GpxFile
import com.paul.infrastructure.utils.GpxFileLoader
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    val sendingFile: MutableState<Boolean> = mutableStateOf(false)
    val loadingMessage: MutableState<String> = mutableStateOf(initialErrorMessage ?: "")
    val htmlMessage: MutableState<String> = mutableStateOf(initialErrorMessage ?: "")
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
            loadFile(fileLoad)
        }

        if (shortGoogleUrl != null) {
            loadFromGoogle(shortGoogleUrl)
        }

        history.add(HistoryItem("dummy1", "dummy1"))
        history.add(HistoryItem("dummy2", "dummy2"))
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

            loadFile(uri)
        }
    }

    fun loadFile(fileName: String) {
        loadFile(Uri.parse(fileName))
    }

    private fun loadFile(fileName: Uri) {
        setLoadingMessage("Parsing gpx input stream...")
        viewModelScope.launch(Dispatchers.IO) {
            val file: GpxFile
            try {
                file = gpxFileLoader.loadGpxFile(fileName)
            }
            catch (e: SecurityException) {
                snackbarHostState.showSnackbar("Failed to load gpx file (you might not have permissions, please restart app to grant)")
                println(e)
                clearLoadingMessage()
                return@launch
            }
            catch (e: Exception) {
                snackbarHostState.showSnackbar("Failed to load gpx file (possibly invalid format)")
                println(e)
                clearLoadingMessage()
                return@launch
            }

            setLoadingMessage("Sending...")
            sendFile(file)
            clearLoadingMessage()
        }
    }

    private fun clearLoadingMessage()
    {
        viewModelScope.launch(Dispatchers.Main) {
            loadingMessage.value = ""
        }
    }

    private fun setLoadingMessage(value: String)
    {
        viewModelScope.launch(Dispatchers.Main) {
            loadingMessage.value = value
        }
    }

    private fun loadFromGoogle(shortGoogleUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            setLoadingMessage("Loading google route from mapstogpx...")
            val loaded = loadFromMapsToGpx(shortGoogleUrl)

            if (loaded == null) {
                snackbarHostState.showSnackbar("Failed to load from mapstogpx, consider using webpage directly")
                clearLoadingMessage()
                return@launch
            }

            val (contentType, gpxInputStream) = loaded
            if (!contentType.contains("application/gpx+xml")) {
                snackbarHostState.showSnackbar("Bad content type: $contentType")
                if(contentType.contains("text/html"))
                {
                    viewModelScope.launch(Dispatchers.IO) {
                        htmlMessage.value = gpxInputStream.readAllBytes().decodeToString()
                    }
                    return@launch
                }
                viewModelScope.launch(Dispatchers.IO) {
                    setLoadingMessage(gpxInputStream.readAllBytes().decodeToString())
                }
                return@launch
            }

            setLoadingMessage("Parsing gpx input stream...")
            val file: GpxFile
            try {
                file = gpxFileLoader.loadGpxFromInputStream(gpxInputStream)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Failed to load gpx file (possibly invalid format)")
                println(e)
                clearLoadingMessage()
                return@launch
            }

            setLoadingMessage("Sending...")
            sendFile(file)
            clearLoadingMessage()
        }
    }

    private suspend fun sendFile(file: GpxFile) {
        viewModelScope.launch(Dispatchers.Main) {
            sendingFile.value = true
        }
        val device = deviceSelector.currentDevice()
        if (device == null) {
            // todo make this a toast or something better for the user
            snackbarHostState.showSnackbar("no devices selected")
            viewModelScope.launch(Dispatchers.Main) {
                sendingFile.value = false
            }
            return
        }

        var route: Route? = null
        try {
            val historyItem = HistoryItem(file.name(), file.uri)
            history.add(historyItem)
            // keep only the last few items, we do not want to overflow out internal storage
            settings.putString(HISTORY_KEY, Json.encodeToString(history.toList().takeLast(100)))
            route = file.toRoute(snackbarHostState)
        } catch (e: Exception) {
            println("Failed to parse route: ${e.message}")
        }

        if (route == null) {
            viewModelScope.launch(Dispatchers.Main) {
                sendingFile.value = false
            }
            snackbarHostState.showSnackbar("Failed to convert to route")
            return
        }
        connection.send(device, route)
        viewModelScope.launch(Dispatchers.Main) {
            sendingFile.value = false
        }
        snackbarHostState.showSnackbar("Route sent")
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

}
