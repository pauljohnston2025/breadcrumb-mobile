package com.paul

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ConnectIQMessageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received Connect IQ message:")
        if (intent.action == "com.garmin.android.connectiq.INCOMING_MESSAGE") {
            // Extract data from the intent
            val sourceDeviceName = intent.getStringExtra("sourceDeviceName")
            val sourceDeviceId = intent.getLongExtra("sourceDeviceId", -1L)
            val messageType = intent.getStringExtra("messageType") //"text", "json", "bytearray"
            val messageText = intent.getStringExtra("messageText")
            val messageJson = intent.getStringExtra("messageJson")
            val messageBytes = intent.getByteArrayExtra("messageBytes")

            Log.d(TAG, "Received Connect IQ message:")
            Log.d(TAG, "  Source Device Name: $sourceDeviceName")
            Log.d(TAG, "  Source Device ID: $sourceDeviceId")
            Log.d(TAG, "  Message Type: $messageType")

            when (messageType) {
                "text" -> Log.d(TAG, "  Message Text: $messageText")
                "json" -> Log.d(TAG, "  Message JSON: $messageJson")
                "bytearray" -> Log.d(TAG, "  Message Bytes: ${messageBytes?.contentToString()}")
                else -> Log.w(TAG, "  Unknown message type")
            }

            // Process the message
            processConnectIQMessage(context, sourceDeviceId, messageType, messageText, messageJson, messageBytes)
        }
    }

    private fun processConnectIQMessage(context: Context, sourceDeviceId: Long, messageType: String?, messageText: String?, messageJson: String?, messageBytes: ByteArray?) {
        // Implement your message handling logic here
        // This is where you would take action based on the received message
        // For example, you might update a UI element, send data to a server, etc.

        // Example: Send a reply back to the Connect IQ app
        // You'll need to use the Connect IQ SDK for Android to do this
        // ConnectIQ.getInstance().sendMessage(context, sourceDeviceId, "Reply from Android App")

    }

    companion object {
        private const val TAG = "ConnectIQMessageReceiver"
    }
}