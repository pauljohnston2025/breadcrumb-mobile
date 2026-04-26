package com.paul.infrastructure.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.paul.domain.StravaActivity
import com.paul.domain.StravaStreamEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface StravaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<StravaActivity>)

    // Selected based on date range. Instant is stored as Long (epochSeconds)
    @Query("SELECT * FROM strava_activities WHERE startDate >= :start AND startDate <= :end ORDER BY startDate DESC")
    fun getActivitiesByDateRange(start: Instant, end: Instant): Flow<List<StravaActivity>>

    @Query("SELECT COUNT(*) FROM strava_activities WHERE startDate >= :start AND startDate <= :end")
    fun getTotalActivityCount(start: Instant, end: Instant): Flow<Long>

    @Query("DELETE FROM strava_activities")
    suspend fun clearAll()

    @Query("DELETE FROM strava_streams")
    suspend fun clearAllStreams()

    @Query("SELECT MAX(startDate) FROM strava_activities")
    suspend fun getLatestTimestamp(): Instant?

    @Query("SELECT MIN(startDate) FROM strava_activities")
    suspend fun getOldestTimestamp(): Instant?

    @Query("SELECT COUNT(*) FROM strava_activities")
    suspend fun size(): Long

    @Query("SELECT COUNT(*) FROM strava_activities")
    fun sizeFlow(): Flow<Long>

    // Stream operations (HEAVY)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStream(stream: StravaStreamEntity)

    @Query("SELECT * FROM strava_streams WHERE activityId = :activityId")
    suspend fun getStreamForActivity(activityId: Long): StravaStreamEntity?

    @Query("SELECT * FROM strava_streams WHERE activityId IN (:ids)")
    suspend fun getStreamsForActivityIds(ids: List<Long>): List<StravaStreamEntity>

    @Query("""
        SELECT * FROM strava_activities 
        WHERE startDate >= :start AND startDate <= :end 
        ORDER BY startDate DESC 
        LIMIT :pageSize OFFSET :page * :pageSize
    """)
    fun getActivitiesByDateRangeAndPage(
        start: Instant,
        end: Instant,
        page: Long,
        pageSize: Long
    ): Flow<List<StravaActivity>>

    @Query("""
        SELECT id FROM strava_activities 
        WHERE id NOT IN (SELECT activityId FROM strava_streams)
    """)
    suspend fun getActivityIdsMissingStreams(): List<Long>

    @Query("SELECT * FROM strava_activities WHERE id = :id")
    suspend fun getActivity(id: Long): StravaActivity?
}