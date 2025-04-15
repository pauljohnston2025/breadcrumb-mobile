package com.paul.infrastructure.connectiq

import com.paul.domain.IqDevice
import com.paul.protocol.fromdevice.ProtocolResponse
import com.paul.protocol.todevice.Protocol
import com.paul.protocol.fromdevice.Protocol as Response

class ConnectIqNeedsInstall : Exception() {
    override val message: String
        get() = "Connect Iq App needs to be installed"
}

class ConnectIqNeedsUpdate : Exception() {
    override val message: String
        get() = "Connect Iq App needs to be updated"
}

interface IConnection {

    companion object {
        val CONNECT_IQ_APP_ID = "20edd04a-9fdc-4291-b061-f49d5699394d"
    }

    suspend fun start()

    suspend fun send(device: IqDevice, payload: Protocol)
    suspend fun <T: Response> query(device: IqDevice, payload: Protocol, type: ProtocolResponse): T
}