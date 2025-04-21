package com.paul.composables

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun byteArrayToImageBitmap(data: ByteArray?): ImageBitmap? {
    return data?.let {
        try {
            BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
        } catch (e: Exception) {
            // Log error
            null
        }
    }
}