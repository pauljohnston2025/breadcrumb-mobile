package com.paul.viewmodels

import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.mutableStateOf // Import mutableStateOf for simple boolean
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.paul.domain.TileServerInfo
import com.paul.infrastructure.repositories.ITileRepository
import com.paul.infrastructure.repositories.TileServerRepo
import com.paul.protocol.todevice.Point
import com.paul.protocol.todevice.Route
import com.paul.ui.Screen
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update // Import update extension
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class MapViewModel(
    private val tileRepository: ITileRepository,
    private val tileServerRepository: TileServerRepo,
    private val snackbarHostState: SnackbarHostState,
    private val navController: NavController,
) : ViewModel() {

    // --- Map State ---
    private val _mapCenter = MutableStateFlow(Point(-27.472077f, 153.022172f, 0f)) // Brisbane
    val mapCenter: StateFlow<Point> = _mapCenter

    private val _mapZoom = MutableStateFlow(10f) // Zoom level (abstract)
    val mapZoom: StateFlow<Float> = _mapZoom

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

    fun displayRoute(route: Route) {
        viewModelScope.launch(Dispatchers.Main) {
            val current = navController.currentDestination
            if (current?.route != Screen.Map.route) {
                navController.navigate(Screen.Map.route)
            }

            _currentRoute.value = route
            _isElevationProfileVisible.value = false // Ensure profile is hidden when new route loads
            route.route.firstOrNull()?.let { centerMapOn(it) }
        }
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

    fun setTileServer(serverInfo: TileServerInfo) {
        // ... (no changes needed here for profile)
        viewModelScope.launch(Dispatchers.IO) {
            tileServerRepository.updateCurrentTileServer(serverInfo)
        }
    }

    suspend fun provideTileData(x: Int, y: Int, z: Int): ByteArray? {
        // ... (no changes needed here for profile)
        return tileRepository.getTile(x, y, z)
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

    private fun centerMapOn(point: Point) {
        // Logic to update _mapCenter (and potentially _mapZoom)
        _mapCenter.value = point
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