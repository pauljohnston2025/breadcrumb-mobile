package com.paul.viewmodels

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paul.infrastructure.repositories.RouteRepository
import com.paul.infrastructure.repositories.SpatialIndexRepository
import com.paul.infrastructure.dao.StravaDao
import com.paul.infrastructure.service.IFileHelper
import com.paul.infrastructure.service.MigrationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StorageViewModel(
    private val fileHelper: IFileHelper,
    private var routesRepository: RouteRepository,
    private val spatialIndexRepository: SpatialIndexRepository,
    private val stravaDao: StravaDao,
    val migrationService: MigrationService,
) : ViewModel() {
    val tileServers: MutableState<Map<String, Long>> = mutableStateOf(mapOf())
    val routesTotalSize: MutableState<Long> = mutableStateOf(-1)

    val segmentCount = MutableStateFlow(0L)
    val tileMappingCount = MutableStateFlow(0L)
    val stravaActivityCount = MutableStateFlow(0L)
    val stravaStreamCount = MutableStateFlow(0L)
    val stravaGearCount = MutableStateFlow(0L)
    val routeCount = MutableStateFlow(0)
    val overlayCount = MutableStateFlow(0)
    val overlaysTotalSize = MutableStateFlow(0L)
    val databaseSize = MutableStateFlow(0L)

    private val _deletingTileServer = MutableStateFlow<String?>(null)
    val deletingTileServer: StateFlow<String?> = _deletingTileServer.asStateFlow()

    private val _deletingOverlays = MutableStateFlow<Boolean>(false)
    val deletingOverlays: StateFlow<Boolean> = _deletingOverlays.asStateFlow()

    private val _deletingRoutes = MutableStateFlow<Boolean>(false)
    val deletingRoutes: StateFlow<Boolean> = _deletingRoutes.asStateFlow()

    private val _loadingTileServer = MutableStateFlow<Boolean>(false)
    val loadingTileServer: StateFlow<Boolean> = _loadingTileServer.asStateFlow()

    private val _loadingRoutes = MutableStateFlow<Boolean>(false)
    val loadingRoutes: StateFlow<Boolean> = _loadingRoutes.asStateFlow()

    private val _loadingDatabase = MutableStateFlow<Boolean>(false)
    val loadingDatabase: StateFlow<Boolean> = _loadingDatabase.asStateFlow()

    private val _loadingOverlays = MutableStateFlow<Boolean>(false)
    val loadingOverlays: StateFlow<Boolean> = _loadingOverlays.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init {
        refresh()
    }

    private suspend fun updateTileServers() {
        _loadingTileServer.value = true
        val result = fileHelper.localContentsSize("tiles")
        delay(500) // delay for a bit, otherwise it looks like the ui is broken (immediately updates - spinners never start but refresh does)
        tileServers.value = result
        _loadingTileServer.value = false
    }

    private suspend fun updateRouteTotalSize() {
        _loadingRoutes.value = true
        routeCount.value = routesRepository.routes.size
        val result = fileHelper.localDirectorySize("routes")
        delay(500) // delay for a bit, otherwise it looks like the ui is broken (immediately updates - spinners never start but refresh does)
        routesTotalSize.value = result
        _loadingRoutes.value = false
    }

    private suspend fun updateDatabaseInfo() {
        _loadingDatabase.value = true
        segmentCount.value = spatialIndexRepository.getSegmentCount()
        tileMappingCount.value = spatialIndexRepository.getTileMappingCount()
        stravaActivityCount.value = stravaDao.size()
        stravaStreamCount.value = stravaDao.streamCount()
        stravaGearCount.value = stravaDao.gearCount()

        val path = fileHelper.getDatabasePath(com.paul.infrastructure.DATABASE_NAME)
        databaseSize.value = fileHelper.localFileSize(path)
        _loadingDatabase.value = false
    }

    private suspend fun updateOverlays() {
        _loadingOverlays.value = true
        overlayCount.value = fileHelper.localFileCount("overlays")
        overlaysTotalSize.value = fileHelper.localDirectorySize("overlays")
        _loadingOverlays.value = false
    }

    fun requestTileDelete(tileServer: String) {
        _deletingTileServer.value = tileServer
    }

    fun cancelTileDelete() {
        _deletingTileServer.value = null
    }

    fun confirmTileDelete() {
        viewModelScope.launch(Dispatchers.IO) {
            _deletingTileServer.value?.let { tileServerToDelete ->
                fileHelper.deleteDir("tiles/$tileServerToDelete")
            }
            updateTileServers()
            _deletingTileServer.value = null // Close dialog
        }
    }

    fun requestRoutesDelete() {
        _deletingRoutes.value = true
    }

    fun cancelRoutesDelete() {
        _deletingRoutes.value = false
    }

    fun requestOverlaysDelete() {
        _deletingOverlays.value = true
    }

    fun cancelOverlaysDelete() {
        _deletingOverlays.value = false
    }

    fun confirmOverlaysDelete() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_deletingOverlays.value) {
                fileHelper.deleteDir("overlays")
            }
            updateOverlays()
            _deletingOverlays.value = false
        }
    }

    fun confirmRoutesDelete() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_deletingRoutes.value) {
                routesRepository.deleteAll()
            }
            updateRouteTotalSize()
            _deletingRoutes.value = false // Close dialog
        }
    }

    fun refresh() {
        _isRefreshing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            updateTileServers()
            updateRouteTotalSize()
            updateDatabaseInfo()
            updateOverlays()

            _isRefreshing.value = false
        }
    }
}
