package com.paul.domain

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

enum class SegmentType {
    ROUTE, STRAVA
}

@Entity(tableName = "segment_info", primaryKeys = ["z", "type", "ownerId", "segmentIndex"])
data class SegmentInfo(
    @ColumnInfo(defaultValue = "0")
    val z: Int,
    val type: SegmentType,
    val ownerId: String, // route id or activity id (as string)
    val segmentIndex: Int,
    @ColumnInfo(defaultValue = "0.0")
    val worldX1: Float,
    @ColumnInfo(defaultValue = "0.0")
    val worldY1: Float,
    @ColumnInfo(defaultValue = "0.0")
    val worldX2: Float,
    @ColumnInfo(defaultValue = "0.0")
    val worldY2: Float
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
