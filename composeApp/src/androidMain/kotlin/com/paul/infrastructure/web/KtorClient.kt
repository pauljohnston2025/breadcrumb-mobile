package com.paul.infrastructure.web

import android.content.Intent
import android.net.Uri
import android.os.Build
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.* // Or import io.ktor.client.engine.android.*

actual fun createHttpClientEngine(): HttpClientEngineFactory<*> {
    return OkHttp
}

actual fun platformInfo(): String
{
    val osName = "Android"
    val osVersion = Build.VERSION.RELEASE ?: "Unknown"
    val deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"

    return "($osName $osVersion $deviceModel)"
}

actual fun versionName(): String = com.paul.BuildConfig.VERSION_NAME
