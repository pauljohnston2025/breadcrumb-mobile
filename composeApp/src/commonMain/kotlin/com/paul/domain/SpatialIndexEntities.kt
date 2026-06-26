package com.paul.domain

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

enum class SegmentType {
    ROUTE, STRAVA
}

@Entity(tableName = "segment_info", primaryKeys = ["type", "ownerId", "segmentIndex"])
data class SegmentInfo(
    val type: SegmentType,
    val ownerId: String, // route id or activity id (as string)
    val segmentIndex: Int,
    @ColumnInfo(defaultValue = "0.0")
    val worldX1: Double,
    @ColumnInfo(defaultValue = "0.0")
    val worldY1: Double,
    @ColumnInfo(defaultValue = "0.0")
    val worldX2: Double,
    @ColumnInfo(defaultValue = "0.0")
    val worldY2: Double,
    // Keep Lat/Lon for touch detection if needed, but let's see if we can use world coords there too
    @ColumnInfo(defaultValue = "0.0")
    val lat1: Double,
    @ColumnInfo(defaultValue = "0.0")
    val lon1: Double,
    @ColumnInfo(defaultValue = "0.0")
    val lat2: Double,
    @ColumnInfo(defaultValue = "0.0")
    val lon2: Double
)

@Entity(
    tableName = "map_segment_tile",
    primaryKeys = ["z", "x", "y", "type", "ownerId", "segmentIndex"],
    indices = [
        Index(value = ["type", "ownerId"])
    ]
)
data class MapSegmentTile(
    val z: Int,
    val x: Int,
    val y: Int,
    val type: SegmentType,
    val ownerId: String,
    val segmentIndex: Int
)
