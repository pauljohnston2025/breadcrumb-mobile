package com.paul.viewmodels

import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.paul.domain.HistoryItem
import com.paul.domain.IRoute
import com.paul.domain.RouteEntry
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.repositories.HistoryRepository
import com.paul.infrastructure.repositories.KomootRepository
import com.paul.infrastructure.repositories.RouteRepository
import com.paul.infrastructure.service.IFileHelper
import com.paul.infrastructure.service.IGpxFileLoader
import com.paul.infrastructure.service.SendMessageHelper
import com.paul.infrastructure.service.SendRoute
import com.paul.infrastructure.web.KtorClient
import com.paul.ui.Screen
import com.russhwolf.settings.Settings
import io.github.aakira.napier.Napier
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class StartViewModel(
    private val connection: IConnection,
    private val deviceSelector: DeviceSelector,
    private val gpxFileLoader: IGpxFileLoader,
    private val fileHelper: IFileHelper,
    private val snackbarHostState: SnackbarHostState,
    private val navController: NavController,
    private val mapViewModel: MapViewModel,
) : ViewModel() {
    val settings: Settings = Settings()

    val sendingFile: MutableState<String> = mutableStateOf("")
    val errorMessage: MutableState<String> = mutableStateOf("")
    val htmlErrorMessage: MutableState<String> = mutableStateOf("")
    private val client = KtorClient.client // Get the singleton client instance
    private val komootRepo = KomootRepository()
    val routeRepo = RouteRepository(fileHelper, gpxFileLoader)
    val historyRepo = HistoryRepository()
    private val _deletingHistoryItem = MutableStateFlow<HistoryItem?>(null)
    val deletingHistoryItem: StateFlow<HistoryItem?> = _deletingHistoryItem.asStateFlow()

    fun requestDelete(historyItem: HistoryItem) {
        _deletingHistoryItem.value = historyItem
    }

    fun cancelDelete() {
        _deletingHistoryItem.value = null
    }

    fun confirmDelete() {
        viewModelScope.launch(Dispatchers.IO) {
            _deletingHistoryItem.value?.let { historyItemToDelete ->
                historyRepo.delete(historyItemToDelete.id)
            }
            _deletingHistoryItem.value = null // Close dialog
        }
    }

    fun load(
        fileLoad: String?,
        shortGoogleUrl: String?,
        komootUrl: String?,
        initialErrorMessage: String?
    ) {
        // return to the main overview screen if we get a gpx route, or any other load call come in
        if (navController.currentDestination != null) {
            navController.navigate(Screen.Start.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }

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

    fun previewRoute(route: RouteEntry) {
        //todo : horrible copy pasta below
        viewModelScope.launch(Dispatchers.IO) {
            var iRoute = routeRepo.getRouteI(route.id)
            if (iRoute == null) {
                snackbarHostState.showSnackbar("Unknown route")
                return@launch
            }
            var coords = iRoute.toRoute(snackbarHostState)
            if (coords == null) {
                snackbarHostState.showSnackbar("Bad coordinates")
                return@launch
            }
            mapViewModel.displayRoute(coords)
        }

        // Navigate if necessary
        val current = navController.currentDestination
        if (current?.route != Screen.Map.route) {
            navController.navigate(Screen.Map.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    inclusive = true
                }
                launchSingleTop = true
            }
        }
    }

    fun loadFileFromHistory(historyItem: HistoryItem) {
        val routeEntry = routeRepo.getRouteEntry(historyItem.routeId)
        if (routeEntry == null) {
            viewModelScope.launch(Dispatchers.Main) {
                snackbarHostState.showSnackbar("Failed to find route for history item")
            }
            Napier.d("route entry not found")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            sendingMessage("Loading route from history...") {
                val route = routeRepo.getRouteI(historyItem.routeId)
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

    private suspend fun sendRoute(
        gpxRoute: IRoute
    ) {
        SendRoute.sendRoute(
            gpxRoute,
            deviceSelector,
            snackbarHostState,
            connection,
            routeRepo,
            historyRepo
        )
        { msg, cb ->
            this.sendingMessage(msg, cb)
        }
    }

    private suspend fun sendingMessage(msg: String, cb: suspend () -> Unit) {
        SendMessageHelper.sendingMessage(viewModelScope, sendingFile, msg, cb)
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
