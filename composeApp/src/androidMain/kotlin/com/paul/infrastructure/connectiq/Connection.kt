package com.paul.infrastructure.connectiq

import android.content.Context
import io.github.aakira.napier.Napier
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.ConnectIQ.ConnectIQListener
import com.garmin.android.connectiq.ConnectIQ.IQMessageStatus
import com.garmin.android.connectiq.ConnectIQ.IQSdkErrorStatus
import com.garmin.android.connectiq.ConnectIQ.IQSendMessageListener
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.paul.domain.IqDevice
import com.paul.infrastructure.service.isEmulator
import com.paul.protocol.fromdevice.ProtocolResponse
import com.paul.protocol.fromdevice.Settings
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CancellationException
import kotlin.coroutines.resumeWithException
import com.paul.protocol.fromdevice.Protocol as Response


class Connection(private val context: Context) : IConnection() {

    private var isConnected = false
    private var connectIQ: ConnectIQ = ConnectIqBuilder(context).getInstance()

    fun getInstance(): ConnectIQ = connectIQ

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun start(): Unit = suspendCancellableCoroutine { continuation ->
        if (isConnected) {
            // we are already connected, todo handle the case where 2 callers call connect at the same time (before onSdkReady is called)
            // need to make an outstanding task that gets returned?
            continuation.resume(Unit) {
                Napier.d("cancelled whilst resuming")
            }
        } else {
            connectIQ.initialize(context, true, object : ConnectIQListener {
                override fun onInitializeError(errStatus: IQSdkErrorStatus) {
                    Napier.d("Failed to initialise: ${errStatus.name}")
                    if (errStatus == IQSdkErrorStatus.GCM_NOT_INSTALLED) {
                        continuation.resumeWithException(ConnectIqNeedsInstall())
                        return
                    } else if (errStatus == IQSdkErrorStatus.GCM_UPGRADE_NEEDED) {
                        continuation.resumeWithException(ConnectIqNeedsUpdate())
                        return
                    }
                    continuation.resumeWithException(Exception(errStatus.name))
                }

                override fun onSdkReady() {
                    Napier.d("onSdkReady()")
                    isConnected = true
                    continuation.resume(Unit) {
                        Napier.d("cancelled whilst resuming")
                    }
                }

                override fun onSdkShutDown() {
                    Napier.d("onSdkShutDown()")
                    isConnected = false
                }
            })
        }
    }

    override suspend fun send(device: CommonDevice, payload: Protocol): Unit {
        try {
            // large routes being sent take some time
            // On my samsung galaxy tab a I could not get this to ever send, then it started working fine.
            // I still have no idea why, i added and removed all permissions (for connectiq app) but could not rerpoduce
            // maybe related to android 11 issue?
            // https://forums.garmin.com/developer/connect-iq/i/bug-reports/android-mobile-sdk-fails-to-initialize-connect-iq-if-compiling-using-android-sdk-30-and-running-on-android-11-devices
            // https://forums.garmin.com/developer/connect-iq/f/discussion/262512/android-sdk-won-t-work-with-android-11
            // It could list devices, and gets device status updates, just sends would never work
            // even calls to connectIQ.getApplicationInfo returned nothing
            // oh well, it works now (somehow). possibly I had not enabled location access all the time and just had it 'when using the app'?
            // might have just been a first time setup thing? removed all permissions (including location) and somehow it still works every time now.
            // I did temporarily disable the ConnectIQMessageReceiver, but everything works with it re-enabled
            // might have been install order? going to chalk it up to bad luck - no other user will ever experience this rando issue right? right?
            withTimeout(30000) {
                start()
                val cd = device as CommonDeviceImpl
                sendInternal(cd.device, payload)
            }
        } catch (e: Exception) {
            Napier.d("failed to send: $e")
            throw e
        }
    }

    private fun deviceMessages(device: IqDevice): Flow<Response?> = callbackFlow {
        val connectIQ = getInstance()
        // Register to receive messages from our application
        val cd = device as CommonDeviceImpl
        val app = IQApp(connectIqAppIdFlow().value)

        fun handleMessage(device: IQDevice, status: IQMessageStatus, messageData: List<Any>) {
            // First inspect the status to make sure this
            // was a SUCCESS. If not then the status will indicate why there
            // was an issue receiving the message from the Connect IQ application.
            Napier.d(
                "mDeviceAppMessageListener():" + device + ": " + status.name + " " + messageData
            )
            try {
                if (status == IQMessageStatus.SUCCESS) {
                    // message data seems to be an array with one item, think its to future proof so
                    // they can add the other options array.
                    // or it's aside effect of how im sending it?
                    // There does not seem to be any other way though
                    trySendBlocking(Response.decode(messageData[0] as List<Any>))
                } else {
                    cancel(CancellationException("Device Listen: $status"))
                }
            } catch (t: Throwable) {
                // exceptions from within the callback crash the app, don't do that
                Napier.d("failed $t");
            }
        }

        // see https://developer.garmin.com/connect-iq/core-topics/mobile-sdk-for-android/
        // Receiving Messages
        // note: this simulator currently has a bug where it sends an empty app id
        // see below
        connectIQ.registerForAppEvents(cd.device, app) { device, _app, messageData, status ->
            handleMessage(device, status, messageData)
        }

        if (isEmulator()) {
            // Listener for the EMPTY app ID (WORKAROUND for simulator bug)
            // https://forums.garmin.com/developer/connect-iq/f/discussion/379720/android-mobile-sdk---tethered-connection---app-status-unknown
            // think this applies for all messages sent to the simulator?
            // onMessageStatus log actually shows this app id
            val simulatorApp = IQApp("")
            connectIQ.registerForAppEvents(
                cd.device,
                simulatorApp
            ) { device, _app, messageData, status ->
                handleMessage(device, status, messageData)
            }
        }

        awaitClose { connectIQ.unregisterForApplicationEvents(cd.device, app) }
    }

    override suspend fun appInfo(device: IqDevice): AppInfo =
        suspendCancellableCoroutine { continuation ->
            val cd = device as CommonDeviceImpl
            val currentApp = connectIqAppIdFlow().value
            connectIQ.getApplicationInfo(
                currentApp,
                cd.device,
                object : ConnectIQ.IQApplicationInfoListener {
                    // workaround to avoid double call of onMessageStatus
                    var completed = false

                    override fun onApplicationInfoReceived(
                        iqApp: IQApp,
                    ) {
                        Napier.d(
                            "app info: " + iqApp.version() + " " + iqApp.displayName + " " + iqApp.status + " " + iqApp.applicationId
                        )

                        continuation.resume(AppInfo(iqApp.version())) {
                            Napier.d("cancelled whilst resuming")
                        }
                    }

                    override fun onApplicationNotInstalled(var1: String) {
                        Napier.d("app info not installed: " + var1)
                        continuation.resumeWithException(RuntimeException(var1))
                    }
                })
        }

    // does not seem to work with data fields
    // get PROMPT_SHOWN_ON_DEVICE if we do not have an activity open (but not running), but no prompt is shown
    // get PROMPT_NOT_SHOWN_ON_DEVICE is the activity is open (but not started/running)
    private suspend fun openApp(device: IqDevice): Unit =
        suspendCancellableCoroutine { continuation ->
            val cd = device as CommonDeviceImpl
            val app = IQApp(connectIqAppIdFlow().value)

            connectIQ.openApplication(
                cd.device,
                app,
                object : ConnectIQ.IQOpenApplicationListener {
                    // workaround to avoid double call of onMessageStatus
                    var completed = false

                    override fun onOpenApplicationResponse(
                        var1: IQDevice,
                        var2: IQApp,
                        status: ConnectIQ.IQOpenApplicationStatus
                    ) {
                        Napier.d("app open response: " + var1 + " " + var2 + " " + status)

                        // garmin likes to double complete things
                        if (!completed) {
                            // we get PROMPT_NOT_SHOWN_ON_DEVICE if the app is already open, so mark it as success
                            if (status == ConnectIQ.IQOpenApplicationStatus.PROMPT_SHOWN_ON_DEVICE || status == ConnectIQ.IQOpenApplicationStatus.PROMPT_NOT_SHOWN_ON_DEVICE) {
                                continuation.resume(Unit) {
                                    Napier.d("cancelled whilst resuming")
                                }
                            } else {
                                continuation.resumeWithException(RuntimeException("failed to open app $status"))
                            }
                        }

                        completed = true
                    }
                })
        }

    // todo add correlation ids to these requests
    // think we ned to buffer any responses that come back
    // and probably have an event queue for handling unsolicited messages
    override suspend fun <T : Response> query(
        device: IqDevice,
        payload: Protocol,
        type: ProtocolResponse
    ): T {
        // some devices can take up to 5 minutes for a response if they are old and using temporal events
        // wait for them, but allow the user to cancel it
        return withTimeout(5 * 60 * 1000 + 10000) {
            start()
            try {
                appInfo(device)
                // we get a log saying PROMPT_SHOWN_ON_DEVICE, but i do to see anything
                // leaving this here incase it does ever work
                openApp(device) // we cannot run commands against the app unless it is open
            } catch (e: Exception) {
                Napier.w("Failed to get appInfo or openApp, ignoring: ${e.message}")
            }
            // pretty hacky impl, we should have the deviceMessages flow running all the time,
            // and then complete futures as messages come in from the device
            // they should be in order though, and this is hopefully ok for now
            val fut = CompletableDeferred<T>()
            val started = CompletableDeferred<Unit>()
            CoroutineScope(Dispatchers.IO).launch {
                started.complete(Unit)
                deviceMessages(device).collect {
                    if (it != null && it.type == type) {
                        fut.complete(it as T)
                        cancel()
                    }
                }
            }
            started.await() // make sure we have launched the coroutine, probably need to make sure the flow of messages has actually started too
            delay(1000) // wait for a bit to ensure the flow has started
            send(device, payload) // send the payload after we are all hooked up and waiting
            fut.await()
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class, ExperimentalCoroutinesApi::class)
    private suspend fun sendInternal(
        device: IQDevice,
        payload: Protocol,
    ): Unit = suspendCancellableCoroutine { continuation ->
        val app = IQApp(connectIqAppIdFlow().value)

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
                    Napier.d(
                        "onMessageStatus device: " + device.toString() + " app: " + app.toString() + " status: " + status.name
                    )
                    if (completed) {
                        return
                    }

                    if (status != IQMessageStatus.SUCCESS) {
                        continuation.resumeWithException(Exception(status.name))
                        return
                    }

                    completed = true
                    Napier.d("onMessageStatus: reciver.send")
                    continuation.resume(Unit) {
                        Napier.d("cancelled whilst resuming")
                    }
                }
            })
    }
}