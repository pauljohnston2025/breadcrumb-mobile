package com.paul.protocol.todevice

class RequestSettings() : Protocol {
    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_REQUEST_SETTINGS
    }

    override fun payload(): List<Any> {
        return mutableListOf()
    }
}