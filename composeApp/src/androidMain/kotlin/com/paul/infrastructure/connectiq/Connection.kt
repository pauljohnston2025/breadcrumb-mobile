package com.paul.infrastructure.connectiq

import android.content.Context
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.ConnectIQ.ConnectIQListener
import com.garmin.android.connectiq.ConnectIQ.IQDeviceEventListener
import com.garmin.android.connectiq.ConnectIQ.IQMessageStatus
import com.garmin.android.connectiq.ConnectIQ.IQSdkErrorStatus
import com.garmin.android.connectiq.ConnectIQ.IQSendMessageListener
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import com.paul.infrastructure.protocol.Protocol
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

class Connection(private val context: Context) {

    companion object {
        val CONNECT_IQ_APP_ID = "20edd04a-9fdc-4291-b061-f49d5699394d"
    }

    private var isConnected = false
    private var connectIQ: ConnectIQ = ConnectIqBuilder(context).getInstance()

    fun getInstance(): ConnectIQ = connectIQ

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun start(): Unit = suspendCancellableCoroutine { continuation ->
        if (isConnected) {
            // we are already connected, todo handle the case where 2 callers call connect at the same time (before onSdkReady is called)
            // need to make an outstanding task that gets returned?
            continuation.resume(Unit) {
                println("cancelled whilst resuming")
            }
        } else {
            connectIQ.initialize(context, true, object : ConnectIQListener {
                override fun onInitializeError(errStatus: IQSdkErrorStatus) {
                    println("Failed to initialise: ${errStatus.name}")
                    continuation.resumeWithException(Exception(errStatus.name))
                }

                override fun onSdkReady() {
                    println("onSdkReady()")
                    isConnected = true
                    continuation.resume(Unit) {
                        println("cancelled whilst resuming")
                    }
                }

                override fun onSdkShutDown() {
                    println("onSdkShutDown()")
                    isConnected = false
                }
            })
        }
    }

    suspend fun send(device: IQDevice, payload: Protocol): Unit {
        start()
        try {
            sendInternal(device, payload)
        } catch (e: Exception) {
            // todo make this a toast or something better
            println("failed to send: $e")
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class, ExperimentalCoroutinesApi::class)
    private suspend fun sendInternal(
        device: IQDevice,
        payload: Protocol,
    ): Unit = suspendCancellableCoroutine { continuation ->
        val app = IQApp(CONNECT_IQ_APP_ID)

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
                    println("onMessageStatus with status: " + status.name)
                    if (completed) {
                        return
                    }

                    if (status != IQMessageStatus.SUCCESS) {
                        continuation.resumeWithException(Exception(status.name))
                        return
                    }

                    completed = true
                    println("onMessageStatus: reciver.send")
                    continuation.resume(Unit) {
                        println("cancelled whilst resuming")
                    }
                }
            })
    }
}