package com.paul.viewmodels

import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benasher44.uuid.uuid4
import com.paul.domain.HistoryItem
import com.paul.domain.IRoute
import com.paul.domain.RouteType
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.repositories.HistoryRepository
import com.paul.infrastructure.repositories.KomootRepository
import com.paul.infrastructure.repositories.RouteRepository
import com.paul.infrastructure.service.IFileHelper
import com.paul.infrastructure.service.IGpxFileLoader
import com.paul.infrastructure.web.KtorClient
import com.paul.protocol.todevice.Route
import com.russhwolf.settings.Settings
import io.github.aakira.napier.Napier
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import kotlin.uuid.ExperimentalUuidApi

class StartViewModel(
    private val connection: IConnection,
    private val deviceSelector: DeviceSelector,
    private val gpxFileLoader: IGpxFileLoader,
    private val fileHelper: IFileHelper,
    private val snackbarHostState: SnackbarHostState
) : ViewModel() {
    val settings: Settings = Settings()

    val sendingFile: MutableState<String> = mutableStateOf("")
    val errorMessage: MutableState<String> = mutableStateOf("")
    val htmlErrorMessage: MutableState<String> = mutableStateOf("")
    private val client = KtorClient.client // Get the singleton client instance
    private val komootRepo = KomootRepository()
    val routeRepo = RouteRepository(fileHelper, gpxFileLoader)
    val historyRepo = HistoryRepository()

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
            loadGpxFile(fileLoad)
        }

        if (shortGoogleUrl != null) {
            loadFromGoogle(shortGoogleUrl)
        }

        if (komootUrl != null) {
            loadFromKomoot(komootUrl)
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

            loadGpxFile(uri)
        }
    }

    fun loadFileFromHistory(historyItem: HistoryItem) {
        val routeEntry = routeRepo.getRoute(historyItem.routeId)
        if (routeEntry == null) {
            viewModelScope.launch(Dispatchers.Main) {
                snackbarHostState.showSnackbar("Failed to find route for history item")
            }
            Napier.d("route entry not found")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            sendingMessage("Loading route from history...") {
                val route = when (routeEntry.type) {
                    RouteType.GPX -> routeRepo.getGpxRoute(historyItem.routeId)
                    RouteType.COORDINATES -> routeRepo.getCoordinatesRoute(historyItem.routeId)
                }
                if (route == null) {
                    snackbarHostState.showSnackbar("Failed to find route for history item")
                    Napier.d("route file not found")
                    return@sendingMessage
                }

                sendRoute(route)
            }
        }
    }

    fun loadGpxFile(fileName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            sendingMessage("Parsing gpx input stream...") {
                try {
                    val fileContents = fileHelper.readFile(fileName)!!
                    val gpxRoute = gpxFileLoader.loadGpxFromBytes(fileContents)
                    sendRoute(gpxRoute)
                } catch (e: SecurityException) {
                    snackbarHostState.showSnackbar("Failed to load gpx file (you might not have permissions, please restart app to grant)")
                    Napier.d(e.toString())
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to load gpx file (possibly invalid format)")
                    Napier.d(e.toString())
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
                    val gpxRoute = try {
                        komootRepo.getRoute(komootUrl)
                    } catch (e: Exception) {
                        Napier.d("failed to load komoot url $e")
                        snackbarHostState.showSnackbar("Failed to load komoot url")
                        return@launch
                    }

                    if (gpxRoute != null) {
                        sendRoute(gpxRoute)
                    }
                }
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
                sendRoute(gpxRoute)
            } catch (e: SecurityException) {
                snackbarHostState.showSnackbar("Failed to load gpx file (you might not have permissions, please restart app to grant)")
                Napier.d(e.toString())
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Failed to load gpx file (possibly invalid format)")
                Napier.d(e.toString())
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun sendRoute(
        gpxRoute: IRoute
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
                routeRepo.saveRoute(gpxRoute)
                val historyItem = HistoryItem(
                    uuid4().toString(),
                    gpxRoute.id,
                    Clock.System.now()
                )
                historyRepo.add(historyItem)
                route = gpxRoute.toRoute(snackbarHostState)
            } catch (e: Exception) {
                Napier.d("Failed to parse route: ${e.message}")
            }
        }

        if (route == null) {
            snackbarHostState.showSnackbar("Failed to convert to route")
            return
        }

        sendingMessage("Sending file") {
            try {
                val version = connection.appInfo(device).version
                if (version > 9 || version == 0
                /** simulator */
                ) {
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
            Napier.d("Failed to do operation: $msg $t")
        } finally {
            viewModelScope.launch(Dispatchers.Main) {
                sendingFile.value = ""
            }
        }
    }

    private suspend fun loadFromMapsToGpx(googleShortUrl: String): Pair<String, ByteArray>? {
        val url =
            "https://mapstogpx.com/load.php?d=default&lang=en&elev=off&tmode=off&pttype=fixed&o=gpx&cmt=off&desc=off&descasname=off&w=on&dtstr=20240804_092634&gdata=" + URLEncoder.encode(
                googleShortUrl.replace("https://", ""),
                "UTF-8"
            )
        try {
            val response = client.get(url) {
                header("Referer", "https://mapstogpx.com/")
            }
            if (!response.status.isSuccess()) {
                return null
            }
            return withContext(Dispatchers.IO) {
                val res = Pair(
                    response.contentType().toString(),
                    response.bodyAsChannel().toByteArray()
                )

                return@withContext res
            }
        } catch (e: Throwable) {
            Napier.d("Problem while loading maps from mapstogpx $e")
        }

        return null
    }

    fun clearHistory() {
        historyRepo.clear()
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
