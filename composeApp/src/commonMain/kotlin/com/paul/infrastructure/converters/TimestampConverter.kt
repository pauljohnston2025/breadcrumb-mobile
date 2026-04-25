package com.paul.infrastructure.converters

import androidx.room.TypeConverter
import kotlinx.datetime.Instant

class TimestampConverter {
    @TypeConverter
    fun fromInstant(value: Instant): Long = value.epochSeconds
    @TypeConverter
    fun toInstant(value: Long): Instant = Instant.fromEpochSeconds(value)
}