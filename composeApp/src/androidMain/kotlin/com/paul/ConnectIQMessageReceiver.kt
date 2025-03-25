package com.paul

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.garmin.android.connectiq.ConnectIQ
import com.paul.infrastructure.connectiq.Connection.Companion.CONNECT_IQ_APP_ID


class ConnectIQMessageReceiver : BroadcastReceiver() {
    // see https://forums.garmin.com/developer/connect-iq/f/discussion/4339/start-an-android-service-from-watch/29284#29284
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.garmin.android.connectiq.INCOMING_MESSAGE") {
            val appId = intent.getStringExtra(ConnectIQ.EXTRA_APPLICATION_ID)
            val payload = intent.getByteArrayExtra(ConnectIQ.EXTRA_PAYLOAD)
// the rest of these get
// Key <EXTRA_BLAH> expected String but value was a android.os.Parcel$LazyValue

//            val remoteApplication = intent.getStringExtra(ConnectIQ.EXTRA_REMOTE_APPLICATION)
//            val remoteDevice = intent.getStringExtra(ConnectIQ.EXTRA_REMOTE_DEVICE)

            Log.d(TAG, "Received Connect IQ message:")
            Log.d(TAG, "  appId: $appId")
            Log.d(TAG, "  payload: $payload")
//            Log.d(TAG, "  remoteApplication: $remoteApplication")
//            Log.d(TAG, "  remoteDevice: $remoteDevice")
            if (appId?.lowercase() == CONNECT_IQ_APP_ID.replace("-", "").lowercase()
                // payload appears to change between runs
                /* && payload.toString().lowercase() == "[B@cd05ca5".lowercase() */
                )
            {
                Log.d(TAG, "got our special start message")
                // apparently you cannot launch an app if the app is closed, or running inthe background
                // https://stackoverflow.com/questions/59636083/broadcastreceiver-cant-start-activity-from-closed-or-in-background-app
                // https://developer.android.com/guide/components/activities/background-starts
                // might need to send notification instead
//                val componentName = ComponentName(
//                    "com.paul.breadcrumb",
//                    "com.paul.MainActivity"
//                )

//                val launchIntent = Intent(Intent.ACTION_VIEW).apply {
////                    component = componentName
//                    data = Uri.parse("myapp://open.breadcrumb.app.example.com")
//                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
//                }
//
//                try {
//                    context.startActivity(launchIntent)
//                } catch (e: Exception) {
//                    Log.e(TAG, "intent error: ${context.packageName} $e")
//                }

                // todo check if we are already running somehow
                // no need to prompt for open if we are already running the tile service
                showNotification(
                    context,
                    "Please open breadcrumb companion app",
                    "The companion app needs to be launched so that we can start reading tiles for maps"
                    )

            }
        }
    }

    companion object {
        private const val TAG = "ConnectIQMessageReceiver"
        private const val CHANNEL_ID = "ConnectIQMessageReceiver"
        private const val NOTIFICATION_ID = 0
    }

    fun showNotification(context: Context, title: String, message: String) {
        val name = "My Channel Name"
        val descriptionText = "My Channel Description"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        // Build the Notification
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)  // Automatically removes the notification when the user taps it

        // Show the Notification
        with(NotificationManagerCompat.from(context)) {
            // notificationId is a unique int for each notification that you must define
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                notify(NOTIFICATION_ID, builder.build())
            }
        }
    }
}