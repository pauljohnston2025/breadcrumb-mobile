package com.paul

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.garmin.android.connectiq.ConnectIQ
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.repositories.TileServerRepo.Companion.TILE_SERVER_ENABLED_KEY
import com.paul.infrastructure.web.CheckStatusRequest
import com.paul.infrastructure.web.KtorClient
import com.russhwolf.settings.Settings
import io.github.aakira.napier.Napier
import io.ktor.client.plugins.resources.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout


class ConnectIQMessageReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ConnectIQMessageReceiver"
        private const val CHANNEL_ID = "ConnectIQMessageReceiver"
        private const val NOTIFICATION_ID = 1
    }

    private val client = KtorClient.client // Get the singleton client instance
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings: Settings = Settings()

    // see https://forums.garmin.com/developer/connect-iq/f/discussion/4339/start-an-android-service-from-watch/29284#29284
    override fun onReceive(context: Context, intent: Intent) {
        val enabled = settings.getBooleanOrNull(TILE_SERVER_ENABLED_KEY)
        if (enabled != null && !enabled) // null check for anyone who has never set the setting, defaults to enabled
        {
            with(NotificationManagerCompat.from(context)) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) ==
                    PackageManager.PERMISSION_GRANTED
                ) {
                    cancel(NOTIFICATION_ID)
                }
            }
            return
        }

        if (intent.action == "com.garmin.android.connectiq.INCOMING_MESSAGE") {
            val appId = intent.getStringExtra(ConnectIQ.EXTRA_APPLICATION_ID)
            val payload = intent.getByteArrayExtra(ConnectIQ.EXTRA_PAYLOAD)
// the rest of these get
// Key <EXTRA_BLAH> expected String but value was a android.os.Parcel$LazyValue

//            val remoteApplication = intent.getStringExtra(ConnectIQ.EXTRA_REMOTE_APPLICATION)
//            val remoteDevice = intent.getStringExtra(ConnectIQ.EXTRA_REMOTE_DEVICE)

            Napier.d("Received Connect IQ message:", tag = TAG)
            Napier.d("  appId: $appId", tag = TAG)
            Napier.d("  payload: $payload", tag = TAG)
//            Napier.d("  remoteApplication: $remoteApplication", tag = TAG)
//            Napier.d("  remoteDevice: $remoteDevice", tag = TAG)
            if (appId?.lowercase() == IConnection.getConnectIqAppIdOnStart().replace("-", "").lowercase()
            // payload appears to change between runs
            /* && payload.toString().lowercase() == "[B@cd05ca5".lowercase() */
            ) {
                Napier.d("got our special start message", tag = TAG)
                // apparently you cannot launch an app if the app is closed, or running inthe background
                // https://stackoverflow.com/questions/59636083/broadcastreceiver-cant-start-activity-from-closed-or-in-background-app
                // https://developer.android.com/guide/components/activities/background-starts
                // need to send notification instead

                scope.launch(Dispatchers.IO) {
                    try {
                        withTimeout(2000) {
                            val response = client.get(CheckStatusRequest())
                            if (response.status.isSuccess()) {
                                return@withTimeout
                            }

                            // somehow we got a bad response?
                            showNotification(context)
                        }
                    } catch (t: Throwable) {
                        // timed out - server not running
                        // some other error - assume server not running
                        showNotification(context)
                    }
                }
            }
        }
    }

    fun showNotification(context: Context) {
        showNotification(
            context,
            "Please open breadcrumb companion app",
            "The companion app needs to be launched so that we can start reading tiles for maps"
        )
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

//        val launchIntent = Intent(Intent.ACTION_VIEW).apply {
//            data = Uri.parse("myapp://open.breadcrumb.app.example.com")
//            flags = Intent.FLAG_ACTIVITY_NEW_TASK
//        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build the Notification
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                notify(NOTIFICATION_ID, builder.build())
            }
        }
    }
}