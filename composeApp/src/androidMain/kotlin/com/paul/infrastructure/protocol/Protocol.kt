package com.paul.infrastructure.protocol

enum class ProtocolType(val value: UByte) {
    PROTOCOL_ROUTE_DATA(0u),
}

interface Protocol {
    fun type(): ProtocolType
    fun payload(): List<Any>
}