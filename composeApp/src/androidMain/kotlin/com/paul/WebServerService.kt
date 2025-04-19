package com.paul

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Build
import android.os.IBinder
import io.github.aakira.napier.Napier
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.paul.WebServerService.Companion.NOTIFICATION_ID
import com.paul.infrastructure.repositories.TileServerRepo.Companion.TILE_SERVER_ENABLED_KEY
import com.paul.infrastructure.service.FileHelper
import com.paul.infrastructure.service.ImageProcessor
import com.paul.infrastructure.repositories.TileRepository
import com.russhwolf.settings.Settings
import com.paul.infrastructure.web.WebServerController as CommonWebServerController
import com.paul.infrastructure.web.WebServerService as CommonWebServerService

class WebServerController(private val context: Context): CommonWebServerController
{
    val settings: Settings = Settings()

    fun onStart()
    {
        val enabled = settings.getBooleanOrNull(TILE_SERVER_ENABLED_KEY)
        if (enabled == null || enabled) // null check for anyone who has never set the setting, defaults to enabled
        {
            startWebServer()
        }
    }

    override fun changeTileServerEnabled(tileServerEnabled: Boolean) {
        if (tileServerEnabled)
        {
            startWebServer()
            return
        }

        val serviceIntent = Intent(context, WebServerService::class.java)
        context.stopService(serviceIntent)
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
    }

    private fun startWebServer()
    {
        try {
            val serviceIntent = Intent(context, WebServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
        catch (t: Throwable)
        {
            Napier.d("failed to start service (lets hope its because it's already running) $t")
        }
    }
}

class WebServerService : Service() {
    companion object {
        private const val CHANNEL_ID = "WebServerService"
        const val NOTIFICATION_ID = 2
    }

    // service has its own impl of tileGetter, since it runs outside of the main activity
    private val tileGetter = TileRepository(
        ImageProcessor(this),
        FileHelper(this)
    )
    private val server = CommonWebServerService(tileGetter)

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        server.start()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, createNotification(this), FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification(this))
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        server.stop()
    }

    private fun createNotification(context: Context): Notification {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // --- 1. Create Notification Channel (Required for API 26+) ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Check if channel already exists (optional, but good practice)
            if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID, // Must match ID used in Builder
                    "Ktor Server Service",   // User-visible name
                    NotificationManager.IMPORTANCE_LOW // Use LOW or MIN to be less intrusive
                ).apply {
                    description = "Channel for background server"
                    // Make it less intrusive (no sound, vibration etc.)
                    setSound(null, null)
                    enableVibration(false)
                    enableLights(false)
                }
                notificationManager.createNotificationChannel(channel)
                Napier.d("KtorServerService: Notification channel created.")
            } else {
                Napier.d("KtorServerService: Notification channel already exists.")
            }
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon)
            .setContentTitle("Server Running")
            .setContentText("Tile sever is active in the background.")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}