package com.paul.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.garmin.android.connectiq.IQDevice
import com.paul.infrastructure.connectiq.Connection
import com.paul.infrastructure.connectiq.DeviceList
import com.paul.ui.Screens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout

class DeviceSelector(private val navController: NavHostController, connection: Connection) :
    ViewModel() {
    private var currentDevice: IQDevice? = null
    private var deviceList = DeviceList(connection)
    private var devicesFlow: MutableSharedFlow<List<IQDevice>> = MutableSharedFlow()

    init {
        viewModelScope.launch {
            val sub = deviceList.subscribe()
            sub.collect {
                devicesFlow.emit(it)
            }
        }
    }

    fun devicesFlow(): Flow<List<IQDevice>> {
        return devicesFlow
    }

    override fun onCleared() {
        println("view model cleared")
    }

    suspend fun currentDevice(): IQDevice? {
        if (currentDevice == null) {
            selectDevice()
        }

        // wait for the device to be selected
        try {
            withTimeout(10000) {
                while (currentDevice == null) {
                    delay(1000);
                }
            }
        }
        catch(e: TimeoutCancellationException) {
            // assume they never went back, though they could have cancelled,
            // and it they did cancel, this job will run again soon and press back again,
            // exiting out of the app (not ideal) but better than all the locks we had,
            // and never killing them on back pressed
            viewModelScope.launch(Dispatchers.Main) {
                navController.popBackStack()
            }
        }

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

    fun onDeviceSelected(device: IQDevice) {
        currentDevice = device
        navController.popBackStack()
    }
}
