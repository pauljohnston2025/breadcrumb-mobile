package com.paul.viewmodels

import android.util.Log
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paul.domain.TileServerInfo
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.repositories.TileServerRepo
import com.paul.infrastructure.web.ChangeTileServer
import com.paul.infrastructure.web.WebServerController
import com.paul.protocol.todevice.DropTileCache
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json


class Settings(
    private val deviceSelector: DeviceSelector,
    private val connection: IConnection,
    private val snackbarHostState: SnackbarHostState,
    private val webServerController: WebServerController
) : ViewModel() {

    val tileServerRepo = TileServerRepo(webServerController)

    val sendingMessage: MutableState<String> = mutableStateOf("")

    fun onServerSelected(tileServer: TileServerInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            val device = deviceSelector.currentDevice()
            if (device == null) {
                // todo make this a toast or something better for the user
                snackbarHostState.showSnackbar("no devices selected")
                return@launch
            }

            sendingMessage("Setting tile server") {
                try {
                    try {
                        tileServerRepo.updateCurrentTileServer(tileServer)
                    } catch (e: Exception) {
                        println("POST request failed: ${e.message}")
                        snackbarHostState.showSnackbar("Failed to update tile server")
                        return@sendingMessage
                    }
                    connection.send(device, DropTileCache())

                    // todo: tell watch to dump tile cache
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
            Log.d("stdout", "Failed to do operation: $msg $t")
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
            var oldVal = tileServerRepo.currentlyEnabled()
            try {
                tileServerRepo.onTileServerEnabledChange(newVal)
            } catch (t: Throwable) {
                Log.d("stdout", "failed to update tile server enabled, reverting: $t")
                snackbarHostState.showSnackbar("Failed to stop/start tile server")
                launch(Dispatchers.Main) {
                    tileServerRepo.rollBackEnabled(oldVal)
                }
            }
        }
    }

    fun onRemoveCustomServer(tileServer: TileServerInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            tileServerRepo.onRemoveCustomServer(tileServer)
        }
    }
}
