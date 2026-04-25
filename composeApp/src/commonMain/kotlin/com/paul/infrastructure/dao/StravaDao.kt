package com.paul.infrastructure.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.paul.domain.StravaActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface StravaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<StravaActivity>)

    // Selected based on date range. Instant is stored as Long (epochSeconds)
    @Query("SELECT * FROM strava_activities WHERE startDate >= :start AND startDate <= :end ORDER BY startDate DESC")
    fun getActivitiesByDateRange(start: Instant, end: Instant): Flow<List<StravaActivity>>

    @Query("DELETE FROM strava_activities")
    suspend fun clearAll()

    @Query("SELECT MAX(startDate) FROM strava_activities")
    suspend fun getLatestTimestamp(): Instant?

    @Query("SELECT MIN(startDate) FROM strava_activities")
    suspend fun getOldestTimestamp(): Instant?

    @Query("SELECT COUNT(*) FROM strava_activities")
    suspend fun size(): Long

    @Query("SELECT COUNT(*) FROM strava_activities")
    fun sizeFlow(): Flow<Long>
}