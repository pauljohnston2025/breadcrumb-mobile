package com.paul.infrastructure.connectiq

import com.paul.domain.IqDevice
import kotlinx.coroutines.flow.Flow

interface IDeviceList {
    suspend fun subscribe(): Flow<List<IqDevice>>
}