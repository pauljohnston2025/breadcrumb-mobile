package com.paul.protocol.todevice

class DropTileCache() : Protocol {
    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_DROP_TILE_CACHE
    }

    override fun payload(): List<Any> {
        return mutableListOf()
    }
}