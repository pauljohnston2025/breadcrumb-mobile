package com.paul.infrastructure.service

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.*

// Simple representation of Latitude/Longitude
data class GeoPosition(val latitude: Double, val longitude: Double)

// Tile identifier
data class TileId(val x: Int, val y: Int, val z: Int)

// Information about a tile to be displayed
data class TileInfo(
    val id: TileId,
    val screenOffset: IntOffset, // Where to draw it on screen
    val size: IntSize = IntSize(TILE_SIZE, TILE_SIZE) // Assuming fixed tile size
)

// --- Constants ---
const val TILE_SIZE = 256 // Standard tile size in pixels
const val EARTH_RADIUS_METERS = 6378137.0
const val EARTH_CIRCUMFERENCE_METERS = 2 * PI * EARTH_RADIUS_METERS
const val MAX_LATITUDE = 85.05112877980659 // Max latitude for Web Mercator

// --- Coordinate Conversion Helpers ---

// Convert Lat/Lon to world pixel coordinates at zoom 0
fun geoToWorldPixel(geo: GeoPosition): Pair<Double, Double> {
    val sinLatitude = sin(Math.toRadians(geo.latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)))
    val x = (geo.longitude + 180.0) / 360.0
    val y = 0.5 - ln((1.0 + sinLatitude) / (1.0 - sinLatitude)) / (4.0 * PI)
    return Pair(x, y)
}

// Convert world pixel coordinates (zoom 0) to Lat/Lon
fun worldPixelToGeo(px: Double, py: Double): GeoPosition {
    val longitude = px * 360.0 - 180.0
    val n = PI - 2.0 * PI * py
    val latitude = Math.toDegrees(atan(0.5 * (exp(n) - exp(-n))))
    return GeoPosition(latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE), longitude)
}

// Get the scale factor (pixels per world pixel unit at zoom 0) for a given zoom level
fun getScaleFactor(zoom: Int): Double {
    return TILE_SIZE * (1 shl zoom).toDouble() // 2^zoom
}

// Convert Lat/Lon to screen pixel offset relative to the top-left of the map viewport
fun geoToScreenPixel(
    geo: GeoPosition,
    mapCenterGeo: GeoPosition,
    zoom: Int,
    viewportSize: IntSize
): IntOffset {
    val scale = getScaleFactor(zoom)
    val (worldCenterX, worldCenterY) = geoToWorldPixel(mapCenterGeo)
    val (worldTargetX, worldTargetY) = geoToWorldPixel(geo)

    // Pixel difference from map center in world pixels (at zoom 0)
    val dxWorld = worldTargetX - worldCenterX
    val dyWorld = worldTargetY - worldCenterY

    // Pixel difference from map center in screen pixels at the current zoom
    val dxScreen = dxWorld * scale
    val dyScreen = dyWorld * scale

    // Screen coordinates relative to the viewport center
    val screenX = viewportSize.width / 2.0 + dxScreen
    val screenY = viewportSize.height / 2.0 + dyScreen

    return IntOffset(screenX.roundToInt(), screenY.roundToInt())
}

// Convert Screen pixel offset to Lat/Lon
fun screenPixelToGeo(
    screenPixel: IntOffset,
    mapCenterGeo: GeoPosition,
    zoom: Int,
    viewportSize: IntSize
): GeoPosition {
    val scale = getScaleFactor(zoom)
    val (worldCenterX, worldCenterY) = geoToWorldPixel(mapCenterGeo)

    // Screen coordinates relative to viewport center
    val screenRelX = screenPixel.x - viewportSize.width / 2.0
    val screenRelY = screenPixel.y - viewportSize.height / 2.0

    // World pixel coordinates relative to viewport center (at zoom 0)
    val dxWorld = screenRelX / scale
    val dyWorld = screenRelY / scale

    // Absolute world pixel coordinates (at zoom 0)
    val worldTargetX = worldCenterX + dxWorld
    val worldTargetY = worldCenterY + dyWorld

    return worldPixelToGeo(worldTargetX, worldTargetY)
}

// Convert Lat/Lon to Tile X/Y (similar to ViewModel one, ensure consistency)
fun latLonToTileXY(lat: Double, lon: Double, zoom: Int): Pair<Int, Int> {
    val n = 1 shl zoom
    val latRad = Math.toRadians(lat)
    val xTile = floor((lon + 180.0) / 360.0 * n).toInt()
    val yTile = floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
    // Clamp values to valid range for the zoom level
    val maxTileIndex = n - 1
    return Pair(xTile.coerceIn(0, maxTileIndex), yTile.coerceIn(0, maxTileIndex))
}
