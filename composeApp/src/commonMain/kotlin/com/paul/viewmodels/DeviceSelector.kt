package com.paul.viewmodels

import android.util.Log
import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.paul.domain.IqDevice
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class DeviceSelector(
    private val navController: NavHostController,
    private val connection: IConnection,
    private val deviceList: IDeviceList,
    private val snackbarHostState: SnackbarHostState,
) :
    ViewModel() {
    var currentDevice: IqDevice? = null
    private var devicesFlow: MutableStateFlow<List<IqDevice>> = MutableStateFlow(listOf())
    private var currentDevicePoll: Job? = null
    val settingsLoading: MutableState<Boolean> = mutableStateOf(false)
    var lastLoadedSettings: Settings? = null

    init {
        viewModelScope.launch {
            Log.d("stdout", "launching list")
            val sub = deviceList.subscribe()
            sub.collect {
                Log.d("stdout", "got device ${it.size} $it")
                devicesFlow.emit(it)
            }
        }.invokeOnCompletion {
            Log.d("stdout", "list completed")
        }
    }

    fun devicesFlow(): Flow<List<IqDevice>> {
        return devicesFlow
    }

    override fun onCleared() {
        Log.d("stdout","view model cleared")
    }

    fun cancelSelection() {
        currentDevicePoll?.cancel()
    }

    suspend fun currentDevice(): IqDevice? {
        if (currentDevice == null) {
            selectDevice()
        }

        currentDevicePoll?.cancel()
        withContext(Dispatchers.IO) {
            currentDevicePoll = launch {
                // wait for the device to be selected
                try {
                    withTimeout(10000) {
                        while (currentDevice == null) {
                            delay(1000);
                        }
                    }
                }
                catch(e: TimeoutCancellationException) {
                    viewModelScope.launch(Dispatchers.Main) {
                        navController.popBackStack()
                    }
                }
            }
        }

        currentDevicePoll?.join()

        return currentDevice
    }

    fun selectDeviceUi() {
        viewModelScope.launch { selectDevice() }
    }

    private fun selectDevice() {
        viewModelScope.launch(Dispatchers.Main) {
            navController.navigate(Screen.DeviceSelection.route)
        }
    }

    fun openDeviceSettings(device: IqDevice) {
        currentDevice = device
        settingsLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = connection.query<Settings>(
                    device,
                    RequestSettings(),
                    ProtocolResponse.PROTOCOL_SEND_SETTINGS
                )
                lastLoadedSettings = settings
                Log.d("stdout", "got settings $settings")
                settingsLoading.value = false
                viewModelScope.launch(Dispatchers.Main) {
                    navController.navigate(Screen.DeviceSettings.route)
                }
            }
            catch (t: Throwable)
            {
                // most likely a timeout exception
                settingsLoading.value = false
                snackbarHostState.showSnackbar("Failed to load settings. Please ensure an activity is running on the watch.")
            }
        }
    }

    fun onDeviceSelected(device: IqDevice) {
        currentDevice = device
        navController.popBackStack()
    }
}
