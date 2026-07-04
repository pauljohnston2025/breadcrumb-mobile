package com.paul.infrastructure.service

import com.paul.infrastructure.dao.StravaDao
import com.paul.infrastructure.repositories.RouteRepository
import com.paul.infrastructure.repositories.SpatialIndexRepository
import com.paul.domain.SegmentType
import com.paul.protocol.todevice.Route.Companion.ROUTE_SUMMARY_VERSION
import com.russhwolf.settings.Settings
import androidx.compose.material.SnackbarHostState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

class MigrationService(
    private val spatialIndexRepository: SpatialIndexRepository,
    private val stravaDao: StravaDao,
    private val routeRepository: RouteRepository,
) {
    private val settings = Settings()
    private val SPATIAL_INDEX_VERSION_KEY = "SPATIAL_INDEX_VERSION"
    private val MIGRATION_STEP_KEY = "SPATIAL_INDEX_MIGRATION_STEP"
    private val MIGRATION_PROGRESS_KEY = "SPATIAL_INDEX_MIGRATION_PROGRESS"
    private val MIGRATION_TARGET_VERSION_KEY = "SPATIAL_INDEX_MIGRATION_TARGET_VERSION"

    private enum class MigrationStep(val value: Int) {
        CLEAR(0),
        ROUTES(1),
        STRAVA(2),
        COMPLETE(3);

        companion object {
            fun fromInt(value: Int) = values().find { it.value == value } ?: CLEAR
        }
    }

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
        
        val targetVersion = SpatialIndexRepository.SPATIAL_INDEX_VERSION
        Napier.i("Starting Spatial Index Migration to version $targetVersion...", tag = "MigrationService")
        
        try {
            val savedTargetVersion = settings.getInt(MIGRATION_TARGET_VERSION_KEY, 0)
            if (savedTargetVersion != targetVersion) {
                // Target version changed or new migration, reset checkpoints
                settings.putInt(MIGRATION_STEP_KEY, MigrationStep.CLEAR.value)
                settings.putLong(MIGRATION_PROGRESS_KEY, 0L)
                settings.putInt(MIGRATION_TARGET_VERSION_KEY, targetVersion)
            }

            var currentStep = MigrationStep.fromInt(settings.getInt(MIGRATION_STEP_KEY, MigrationStep.CLEAR.value))
            var currentProgress = settings.getLong(MIGRATION_PROGRESS_KEY, 0L)

            if (currentStep == MigrationStep.CLEAR) {
                // Clear existing index first to ensure a clean migration
                _migrationStatus.value = "Clearing old index (Segments)..."
                spatialIndexRepository.dao.clearAllSegments()
                yield()
                
                _migrationStatus.value = "Clearing old index (Mappings)..."
                spatialIndexRepository.dao.clearAllTileMappings()
                yield()

                currentStep = MigrationStep.ROUTES
                currentProgress = 0L
                settings.putInt(MIGRATION_STEP_KEY, currentStep.value)
                settings.putLong(MIGRATION_PROGRESS_KEY, currentProgress)
            }

            if (currentStep == MigrationStep.ROUTES) {
                // 1. Migrate Routes
                val routesToMigrate = routeRepository.routes.toList()
                val totalRoutes = routesToMigrate.size
                Napier.i("Found $totalRoutes routes to index", tag = "MigrationService")
                val dummySnackbarHostState = SnackbarHostState()
                
                for (index in currentProgress.toInt() until totalRoutes) {
                    val routeEntry = routesToMigrate[index]
                    val status = "Indexing Routes: ${index + 1} / $totalRoutes"
                    _migrationStatus.value = status
                    Napier.i(status, tag = "MigrationService")
                    
                    // Use getRouteEntrySummary to ensure summary is generated and persisted if missing
                    // getRouteEntrySummary will call updateRouteSummary which handles the spatial indexing
                    if (routeEntry.summary == null || routeEntry.summaryVersion != ROUTE_SUMMARY_VERSION) {
                        routeRepository.getRouteEntrySummary(routeEntry, dummySnackbarHostState)
                    } else {
                        val iRoute = routeRepository.getRouteI(routeEntry.id)
                        val points = if (iRoute != null) {
                            routeRepository.getFullPoints(iRoute, dummySnackbarHostState)
                        } else {
                            routeEntry.summary ?: emptyList()
                        }
                        spatialIndexRepository.indexRoute(routeEntry.id, points)
                    }
                    
                    // Checkpoint every 10 routes
                    if ((index + 1) % 10 == 0 || (index + 1) == totalRoutes) {
                        settings.putLong(MIGRATION_PROGRESS_KEY, (index + 1).toLong())
                    }
                    yield() // Let other coroutines/queries run
                }

                currentStep = MigrationStep.STRAVA
                currentProgress = 0L
                settings.putInt(MIGRATION_STEP_KEY, currentStep.value)
                settings.putLong(MIGRATION_PROGRESS_KEY, currentProgress)
            }

            if (currentStep == MigrationStep.STRAVA) {
                // 2. Migrate Strava Activities
                val totalStrava = stravaDao.size()
                val pageSize = 50L
                val totalPages = (totalStrava + pageSize - 1) / pageSize
                
                for (page in currentProgress until totalPages) {
                    val activities = stravaDao.getAllActivitiesPaged(page, pageSize)
                    activities.forEachIndexed { index, activity ->
                        val globalIndex = page * pageSize + index + 1
                        val status = "Indexing Strava: $globalIndex / $totalStrava"
                        _migrationStatus.value = status
                        Napier.i(status, tag = "MigrationService")
                        
                        val stream = stravaDao.getStreamForActivity(activity.id)
                        val points = stream?.points ?: activity.summaryToRoute().route
                        val spatialPoints = if (points.isNotEmpty()) points else activity.summaryToRoute().route
                        if (spatialPoints.isNotEmpty()) {
                            spatialIndexRepository.indexStravaActivity(activity.id, spatialPoints)
                        }
                    }
                    // Checkpoint every page
                    settings.putLong(MIGRATION_PROGRESS_KEY, page + 1)
                    yield() // Let other coroutines/queries run
                }

                currentStep = MigrationStep.COMPLETE
                settings.putInt(MIGRATION_STEP_KEY, currentStep.value)
            }

            settings.putInt(SPATIAL_INDEX_VERSION_KEY, targetVersion)
            // Cleanup checkpoints
            settings.remove(MIGRATION_STEP_KEY)
            settings.remove(MIGRATION_PROGRESS_KEY)
            settings.remove(MIGRATION_TARGET_VERSION_KEY)

            _migrationStatus.value = "Spatial Index Migration complete."
            Napier.i("Spatial Index Migration complete.", tag = "MigrationService")
        } catch (e: Exception) {
            _migrationStatus.value = "Spatial Index Migration failed: ${e.message}"
            Napier.e("Spatial Index Migration failed", e, tag = "MigrationService")
        } finally {
            _isMigrating.value = false
        }
    }
}
