package com.paul.infrastructure.service

import java.io.DataInputStream
import java.io.InputStream


class InputStreamHelpers {
    companion object {
        // inputStream.readAllBytes() is not supported on api 26
        fun readAllBytes(inputStream: InputStream): ByteArray {
            // inputStream.reset() not supported on api 26
            val bytes = ByteArray(inputStream.available())
            val dataInputStream = DataInputStream(inputStream)
            dataInputStream.readFully(bytes)
            return bytes
        }
    }
}