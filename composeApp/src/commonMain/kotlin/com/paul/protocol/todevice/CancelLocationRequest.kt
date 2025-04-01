package com.paul.protocol.todevice

class CancelLocationRequest() : Protocol {
    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_CANCEL_LOCATION_REQUEST
    }

    override fun payload(): List<Any> {
        return mutableListOf()
    }
}