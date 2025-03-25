package com.paul.infrastructure.connectiq

import android.util.Log
import com.garmin.android.connectiq.ConnectIQ.IQApplicationEventListener
import com.garmin.android.connectiq.ConnectIQ.IQDeviceEventListener
import com.garmin.android.connectiq.ConnectIQ.IQMessageStatus
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import com.paul.infrastructure.connectiq.Connection.Companion.CONNECT_IQ_APP_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class DeviceList(private val connection: Connection) {

    private var deviceList: List<IQDevice> = mutableListOf()
    private var deviceListFlow: MutableStateFlow<List<IQDevice>> = MutableStateFlow(listOf())
    private var job: Job? = null

    private val mDeviceEventListener = IQDeviceEventListener { device, status ->
        Log.d("stdout","onDeviceStatusChanged():" + device + ": " + status.name)

        // todo don't block
        // todo update the device status too, so we know wheat it looks like
        runBlocking {
            deviceListFlow.emit(deviceList)
        }
    }

    // see https://developer.garmin.com/connect-iq/core-topics/mobile-sdk-for-android/
    // Receiving Messages
    private val mDeviceAppMessageListener = IQApplicationEventListener { device, app, messageData, status ->
        // First inspect the status to make sure this
        // was a SUCCESS. If not then the status will indicate why there
        // was an issue receiving the message from the Connect IQ application.
        if (status == IQMessageStatus.SUCCESS) {
            // Handle the message.
        }

        Log.d("stdout","mDeviceAppMessageListener():" + device + ": " + status.name + " " + messageData)
    }

    suspend fun subscribe(): Flow<List<IQDevice>> {
        connection.start()
        loadDevicesLoop()
        return deviceListFlow
    }

    private suspend fun loadDevicesLoop() {
        // battery performance of calling this in a tight loop?
        if (job == null) {
            job = CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
                while(true) {
                    loadDevices()
                    delay(1000)
                }
            }
        }
    }

    private suspend fun loadDevices() {
//        Log.d("stdout","loadDevices")
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
                // Register to receive messages from our application
                connectIQ.registerForAppEvents(device, IQApp(CONNECT_IQ_APP_ID), mDeviceAppMessageListener)
//                Log.d("stdout","device: ${device.friendlyName} status: ${device.status.name}")
            }
            deviceListFlow.emit(deviceList)
        } catch (e: InvalidStateException) {
            // This generally means you forgot to call initialize(), but since
            // we are in the callback for initialize(), this should never happen
            Log.d("stdout","invalid state")
        } catch (e: ServiceUnavailableException) {
            // This will happen if for some reason your app was not able to connect
            // to the ConnectIQ service running within Garmin Connect Mobile.  This
            // could be because Garmin Connect Mobile is not installed or needs to
            // be upgraded.
            Log.d("stdout","service unavailable")
        }
    }
}