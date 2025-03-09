package com.paul.infrastructure.protocol

import kotlin.math.max
import kotlin.math.min

data class Point(val latitude: Float, val longitude: Float, val altitude: Float)

class Route(private val route: List<Point>) : Protocol {
    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_ROUTE_DATA
    }

    override fun payload(): List<Any> {
        val data = mutableListOf<Any>()

        for (point in route) {
            data.add(point.latitude)
            data.add(point.longitude)
            data.add(point.altitude)
        }

        return data
    }
}