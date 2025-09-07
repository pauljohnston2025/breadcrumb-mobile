package com.paul.viewmodels

import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paul.domain.RouteSettings
import com.paul.domain.TileServerInfo
import com.paul.infrastructure.connectiq.IConnection
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow


class Settings(
    private val deviceSelector: DeviceSelector,
    private val connection: IConnection,
    private val snackbarHostState: SnackbarHostState,
    webServerController: WebServerController,
    tileRepo: ITileRepository,
    public val routesRepo: RouteRepository,
) : ViewModel() {

    val tileServerRepo = TileServerRepo(webServerController, tileRepo)

    val sendingMessage: MutableState<String> = mutableStateOf("")

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

            sendingMessage("Setting tile server") {
                try {
                    try {
                        tileServerRepo.updateCurrentTileType(tileType)
                    } catch (e: Exception) {
                        Napier.d("POST request failed: ${e.message}")
                        snackbarHostState.showSnackbar("Failed to update tile type")
                        return@sendingMessage
                    }
                    val tilServer = tileServerRepo.currentServerFlow().value
                    connection.send(
                        device, CompanionAppTileServerChanged(
                            tilServer.tileLayerMin,
                            tilServer.tileLayerMax,
                        )
                    )
                } catch (t: TimeoutCancellationException) {
                    snackbarHostState.showSnackbar("Timed out clearing tile cache")
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
                    connection.send(
                        device, CompanionAppTileServerChanged(
                            tileServer.tileLayerMin,
                            tileServer.tileLayerMax,
                        )
                    )
                } catch (t: TimeoutCancellationException) {
                    snackbarHostState.showSnackbar("Timed out clearing tile cache")
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

    fun onAddCustomServer(tileServer: TileServerInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            tileServerRepo.onAddCustomServer(tileServer)
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
                    connection.send(
                        device, CompanionAppTileServerChanged(
                            tilServer.tileLayerMin,
                            tilServer.tileLayerMax,
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
            tileServerRepo.onRemoveCustomServer(tileServer)
        }
    }
}