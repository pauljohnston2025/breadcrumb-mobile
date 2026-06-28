package com.paul.infrastructure.connectiq

import com.paul.domain.IqDevice
import com.paul.protocol.fromdevice.Protocol
import kotlinx.coroutines.flow.Flow

interface IDeviceList {
    val isLoaded: Flow<Boolean>
    suspend fun subscribe(): Flow<List<IqDevice>>
}
