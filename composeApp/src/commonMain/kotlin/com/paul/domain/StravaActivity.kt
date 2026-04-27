package com.paul.domain

import androidx.compose.material.SnackbarHostState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Kayaking
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Water
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.benasher44.uuid.uuid4
import com.paul.protocol.todevice.Point
import com.paul.protocol.todevice.Route
import kotlinx.datetime.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

@Entity(
    tableName = "strava_streams",
    indices = [Index(value = ["activityId"])],
)
data class StravaStreamEntity(
    @PrimaryKey
    val activityId: Long,
    val points: List<Point> // This still uses your TypeConverter
) {
    fun toRoute(name: String): Route {
        return Route(name, points, emptyList())
    }

    fun toRouteForDevice(name: String): Route {
        return Route.prepareForDevice(name, points, emptyList())
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
    @SerialName("gear_id") val gearId: String? = null,
) {
    companion object {
        val SUPPORTED_TYPES = listOf(
            "All",
            "Run",
            "Ride",
            "Hike",
            "Walk",
            "Swim",
            "WaterSport",
            "Kayaking",
            "Unknown"
        )

        fun getActivityIcon(type: String?): ImageVector {
            return when (type) {
                "All" -> Icons.Default.Layers
                "Run", "VirtualRun" -> Icons.Default.DirectionsRun
                "Ride", "VirtualRide", "EBikeRide" -> Icons.Default.DirectionsBike
                "Hike" -> Icons.Default.Hiking
                "Walk" -> Icons.Default.DirectionsWalk
                "Swim" -> Icons.Default.Pool
                "WaterSport" -> Icons.Default.Water
                "Kayaking" -> Icons.Default.Kayaking
                else -> Icons.Default.QuestionMark
            }
        }
    }

    fun summaryToRoute(): Route {
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

class StaveIRoute(
    val activity: StravaActivity,
    val stravaStreamEntity: StravaStreamEntity
) : IRoute("strava:${activity.id}") {
    override suspend fun toRouteForDevice(snackbarHostState: SnackbarHostState): Route? {
        return stravaStreamEntity.toRouteForDevice(activity.name)
    }

    override suspend fun toSummary(snackbarHostState: SnackbarHostState): List<Point> {
        return activity.summaryToRoute().route
    }

    override fun name(): String {
        return activity.name
    }

    override fun rawBytes(): ByteArray {
        return byteArrayOf()
    }

    override fun setName(name: String) {

    }

    override fun hasDirectionInfo(): Boolean {
        return false
    }
}

@Serializable
data class StravaAthleteResponse(
    val bikes: List<StravaGear> = emptyList(),
    val shoes: List<StravaGear> = emptyList()
)

@Serializable
@Entity(tableName = "strava_gear")
data class StravaGear(
    @PrimaryKey
    val id: String,
    val name: String,
    val primary: Boolean,
    @EncodeDefault
    val type: String = TYPE_BIKE
) {
    companion object {
        const val TYPE_BIKE = "Bike"
        const val TYPE_SHOE = "Shoes"

        fun getGearIcon(type: String): ImageVector {
            return when (type) {
                TYPE_BIKE -> Icons.Default.DirectionsBike
                TYPE_SHOE -> Icons.Default.DirectionsRun
                else -> Icons.Default.QuestionMark
            }
        }
    }
}