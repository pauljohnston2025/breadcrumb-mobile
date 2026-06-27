package com.paul.composables

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

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

actual fun imageBitmapToByteArray(bitmap: ImageBitmap): ByteArray? {
    val stream = ByteArrayOutputStream()
    return try {
        bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.toByteArray()
    } catch (e: Exception) {
        null
    }
}
