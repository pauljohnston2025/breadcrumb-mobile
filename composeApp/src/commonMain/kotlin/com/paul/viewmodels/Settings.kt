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
import com.paul.infrastructure.repositories.ITileRepository
import com.paul.infrastructure.repositories.RouteRepository
import com.paul.infrastructure.repositories.TileServerRepo
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

data class ConnectIqApp(val name: String, val id: String)

class Settings(
    private val deviceSelector: DeviceSelector,
    private val connection: IConnection,
    private val snackbarHostState: SnackbarHostState,
    webServerController: WebServerController,
    tileRepo: ITileRepository,
    public val routesRepo: RouteRepository,
    private val colourPaletteRepository: ColourPaletteRepository, // Inject ColourPaletteRepository
) : ViewModel() {

    val tileServerRepo = TileServerRepo(webServerController, tileRepo)

    // Expose ColourPaletteRepository flows as Compose states
    val currentColourPalette = colourPaletteRepository.currentColourPaletteFlow
    val availableColourPalettes = colourPaletteRepository.availableColourPalettesFlow

    val sendingMessage: MutableState<String> = mutableStateOf("")

    val connectIqAppId = connection.connectIqAppIdFlow()
    val availableConnectIqApps = listOf(
        ConnectIqApp("BreadcrumbDataField", "20edd04a-9fdc-4291-b061-f49d5699394d"),
        ConnectIqApp("BreadcrumbApp", "fa3e1362-11b0-4420-90cb-9ac14591bf68")
    )

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
                Napier.d("Failed to save route settings: $t")
                snackbarHostState.showSnackbar("Error saving route settings")
                // Optional: You could roll back the change here if saving fails
                // _routeSettings.value = routesRepo.getSettings()
            }
        }
    }

    fun onTileTypeSelected(tileType: TileType) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!tileServerRepo.currentlyEnabled()) {
                sendingMessage("Setting tile type") {
                    try {
                        tileServerRepo.updateCurrentTileType(tileType)
                    } catch (e: Exception) {
                        Napier.d("tile type update failed: ${e.message}")
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

            sendingMessage("Setting tile type") {
                try {
                    try {
                        tileServerRepo.updateCurrentTileType(tileType)
                    } catch (e: Exception) {
                        Napier.d("POST request failed: ${e.message}")
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
                    )
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
                sendingMessage("Setting tile server") {
                    try {
                        tileServerRepo.updateCurrentTileServer(tileServer)
                    } catch (e: Exception) {
                        Napier.d("tile server update failed: ${e.message}")
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

            sendingMessage("Setting tile server") {
                try {
                    try {
                        tileServerRepo.updateCurrentTileServer(tileServer)
                    } catch (e: Exception) {
                        Napier.d("POST request failed: ${e.message}")
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
                    )
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

    private suspend fun sendingMessage(msg: String, cb: suspend () -> Unit) {
        try {
            viewModelScope.launch(Dispatchers.Main) {
                sendingMessage.value = msg
            }
            cb()
        } catch (t: Throwable) {
            Napier.d("Failed to do operation: $msg $t")
        } finally {
            viewModelScope.launch(Dispatchers.Main) {
                sendingMessage.value = ""
            }
        }
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
            sendingMessage("Saving custom tile server...") {
                try {
                    if (tileServerRepo.saveCustomServer(tileServer) && tileServerRepo.currentlyEnabled()) {
                        watchSendTileServerChanged()
                    }
                    snackbarHostState.showSnackbar("Tile server saved successfully")
                } catch (e: Exception) {
                    Napier.e("Failed to save custom tile server", e)
                    snackbarHostState.showSnackbar("Error: Could not save tile server")
                }
            }
        }
    }

    fun onTileServerEnabledChange(newVal: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            sendingMessage("Enabling tile server") {
                var oldVal = tileServerRepo.currentlyEnabled()
                try {
                    tileServerRepo.onTileServerEnabledChange(newVal)
                } catch (t: Throwable) {
                    Napier.d("failed to update tile server enabled, reverting: $t")
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
                    )
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
                Napier.d("failed to update tile server auth token: $t")
                snackbarHostState.showSnackbar("Failed to update tile server auth token")
            }
        }
    }

    fun onRemoveCustomServer(tileServer: TileServerInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            if (tileServerRepo.onRemoveCustomServer(tileServer) && tileServerRepo.currentlyEnabled()) {
                watchSendTileServerChanged()
            }
        }
    }

    // --- Colour Palette specific functions ---
    fun onColourPaletteSelected(palette: ColourPalette) {
        viewModelScope.launch(Dispatchers.IO) {
            sendingMessage("Setting colour palette") {
                try {
                    colourPaletteRepository.updateCurrentColourPalette(palette)
                    watchSendTileServerChanged()
                } catch (e: Exception) {
                    Napier.d("colour palette update failed: ${e.message}")
                    snackbarHostState.showSnackbar("Failed to update colour palette")
                }
            }
        }
    }

    fun onAddOrUpdateCustomPalette(palette: ColourPalette) {
        viewModelScope.launch(Dispatchers.IO) {
            sendingMessage("Saving colour palette") {
                try {
                    if (colourPaletteRepository.addOrUpdateCustomPalette(palette)) {
                        watchSendTileServerChanged()
                    }
                    snackbarHostState.showSnackbar("Colour palette saved")
                } catch (e: Exception) {
                    Napier.d("failed to save custom palette: ${e.message}")
                    snackbarHostState.showSnackbar("Failed to save colour palette")
                }
            }
        }
    }

    fun onRemoveCustomPalette(palette: ColourPalette) {
        viewModelScope.launch(Dispatchers.IO) {
            sendingMessage("Removing colour palette") {
                try {
                    if (colourPaletteRepository.removeCustomPalette(palette)) {
                        watchSendTileServerChanged()
                    }
                    snackbarHostState.showSnackbar("Colour palette removed")
                } catch (e: Exception) {
                    Napier.d("failed to remove custom palette: ${e.message}")
                    snackbarHostState.showSnackbar("Failed to remove colour palette")
                }
            }
        }
    }

    suspend fun watchSendTileServerChanged() {
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
            )
        }
    }
}