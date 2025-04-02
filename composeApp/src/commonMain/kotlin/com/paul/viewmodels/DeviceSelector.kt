package com.paul.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.paul.domain.IqDevice
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.connectiq.IDeviceList
import com.paul.protocol.fromdevice.Protocol
import com.paul.ui.Screens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class DeviceSelector(
    private val navController: NavHostController,
    connection: IConnection,
    private val deviceList: IDeviceList) :
    ViewModel() {
    private var currentDevice: IqDevice? = null
    private var devicesFlow: MutableStateFlow<List<IqDevice>> = MutableStateFlow(listOf())
    private var currentDevicePoll: Job? = null

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
            navController.navigate(Screens.DeviceSelector.name)
        }
    }

    fun onDeviceSelected(device: IqDevice) {
        currentDevice = device
        navController.popBackStack()
    }

    fun openDeviceSettings(device: IqDevice) {

    }
}
