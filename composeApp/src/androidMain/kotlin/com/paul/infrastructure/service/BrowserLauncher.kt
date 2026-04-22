package com.paul.infrastructure.service

import android.content.Context
import android.content.Intent
import android.net.Uri

class BrowserLauncher(private val context: Context) : IBrowserLauncher {
    override fun openUri(uri: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}