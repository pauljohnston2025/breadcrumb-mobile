package com.paul.infrastructure.service

import com.paul.domain.FitRoute
import com.paul.protocol.todevice.Point

interface IFitFileLoader {
    suspend fun loadFitFromBytes(bytes: ByteArray, name: String = "FIT Route"): FitRoute
}
