package com.paul.viewmodels

import io.github.aakira.napier.Napier
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paul.domain.IqDevice
import com.paul.infrastructure.connectiq.ConnectIqNeedsInstall
import com.paul.infrastructure.connectiq.ConnectIqNeedsUpdate
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.connectiq.IDeviceList
import com.paul.protocol.fromdevice.ProtocolResponse
import com.paul.protocol.fromdevice.Settings
import com.paul.protocol.todevice.RequestSettings
import com.paul.ui.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

sealed class NavigationEvent {
    // Represents a command to navigate to a specific route
    data class NavigateTo(val route: String) : NavigationEvent()

    // Represents a command to pop the back stack
    object PopBackStack : NavigationEvent()
}

class DeviceSelector(
    private val connection: IConnection,
    private val deviceList: IDeviceList,
    private val snackbarHostState: SnackbarHostState,
) :
    ViewModel() {
    var currentDevice: MutableState<IqDevice?> = mutableStateOf(null)
    private var devicesFlow: MutableStateFlow<List<IqDevice>> = MutableStateFlow(listOf())
    private var currentDevicePoll: Job? = null
    val settingsLoading: MutableState<Boolean> = mutableStateOf(false)
    val errorMessage: MutableState<String> = mutableStateOf("")
    var lastLoadedSettings: Settings? = null

    // apparently passing navigation to the view models is not safe, and can lead to navigation quirks
    // before i did this there was a bug where the device settings would remain open
    // reproduction:
    // open device page
    // click settings cog (device settings appears)
    // click hamburger menu and open any other page
    // click hamburger menu and open devices page, its stuck on settings for some reason
    private val _navigationEvents = MutableSharedFlow<NavigationEvent>()
    val navigationEvents: SharedFlow<NavigationEvent> = _navigationEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            try {
                Napier.d("launching list")
                val sub = deviceList.subscribe()
                sub.collect {
                    Napier.d("got device ${it.size} $it")
                    devicesFlow.emit(it)
                }
            } catch (t: ConnectIqNeedsUpdate) {
                errorMessage.value = "Please update the garmin connect app"
                Napier.d("failed to subscribe to device list $t")
            } catch (t: ConnectIqNeedsInstall) {
                errorMessage.value = "Please install the garmin connect app"
                Napier.d("failed to subscribe to device list $t")
            } catch (t: Throwable) {
                Napier.d("failed to subscribe to device list $t")
            }
        }.invokeOnCompletion {
            Napier.d("list completed")
        }
    }

    fun devicesFlow(): Flow<List<IqDevice>> {
        return devicesFlow
    }

    override fun onCleared() {
        Napier.d("view model cleared")
    }

    fun cancelSelection() {
        currentDevicePoll?.cancel()
    }

    suspend fun currentDevice(): IqDevice? {
        if (currentDevice.value == null) {
            selectDevice()
        }

        currentDevicePoll?.cancel()
        withContext(Dispatchers.IO) {
            currentDevicePoll = launch {
                // wait for the device to be selected
                try {
                    withTimeout(10000) {
                        while (currentDevice.value == null) {
                            delay(1000);
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    _navigationEvents.emit(NavigationEvent.PopBackStack)
                }
            }
        }

        currentDevicePoll?.join()

        return currentDevice.value
    }

    fun selectDeviceUi() {
        viewModelScope.launch { selectDevice() }
    }

    private fun selectDevice() {
        viewModelScope.launch(Dispatchers.Main) {
            _navigationEvents.emit(NavigationEvent.NavigateTo(Screen.DeviceSelection.route))
        }
    }

    fun openDeviceSettings(device: IqDevice) {
        currentDevice.value = device
        settingsLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            openDeviceSettingsSuspend(device)
        }
    }

    suspend fun openDeviceSettingsSuspend(device: IqDevice) {
        try {
            val settings = connection.query<Settings>(
                device,
                RequestSettings(),
                ProtocolResponse.PROTOCOL_SEND_SETTINGS
            )
            lastLoadedSettings = settings
            Napier.d("got settings $settings")
            settingsLoading.value = false
            // INSTEAD of navigating, emit an event to the UI
            _navigationEvents.emit(NavigationEvent.NavigateTo(Screen.DeviceSettings.route))
        } catch (t: Throwable) {
            // most likely a timeout exception
            settingsLoading.value = false
            snackbarHostState.showSnackbar("Failed to load settings. Please ensure an activity is running on the watch.")
        }
    }

    fun onDeviceSettingsClosed() {
        lastLoadedSettings = null
    }

    fun onDeviceSelected(device: IqDevice, selectingDevice: Boolean) {
        currentDevice.value = device
        if (selectingDevice) {
            viewModelScope.launch {
                _navigationEvents.emit(NavigationEvent.PopBackStack)
            }
        }
    }
}
