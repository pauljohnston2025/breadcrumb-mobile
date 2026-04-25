package com.paul.domain

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.paul.protocol.todevice.Point
import com.paul.protocol.todevice.Route
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class StravaStreamResponse(
    val latlng: StravaLatLngStream? = null,
    val altitude: StravaAltitudeStream? = null,
)

@Serializable
data class StravaLatLngStream(
    val data: List<List<Double>>,
    val series_type: String
)

@Serializable
data class StravaAltitudeStream(
    val data: List<Float>,
    val series_type: String
)

@Serializable
data class StravaTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String
)

@Entity(tableName = "strava_streams")
data class StravaStreamEntity(
    @PrimaryKey
    val activityId: Long,
    val points: List<Point> // This still uses your TypeConverter
) {
    fun toRoute(name: String): Route {
        return Route(name, points, emptyList())
    }
}

@Serializable
@Entity(tableName = "strava_activities")
data class StravaActivity(
    @PrimaryKey
    val id: Long,
    val name: String,
    @SerialName("start_date")
    val startDate: Instant,
    @Embedded
    val map: StravaMap? = null,
    val type: String? = null,
) {
    companion object {
        val SUPPORTED_TYPES = listOf("All", "Run", "Ride", "Hike", "Walk", "Unknown")

        fun getActivityIcon(type: String?): ImageVector {
            return when (type) {
                "All" -> Icons.Default.Layers
                "Ride", "VirtualRide", "EBikeRide" -> Icons.Default.DirectionsBike
                "Run", "VirtualRun" -> Icons.Default.DirectionsRun
                "Walk" -> Icons.Default.DirectionsWalk
                "Hike" -> Icons.Default.Hiking
                "Swim" -> Icons.Default.Pool
                else -> Icons.Default.QuestionMark
            }
        }
    }

    fun toRoute(): Route {
        return Route(name, map?.decodePolyline() ?: emptyList(), emptyList())
    }

    fun getActivityIcon(): ImageVector {
        return getActivityIcon(type)
    }
}

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