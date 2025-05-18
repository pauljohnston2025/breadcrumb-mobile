package com.paul.viewmodels

import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paul.composables.byteArrayToImageBitmap
import com.paul.domain.TileServerInfo
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.repositories.ITileRepository
import com.paul.infrastructure.repositories.TileServerRepo
import com.paul.infrastructure.service.GeoPosition
import com.paul.infrastructure.service.SendMessageHelper.Companion.sendingMessage
import com.paul.infrastructure.service.TileId
import com.paul.protocol.todevice.CacheCurrentArea
import com.paul.protocol.todevice.Point
import com.paul.protocol.todevice.RequestLocationLoad
import com.paul.protocol.todevice.Route
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MapViewModel(
    private val connection: IConnection,
    private val deviceSelector: DeviceSelector,
    private val tileRepository: ITileRepository,
    val tileServerRepository: TileServerRepo,
    private val snackbarHostState: SnackbarHostState
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
    private val _zSeedingProgress = MutableStateFlow(0) // 0 to tile layer max
    val zSeedingProgress: StateFlow<Int> = _zSeedingProgress

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

    val sendingFile: MutableState<String> = mutableStateOf("")

    private val _watchSendStarted = MutableStateFlow<RequestLocationLoad?>(null)
    val watchSendStarted: StateFlow<RequestLocationLoad?> = _watchSendStarted.asStateFlow()

    fun refresh() {
        val serverId = tileServerRepository.currentServerFlow().value.id
        currentVisibleTiles = currentVisibleTiles.map { it.copy(serverId = serverId) }.toSet()
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
                        // Napier.d("VM Fetching $tileId")
                        val data = tileRepository.getTile(
                            tileId.x,
                            tileId.y,
                            tileId.z
                        ) // Use injected repo
                        if (data.first != 200 || data.second == null) {
                            return@launch
                        }
                        if (!isActive) return@launch // Check cancellation *before* conversion
                        bitmapResult =
                            byteArrayToImageBitmap(data.second!!) // Non-composable conversion
                        if (!isActive) return@launch // Check cancellation *after* conversion

                        // Update cache and state flow (ensure thread safety if needed, StateFlow is safe)
                        launch(Dispatchers.Main) {
                            _tileBitmapCache[tileId] = bitmapResult
                            _tileCacheState.value = _tileBitmapCache.toMap() // Emit new map state
                        }.join()

                    } catch (e: CancellationException) {
                        // Napier.d("VM Job cancelled for $tileId")
                        // Don't update cache
                    } catch (e: Exception) {
                        // Napier.d("VM Error $tileId: ${e.message}")
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

    suspend fun getLocationArea(
        centerGeo: GeoPosition,
        topLeftGeo: GeoPosition,
        bottomRightGeo: GeoPosition,
    ): RequestLocationLoad? {
        val tl = Point(
            topLeftGeo.latitude.toFloat(),
            topLeftGeo.longitude.toFloat(),
            0f
        ).convert2XY()
        val br = Point(
            bottomRightGeo.latitude.toFloat(),
            bottomRightGeo.longitude.toFloat(),
            0f
        ).convert2XY()
        if (tl == null || br == null) {
            snackbarHostState.showSnackbar("Invalid position")
            return null
        }

        // should be positive, but in the wonderful world of negative on the bottom of the world who knows
        val xDim = abs(br.x - tl.x)
        val yDim = abs(tl.y - br.y)

        val maxDim = max(xDim, yDim)

        return RequestLocationLoad(
            centerGeo.latitude.toFloat(),
            centerGeo.longitude.toFloat(),
            maxDim
        )
    }

    fun startSeedingAreaToWatch(
        centerGeo: GeoPosition,
        topLeftGeo: GeoPosition,
        bottomRightGeo: GeoPosition,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _watchSendStarted.value = getLocationArea(
                    centerGeo,
                    topLeftGeo,
                    bottomRightGeo,
                )
            } catch (t: Throwable) {
                snackbarHostState.showSnackbar("Failed to start seed on watch")
                Napier.e("Failed to start seed on watch", t) // Log the full exception
            }
        }
    }

    fun cancelWatchLocationLoad() {
        _watchSendStarted.value = null
    }

    fun confirmWatchLocationLoad() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val device = deviceSelector.currentDevice()
                if (device == null) {
                    snackbarHostState.showSnackbar("no devices selected")
                    _watchSendStarted.value = null
                    return@launch
                }

                sendingMessage("Caching current map area on Device.\nEnsure an activity with the datafield is running. Progress will be shown on the datafield") {
                    _watchSendStarted.value = null
                    connection.send(device, _watchSendStarted.value!!)
                    connection.send(device, CacheCurrentArea())
                    // todo wait for response to say finished? its a very long process though
                    delay(10000) // wait for a bit so users can read message
                }
            } catch (t: Throwable) {
                snackbarHostState.showSnackbar("Failed to start seed on watch")
                Napier.e("Failed to start seed on watch", t) // Log the full exception
            } finally {
                _watchSendStarted.value = null
            }
        }
    }

    fun showLocationOnWatch(
        centerGeo: GeoPosition,
        topLeftGeo: GeoPosition,
        bottomRightGeo: GeoPosition,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val location = getLocationArea(
                    centerGeo,
                    topLeftGeo,
                    bottomRightGeo,
                )
                if (location == null) {
                    return@launch
                }

                val device = deviceSelector.currentDevice()
                if (device == null) {
                    snackbarHostState.showSnackbar("no devices selected")
                    _watchSendStarted.value = null
                    return@launch
                }

                sendingMessage("Showing current location on watch, ensure the datafield is open and running") {
                    connection.send(device, location)
                }
            } catch (t: Throwable) {
                snackbarHostState.showSnackbar("Failed to show location on watch")
                Napier.e("Failed to show location on watch", t) // Log the full exception
            } finally {
                _watchSendStarted.value = null
            }
        }
    }

    private suspend fun sendingMessage(msg: String, cb: suspend () -> Unit) {
        sendingMessage(viewModelScope, sendingFile, msg, cb)
    }

    // Called by the user to start seeding an area
    fun startSeedingArea(
        minLat: Float,
        maxLat: Float,
        minLon: Float,
        maxLon: Float,
        minZoom: Int,
        maxZoom: Int
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
                // --- Pre-calculate bounds for total count ---
                for (z in effectiveMinZoom..effectiveMaxZoom) {
                    // Get tile indices for the corners
                    val (xTileMinLon, yTileMaxLat) = latLonToTileXY(maxLat, minLon, z) // Top-Left
                    val (xTileMaxLon, yTileMinLat) = latLonToTileXY(
                        minLat,
                        maxLon,
                        z
                    ) // Bottom-Right

                    // Determine the actual min/max tile indices for X and Y
                    val currentXMin = min(xTileMinLon, xTileMaxLon)
                    val currentXMax = max(xTileMinLon, xTileMaxLon)
                    val currentYMin = min(yTileMaxLat, yTileMinLat) // Smaller Y index (North)
                    val currentYMax = max(yTileMaxLat, yTileMinLat) // Larger Y index (South)

                    // --- CORRECTED Total Calculation ---
                    val widthInTiles = currentXMax - currentXMin + 1L
                    val heightInTiles = currentYMax - currentYMin + 1L
                    if (widthInTiles > 0 && heightInTiles > 0) { // Avoid adding if calculation is weird
                        totalTilesExpected += widthInTiles * heightInTiles
                    }
                }

                Napier.d("Total tiles expected: $totalTilesExpected") // Debug log

                if (totalTilesExpected <= 0) {
                    // It's possible the area is too small or spans across anti-meridian incorrectly handled
                    Napier.d("Warning: No tiles expected for the given area/zoom. Lat: $minLat/$maxLat, Lon: $minLon/$maxLon, Zoom: $effectiveMinZoom..$effectiveMaxZoom")
                    // Don't throw an exception, just finish gracefully
                    _seedingProgress.value = 1f
                    _isSeeding.value = false
                    return@launch // Exit the launch block
                    // throw IllegalStateException("No tiles to download in the selected area/zoom ($minLat/$maxLat, $minLon/$maxLon, $effectiveMinZoom..$effectiveMaxZoom).")
                }

                var tilesProcessed = 0L
                val progressUpdateThreshold =
                    (totalTilesExpected / 100).coerceAtLeast(1L) // Update progress roughly every 1% or every tile

                for (z in effectiveMinZoom..effectiveMaxZoom) {
                    // Convert lat/lon bounds to tile indices for this zoom level (same as above)
                    val (xTileMinLon, yTileMaxLat) = latLonToTileXY(maxLat, minLon, z) // Top-Left
                    val (xTileMaxLon, yTileMinLat) = latLonToTileXY(
                        minLat,
                        maxLon,
                        z
                    ) // Bottom-Right

                    // Determine the actual min/max tile indices for the loop
                    val finalXMin = min(xTileMinLon, xTileMaxLon)
                    val finalXMax = max(xTileMinLon, xTileMaxLon)
                    val finalYMin = min(yTileMaxLat, yTileMinLat) // Smaller Y index (North)
                    val finalYMax = max(yTileMaxLat, yTileMinLat) // Larger Y index (South)

                    Napier.d("Seeding Layer z=$z: X=$finalXMin..$finalXMax, Y=$finalYMin..$finalYMax") // Debug log
                    _zSeedingProgress.value = z

                    // --- Pass the correct bounds to seedLayer ---
                    tileRepository.seedLayer(
                        xMin = finalXMin, // Use correct min/max
                        xMax = finalXMax,
                        yMin = finalYMin, // Use correct min/max (smaller Y index is North)
                        yMax = finalYMax, // Use correct min/max (larger Y index is South)
                        z = z,
                        progressCallback = { layerProgress -> // This callback comes from *within* seedLayer now
                            // Need a way to map layerProgress to overall progress
                            // This simplistic approach assumes seedLayer finishes sequentially
                            // You might need a more sophisticated progress update from seedLayer itself
                            // For now, let's update based on tiles processed count from within seedLayer if possible
                            // If not, we update crudely after seedLayer finishes.
                            _seedingProgress.value =
                                layerProgress // This only shows progress within one layer
                        },
                        errorCallback = { x, y, z, e ->
                            // Error handling remains the same
                            Napier.e("Failed to seed: $x, $y, $z, $e")
                            // Launch on Main for Snackbar
                            launch(Dispatchers.Main) {
                                snackbarHostState.showSnackbar("failed to seed: $x, $y, $z")
                            }
                            // Optionally increment processed count even on error for progress
                            val currentProcessed = tilesProcessed++ // Use post-increment
                            if (currentProcessed % progressUpdateThreshold == 0L || currentProcessed == totalTilesExpected) {
                                _seedingProgress.value =
                                    (currentProcessed.toFloat() / totalTilesExpected.toFloat()).coerceIn(
                                        0f,
                                        1f
                                    )
                            }
                        },
                    )
                    // --- Crude progress update if seedLayer doesn't provide success callback ---
                    // val tilesInLayer = (finalXMax - finalXMin + 1L) * (finalYMax - finalYMin + 1L)
                    // tilesProcessed += tilesInLayer
                    // _seedingProgress.value = (tilesProcessed.toFloat() / totalTilesExpected.toFloat()).coerceIn(0f, 1f)
                }
                _seedingProgress.value = 1f // Mark as complete
            } catch (e: Exception) {
                _seedingError.value = "Seeding failed: ${e.message}"
                _seedingProgress.value = 0f // Reset progress on error
                Napier.e("Seeding error", e) // Log the full exception
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