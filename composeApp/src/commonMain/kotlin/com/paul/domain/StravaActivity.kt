package com.paul.domain

import com.paul.protocol.todevice.Point
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StravaTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class StravaActivity(
    val id: Long,
    val name: String,
    @SerialName("start_date") val startDate: Instant,
    val map: StravaMap? = null
)

@Serializable
data class StravaMap(
    @SerialName("summary_polyline") val summaryPolyline: String? = null
) {
    /**
     * Decodes the Google Encoded Polyline algorithm into a list of Point objects.
     * Summary polylines only contain Latitude and Longitude.
     */
    fun decodePolyline(): List<Point> {
        val polyline = summaryPolyline ?: return emptyList()
        val points = mutableListOf<Point>()
        var index = 0
        var lat = 0
        var lng = 0

        try {
            while (index < polyline.length) {
                // Decode Latitude
                var b: Int
                var shift = 0
                var result = 0
                do {
                    b = polyline[index++].code - 63
                    result = result or (b and 0x1f shl shift)
                    shift += 5
                } while (b >= 0x20)
                val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
                lat += dlat

                // Decode Longitude
                shift = 0
                result = 0
                do {
                    b = polyline[index++].code - 63
                    result = result or (b and 0x1f shl shift)
                    shift += 5
                } while (b >= 0x20)
                val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
                lng += dlng

                // Strava summary polylines are E5 (1e5) precision
                points.add(
                    Point(
                        latitude = lat.toFloat() / 100000f,
                        longitude = lng.toFloat() / 100000f,
                        altitude = 0f // Altitude is not available in summary_polyline
                    )
                )
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return points
    }
}