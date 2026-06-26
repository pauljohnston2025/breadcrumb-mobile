package com.paul.infrastructure.service

import com.paul.infrastructure.dao.StravaDao
import com.paul.infrastructure.repositories.RouteRepository
import com.paul.infrastructure.repositories.SpatialIndexRepository
import com.paul.domain.SegmentType
import com.russhwolf.settings.Settings
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MigrationService(
    private val spatialIndexRepository: SpatialIndexRepository,
    private val stravaDao: StravaDao,
    private val routeRepository: RouteRepository,
) {
    private val settings = Settings()
    private val SPATIAL_INDEX_VERSION_KEY = "SPATIAL_INDEX_VERSION"

    private val _migrationStatus = MutableStateFlow<String?>(null)
    val migrationStatus: StateFlow<String?> = _migrationStatus.asStateFlow()

    private val _isMigrating = MutableStateFlow(false)
    val isMigrating: StateFlow<Boolean> = _isMigrating.asStateFlow()

    fun checkAndRunMigrations(scope: CoroutineScope) {
        val currentVersion = settings.getInt(SPATIAL_INDEX_VERSION_KEY, 0)
        if (currentVersion < SpatialIndexRepository.SPATIAL_INDEX_VERSION) {
            scope.launch(Dispatchers.Default) {
                runSpatialIndexMigration()
            }
        }
    }

    private suspend fun runSpatialIndexMigration() {
        _isMigrating.value = true
        _migrationStatus.value = "Starting Spatial Index Migration..."
        Napier.i("Starting Spatial Index Migration to version ${SpatialIndexRepository.SPATIAL_INDEX_VERSION}...", tag = "MigrationService")
        try {
            // Clear existing index first to ensure a clean migration
            _migrationStatus.value = "Clearing old index..."
            spatialIndexRepository.clearAll()

            // 1. Migrate Routes
            // Ensure routes are loaded. 
            // Since routeRepository.routes is a mutableStateListOf populated in init, 
            // it should be ready, but let's give it a tiny delay just in case of any race.
            val totalRoutes = routeRepository.routes.size
            Napier.i("Found $totalRoutes routes to index", tag = "MigrationService")
            routeRepository.routes.forEachIndexed { index, routeEntry ->
                _migrationStatus.value = "Indexing Routes: ${index + 1} / $totalRoutes"
                routeEntry.summary?.let { points ->
                    spatialIndexRepository.indexRoute(routeEntry.id, points)
                }
            }

            // 2. Migrate Strava Activities
            val totalStrava = stravaDao.size()
            val pageSize = 50L
            val totalPages = (totalStrava + pageSize - 1) / pageSize
            
            for (page in 0 until totalPages) {
                // We need a way to fetch a page of activities.
                // StravaDao has getActivitiesByDateRangeAndPage but that requires a date range.
                // Let's add a simple paged getAllActivities.
                val activities = stravaDao.getAllActivitiesPaged(page, pageSize)
                activities.forEachIndexed { index, activity ->
                    val globalIndex = page * pageSize + index + 1
                    _migrationStatus.value = "Indexing Strava: $globalIndex / $totalStrava"
                    
                    val stream = stravaDao.getStreamForActivity(activity.id)
                    val points = stream?.points ?: activity.summaryToRoute().route
                    if (points.isNotEmpty()) {
                        spatialIndexRepository.indexStravaActivity(activity.id, points)
                    }
                }
            }

            settings.putInt(SPATIAL_INDEX_VERSION_KEY, SpatialIndexRepository.SPATIAL_INDEX_VERSION)
            _migrationStatus.value = "Spatial Index Migration complete."
            Napier.i("Spatial Index Migration complete.", tag = "MigrationService")
        } catch (e: Exception) {
            _migrationStatus.value = "Spatial Index Migration failed: ${e.message}"
            Napier.e("Spatial Index Migration failed", e, tag = "MigrationService")
        } finally {
            _isMigrating.value = false
            // Keep the status for a few seconds then clear it if successful? 
            // Or just leave it for the UI to handle.
        }
    }
}
