package com.paul.protocol.todevice

class ReturnToUser() : Protocol {
    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_RETURN_TO_USER
    }

    override fun payload(): List<Any> {
        return mutableListOf()
    }
}