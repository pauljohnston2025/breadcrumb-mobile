package com.paul.protocol.todevice

class RequestLocationLoad(
 private val lat: Float,
 private val long: Float,
 private val metersAroundUser: Float,
) : Protocol {
    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_REQUEST_LOCATION_LOAD
    }

    override fun payload(): List<Any> {
        return mutableListOf(lat, long, metersAroundUser)
    }
}