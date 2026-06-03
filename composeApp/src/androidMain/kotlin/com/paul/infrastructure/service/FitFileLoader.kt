package com.paul.infrastructure.service

import androidx.compose.material.SnackbarHostState
import com.garmin.fit.CourseMesg
import com.garmin.fit.CourseMesgListener
import com.garmin.fit.Decode
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.RecordMesg
import com.garmin.fit.RecordMesgListener
import com.garmin.fit.SessionMesg
import com.garmin.fit.SessionMesgListener
import com.paul.domain.FitRoute
import com.paul.protocol.todevice.Point
import com.paul.protocol.todevice.Route
import io.github.aakira.napier.Napier
import java.io.ByteArrayInputStream

class FitFileLoader : IFitFileLoader {
    private val TAG = "FitFileLoader"

    override suspend fun loadFitFromBytes(bytes: ByteArray, name: String): FitRoute {
        val result = parseFitMetadata(bytes)
        val finalName = result.internalName ?: name
        return FitFile(bytes, this, finalName)
    }

    private data class FitParseResult(
        val points: List<Point>,
        val internalName: String?
    )

    private fun parseFitMetadata(bytes: ByteArray): FitParseResult {
        val points = mutableListOf<Point>()
        var internalName: String? = null
        if (bytes.isEmpty()) return FitParseResult(points, null)

        val decode = Decode()
        val mesgBroadcaster = MesgBroadcaster(decode)
        
        mesgBroadcaster.addListener(object : RecordMesgListener {
            override fun onMesg(mesg: RecordMesg) {
                val lat = mesg.positionLat
                val lng = mesg.positionLong
                val alt = mesg.altitude
                if (lat != null && lng != null) {
                    val latDeg = lat.toFloat() * (180f / 2147483648f)
                    val lngDeg = lng.toFloat() * (180f / 2147483648f)
                    points.add(Point(latDeg, lngDeg, alt ?: 0f))
                }
            }
        })

        mesgBroadcaster.addListener(object : CourseMesgListener {
            override fun onMesg(mesg: CourseMesg) {
                // 'Course' is used for routes/planned paths
                // anything downloaded from garmin or strava does not seem to include the name in
                // the fit file, but it does in the gpx export - go figure
                mesg.name?.let { if (it.isNotBlank()) internalName = it }
            }
        })

        mesgBroadcaster.addListener(object : SessionMesgListener {
            override fun onMesg(mesg: SessionMesg) {
                // 'Session' is used for recorded activities
                // Sometimes the name is in firstSessionSubject, but less common than Course name
                // Napier.d { "got session message" }
            }
        })

        try {
            decode.read(ByteArrayInputStream(bytes), mesgBroadcaster, mesgBroadcaster)
        } catch (e: Exception) {
            Napier.e("FIT parse error", e, tag = TAG)
        }
        
        return FitParseResult(points, internalName)
    }

    suspend fun getPointsFromBytes(bytes: ByteArray): List<Point> {
        return parseFitMetadata(bytes).points
    }
}

data class FitFile(
    val bytes: ByteArray,
    private val loader: FitFileLoader,
    private var _name: String = "FIT Route"
) : FitRoute() {
    override suspend fun toRouteForDevice(snackbarHostState: SnackbarHostState): Route? {
        val points = getPoints()
        return Route.prepareForDevice(_name, points, emptyList())
    }

    override suspend fun toSummary(snackbarHostState: SnackbarHostState): List<Point> {
        return Route.summary(getPoints())
    }

    override fun name(): String = _name

    override fun rawBytes(): ByteArray = bytes

    override fun setName(name: String) {
        _name = name
    }

    override fun hasDirectionInfo(): Boolean = false

    override suspend fun getPoints(): List<Point> {
        return loader.getPointsFromBytes(bytes)
    }
}
