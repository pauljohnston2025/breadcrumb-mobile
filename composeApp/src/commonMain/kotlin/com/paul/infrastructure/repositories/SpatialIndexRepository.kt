package com.paul.infrastructure.repositories

import com.paul.domain.MapSegmentTile
import com.paul.domain.SegmentInfo
import com.paul.domain.SegmentType
import com.paul.infrastructure.dao.SpatialIndexDao
import com.paul.infrastructure.service.GeoPosition
import com.paul.infrastructure.service.geoToWorldPixel
import com.paul.protocol.todevice.Point
import kotlinx.coroutines.yield
import kotlin.math.*

class SpatialIndexRepository(public val dao: SpatialIndexDao) {
    companion object {
        const val SPATIAL_INDEX_ZOOM = 14
        const val SPATIAL_INDEX_VERSION = 10
    }

    suspend fun indexStravaActivity(activityId: Long, points: List<Point>) {
        indexSegments(SegmentType.STRAVA, activityId.toString(), points)
    }

    suspend fun indexRoute(routeId: String, points: List<Point>) {
        indexSegments(SegmentType.ROUTE, routeId, points)
    }

    suspend fun deleteStravaActivity(activityId: Long) {
        dao.deleteSegments(SegmentType.STRAVA, activityId.toString())
        dao.deleteTileMappings(SegmentType.STRAVA, activityId.toString())
    }

    suspend fun deleteRoute(routeId: String) {
        dao.deleteSegments(SegmentType.ROUTE, routeId)
        dao.deleteTileMappings(SegmentType.ROUTE, routeId)
    }

    suspend fun clear(type: SegmentType) {
        dao.clearSegments(type)
        dao.clearTileMappings(type)
    }

    suspend fun clearAll() {
        dao.clearAllSegments()
        dao.clearAllTileMappings()
    }

    suspend fun getSegmentCount(): Long = dao.getSegmentCount()
    suspend fun getTileMappingCount(): Long = dao.getTileMappingCount()

    private suspend fun indexSegments(type: SegmentType, ownerId: String, points: List<Point>) {
        if (points.size < 2) {
            dao.deleteSegments(type, ownerId)
            dao.deleteTileMappings(type, ownerId)
            return
        }

        dao.deleteSegments(type, ownerId)
        dao.deleteTileMappings(type, ownerId)

        val batchSize = 100
        for (i in 0 until points.size - 1 step batchSize) {
            val segments = mutableListOf<SegmentInfo>()
            val mappings = mutableListOf<MapSegmentTile>()

            for (j in i until min(i + batchSize, points.size - 1)) {
                val p1 = points[j]
                val p2 = points[j + 1]

                val p1Geo = GeoPosition(p1.latitude.toDouble(), p1.longitude.toDouble())
                val p2Geo = GeoPosition(p2.latitude.toDouble(), p2.longitude.toDouble())
                val p1World = geoToWorldPixel(p1Geo)
                val p2World = geoToWorldPixel(p2Geo)

                val segmentInfo = SegmentInfo(
                    type = type,
                    ownerId = ownerId,
                    segmentIndex = j,
                    worldX1 = p1World.first,
                    worldY1 = p1World.second,
                    worldX2 = p2World.first,
                    worldY2 = p2World.second,
                    lat1 = p1Geo.latitude,
                    lon1 = p1Geo.longitude,
                    lat2 = p2Geo.latitude,
                    lon2 = p2Geo.longitude
                )
                segments.add(segmentInfo)

                val intersectedTiles = getTilesIntersectingSegment(
                    p1Geo.latitude, p1Geo.longitude,
                    p2Geo.latitude, p2Geo.longitude,
                    SPATIAL_INDEX_ZOOM
                )

                intersectedTiles.forEach { (tx, ty) ->
                    mappings.add(MapSegmentTile(SPATIAL_INDEX_ZOOM, tx, ty, type, ownerId, j))
                }
            }

            dao.insertSegments(segments)
            dao.insertTileMappings(mappings)
            yield()
        }
    }

    private fun getTilesIntersectingSegment(lat1: Double, lon1: Double, lat2: Double, lon2: Double, zoom: Int): List<Pair<Int, Int>> {
        val n = 1 shl zoom
        
        fun latLonToFractionalTile(lat: Double, lon: Double): Pair<Double, Double> {
            val latRad = lat * PI / 180.0
            val x = (lon + 180.0) / 360.0 * n
            val y = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
            return x to y
        }

        val (x1, y1) = latLonToFractionalTile(lat1.coerceIn(-85.05, 85.05), lon1)
        val (x2, y2) = latLonToFractionalTile(lat2.coerceIn(-85.05, 85.05), lon2)

        val tiles = mutableListOf<Pair<Int, Int>>()
        
        var cx = floor(x1).toInt()
        var cy = floor(y1).toInt()
        val endX = floor(x2).toInt()
        val endY = floor(y2).toInt()

        val dx = abs(x2 - x1)
        val dy = abs(y2 - y1)
        
        val stepX = if (x2 > x1) 1 else -1
        val stepY = if (y2 > y1) 1 else -1

        var tMaxX = if (dx < 0.000001) Double.MAX_VALUE else {
            val nextX = if (stepX > 0) floor(x1) + 1 else ceil(x1) - 1
            abs(nextX - x1) / dx
        }
        var tMaxY = if (dy < 0.000001) Double.MAX_VALUE else {
            val nextY = if (stepY > 0) floor(y1) + 1 else ceil(y1) - 1
            abs(nextY - y1) / dy
        }

        val tDeltaX = if (dx < 0.000001) Double.MAX_VALUE else 1.0 / dx
        val tDeltaY = if (dy < 0.000001) Double.MAX_VALUE else 1.0 / dy

        val maxIter = 1000 // Safety break
        var iter = 0
        
        tiles.add(cx to cy)

        while ((cx != endX || cy != endY) && iter < maxIter) {
            iter++
            if (tMaxX < tMaxY) {
                tMaxX += tDeltaX
                cx += stepX
            } else {
                tMaxY += tDeltaY
                cy += stepY
            }
            tiles.add(cx to cy)
        }
        
        return tiles.distinct()
    }
}
