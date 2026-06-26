package com.paul.infrastructure.converters

import androidx.room.TypeConverter
import com.paul.domain.SegmentType

class SegmentTypeConverter {
    @TypeConverter
    fun fromString(value: String): SegmentType {
        return try {
            SegmentType.valueOf(value)
        } catch (e: Exception) {
            SegmentType.ROUTE // Default to Route if corrupted
        }
    }

    @TypeConverter
    fun toString(type: SegmentType): String = type.name
}
