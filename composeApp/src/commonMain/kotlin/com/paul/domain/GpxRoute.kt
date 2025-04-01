package com.paul.domain

import androidx.compose.material.SnackbarHostState
import com.paul.protocol.todevice.Route

interface GpxRoute {
    suspend fun toRoute(snackbarHostState: SnackbarHostState): Route?
    fun name() : String
    fun rawBytes() : ByteArray
}