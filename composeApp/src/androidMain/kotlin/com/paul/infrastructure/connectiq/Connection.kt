package com.paul.infrastructure.connectiq

import android.content.Context
import android.util.Log
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.ConnectIQ.ConnectIQListener
import com.garmin.android.connectiq.ConnectIQ.IQMessageStatus
import com.garmin.android.connectiq.ConnectIQ.IQSdkErrorStatus
import com.garmin.android.connectiq.ConnectIQ.IQSendMessageListener
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.paul.protocol.todevice.Protocol
import com.paul.domain.IqDevice as CommonDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException


class Connection(private val context: Context) : IConnection {

    private var isConnected = false
    private var connectIQ: ConnectIQ = ConnectIqBuilder(context).getInstance()

    fun getInstance(): ConnectIQ = connectIQ

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun start(): Unit = suspendCancellableCoroutine { continuation ->
        if (isConnected) {
            // we are already connected, todo handle the case where 2 callers call connect at the same time (before onSdkReady is called)
            // need to make an outstanding task that gets returned?
            continuation.resume(Unit) {
                Log.d("stdout","cancelled whilst resuming")
            }
        } else {
            connectIQ.initialize(context, true, object : ConnectIQListener {
                override fun onInitializeError(errStatus: IQSdkErrorStatus) {
                    Log.d("stdout","Failed to initialise: ${errStatus.name}")
                    continuation.resumeWithException(Exception(errStatus.name))
                }

                override fun onSdkReady() {
                    Log.d("stdout","onSdkReady()")
                    isConnected = true
                    continuation.resume(Unit) {
                        Log.d("stdout","cancelled whilst resuming")
                    }
                }

                override fun onSdkShutDown() {
                    Log.d("stdout","onSdkShutDown()")
                    isConnected = false
                }
            })
        }
    }

    override suspend fun send(device: CommonDevice, payload: Protocol): Unit {
        start()
        try {
            val cd = device as CommonDeviceImpl
            sendInternal(cd.device, payload)
        } catch (e: Exception) {
            // todo make this a toast or something better
            Log.d("stdout","failed to send: $e")
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class, ExperimentalCoroutinesApi::class)
    private suspend fun sendInternal(
        device: IQDevice,
        payload: Protocol,
    ): Unit = suspendCancellableCoroutine { continuation ->
        val app = IQApp(IConnection.CONNECT_IQ_APP_ID)

        val toSend = payload.payload().toMutableList()
        toSend.add(0, payload.type().value.toInt())

        connectIQ.sendMessage(
            device,
            app,
            toSend,
            object : IQSendMessageListener {
                // workaround to avoid double call of onMessageStatus
                var completed = false

                override fun onMessageStatus(
                    device: IQDevice,
                    app: IQApp,
                    status: IQMessageStatus
                ) {
                    Log.d("stdout","onMessageStatus device: " + device.toString() + " app: " + app.toString() + " status: " + status.name)
                    if (completed) {
                        return
                    }

                    if (status != IQMessageStatus.SUCCESS) {
                        continuation.resumeWithException(Exception(status.name))
                        return
                    }

                    completed = true
                    Log.d("stdout","onMessageStatus: reciver.send")
                    continuation.resume(Unit) {
                        Log.d("stdout","cancelled whilst resuming")
                    }
                }
            })
    }
}