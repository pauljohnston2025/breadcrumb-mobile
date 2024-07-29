package com.paul.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.garmin.android.connectiq.IQDevice
import com.paul.infrastructure.connectiq.Connection
import com.paul.infrastructure.connectiq.DeviceList
import com.paul.ui.Screens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class DeviceSelector(private val navController: NavHostController, connection: Connection) :
    ViewModel() {
    private var currentDevice: IQDevice? = null
    private var deviceList = DeviceList(connection)
    private var devicesFlow: MutableSharedFlow<List<IQDevice>> = MutableSharedFlow()
    private var getDeviceMutex: Mutex = Mutex()
    private var selectDeviceLock: Mutex = Mutex()

    fun devicesFlow(): Flow<List<IQDevice>> {
        // need to start the scan every time for whatever reason (think the view model gets cleared multiple times)
        // cannot do this in init, as it gets no values (think the scope changes)
        // seems in init the scope is pointing to a different object
        viewModelScope.launch {
            deviceList.subscribe().collect {
                devicesFlow.emit(it)
            }
        }
        return devicesFlow
    }

    override fun onCleared() {
        println("view model cleared")
    }

    suspend fun currentDevice(): IQDevice? {
        // these locks are really bad, need a better way to do this
        getDeviceMutex.lock()
        if (currentDevice == null) {
            selectDevice()
            // need to cancel this on back pressed too
        }

        // wait for selectDevice to unlock the lock
        // these locks are really bad, need a better way to do this
        getDeviceMutex.unlock()
        return currentDevice
    }

    fun selectDeviceUi() {
        viewModelScope.launch { selectDevice() }
    }

    suspend fun selectDevice() {
        // these locks are really bad, need a better way to do this
        selectDeviceLock.lock()
        viewModelScope.launch(Dispatchers.Main) {
            navController.navigate(Screens.DeviceSelector.name)
        }
    }

    fun onDeviceSelected(device: IQDevice) {
        currentDevice = device
        navController.popBackStack()

        // these locks are really bad, need a better way to do this
        selectDeviceLock.unlock()
    }
}
