package com.paul.infrastructure.connectiq

import android.util.Log
import com.garmin.android.connectiq.ConnectIQ.IQApplicationEventListener
import com.garmin.android.connectiq.ConnectIQ.IQDeviceEventListener
import com.garmin.android.connectiq.ConnectIQ.IQMessageStatus
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import com.paul.domain.IqDevice
import com.paul.infrastructure.connectiq.IConnection.Companion.CONNECT_IQ_APP_ID
import com.paul.protocol.fromdevice.Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class DeviceList(private val connection: Connection) : IDeviceList {

    private var deviceList: List<CommonDeviceImpl> = listOf()
    private var deviceListFlow: MutableStateFlow<List<CommonDeviceImpl>> =
        MutableStateFlow(listOf())
    private var job: Job? = null

    private val mDeviceEventListener = IQDeviceEventListener { device, status ->
        Log.d("stdout", "onDeviceStatusChanged():" + device + ": " + status.name)

        val oldDevice = deviceList.find { device.deviceIdentifier == it.device.deviceIdentifier }
        val list = deviceList.filter {
            device.deviceIdentifier != it.device.deviceIdentifier
        }.toMutableList()

        // what the actual fuck, device.status = UNKNOWN, but status = CONNECTED
        // are they expecting us to update the device they give us?
        // and looks like the device looses its label too
        device.status = status
        val toAdd = CommonDeviceImpl(device)
        // on disconnect and reconnect it seems to loose its name
        toAdd.friendlyName =
            if (toAdd.friendlyName != null && toAdd.friendlyName != "") toAdd.friendlyName else oldDevice?.friendlyName
                ?: ""

        // dummy device for testing
//        val dummyDevice = CommonDeviceImpl(device)
//        val dummyId = (Math.random()*10000).toLong()
//        dummyDevice.friendlyName = "dummy device $dummyId"
//        dummyDevice.id = dummyId
//        list.add(dummyDevice)
        list.add(toAdd)
//        val dummyDevice2 = CommonDeviceImpl(device)
//        val dummyId2 = (Math.random()*10000).toLong()
//        dummyDevice2.status = "NOT_CONNECTED"
//        dummyDevice2.friendlyName = "dummy device $dummyId2"
//        dummyDevice2.id = dummyId2
//        list.add(dummyDevice2)
        deviceList = list
        CoroutineScope(Dispatchers.IO).launch {
            Log.d("stdout", "emitting $list")
            deviceListFlow.emit(list.toList())
        }
    }

    override suspend fun subscribe(): Flow<List<IqDevice>> {
        connection.start()
        loadDevicesLoop()
        return deviceListFlow
    }

    private suspend fun loadDevicesLoop() {
        // battery performance of calling this in a tight loop?
        if (job == null) {
            job = CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
                while (true) {
                    loadDevices()
                    delay(1000)
                }
            }
        }
    }

    private suspend fun loadDevices() {
//        Log.d("stdout", "loadDevices")
        val connectIQ = connection.getInstance()
        try {
            // would love to use a callback flow, but there
            // does not seem to be any way of doing that with the garmin api
            // get new list
            // looks like onDeviceStatusChanged get called after this, but the next call to
            // connectIQ.knownDevices moves the connection status back to unknown
            val currentDevices = connectIQ.knownDevices.map { CommonDeviceImpl(it) }
            // we only want to remove them if they are no longer present
            // so that we can keep the connection status connectIQ.knownDevices does not preserve it for us
            // cleanup from old run
            val toRemove = deviceList.filter { device ->
                currentDevices.find { currentDevice ->
                    currentDevice.device.deviceIdentifier == device.device.deviceIdentifier
                } == null
            }

            val toAdd = currentDevices.filter { currentDevice ->
                deviceList.find { device ->
                    currentDevice.device.deviceIdentifier == device.device.deviceIdentifier
                } == null
            }

            for (device in toRemove) {
                Log.d("stdout", "removing device $device")
            }
            val newList = deviceList.filter { device ->
                toRemove.find { device.device.deviceIdentifier == it.device.deviceIdentifier } == null
            }.toMutableList()

            for (device in toAdd) {
                Log.d("stdout", "adding device $device")
                newList.add(device)
            }

            // emit the list before hooking up the callbacks, otherwise the callbacks can be called
            // first, and we get the wrong order
            deviceList = newList
//            Log.d("stdout", "emitting from loop $newList")
            // note: if no objects in the list change this emit does nothing, because stateflow does
            // not see it as a change
            deviceListFlow.emit(newList)

//            Log.d("stdout", deviceList[0].toString())
            // Let's register for device status updates.  By doing so we will
            // automatically get a status update for each device so we do not
            // need to call getStatus()
            for (device in toAdd) {
                connectIQ.registerForDeviceEvents(device.device, mDeviceEventListener)
//                val app = IQApp(CONNECT_IQ_APP_ID)
//                connectIQ.registerForAppEvents(device.device, app) { device, _app, messageData, status ->
//                    // First inspect the status to make sure this
//                    // was a SUCCESS. If not then the status will indicate why there
//                    // was an issue receiving the message from the Connect IQ application.
//                    Log.d(
//                        "stdout",
//                        "device message:" + device + ": " + status.name + " " + messageData
//                    )
//                }
//                Log.d("stdout","device: ${device.friendlyName} status: ${device.status.name}")
            }
        } catch (e: InvalidStateException) {
            // This generally means you forgot to call initialize(), but since
            // we are in the callback for initialize(), this should never happen
            Log.d("stdout", "invalid state")
        } catch (e: ServiceUnavailableException) {
            // This will happen if for some reason your app was not able to connect
            // to the ConnectIQ service running within Garmin Connect Mobile.  This
            // could be because Garmin Connect Mobile is not installed or needs to
            // be upgraded.
            Log.d("stdout", "service unavailable")
        }
    }
}