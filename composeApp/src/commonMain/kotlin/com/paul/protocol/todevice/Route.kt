package com.paul.protocol.todevice

import kotlinx.serialization.Serializable
import kotlin.Float.Companion.NaN
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.tan

data class RectPoint(val x: Float, val y: Float, val altitude: Float)
{
    fun valid(): Boolean
    {
        return !x.isNaN() && !y.isNaN() && !altitude.isNaN()
    }
}

@Serializable
data class Point(val latitude: Float, val longitude: Float, val altitude: Float) {
    val _pi360 = PI / 360.0f
    val _lonConversion = 20037508.34f / 180.0f
    val _pi180 = PI / 180.0f;

    // this function needs to exactly match RectangularPoint.latLon2xy on the watch app
    fun convert2XY(): RectPoint? {

        val latRect = ((ln(tan((90 + latitude) * _pi360)) / _pi180) * _lonConversion)
        val lonRect = longitude * _lonConversion;

        val point = RectPoint(lonRect, latRect.toFloat(), altitude);
        if (!point.valid()) {
            return null
        }

        return point;
    }
}

class Route(private val name: String, private val route: List<Point>) : Protocol {
    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_ROUTE_DATA
    }

    override fun payload(): List<Any> {
        val data = mutableListOf<Any>(name)

        for (point in route) {
            data.add(point.latitude)
            data.add(point.longitude)
            data.add(point.altitude)
        }

        return data
    }

    fun toV2(): Route2
    {
        return Route2(name, route.map { it.convert2XY() }.mapNotNull { it })
    }
}

class Route2(private val name: String, private val route: List<RectPoint>) : Protocol {
    override fun type(): ProtocolType {
        return ProtocolType.PROTOCOL_ROUTE_DATA2
    }

    override fun payload(): List<Any> {
        val data = mutableListOf<Any>(name)

        val routeData = mutableListOf<Any>()
        for (point in route) {
            routeData.add(point.x)
            routeData.add(point.y)
            routeData.add(point.altitude)
        }
        data.add(routeData)

        return data
    }
}