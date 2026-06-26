package com.paul.domain

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
    val lat1: Float,
    val lon1: Float,
    val lat2: Float,
    val lon2: Float
)

@Entity(
    tableName = "map_segment_tile",
    primaryKeys = ["x", "y", "z", "type", "ownerId", "segmentIndex"],
    indices = [Index(value = ["type", "ownerId"])]
)
data class MapSegmentTile(
    val x: Int,
    val y: Int,
    val z: Int,
    val type: SegmentType,
    val ownerId: String,
    val segmentIndex: Int
)
