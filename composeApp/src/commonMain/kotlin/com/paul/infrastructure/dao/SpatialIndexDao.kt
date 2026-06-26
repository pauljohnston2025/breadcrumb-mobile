package com.paul.infrastructure.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.paul.domain.MapSegmentTile
import com.paul.domain.SegmentInfo
import com.paul.domain.SegmentType

@Dao
interface SpatialIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(segments: List<SegmentInfo>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTileMappings(mappings: List<MapSegmentTile>)

    @Query("DELETE FROM segment_info WHERE type = :type AND ownerId = :ownerId")
    suspend fun deleteSegments(type: SegmentType, ownerId: String)

    @Query("DELETE FROM map_segment_tile WHERE type = :type AND ownerId = :ownerId")
    suspend fun deleteTileMappings(type: SegmentType, ownerId: String)

    @Query("DELETE FROM segment_info WHERE type = :type")
    suspend fun clearSegments(type: SegmentType)

    @Query("DELETE FROM map_segment_tile WHERE type = :type")
    suspend fun clearTileMappings(type: SegmentType)

    @Query("DELETE FROM segment_info")
    suspend fun clearAllSegments()

    @Query("DELETE FROM map_segment_tile")
    suspend fun clearAllTileMappings()

    @Query("SELECT COUNT(*) FROM segment_info")
    suspend fun getSegmentCount(): Long

    @Query("SELECT COUNT(*) FROM map_segment_tile")
    suspend fun getTileMappingCount(): Long

    @Query("""
        SELECT si.* FROM segment_info si
        INNER JOIN map_segment_tile mst ON si.type = mst.type AND si.ownerId = mst.ownerId AND si.segmentIndex = mst.segmentIndex
        WHERE mst.z = :z AND mst.x = :x AND mst.y = :y
    """)
    suspend fun getSegmentsInTile(x: Int, y: Int, z: Int): List<SegmentInfo>

    @Query("""
        SELECT si.* FROM segment_info si
        INNER JOIN (
            SELECT DISTINCT type, ownerId, segmentIndex 
            FROM map_segment_tile 
            WHERE z = :z AND x BETWEEN :xMin AND :xMax AND y BETWEEN :yMin AND :yMax
        ) mst ON si.type = mst.type AND si.ownerId = mst.ownerId AND si.segmentIndex = mst.segmentIndex
        ORDER BY si.type, si.ownerId, si.segmentIndex
    """)
    suspend fun getSegmentsInTiles(xMin: Int, xMax: Int, yMin: Int, yMax: Int, z: Int): List<SegmentInfo>
}
