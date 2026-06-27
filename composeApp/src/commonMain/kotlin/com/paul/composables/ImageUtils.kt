package com.paul.composables

import androidx.compose.ui.graphics.ImageBitmap

expect fun byteArrayToImageBitmap(data: ByteArray?): ImageBitmap?
expect fun imageBitmapToByteArray(bitmap: ImageBitmap): ByteArray?
