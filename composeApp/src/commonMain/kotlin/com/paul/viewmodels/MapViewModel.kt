package com.paul.viewmodels

import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.mutableStateOf // Import mutableStateOf for simple boolean
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.paul.composables.byteArrayToImageBitmap
import com.paul.domain.TileServerInfo
import com.paul.infrastructure.repositories.ITileRepository
import com.paul.infrastructure.repositories.TileServerRepo
import com.paul.infrastructure.service.GeoPosition
import com.paul.infrastructure.service.TileId
import com.paul.protocol.todevice.Point
import com.paul.protocol.todevice.Route
import com.paul.ui.Screen
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update // Import update extension
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min

class MapViewModel(
    private val tileRepository: ITileRepository,
    val tileServerRepository: TileServerRepo,
    private val snackbarHostState: SnackbarHostState,
    private val navController: NavController,
) : ViewModel() {

    // --- Map State ---
    private val _mapCenter = MutableStateFlow(Point(-27.472077f, 153.022172f, 0f)) // Brisbane
    val mapCenter: StateFlow<Point> = _mapCenter

    private val _mapZoom = MutableStateFlow(10)
    val mapZoom: StateFlow<Int> = _mapZoom

    val currentTileServer: StateFlow<TileServerInfo> = tileServerRepository.currentServerFlow()

    // --- Route State ---
    private val _currentRoute = MutableStateFlow<Route?>(null)
    val currentRoute: StateFlow<Route?> = _currentRoute

    // --- Elevation Profile State ---
    private val _isElevationProfileVisible = MutableStateFlow(false) // Initially hidden
    val isElevationProfileVisible: StateFlow<Boolean> = _isElevationProfileVisible

    // --- Seeding / Offline State ---
    private val _isSeeding = MutableStateFlow(false)
    val isSeeding: StateFlow<Boolean> = _isSeeding

    private val _seedingProgress = MutableStateFlow(0f) // 0.0 to 1.0
    val seedingProgress: StateFlow<Float> = _seedingProgress

    private val _seedingError = MutableStateFlow<String?>(null)
    val seedingError: StateFlow<String?> = _seedingError
    private val _tileBitmapCache = mutableMapOf<TileId, ImageBitmap?>() // Internal cache
    private val _tileCacheState = MutableStateFlow<Map<TileId, ImageBitmap?>>(emptyMap())
    val tileCacheState: StateFlow<Map<TileId, ImageBitmap?>> = _tileCacheState // Expose state

    private val _loadingTiles = mutableSetOf<TileId>() // Track loading IDs
// private val _loadingTilesState = MutableStateFlow<Set<TileId>>(emptySet()) // Optionally expose loading state too
// val loadingTilesState: StateFlow<Set<TileId>> = _loadingTilesState

    private val loadingJobs = mutableMapOf<TileId, Job>() // Still needed for cancellation
    val isActive = true
    private var currentVisibleTiles: Set<TileId> = setOf()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            tileServerRepository.currentServerFlow().onEach { refresh() }.collect()
            tileServerRepository.authTokenFlow().onEach { refresh() }.collect()
        }
    }

    fun refresh()
    {
        val serverId = tileServerRepository.currentServerFlow().value.id
        currentVisibleTiles = currentVisibleTiles.map { it.copy(serverId=serverId) }.toSet()
        requestTilesForViewport(currentVisibleTiles)
    }

    // Function called by Composable
    fun requestTilesForViewport(visibleTileIds: Set<TileId>) {
        currentVisibleTiles = visibleTileIds
        // 1. Cancel jobs for tiles no longer needed
        val jobsToCancel = loadingJobs.filterKeys { it !in visibleTileIds }
        jobsToCancel.forEach { (_, job) -> job.cancel() }
        jobsToCancel.keys.forEach { loadingJobs.remove(it) }
        _loadingTiles.removeAll(jobsToCancel.keys)

        // 2. Request missing tiles
        visibleTileIds.forEach { tileId ->
            if (!_tileBitmapCache.containsKey(tileId) && !loadingJobs.containsKey(tileId)) {
                // Launch within viewModelScope - won't be cancelled by recomposition
                val job = viewModelScope.launch(Dispatchers.IO) {
                    var bitmapResult: ImageBitmap? = null
                    _loadingTiles.add(tileId) // Mark as loading (update state flow if exposing)
                    try {
                        // println("VM Fetching $tileId")
                        val data = tileRepository.getTile(
                            tileId.x,
                            tileId.y,
                            tileId.z
                        ) // Use injected repo
                        if (!isActive) return@launch // Check cancellation *before* conversion
                        bitmapResult = byteArrayToImageBitmap(data) // Non-composable conversion
                        if (!isActive) return@launch // Check cancellation *after* conversion

                        // Update cache and state flow (ensure thread safety if needed, StateFlow is safe)
                        launch(Dispatchers.Main) {
                            _tileBitmapCache[tileId] = bitmapResult
                            _tileCacheState.value = _tileBitmapCache.toMap() // Emit new map state
                        }.join()

                    } catch (e: CancellationException) {
                        // println("VM Job cancelled for $tileId")
                        // Don't update cache
                    } catch (e: Exception) {
                        // println("VM Error $tileId: ${e.message}")
                        launch(Dispatchers.Main) {
                            _tileBitmapCache[tileId] = null // Cache error state
                            _tileCacheState.value = _tileBitmapCache.toMap()
                        }.join()
                    } finally {
                        // Always remove from loading state and jobs map
                        launch(Dispatchers.Main) {
                            _loadingTiles.remove(tileId)
                            loadingJobs.remove(tileId)
                        }.join()
                        // _loadingTilesState.value = _loadingTiles.toSet() // Emit loading state change
                    }
                }
                viewModelScope.launch(Dispatchers.Main) {
                    loadingJobs[tileId] = job
                }
            }
        }
    }

    fun displayRoute(route: Route) {
        viewModelScope.launch(Dispatchers.Main) {
            // Navigate if necessary
            val current = navController.currentDestination
            if (current?.route != Screen.Map.route) {
                navController.navigate(Screen.Map.route)
            }

            _currentRoute.value = route // Set the route
            _isElevationProfileVisible.value = false

            // --- Calculate geographic center of the route ---
            val boundingBoxCenter = calculateRouteCenter(route)

            // Set the map center initially to the route's geographic center
            // The zoom will be adjusted by the Composable once its size is known
            boundingBoxCenter?.let { centerGeo ->
                _mapCenter.value = Point(
                    centerGeo.latitude.toFloat(),
                    centerGeo.longitude.toFloat(),
                    0f // Assuming altitude isn't needed for centering
                )
                // Keep existing zoom or reset to a default? Let's keep it for now.
                // Zoom adjustment will happen in LaunchedEffect in the Composable.
            } ?: run {
                // Fallback: Center on the first point if center calculation fails (e.g., empty route)
                route.route.firstOrNull()?.let { centerMapOn(it) }
            }
        }
    }

    private fun calculateRouteCenter(route: Route): GeoPosition? {
        if (route.route.isEmpty()) return null
        if (route.route.size == 1) return GeoPosition(
            route.route.first().latitude.toDouble(),
            route.route.first().longitude.toDouble()
        )

        var minLat = route.route.first().latitude.toDouble()
        var maxLat = route.route.first().latitude.toDouble()
        var minLon = route.route.first().longitude.toDouble()
        var maxLon = route.route.first().longitude.toDouble()

        route.route.drop(1).forEach { point ->
            minLat = min(minLat, point.latitude.toDouble())
            maxLat = max(maxLat, point.latitude.toDouble())
            minLon = min(minLon, point.longitude.toDouble())
            maxLon = max(maxLon, point.longitude.toDouble())
        }

        // Simple midpoint calculation (doesn't handle longitude wrapping well, but ok for most routes)
        val centerLat = minLat + (maxLat - minLat) / 2.0
        val centerLon = minLon + (maxLon - minLon) / 2.0

        return GeoPosition(centerLat, centerLon)
    }

    fun clearRoute() {
        _currentRoute.value = null
        _isElevationProfileVisible.value = false // Hide profile when route is cleared
    }

    // --- New function to toggle profile visibility ---
    fun toggleElevationProfileVisibility() {
        // Only toggle if a route actually exists
        if (_currentRoute.value != null) {
            _isElevationProfileVisible.update { !it }
        }
    }

    // Called by the user to start seeding an area
    fun startSeedingArea(
        minLat: Float, maxLat: Float, minLon: Float, maxLon: Float,
        minZoom: Int, maxZoom: Int
    ) {
        if (_isSeeding.value) return // Already seeding

        _isSeeding.value = true
        _seedingProgress.value = 0f
        _seedingError.value = null
        val server = tileServerRepository.currentServerFlow().value

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val effectiveMinZoom = max(minZoom, server.tileLayerMin)
                val effectiveMaxZoom = min(maxZoom, server.tileLayerMax)

                var totalTilesExpected = 0L
                for (z in effectiveMinZoom..effectiveMaxZoom) {
                    val (xMin, yMin) = latLonToTileXY(minLat, minLon, z)
                    val (xMax, yMax) = latLonToTileXY(maxLat, maxLon, z)
                    totalTilesExpected += (xMax - xMin + 1L) * (yMax - yMin + 1L)
                }
                if (totalTilesExpected <= 0) {
                    throw IllegalStateException("No tiles to download in the selected area/zoom.")
                }

                var tilesProcessed = 0L

                for (z in effectiveMinZoom..effectiveMaxZoom) {
                    // Convert lat/lon bounds to tile indices for this zoom level
                    val (xMin, yMin) = latLonToTileXY(minLat, minLon, z)
                    val (xMax, yMax) = latLonToTileXY(maxLat, maxLon, z)

                    // Ensure min <= max after conversion
                    val finalXMin = min(xMin, xMax)
                    val finalXMax = max(xMin, xMax)
                    val finalYMin = min(yMin, yMax)
                    val finalYMax = max(yMin, yMax)

                    // Tell the repository to seed this layer
                    tileRepository.seedLayer(
                        finalXMin,
                        finalXMax,
                        finalYMin,
                        finalYMax,
                        z, { layerProgress ->
                            // This callback might be tricky - seedLayer might not easily provide overall progress
                            // Calculate overall progress based on layer progress and number of tiles in layer
                            // This needs refinement based on how seedLayer reports progress.
                            // Simplified placeholder: Assume seedLayer finishes before next loop iteration
                            _seedingProgress.value = layerProgress
                        }
                    ) { x, y, z, e ->
                        Napier.e("Failed to seed: $x, $y, $z, $e")
                        snackbarHostState.showSnackbar("failed to seed: $x, $y, $z")
                    }
                }
                _seedingProgress.value = 1f // Mark as complete
            } catch (e: Exception) {
                _seedingError.value = "Seeding failed: ${e.message}"
                _seedingProgress.value = 0f // Reset progress on error
                // Log e
            } finally {
                _isSeeding.value = false
            }
        }
    }

    // --- Helper Functions (Needs Implementation) ---

    fun centerMapOn(point: Point) {
        // Logic to update _mapCenter (and potentially _mapZoom)
        _mapCenter.value = point
    }

    fun setMapZoom(z: Int) {
        _mapZoom.value = z
    }

    // Essential: Convert Lat/Lon to Tile X/Y for a given zoom
    // Standard Web Mercator formulas needed here
    private fun latLonToTileXY(lat: Float, lon: Float, zoom: Int): Pair<Int, Int> {
        // Implementation based on OSM/Mercator tile calculations
        // See: https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames#Kotlin
        // ... (add the actual calculation logic) ...
        val n = 1 shl zoom // 2^zoom
        val latRad = Math.toRadians(lat.toDouble())
        val xTile = ((lon + 180.0) / 360.0 * n).toInt()
        val yTile =
            ((1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n).toInt()
        return Pair(xTile, yTile)
    }
}