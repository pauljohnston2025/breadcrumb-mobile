package com.paul.infrastructure.protocol

class RequestTileLoad(
 private val lat: Float,
 private val long: Float,
) : Protocol {
    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_REQUEST_TILE_LOAD
    }

    override fun payload(): List<Any> {
        return mutableListOf(lat, long)
    }
}