package com.paul.infrastructure.protocol

import kotlin.random.Random

class RequestTileLoad() : Protocol {
    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_REQUEST_TILE_LOAD
    }

    override fun payload(): List<Any> {
        return mutableListOf()
    }
}