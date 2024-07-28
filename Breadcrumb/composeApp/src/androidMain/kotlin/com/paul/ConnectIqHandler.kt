import android.content.Context
import android.os.Build
import android.util.Log
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.ConnectIQ.ConnectIQListener
import com.garmin.android.connectiq.ConnectIQ.IQConnectType
import com.garmin.android.connectiq.ConnectIQ.IQDeviceEventListener
import com.garmin.android.connectiq.ConnectIQ.IQMessageStatus
import com.garmin.android.connectiq.ConnectIQ.IQSdkErrorStatus
import com.garmin.android.connectiq.ConnectIQ.IQSendMessageListener
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.exception.InvalidStateException
import com.garmin.android.connectiq.exception.ServiceUnavailableException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.ArrayList
import kotlin.coroutines.resumeWithException

enum class Protocol(val value: UByte) {
    PROTOCOL_ROUTE_DATA(0u),
}

class ConnectIqHandler(private val context: Context) {

    companion object {
        val CONNECT_IQ_APP_ID = "20edd04a-9fdc-4291-b061-f49d5699394d"
    }

    var readyToSEnd = false
    private var deviceList: List<IQDevice>? = null
    private var connectIQ: ConnectIQ? = null

    private val mListener = object : ConnectIQListener {
        override fun onInitializeError(errStatus: IQSdkErrorStatus) {
            println("Failed to initialise: ${errStatus.name}")
            readyToSEnd = false
        }

        override fun onSdkReady() {
            println("onSdkReady()")
            loadDevices()
            readyToSEnd = true
        }

        override fun onSdkShutDown() {
            println("onSdkShutDown()")
            readyToSEnd = false
        }
    }


    private val mDeviceEventListener =
        IQDeviceEventListener { device, status ->
            println("onDeviceStatusChanged():" + device + ": " + status.name)
        }

    fun initialize() {
        if (Build.FINGERPRINT.contains("generic")) {
            // TETHERED for emulator
            connectIQ = ConnectIQ.getInstance(context, IQConnectType.TETHERED)
        }
        else {
            connectIQ = ConnectIQ.getInstance(context, IQConnectType.WIRELESS)
        }

        // Initialize the SDK
        connectIQ!!.initialize(context, true, mListener)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    suspend fun send(type: Protocol, payload: List<Any>): Unit {
        if (!readyToSEnd) {
            println("cant send yet")
            return
        }

        if (connectIQ == null) {
            println("no connectiq instance")
            return
        }

        if (deviceList != null && deviceList!!.isEmpty()) {
            println("no devices")
            return
        }

        try {
            sendInternal(type, connectIQ!!, deviceList!![0], payload)
        }
        catch (e: Exception)
        {
            println("failed to send: $e")
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class, ExperimentalCoroutinesApi::class)
    private suspend fun sendInternal(
        type: Protocol,
        connectIQ: ConnectIQ,
        device: IQDevice,
        payload: List<Any>,
    ): Unit = suspendCancellableCoroutine { continuation ->
        val app = IQApp(CONNECT_IQ_APP_ID)

        val toSend = payload.toMutableList()
        toSend.add(0, type.value.toInt())

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

    fun loadDevices() {
        // Retrieve the list of known devices
        println("loadDevices")
        try {
            if (deviceList != null && deviceList!!.isNotEmpty()) {
                for (device in deviceList!!) {
                    connectIQ!!.unregisterForDeviceEvents(device)
                }
            }
            // get new list
            deviceList = connectIQ!!.getKnownDevices()
            println(deviceList.toString())
            if (deviceList != null) {
                // Let's register for device status updates.  By doing so we will
                // automatically get a status update for each device so we do not
                // need to call getStatus()
                for (device in deviceList!!) {
                    connectIQ!!.registerForDeviceEvents(device, mDeviceEventListener)
                    println(device.friendlyName)
                    println(device.status.name)
                }
            }
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