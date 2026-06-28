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

    companion object {
        private const val TAG = "Connection"
    }

    private var isConnected = false
    private var connectIQ: ConnectIQ = ConnectIqBuilder(context).getInstance()
    private var initializationDeferred: Deferred<Unit>? = null

    fun getInstance(): ConnectIQ = connectIQ

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun start(): Unit {
        if (isConnected) return

        val deferred = synchronized(this) {
            if (initializationDeferred == null) {
                val newDeferred = CompletableDeferred<Unit>()
                initializationDeferred = newDeferred
                
                connectIQ.initialize(context, true, object : ConnectIQListener {
                    override fun onInitializeError(errStatus: IQSdkErrorStatus) {
                        Napier.e("Failed to initialise ConnectIQ: ${errStatus.name}", tag = TAG)
                        val exception = when (errStatus) {
                            IQSdkErrorStatus.GCM_NOT_INSTALLED -> ConnectIqNeedsInstall()
                            IQSdkErrorStatus.GCM_UPGRADE_NEEDED -> ConnectIqNeedsUpdate()
                            else -> Exception(errStatus.name)
                        }
                        newDeferred.completeExceptionally(exception)
                        synchronized(this@Connection) {
                            initializationDeferred = null
                        }
                    }

                    override fun onSdkReady() {
                        Napier.i("ConnectIQ SDK ready", tag = TAG)
                        isConnected = true
                        newDeferred.complete(Unit)
                    }

                    override fun onSdkShutDown() {
                        Napier.i("ConnectIQ SDK shut down", tag = TAG)
                        isConnected = false
                        synchronized(this@Connection) {
                            initializationDeferred = null
                        }
                    }
                })
                newDeferred
            } else {
                initializationDeferred!!
            }
        }
        deferred.await()
    }

    override suspend fun send(device: IqDevice, payload: Protocol, targetAppId: String?, excludingApps : List<String>, onProgress: (suspend (String) -> Unit)?): Unit {
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
                val currentAppId = targetAppId ?: connectIqAppIdFlow().value
                val cd = device as CommonDeviceImpl
                
                if (currentAppId == AUTO_CONNECT_IQ_APP_ID) {
                    val installedApps = availableConnectIqApps.filter { it.id != AUTO_CONNECT_IQ_APP_ID && !excludingApps.contains(it.id) }.filter { app ->
                        try {
                            appInfo(device, app.id)
                            true
                        } catch (e: Exception) {
                            false
                        }
                    }

                    if (installedApps.isEmpty()) {
                        val selected = appSelector?.invoke(availableConnectIqApps.filter { it.id != AUTO_CONNECT_IQ_APP_ID && !excludingApps.contains(it.id) }) ?: availableConnectIqApps[1]
                        val appName = selected.name
                        onProgress?.invoke(appName)
                        appInfo(device, selected.id) // This will trigger the appNotInstalledHandler
                        return@withTimeout
                    }

                    installedApps.forEach { app ->
                        try {
                            onProgress?.invoke(app.name)
                            val info = appInfo(device, app.id)
                            val transformed = payload.transform(app.id, info.version)
                            sendInternal(cd.device, transformed, app.id)
                        } catch (e: Exception) {
                            Napier.e("Failed to send to ${app.name}: ${e.message}")
                        }
                    }
                } else {
                    val appName = availableConnectIqApps.find { it.id == currentAppId }?.name ?: currentAppId
                    onProgress?.invoke(appName)
                    try {
                        val info = appInfo(device, currentAppId)
                        val transformed = payload.transform(currentAppId, info.version)
                        sendInternal(cd.device, transformed, currentAppId)
                    } catch (e: ConnectIqAppNotInstalled) {
                        val proceed = appNotInstalledHandler?.invoke(currentAppId) == true
                        if (proceed) {
                            // proceed anyway, assume latest version for transformation
                            val transformed = payload.transform(currentAppId, 0)
                            sendInternal(cd.device, transformed, currentAppId)
                        } else {
                            throw e
                        }
                    } catch (e: Exception) {
                        // ignore other appInfo errors, just send transformed as version 0
                        val transformed = payload.transform(currentAppId, 0)
                        sendInternal(cd.device, transformed, currentAppId)
                    }
                }
            }
        } catch (e: Exception) {
            Napier.e("Failed to send payload to device: $e", e, tag = TAG)
            throw e
        }
    }

    private fun deviceMessages(device: IqDevice, appId: String): Flow<Response?> = callbackFlow {
        val connectIQ = getInstance()
        // Register to receive messages from our application
        val cd = device as CommonDeviceImpl
        val app = IQApp(appId)

        fun handleMessage(device: IQDevice, status: IQMessageStatus, messageData: List<Any>) {
            // First inspect the status to make sure this
            // was a SUCCESS. If not then the status will indicate why there
            // was an issue receiving the message from the Connect IQ application.
            Napier.v(
                "handleMessage(): $device: status=${status.name}, data=$messageData",
                tag = TAG
            )
            try {
                if (status == IQMessageStatus.SUCCESS) {
                    // message data seems to be an array with one item, think its to future proof so
                    // they can add the other options array.
                    // or it's aside effect of how im sending it?
                    // There does not seem to be any other way though
                    @Suppress("UNCHECKED_CAST")
                    trySendBlocking(Response.decode(messageData[0] as List<Any>))
                } else {
                    cancel(CancellationException("Device Listen Error: $status"))
                }
            } catch (t: Throwable) {
                // exceptions from within the callback crash the app, don't do that
                Napier.e("Exception processing device message", t, tag = TAG);
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

    override suspend fun appInfo(device: IqDevice, appId: String?): AppInfo {
        val cd = device as CommonDeviceImpl
        val currentApp = appId ?: connectIqAppIdFlow().value
        
        if (currentApp == AUTO_CONNECT_IQ_APP_ID) {
            throw IllegalArgumentException("Cannot call appInfo with 'auto', use allAppInfos instead")
        }

        return withTimeout(10000) {
            val deferred = CompletableDeferred<AppInfo>()
            connectIQ.getApplicationInfo(
                currentApp,
                cd.device,
                object : ConnectIQ.IQApplicationInfoListener {
                    override fun onApplicationInfoReceived(iqApp: IQApp) {
                        Napier.i(
                            "Application info received: ver=${iqApp.version()}, name=${iqApp.displayName}, status=${iqApp.status}, id=${iqApp.applicationId}",
                            tag = TAG
                        )
                        val displayName = if (iqApp.displayName.isNullOrBlank()) {
                            availableConnectIqApps.find { it.id.lowercase() == iqApp.applicationId.lowercase() || it.id.replace("-", "").lowercase() == iqApp.applicationId.lowercase() }?.name ?: iqApp.applicationId
                        } else {
                            iqApp.displayName
                        }
                        // apps returned from garmin are missing the hyphen
                        val foundAppId = availableConnectIqApps.find { it.id.lowercase() == iqApp.applicationId.lowercase() || it.id.replace("-", "").lowercase() == iqApp.applicationId.lowercase() }?.id ?: iqApp.applicationId
                        deferred.complete(AppInfo(iqApp.version(), foundAppId, displayName))
                    }

                    override fun onApplicationNotInstalled(var1: String) {
                        Napier.w("Application not installed: $var1", tag = TAG)
                        deferred.completeExceptionally(ConnectIqAppNotInstalled(currentApp))
                    }
                })
            deferred.await()
        }
    }

    override suspend fun allAppInfos(device: IqDevice): Map<String, AppInfo> {
        val results = mutableMapOf<String, AppInfo>()
        availableConnectIqApps.filter { it.id != AUTO_CONNECT_IQ_APP_ID }.forEach { app ->
            try {
                results[app.id] = appInfo(device, app.id)
            } catch (e: Exception) {
                // ignore not installed or timeouts
            }
        }
        return results
    }

    // does not seem to work with data fields
    // get PROMPT_SHOWN_ON_DEVICE if we do not have an activity open (but not running), but no prompt is shown
    // get PROMPT_NOT_SHOWN_ON_DEVICE is the activity is open (but not started/running)
    private suspend fun openApp(device: IqDevice, appId: String): Unit {
        val cd = device as CommonDeviceImpl
        val app = IQApp(appId)
        val deferred = CompletableDeferred<Unit>()

        connectIQ.openApplication(
            cd.device,
            app,
            object : ConnectIQ.IQOpenApplicationListener {
                override fun onOpenApplicationResponse(
                    var1: IQDevice,
                    var2: IQApp,
                    status: ConnectIQ.IQOpenApplicationStatus
                ) {
                    Napier.i("Open application response: device=$var1, app=$var2, status=$status", tag = TAG)

                    // we get PROMPT_NOT_SHOWN_ON_DEVICE if the app is already open, so mark it as success
                    if (status == ConnectIQ.IQOpenApplicationStatus.PROMPT_SHOWN_ON_DEVICE || status == ConnectIQ.IQOpenApplicationStatus.PROMPT_NOT_SHOWN_ON_DEVICE) {
                        deferred.complete(Unit)
                    } else {
                        deferred.completeExceptionally(RuntimeException("failed to open app $status"))
                    }
                }
            })
        return deferred.await()
    }

    // todo add correlation ids to these requests
    // think we ned to buffer any responses that come back
    // and probably have an event queue for handling unsolicited messages
    override suspend fun <T : Response> query(
        device: IqDevice,
        payload: Protocol,
        type: ProtocolResponse,
        onProgress: (suspend (String) -> Unit)?
    ): QueryResult<T> {
        // some devices can take up to 5 minutes for a response if they are old and using temporal events
        // wait for them, but allow the user to cancel it
        return withTimeout(5 * 60 * 1000 + 10000) {
            start()

            val currentAppId = connectIqAppIdFlow().value
            val targetAppId = if (currentAppId == AUTO_CONNECT_IQ_APP_ID) {
                val installedApps = availableConnectIqApps.filter { it.id != AUTO_CONNECT_IQ_APP_ID }.filter { app ->
                    try {
                        appInfo(device, app.id)
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                if (installedApps.isEmpty()) {
                    val selected = appSelector?.invoke(availableConnectIqApps.filter { it.id != AUTO_CONNECT_IQ_APP_ID }) ?: availableConnectIqApps[1]
                    selected.id
                } else if (installedApps.size == 1) {
                    installedApps[0].id
                } else {
                    val selected = appSelector?.invoke(installedApps) ?: installedApps[0]
                    selected.id
                }
            } else {
                currentAppId
            }

            try {
                val appName = availableConnectIqApps.find { it.id == targetAppId }?.name ?: targetAppId
                onProgress?.invoke(appName)

                val info = try {
                    appInfo(device, targetAppId)
                } catch (e: ConnectIqAppNotInstalled) {
                    val proceed = appNotInstalledHandler?.invoke(targetAppId) == true
                    if (proceed) {
                        // proceed anyway, assume latest version for transformation
                        AppInfo(0, targetAppId, appName)
                    } else {
                        throw e
                    }
                } catch (e: Exception) {
                    // ignore other appInfo errors, just assume version 0
                    AppInfo(0, targetAppId, appName)
                }

                val transformed = payload.transform(targetAppId, info.version)
                // we get a log saying PROMPT_SHOWN_ON_DEVICE, but i do to see anything
                // leaving this here incase it does ever work
                openApp(device, targetAppId) // we cannot run commands against the app unless it is open
                
                // pretty hacky impl, we should have the deviceMessages flow running all the time,
                // and then complete futures as messages come in from the device
                // they should be in order though, and this is hopefully ok for now
                val fut = CompletableDeferred<T>()
                val started = CompletableDeferred<Unit>()
                val cd = device as CommonDeviceImpl
                CoroutineScope(Dispatchers.IO).launch {
                    started.complete(Unit)
                    deviceMessages(device, targetAppId).collect {
                        if (it != null && it.type == type) {
                            @Suppress("UNCHECKED_CAST")
                            fut.complete(it as T)
                            cancel()
                        }
                    }
                }
                started.await() // make sure we have launched the coroutine, probably need to make sure the flow of messages has actually started too
                delay(1000) // wait for a bit to ensure the flow has started
                sendInternal(cd.device, transformed, targetAppId) // send the payload after we are all hooked up and waiting
                val result = fut.await()
                QueryResult(result, targetAppId)
            } catch (e: Exception) {
                Napier.w("Failed to query device: ${e.message}")
                throw e
            }
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class, ExperimentalCoroutinesApi::class)
    private suspend fun sendInternal(
        device: IQDevice,
        payload: Protocol,
        appId: String
    ): Unit {
        val app = IQApp(appId)
        val deferred = CompletableDeferred<Unit>()

        val toSend = payload.payload().toMutableList()
        toSend.add(0, payload.type().value.toInt())

        connectIQ.sendMessage(
            device,
            app,
            toSend,
            object : IQSendMessageListener {
                override fun onMessageStatus(
                    device: IQDevice,
                    app: IQApp,
                    status: IQMessageStatus
                ) {
                    Napier.v(
                        "onMessageStatus(): device=$device, app=$app, status=${status.name}",
                        tag = TAG
                    )

                    if (status != IQMessageStatus.SUCCESS) {
                        deferred.completeExceptionally(Exception(status.name))
                        return
                    }

                    Napier.v("onMessageStatus(): SUCCESS", tag = TAG)
                    deferred.complete(Unit)
                }
            })
        return deferred.await()
    }
}
