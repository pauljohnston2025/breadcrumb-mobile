// com.paul.viewmodels.Settings.kt
package com.paul.viewmodels

import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paul.domain.ColourPalette
import com.paul.domain.RouteSettings
import com.paul.domain.TileServerInfo
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.repositories.ColourPaletteRepository
import com.paul.infrastructure.repositories.GeneralSettingsRepository
import com.paul.infrastructure.repositories.ITileRepository
import com.paul.infrastructure.repositories.RouteRepository
import com.paul.infrastructure.repositories.StravaRepository
import com.paul.infrastructure.repositories.TileServerRepo
import com.paul.infrastructure.repositories.TileServerRepo.Companion.defaultWebPort
import com.paul.infrastructure.service.SendMessageHelper
import com.paul.infrastructure.web.TileType
import com.paul.infrastructure.web.WebServerController
import com.paul.protocol.todevice.CompanionAppTileServerChanged
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class Settings(
    private val deviceSelector: DeviceSelector,
    private val connection: IConnection,
    private val snackbarHostState: SnackbarHostState,
    webServerController: WebServerController,
    tileRepo: ITileRepository,
    public val routesRepo: RouteRepository,
    public val generalSettingsRepo: GeneralSettingsRepository,
    private val colourPaletteRepository: ColourPaletteRepository,
    public val stravaRepo: StravaRepository,
) : ViewModel() {
    companion object {
        private const val TAG = "SettingsViewModel"
    }

    val tileServerRepo = TileServerRepo(webServerController, tileRepo)

    // Expose ColourPaletteRepository flows as Compose states
    val currentColourPalette = colourPaletteRepository.currentColourPaletteFlow
    val availableColourPalettes = colourPaletteRepository.availableColourPalettesFlow

    val sendingMessage: MutableState<String> = mutableStateOf("")

    val connectIqAppId = connection.connectIqAppIdFlow()

    val webServerPortFlow = tileServerRepo.webServerPortFlow()

    val stravaClientId = stravaRepo.clientId

    val stravaClientSecret = stravaRepo.clientSecret

    fun stravaLogin() {
        viewModelScope.launch(Dispatchers.IO) {
            // Check if credentials exist before trying to login
            if (stravaRepo.getClientId().isBlank() || stravaRepo.getClientSecret().isBlank()) {
                snackbarHostState.showSnackbar("Please enter Strava Client ID and Secret first")
                return@launch
            }

            // This triggers the browser launch logic we discussed earlier
            // Note: stravaRepo.login() here should trigger the URL building
            stravaRepo.launchAuthFlow()
        }
    }

    fun onStravaClientIdChange(newId: String) {
        stravaRepo.saveClientId(newId)
    }

    fun onStravaClientSecretChange(newSecret: String) {
        stravaRepo.saveClientSecret(newSecret)
    }

    fun syncStravaActivities() {
        if (stravaRepo.getClientId().isBlank() || stravaRepo.getClientSecret().isBlank()) {
            viewModelScope.launch {
                snackbarHostState.showSnackbar("Please enter Strava Client ID and Secret first")
            }
            return
        }
        stravaRepo.startSyncActivities()
    }

    fun stopSyncStravaActivities() {
        stravaRepo.stopSyncActivities()
    }

    fun clearStravaCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                stravaRepo.clearAllStravaData()
            } catch (e: Exception) {
                Napier.e("Clear strava data failed", e, tag = TAG)
                snackbarHostState.showSnackbar("Clear strava data failed: ${e.message}")
            }
        }
    }

    fun onWebPortChange(newPort: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            tileServerRepo.updateWebPort(newPort)
        }
    }

    fun onConnectIqAppIdChange(newAppId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            connection.updateConnectIqAppId(newAppId)
            snackbarHostState.showSnackbar("Connect IQ App ID updated")
        }
    }

    /**
     * Updates the route settings and persists them to the repository.
     */
    fun onRouteSettingsChanged(newSettings: RouteSettings) {
        // Launch a coroutine to save the settings
        viewModelScope.launch(Dispatchers.IO) {
            try {
                routesRepo.saveSettings(newSettings)
            } catch (t: Throwable) {
                Napier.e("Failed to save route settings", t, tag = TAG)
                snackbarHostState.showSnackbar("Error saving route settings")
            }
        }
    }

    fun onGeneralSettingsChanged(newSettings: com.paul.domain.GeneralSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                generalSettingsRepo.saveSettings(newSettings)
            } catch (t: Throwable) {
                Napier.e("Failed to save general settings", t, tag = TAG)
                snackbarHostState.showSnackbar("Error saving general settings")
            }
        }
    }

    fun onTileTypeSelected(tileType: TileType) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!tileServerRepo.currentlyEnabled()) {
                sendingMessage("Setting tile type") { _ ->
                    try {
                        tileServerRepo.updateCurrentTileType(tileType)
                    } catch (e: Exception) {
                        Napier.e("tile type update failed", e, tag = TAG)
                        snackbarHostState.showSnackbar("Failed to update tile type")
                        return@sendingMessage
                    }
                }
                return@launch
            }
            // we also need to tell the watch about the changes
            val device = deviceSelector.currentDevice()
            if (device == null) {
                snackbarHostState.showSnackbar("no devices selected")
                return@launch
            }

            val baseMsg = "Setting tile type"
            sendingMessage(baseMsg) { updateMsg ->
                try {
                    try {
                        tileServerRepo.updateCurrentTileType(tileType)
                    } catch (e: Exception) {
                        Napier.e("POST request failed for tile type update", e, tag = TAG)
                        snackbarHostState.showSnackbar("Failed to update tile type")
                        return@sendingMessage
                    }
                    val tilServer = tileServerRepo.currentServerFlow().value
                    val currentPalette = colourPaletteRepository.currentColourPaletteFlow.value
                    connection.send(
                        device, CompanionAppTileServerChanged(
                            tilServer.tileLayerMin,
                            tilServer.tileLayerMax,
                            // always send it even if we are not in 'TILE_DATA_TYPE_64_COLOUR format, since it will be the next pallet to use if that mode is enabled'
                            // though when tile type changes we send this again :shrug:
                            currentPalette
                        )
                    ) { appName ->
                        updateMsg("$appName\n$baseMsg")
                    }
                } catch (t: TimeoutCancellationException) {
                    snackbarHostState.showSnackbar("Timed out setting tile type")
                    return@sendingMessage
                } catch (t: Throwable) {
                    snackbarHostState.showSnackbar("Failed to send to selected device")
                    return@sendingMessage
                }
                snackbarHostState.showSnackbar("Tile type updated")
            }
        }
    }

    fun onServerSelected(tileServer: TileServerInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!tileServerRepo.currentlyEnabled()) {
                sendingMessage("Setting tile server") { _ ->
                    try {
                        tileServerRepo.updateCurrentTileServer(tileServer)
                    } catch (e: Exception) {
                        Napier.e("tile server update failed", e, tag = TAG)
                        snackbarHostState.showSnackbar("Failed to update tile server")
                        return@sendingMessage
                    }
                }
                return@launch
            }

            // we also need to tell the watch about the changes
            val device = deviceSelector.currentDevice()
            if (device == null) {
                snackbarHostState.showSnackbar("no devices selected")
                return@launch
            }

            val baseMsg = "Setting tile server"
            sendingMessage(baseMsg) { updateMsg ->
                try {
                    try {
                        tileServerRepo.updateCurrentTileServer(tileServer)
                    } catch (e: Exception) {
                        Napier.e("POST request failed for tile server update", e, tag = TAG)
                        snackbarHostState.showSnackbar("Failed to update tile server")
                        return@sendingMessage
                    }
                    val currentPalette = colourPaletteRepository.currentColourPaletteFlow.value
                    connection.send(
                        device, CompanionAppTileServerChanged(
                            tileServer.tileLayerMin,
                            tileServer.tileLayerMax,
                            // always send it even if we are not in 'TILE_DATA_TYPE_64_COLOUR format, since it will be the next pallet to use if that mode is enabled'
                            // though when tile type changes we send this again :shrug:
                            currentPalette
                        )
                    ) { appName ->
                        updateMsg("$appName\n$baseMsg")
                    }
                } catch (t: TimeoutCancellationException) {
                    snackbarHostState.showSnackbar("Timed out setting tile server")
                    return@sendingMessage
                } catch (t: Throwable) {
                    snackbarHostState.showSnackbar("Failed to send to selected device")
                    return@sendingMessage
                }
                snackbarHostState.showSnackbar("Tile server updated")
            }
        }
    }

    private suspend fun sendingMessage(msg: String, cb: suspend (updateMsg: suspend (String) -> Unit) -> Unit) {
        SendMessageHelper.sendingMessage(viewModelScope, sendingMessage, msg, cb)
    }

    /**
     * Handles both creating a new custom tile server and updating an existing one.
     * It calls the repository's unified save method.
     *
     * @param tileServer The TileServerInfo object to be saved. If its ID already exists
     *                   in the repository, it will be updated; otherwise, it will be added.
     */
    fun onSaveCustomServer(tileServer: TileServerInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val baseMsg = "Saving custom tile server..."
            sendingMessage(baseMsg) { updateMsg ->
                try {
                    if (tileServerRepo.saveCustomServer(tileServer) && tileServerRepo.currentlyEnabled()) {
                        watchSendTileServerChanged(baseMsg, updateMsg)
                    }
                    snackbarHostState.showSnackbar("Tile server saved successfully")
                } catch (e: Exception) {
                    Napier.e("Failed to save custom tile server", e, tag = TAG)
                    snackbarHostState.showSnackbar("Error: Could not save tile server")
                }
            }
        }
    }

    fun onTileServerEnabledChange(newVal: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val baseMsg = "Enabling tile server"
            sendingMessage(baseMsg) { updateMsg ->
                var oldVal = tileServerRepo.currentlyEnabled()
                try {
                    tileServerRepo.onTileServerEnabledChange(newVal)
                } catch (t: Throwable) {
                    Napier.e("failed to update tile server enabled, reverting", t, tag = TAG)
                    snackbarHostState.showSnackbar("Failed to stop/start tile server")
                    launch(Dispatchers.Main) {
                        tileServerRepo.rollBackEnabled(oldVal)
                    }
                }

                if (!newVal) {
                    return@sendingMessage
                }

                // we also need to tell the watch about the changes
                val device = deviceSelector.currentDevice()
                if (device == null) {
                    snackbarHostState.showSnackbar("no devices selected")
                    return@sendingMessage
                }

                try {

                    val tilServer = tileServerRepo.currentServerFlow().value
                    val currentPalette = colourPaletteRepository.currentColourPaletteFlow.value
                    connection.send(
                        device, CompanionAppTileServerChanged(
                            tilServer.tileLayerMin,
                            tilServer.tileLayerMax,
                            // always send it even if we are not in 'TILE_DATA_TYPE_64_COLOUR format, since it will be the next pallet to use if that mode is enabled'
                            // though when tile type changes we send this again :shrug:
                            currentPalette
                        )
                    ) { appName ->
                        updateMsg("$appName\n$baseMsg")
                    }
                } catch (t: TimeoutCancellationException) {
                    snackbarHostState.showSnackbar("Timed out enabling tile server")
                    return@sendingMessage
                } catch (t: Throwable) {
                    snackbarHostState.showSnackbar("Failed to send to selected device")
                    return@sendingMessage
                }
                snackbarHostState.showSnackbar("Tile server updated")
            }
        }
    }

    fun onAuthKeyChange(newVal: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                tileServerRepo.updateAuthToken(newVal)
            } catch (t: Throwable) {
                Napier.e("failed to update tile server auth token", t, tag = TAG)
                snackbarHostState.showSnackbar("Failed to update tile server auth token")
            }
        }
    }

    fun onRemoveCustomServer(tileServer: TileServerInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val baseMsg = "Setting colour palette"
            sendingMessage(baseMsg) { updateMsg ->
                if (tileServerRepo.onRemoveCustomServer(tileServer) && tileServerRepo.currentlyEnabled()) {
                    watchSendTileServerChanged(baseMsg, updateMsg)
                }
            }
        }
    }

    // --- Colour Palette specific functions ---
    fun onColourPaletteSelected(palette: ColourPalette) {
        viewModelScope.launch(Dispatchers.IO) {
            val baseMsg = "Setting colour palette"
            sendingMessage(baseMsg) { updateMsg ->
                try {
                    colourPaletteRepository.updateCurrentColourPalette(palette)
                    watchSendTileServerChanged(baseMsg, updateMsg)
                } catch (e: Exception) {
                    Napier.e("colour palette update failed", e, tag = TAG)
                    snackbarHostState.showSnackbar("Failed to update colour palette")
                }
            }
        }
    }

    fun onAddOrUpdateCustomPalette(palette: ColourPalette) {
        viewModelScope.launch(Dispatchers.IO) {
            val baseMsg = "Saving colour palette"
            sendingMessage(baseMsg) { updateMsg ->
                try {
                    if (colourPaletteRepository.addOrUpdateCustomPalette(palette)) {
                        watchSendTileServerChanged(baseMsg, updateMsg)
                    }
                    snackbarHostState.showSnackbar("Colour palette saved")
                } catch (e: Exception) {
                    Napier.e("failed to save custom palette", e, tag = TAG)
                    snackbarHostState.showSnackbar("Failed to save colour palette")
                }
            }
        }
    }

    fun onRemoveCustomPalette(palette: ColourPalette) {
        viewModelScope.launch(Dispatchers.IO) {
            val baseMsg = "Removing colour palette"
            sendingMessage(baseMsg) { updateMsg ->
                try {
                    if (colourPaletteRepository.removeCustomPalette(palette)) {
                        watchSendTileServerChanged(baseMsg, updateMsg)
                    }
                    snackbarHostState.showSnackbar("Colour palette removed")
                } catch (e: Exception) {
                    Napier.e("failed to remove custom palette", e, tag = TAG)
                    snackbarHostState.showSnackbar("Failed to remove colour palette")
                }
            }
        }
    }

    suspend fun watchSendTileServerChanged(baseMsg: String, updateMsg: suspend (String) -> Unit) {
        // If tile server is enabled, send update to watch
        if (tileServerRepo.currentlyEnabled()) {
            val device = deviceSelector.currentDevice()
            if (device == null) {
                snackbarHostState.showSnackbar("no devices selected")
                return
            }
            val tilServer = tileServerRepo.currentServerFlow().value
            val currentPalette = colourPaletteRepository.currentColourPaletteFlow.value
            connection.send(
                device, CompanionAppTileServerChanged(
                    tilServer.tileLayerMin,
                    tilServer.tileLayerMax,
                    // always send it even if we are not in 'TILE_DATA_TYPE_64_COLOUR format, since it will be the next pallet to use if that mode is enabled'
                    // though when tile type changes we send this again :shrug:
                    currentPalette
                )
            ) { appName ->
                updateMsg("$appName\n$baseMsg")
            }
        }
    }
}