import kotlin.math.max
import kotlin.math.min

data class Point(val latitude: Double, val longitude: Double, val altitude: Float)

data class BoundingBox(
    val topLeftLat: Double,
    val topLeftLon: Double,
    val bottomRightLat: Double,
    val bottomRightLon: Double
)

class RouteHandler(private val connectIqHandler: ConnectIqHandler) {

    @OptIn(ExperimentalUnsignedTypes::class)
    suspend fun sendRoute(route: List<Point>): Unit {
        val data = mutableListOf<Any>()

        val boundingBox = findBoundingBox(route)
        data.add(boundingBox.topLeftLat.toFloat())
        data.add(boundingBox.topLeftLon.toFloat())
        data.add(boundingBox.bottomRightLat.toFloat())
        data.add(boundingBox.bottomRightLon.toFloat())

        for (point in route) {
            data.add(point.latitude.toFloat())
            data.add(point.longitude.toFloat())
            data.add(point.altitude)
        }

        connectIqHandler.send(Protocol.PROTOCOL_ROUTE_DATA, data)
    }

    /**
     * Calculate bounding box for given List of GeoPoints
     * Based on the osmdroid code by Nicolas Gramlich, released under the Apache License 2.0
     * https://github.com/osmdroid/osmdroid/blob/master/osmdroid-android/src/main/java/org
     * /osmdroid/util/BoundingBox.java
     */
    private fun findBoundingBox(geoPoints: List<Point>): BoundingBox {
        var minLat = Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE

        for (geoPoint in geoPoints) {
            val latitude: Double = geoPoint.latitude
            val longitude: Double = geoPoint.longitude

            minLat = min(minLat, latitude)
            minLon = min(minLon, longitude)
            maxLat = max(maxLat, latitude)
            maxLon = max(maxLon, longitude)
        }
        return BoundingBox(maxLat, maxLon, minLat, minLon)
    }
}