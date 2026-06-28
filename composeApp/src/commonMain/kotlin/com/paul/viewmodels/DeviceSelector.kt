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
import kotlinx.coroutines.flow.first
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

    companion object {
        private const val TAG = "DeviceSelector"
    }

    var currentDevice: MutableState<IqDevice?> = mutableStateOf(null)
    private var devicesFlow: MutableStateFlow<List<IqDevice>> = MutableStateFlow(listOf())
    private var currentDevicePoll: Job? = null
    val settingsLoading: MutableState<String?> = mutableStateOf(null)
    public var settingsJob: Job? = null
    val errorMessage: MutableState<String> = mutableStateOf("")
    var lastLoadedSettings: Settings? = null
    var lastLoadedAppId: String? = null

    // apparently passing navigation to the view models is not safe, and can lead to navigation quirks
    // before i did this there was a bug where the device settings would remain open
    // reproduction:
    // open device page
    // click settings cog (device settings appears)
    // click hamburger menu and open any other page
    // click hamburger menu and open devices page, its stuck on settings for some reason
    private val _navigationEvents = MutableSharedFlow<NavigationEvent>()
    val navigationEvents: SharedFlow<NavigationEvent> = _navigationEvents.asSharedFlow()
    private var deviceListJob: Job? = null

    init {
        viewModelScope.launch {
            devicesFlow.subscriptionCount.collect { count ->
                if (count > 0 && deviceListJob == null) {
                    deviceListJob = launch {
                        try {
                            Napier.i("Subscribing to device list", tag = TAG)
                            val sub = deviceList.subscribe()
                            sub.collect {
                                Napier.v("Received device list update: ${it.size} devices", tag = TAG)
                                devicesFlow.emit(it)
                            }
                        } catch (t: ConnectIqNeedsUpdate) {
                            errorMessage.value = "Please update the garmin connect app"
                            Napier.e("ConnectIQ needs update", t, tag = TAG)
                        } catch (t: ConnectIqNeedsInstall) {
                            errorMessage.value = "Please install the garmin connect app"
                            Napier.e("ConnectIQ needs install", t, tag = TAG)
                        } catch (t: Throwable) {
                            Napier.e("Failed to subscribe to device list", t, tag = TAG)
                        } finally {
                            Napier.v("Device list subscription completed", tag = TAG)
                        }
                    }
                } else if (count == 0) {
                    deviceListJob?.cancel()
                    deviceListJob = null
                }
            }
        }
    }

    fun devicesFlow(): Flow<List<IqDevice>> {
        return devicesFlow
    }

    override fun onCleared() {
        Napier.v("DeviceSelector onCleared", tag = TAG)
    }

    fun cancelSelection() {
        currentDevicePoll?.cancel()
    }

    suspend fun currentDevice(): IqDevice? {
        if (currentDevice.value == null) {
            // Manual subscription to trigger the poll if not already running
            val triggerJob = viewModelScope.launch {
                devicesFlow.collect { }
            }

            // Wait until the device list has performed at least one poll
            try {
                withTimeout(5000) {
                    deviceList.isLoaded.first { it }
                }

                // If we found exactly one device, wait a moment to see if it connects automatically
                if (devicesFlow.value.size == 1 && devicesFlow.value[0].status != "CONNECTED") {
                    withTimeout(1000) {
                        devicesFlow.first { it.size == 1 && it[0].status == "CONNECTED" }
                    }
                }
            } catch (e: Exception) {
                // Timeout is fine, we'll check the current state below
            }

            val knownDevices = devicesFlow.value
            // most users will only have 1 device, so just select it for them
            if (knownDevices.size == 1 && knownDevices[0].status == "CONNECTED") {
                currentDevice.value = knownDevices[0]
            } else {
                selectDevice()
            }

            triggerJob.cancel()
        }

        currentDevicePoll?.cancel()
        withContext(Dispatchers.IO) {
            currentDevicePoll = launch {
                // wait for the device to be selected
                try {
                    withTimeout(10000) {
                        while (currentDevice.value == null) {
                            delay(1000)
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
        settingsJob = viewModelScope.launch(Dispatchers.IO) {
            openDeviceSettingsSuspend(device)
        }
    }

    suspend fun openDeviceSettingsSuspend(device: IqDevice, onProgress: (suspend (String) -> Unit)? = null) {
        settingsLoading.value = "Loading Settings From Device.\nEnsure the datafield/app is running (or at least open) or this will fail. Note: this can take a long time (up to 5 minutes) on older devices, be patient. Press back to cancel"
        try {
            val result = connection.query<Settings>(
                device,
                RequestSettings(),
                ProtocolResponse.PROTOCOL_SEND_SETTINGS
            ) { appName ->
                if (onProgress != null)
                {
                    onProgress(appName)
                    return@query
                }
                if (settingsLoading.value != null)
                {
                    val baseMsg = (settingsLoading.value ?: "")
                    settingsLoading.value = "$appName\n$baseMsg"
                }
            }
            lastLoadedSettings = result.response
            lastLoadedAppId = result.appId
            Napier.i("Successfully loaded device settings for ${result.appId}", tag = TAG)
            settingsLoading.value = null
            // INSTEAD of navigating, emit an event to the UI
            _navigationEvents.emit(NavigationEvent.NavigateTo(Screen.DeviceSettings.route))
        } catch (t: Throwable) {
            // most likely a timeout exception
            Napier.e("Failed to load device settings", t, tag = TAG)
            settingsLoading.value = null
            snackbarHostState.showSnackbar("Failed to load settings. Please ensure an activity is running on the watch.")
        }
    }

    fun cancelDeviceSettingsLoading() {
        Napier.i("Cancelling device settings load job", tag = TAG)
        settingsJob?.cancel()
        // Immediately update UI state
        settingsLoading.value = null
    }

    fun onDeviceSettingsClosed() {
        lastLoadedSettings = null
        lastLoadedAppId = null
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
