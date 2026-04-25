package com.paul.infrastructure.persistence

import androidx.room.TypeConverter
import com.paul.protocol.todevice.Point
import kotlinx.serialization.json.Json

class PointListConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromPointList(points: List<Point>?): String? {
        return points?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toPointList(data: String?): List<Point>? {
        return data?.let { json.decodeFromString<List<Point>>(it) }
    }
}