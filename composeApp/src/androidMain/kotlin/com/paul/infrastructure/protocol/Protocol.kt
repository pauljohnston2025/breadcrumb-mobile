package com.paul.infrastructure.protocol

enum class ProtocolType(val value: UByte) {
    PROTOCOL_ROUTE_DATA(0u),
    PROTOCOL_MAP_TILE(1u),
    PROTOCOL_REQUEST_TILE_LOAD(2u),
}

interface Protocol {
    fun type(): ProtocolType
    fun payload(): List<Any>
}