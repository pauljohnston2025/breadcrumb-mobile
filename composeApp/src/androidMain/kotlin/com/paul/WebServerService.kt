package com.paul

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.paul.infrastructure.service.FileHelper
import com.paul.infrastructure.service.ImageProcessor
import com.paul.infrastructure.service.TileGetter
import com.paul.infrastructure.web.WebServerService as CommonWebServerService

class WebServerService : Service() {
    // service has its own impl of tileGetter, since it runs outside of the main activity
    private val tileGetter = TileGetter(
        ImageProcessor(this),
        FileHelper(this)
    )
    private val server = CommonWebServerService(tileGetter)

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return server.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        server.stop()
    }
}