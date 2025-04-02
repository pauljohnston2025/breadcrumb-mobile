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
import com.paul.domain.IqDevice
import com.paul.infrastructure.connectiq.IConnection.Companion.CONNECT_IQ_APP_ID
import com.paul.protocol.fromdevice.ProtocolResponse
import com.paul.protocol.todevice.Protocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import com.paul.domain.IqDevice as CommonDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CancellationException
import kotlin.coroutines.resumeWithException
import com.paul.protocol.fromdevice.Protocol as Response


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

    private fun deviceMessages(device: IqDevice): Flow<Response?> = callbackFlow {
        val connectIQ = getInstance()
        // Register to receive messages from our application
        val cd = device as CommonDeviceImpl
        val app = IQApp(CONNECT_IQ_APP_ID)
        // see https://developer.garmin.com/connect-iq/core-topics/mobile-sdk-for-android/
        // Receiving Messages
        // note: this does not seem to work in the simulator, not entirely sure why
        // works fine on a real device
        connectIQ.registerForAppEvents(cd.device, app) { device, _app, messageData, status ->
            // First inspect the status to make sure this
            // was a SUCCESS. If not then the status will indicate why there
            // was an issue receiving the message from the Connect IQ application.
            Log.d(
                "stdout",
                "mDeviceAppMessageListener():" + device + ": " + status.name + " " + messageData
            )
            try {
                if (status == IQMessageStatus.SUCCESS) {
                    trySendBlocking(Response.decode(messageData))
                } else {
                    cancel(CancellationException("Device Listen: $status"))
                }
            }
            catch (t: Throwable)
            {
                // exceptions from within the callback crash the app, don't do that
                Log.d("stdout", "failed $t");
            }
        }

        awaitClose { connectIQ.unregisterForApplicationEvents(cd.device, app) }
    }

    // todo add correlation ids to these requests
    // think we ned to buffer any responses that come back
    // and probably have an event queue for handling unsolicited messages
    override suspend fun <T: Response> query(device: IqDevice, payload: Protocol, type: ProtocolResponse): T
    {
        // pretty hacky impl, we should have the deviceMessages flow running all the time,
        // and then complete futures as messages come in from the device
        // they should be in order though, and this is hopefully ok for now
        return withTimeout(10000) {
            val fut = CompletableDeferred<T>()
            CoroutineScope(Dispatchers.IO).launch {
                deviceMessages(device).collect {
                    if (it != null && it.type == type) {
                        fut.complete(it as T)
                        cancel()
                    }
                }
            }
            fut.await()
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