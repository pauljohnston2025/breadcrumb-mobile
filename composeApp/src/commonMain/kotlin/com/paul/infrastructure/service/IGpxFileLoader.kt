package com.paul.infrastructure.service

import com.paul.domain.GpxRoute

interface IGpxFileLoader {
    suspend fun loadGpxFromBytes(stream: ByteArray): GpxRoute
}
