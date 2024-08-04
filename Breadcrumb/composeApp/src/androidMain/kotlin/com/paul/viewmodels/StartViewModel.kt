package com.paul.viewmodels

import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paul.infrastructure.connectiq.Connection
import com.paul.infrastructure.protocol.Route
import com.paul.infrastructure.utils.GpxFile
import com.paul.infrastructure.utils.GpxFileLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder

class StartViewModel(
    private val connection: Connection,
    private val deviceSelector: DeviceSelector,
    private val gpxFileLoader: GpxFileLoader,
    private val snackbarHostState: SnackbarHostState,
    initialSend: Uri?,
    shortGoogleUrl: String?,
    initialErrorMessage: String?
) : ViewModel() {

    val sendingFile: MutableState<Boolean> = mutableStateOf(false)
    val loadingMessage: MutableState<String> = mutableStateOf(initialErrorMessage ?: "")

    init {
        if (initialSend != null) {
            loadInitialFile(initialSend)
        }

        if (shortGoogleUrl != null) {
            loadFromGoogle(shortGoogleUrl)
        }
    }

    fun pickRoute() {
        viewModelScope.launch(Dispatchers.IO) {
            val file: GpxFile
            try {
                file = gpxFileLoader.searchForGpxFile()
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Failed to find file (invalid or no selection)")
                return@launch
            }

            sendFile(file)
        }
    }

    private fun loadInitialFile(initalFile: Uri) {
        viewModelScope.launch(Dispatchers.Main) {
            loadingMessage.value = "Parsing gpx input stream..."
        }
        viewModelScope.launch(Dispatchers.IO) {
            val file: GpxFile
            try {
                file = gpxFileLoader.loadGpxFile(initalFile)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Failed to load gpx file (possibly invalid format)")
                clearLoadingMessage()
                return@launch
            }

            viewModelScope.launch(Dispatchers.Main) {
                loadingMessage.value = "Sending..."
            }
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

    private fun loadFromGoogle(shortGoogleUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            viewModelScope.launch(Dispatchers.Main) {
                loadingMessage.value = "Loading google route from mapstogpx..."
            }
            val loaded = loadFromMapsToGpx(shortGoogleUrl)

            if (loaded == null) {
                snackbarHostState.showSnackbar("Failed to load from mapstogpx, consider using webpage directly")
                clearLoadingMessage()
                return@launch
            }

            val (contentType, gpxInputStream) = loaded
            if (!contentType.contains("application/gpx+xml")) {
                snackbarHostState.showSnackbar("Bad content type, trying anyway: ${contentType}")
            }

            viewModelScope.launch(Dispatchers.Main) {
                loadingMessage.value = "Parsing gpx input stream..."
            }
            val file: GpxFile
            try {
                file = gpxFileLoader.loadGpxFromInputStream(gpxInputStream)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Failed to load gpx file (possibly invalid format)")
                clearLoadingMessage()
                return@launch
            }

            viewModelScope.launch(Dispatchers.Main) {
                loadingMessage.value = "Sending..."
            }
            sendFile(file)
            clearLoadingMessage()
        }
    }

    private fun loadFromGoogleUsingFullUrl(shortGoogleUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // getting points from url did not seem to work (though i did get the full url)
            val fullUrl = getFullUrl(shortGoogleUrl)
            if (fullUrl == null) {
                snackbarHostState.showSnackbar("failed to parse google full url")
                return@launch
            }
            val points = parsePoints(fullUrl)
            println(fullUrl)
        }
    }

    // see https://stackoverflow.com/a/69865988
    fun String?.indexesOf(pat: String, ignoreCase: Boolean = true): List<Int> =
        pat.toRegex(if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
            .findAll(this ?: "")
            .map { it.range.first }
            .toList()

    private fun parsePoints(expandedUrl: String): List<Pair<Float, Float>> {
        val latLngList = arrayListOf<Pair<Float, Float>>()
        val indexes = expandedUrl.indexesOf("!3d")

        indexes.forEach {
            val coordinatesStr = expandedUrl.substring(it + 3).substringBefore("!2").split("!4d")
            latLngList.add(Pair(coordinatesStr[0].toFloat(), coordinatesStr[1].toFloat()))
        }

        return latLngList
    }

    private suspend fun sendFile(file: GpxFile) {
        viewModelScope.launch(Dispatchers.Main) {
            sendingFile.value = true
        }
        val device = deviceSelector.currentDevice()
        if (device == null) {
            // todo make this a toast or something better for the user
            snackbarHostState.showSnackbar("no devices selected")
            return
        }

        var route: Route? = null
        try {
            route = file.toRoute(snackbarHostState)
        } catch (e: Exception) {
            snackbarHostState.showSnackbar("Failed to parse route: ${e.message}")
        }

        if (route == null) {
            snackbarHostState.showSnackbar("Failed to convert to route")
            return
        }
        connection.send(device, route)
        viewModelScope.launch(Dispatchers.Main) {
            sendingFile.value = false
        }
        snackbarHostState.showSnackbar("Route sent")
    }

    fun getFullUrl(shortUrl: String): String? {
        // see https://stackoverflow.com/a/31545634
        var address = URL(shortUrl)

        //Connect & check for the location field
        var connection: HttpURLConnection? = null
        try {
            connection = address.openConnection(Proxy.NO_PROXY) as HttpURLConnection
            connection!!.instanceFollowRedirects = false
            connection!!.connect()
            val expandedURL = connection!!.getHeaderField("Location")
            if (expandedURL != null) {
                return expandedURL
            }
        } catch (e: Throwable) {
            println("Problem while expanding {}$address$e")
        } finally {
            if (connection != null) {
                println(connection.inputStream)
            }
        }

        return null
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun loadFromMapsToGpx(googleShortUrl: String): Pair<String, InputStream>? {
        val url =
            "https://mapstogpx.com/load.php?d=default&lang=en&elev=off&tmode=off&pttype=fixed&o=gpx&cmt=off&desc=off&descasname=off&w=on&dtstr=20240804_092634&gdata=" + URLEncoder.encode(
                googleShortUrl.replace("https://", "")
            )
        var address = URL(url)

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
