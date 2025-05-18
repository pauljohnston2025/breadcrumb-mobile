package com.paul.protocol.todevice

enum class ProtocolType(val value: UByte) {
    PROTOCOL_ROUTE_DATA(0u), // deprecated, we still support for older watch apps
//    PROTOCOL_MAP_TILE(1u), deprecated, watch queries off us now
    PROTOCOL_REQUEST_LOCATION_LOAD(2u),
    PROTOCOL_CANCEL_LOCATION_REQUEST(3u),
    PROTOCOL_REQUEST_SETTINGS(4u),
    PROTOCOL_SAVE_SETTINGS(5u),
    PROTOCOL_COMPANION_APP_TILE_SERVER_CHANGED(6u),
    PROTOCOL_ROUTE_DATA2(7u),
}

interface Protocol {
    fun type(): ProtocolType
    fun payload(): List<Any>
}