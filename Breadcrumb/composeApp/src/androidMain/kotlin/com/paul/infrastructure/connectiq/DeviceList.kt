package com.paul.infrastructure.connectiq

import com.garmin.android.connectiq.ConnectIQ.IQDeviceEventListener
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.publish
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class DeviceList(private val connection: Connection) {

    private var deviceList: List<IQDevice> = mutableListOf()
    private var deviceListFlow: MutableSharedFlow<List<IQDevice>> = MutableSharedFlow()
    private var job: Job? = null

    private val mDeviceEventListener = IQDeviceEventListener { device, status ->
        println("onDeviceStatusChanged():" + device + ": " + status.name)

        // todo don't block
        runBlocking {
            deviceListFlow.emit(deviceList)
        }
    }

    suspend fun subscribe(): Flow<List<IQDevice>> {
        connection.start()
        loadDevicesLoop()
        return deviceListFlow
    }

    private suspend fun loadDevicesLoop() {
        coroutineScope {
            if (job == null) {
                job = launch {
                    while(true) {
                        loadDevices()
                        delay(1000)
                    }
                }
            }
        }
    }

    private suspend fun loadDevices() {
        println("loadDevices")
        val connectIQ = connection.getInstance()
        try {
            // cleanup from old run
            if (deviceList.isNotEmpty()) {
                for (device in deviceList) {
                    connectIQ.unregisterForDeviceEvents(device)
                }
            }

            // get new list
            deviceList = connectIQ.knownDevices.toList()
            // Let's register for device status updates.  By doing so we will
            // automatically get a status update for each device so we do not
            // need to call getStatus()
            for (device in deviceList) {
                connectIQ.registerForDeviceEvents(device, mDeviceEventListener)
                println("device: ${device.friendlyName} status: ${device.status.name}")
            }
            deviceListFlow.emit(deviceList)
        } catch (e: InvalidStateException) {
            // This generally means you forgot to call initialize(), but since
            // we are in the callback for initialize(), this should never happen
            println("invalid state")
        } catch (e: ServiceUnavailableException) {
            // This will happen if for some reason your app was not able to connect
            // to the ConnectIQ service running within Garmin Connect Mobile.  This
            // could be because Garmin Connect Mobile is not installed or needs to
            // be upgraded.
            println("service unavailable")
        }
    }
}