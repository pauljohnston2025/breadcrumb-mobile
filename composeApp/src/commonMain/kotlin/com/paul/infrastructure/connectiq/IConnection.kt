package com.paul.infrastructure.connectiq

import com.paul.domain.IqDevice
import com.paul.protocol.todevice.Protocol

interface IConnection {

    companion object {
        val CONNECT_IQ_APP_ID = "20edd04a-9fdc-4291-b061-f49d5699394d"
    }

    suspend fun start()

    suspend fun send(device: IqDevice, payload: Protocol)
}