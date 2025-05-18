package com.paul.protocol.todevice

class CacheCurrentArea() : Protocol {
    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_CACHE_CURRENT_AREA
    }

    override fun payload(): List<Any> {
        return mutableListOf()
    }
}