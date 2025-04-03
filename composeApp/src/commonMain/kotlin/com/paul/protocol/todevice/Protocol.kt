package com.paul.protocol.todevice

enum class ProtocolType(val value: UByte) {
    PROTOCOL_ROUTE_DATA(0u),
    PROTOCOL_MAP_TILE(1u),
    PROTOCOL_REQUEST_LOCATION_LOAD(2u),
    PROTOCOL_CANCEL_LOCATION_REQUEST(3u),
    PROTOCOL_REQUEST_SETTINGS(4u),
    PROTOCOL_SAVE_SETTINGS(5u),
}

interface Protocol {
    fun type(): ProtocolType
    fun payload(): List<Any>
}