package com.paul.infrastructure.service

import androidx.compose.material.SnackbarHostState
import com.garmin.fit.Decode
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.RecordMesg
import com.garmin.fit.RecordMesgListener
import com.paul.domain.FitRoute
import com.paul.protocol.todevice.Point
import com.paul.protocol.todevice.Route
import io.github.aakira.napier.Napier
import java.io.ByteArrayInputStream

class FitFileLoader : IFitFileLoader {
    private val TAG = "FitFileLoader"

    override suspend fun loadFitFromBytes(bytes: ByteArray, name: String): FitRoute {
        return FitFile(bytes, this, name)
    }

    suspend fun getPointsFromBytes(bytes: ByteArray): List<Point> {
        val points = mutableListOf<Point>()
        if (bytes.isEmpty()) return points

        val decode = Decode()
        val mesgBroadcaster = MesgBroadcaster(decode)
        
        mesgBroadcaster.addListener(object : RecordMesgListener {
            override fun onMesg(mesg: RecordMesg) {
                val lat = mesg.positionLat
                val lng = mesg.positionLong
                val alt = mesg.altitude
                
                if (lat != null && lng != null) {
                    // FIT coordinates are in semicircles. Convert to degrees.
                    val latDeg = lat.toFloat() * (180f / 2147483648f)
                    val lngDeg = lng.toFloat() * (180f / 2147483648f)
                    points.add(Point(latDeg, lngDeg, alt ?: 0f))
                }
            }
        })

        try {
            decode.read(ByteArrayInputStream(bytes), mesgBroadcaster, mesgBroadcaster)
        } catch (e: Exception) {
            Napier.e("FIT parse error", e, tag = TAG)
        }
        
        return points
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
