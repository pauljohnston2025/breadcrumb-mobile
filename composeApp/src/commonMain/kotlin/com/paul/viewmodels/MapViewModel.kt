package com.paul.viewmodels

import androidx.compose.material.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benasher44.uuid.uuid4
import com.paul.composables.byteArrayToImageBitmap
import com.paul.domain.ColourPalette
import com.paul.domain.GpxRoute
import com.paul.domain.IRoute
import com.paul.domain.StaveIRoute
import com.paul.domain.StravaActivity
import com.paul.domain.TileServerInfo
import com.paul.infrastructure.connectiq.IConnection
import com.paul.infrastructure.repositories.HistoryRepository
import com.paul.infrastructure.repositories.ITileRepository
import com.paul.infrastructure.repositories.RouteRepository
import com.paul.infrastructure.repositories.StravaRepository
import com.paul.infrastructure.repositories.TileServerRepo
import com.paul.infrastructure.service.ColourPaletteConverter
import com.paul.infrastructure.service.GeoPosition
import com.paul.infrastructure.service.ILocationService
import com.paul.infrastructure.service.SendMessageHelper
import com.paul.infrastructure.service.SendMessageHelper.Companion.sendingMessage
import com.paul.infrastructure.service.SendRoute
import com.paul.infrastructure.service.TileId
import com.paul.infrastructure.service.UserLocation
import com.paul.protocol.todevice.CacheCurrentArea
import com.paul.protocol.todevice.Point
import com.paul.protocol.todevice.RectPoint
import com.paul.protocol.todevice.RequestLocationLoad
import com.paul.protocol.todevice.ReturnToUser
import com.paul.protocol.todevice.Route
import com.paul.ui.Screen
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.IntOffset
import com.paul.domain.RouteEntry
import com.paul.infrastructure.service.geoToScreenPixel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlin.collections.toMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.ranges.coerceIn

sealed class MapViewNavigationEvent {
    // Represents a command to navigate to a specific route
    data class NavigateTo(val route: String) : MapViewNavigationEvent()
}

class MapViewModel(
    public val connection: IConnection,
    private val deviceSelector: DeviceSelector,
    private val tileRepository: ITileRepository,
    val tileServerRepository: TileServerRepo,
    private val snackbarHostState: SnackbarHostState,
    private val locationService: ILocationService,
    val stravaRepo: StravaRepository,
    val routeRepository: RouteRepository,
) : ViewModel() {

    val historyRepo = HistoryRepository()

    private var seedingJob: Job? = null

    private val _newlyCreatedPalette = MutableStateFlow<ColourPalette?>(null)
    val newlyCreatedPalette: StateFlow<ColourPalette?> = _newlyCreatedPalette.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<MapViewNavigationEvent>()
    val navigationEvents: SharedFlow<MapViewNavigationEvent> = _navigationEvents.asSharedFlow()

    private val _userLocation = MutableStateFlow<UserLocation?>(null)
    val userLocation: StateFlow<UserLocation?> = _userLocation.asStateFlow()

    private var isInitialLocationSet = false // Flag to center the map only once

    // --- Map State ---
    private val _mapCenter = MutableStateFlow(Point(-27.472077f, 153.022172f, 0f)) // Brisbane
    val mapCenter: StateFlow<Point> = _mapCenter

    private val _mapZoom = MutableStateFlow(10f)
    val mapZoom: StateFlow<Float> = _mapZoom

    val currentTileServer: StateFlow<TileServerInfo> = tileServerRepository.currentServerFlow()

    // --- Route State ---
    private val _currentRoute = MutableStateFlow<Route?>(null)
    val currentRoute: StateFlow<Route?> = _currentRoute

    private val _currentRouteI = MutableStateFlow<IRoute?>(null)
    val currentRouteI: StateFlow<IRoute?> = _currentRouteI

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
        // Start collecting location updates as soon as the ViewModel is created
        startLocationUpdates()

        viewModelScope.launch(Dispatchers.IO) {
            tileServerRepository.currentServerFlow().onEach { refresh() }.collect()
            tileServerRepository.authTokenFlow().onEach { refresh() }.collect()
        }
    }

    val sendingFile: MutableState<String> = mutableStateOf("")

    private val _watchSendStarted = MutableStateFlow<RequestLocationLoad?>(null)
    val watchSendStarted: StateFlow<RequestLocationLoad?> = _watchSendStarted.asStateFlow()

    // Toggle for the Strava Layer
    private val _isStravaEnabled = MutableStateFlow(false)
    val isStravaEnabled = _isStravaEnabled.asStateFlow()

    // Toggle for the Stored Routes Layer
    private val _isRoutesEnabled = MutableStateFlow(false)
    val isRoutesEnabled = _isRoutesEnabled.asStateFlow()

    // Results of the 20m tap-check
    private val _nearbyActivities = MutableStateFlow<List<StravaActivity>>(emptyList())
    val nearbyActivities = _nearbyActivities.asStateFlow()

    // Results of the 20m tap-check for stored routes
    private val _nearbyStoredRoutes = MutableStateFlow<List<RouteEntry>>(emptyList())
    val nearbyStoredRoutes = _nearbyStoredRoutes.asStateFlow()

    fun setDateRange(start: Instant, end: Instant) {
        stravaRepo.setDateRange(start, end)
    }

    val storedRoutes: StateFlow<Map<RouteEntry, Route>> = snapshotFlow { routeRepository.routes.toList() }
        .map { entries ->
            kotlinx.coroutines.coroutineScope {
                entries.map { entry ->
                    async {
                        routeRepository.getRouteEntrySummary(entry, snackbarHostState)?.let { entry to it }
                    }
                }.awaitAll().filterNotNull().toMap()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    // High-fidelity routes derived from the repo's filtered activities
    // When the repo's date range changes, 'activities' emits, and this transforms them
    val stravaRoutes: StateFlow<Map<StravaActivity, Route>> = stravaRepo.activitiesByDateRangeAndPage
        .map { activities ->
            val ids = activities.map { it.id }

//            val streamsMap = stravaRepo.getStreamsForActivityIds(ids)

            kotlinx.coroutines.coroutineScope {
                activities.map { activity ->
                    async(Dispatchers.Default) {
//                        val stream = streamsMap[activity.id]
                        // significantly less points when using the summary polyline
                        // and i can't tell the difference (resolution seems fine)
                        // note: the summary polyline does not have elevation data,
                        // but we do not need that for display and click functionality
                        val route = activity.summaryToRoute()

                        // Accessing projectedPoints here triggers the lazy
                        // calculation in the background.
                        val _trigger = route.projectedPoints

                        activity to route
                    }
                }.awaitAll().toMap()
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    fun toggleStrava(enabled: Boolean) {
        _isStravaEnabled.value = enabled
    }

    fun toggleStoredRoutes(enabled: Boolean) {
        _isRoutesEnabled.value = enabled
    }

    fun clearNearbyActivities() {
        _nearbyActivities.value = emptyList()
        _nearbyStoredRoutes.value = emptyList()
    }

    fun findNearbyActivities(tappedGeo: GeoPosition, viewportSize: IntSize) {
        if (!_isStravaEnabled.value && !_isRoutesEnabled.value) return
        if (viewportSize == IntSize.Zero) return

        val currentZoom = _mapZoom.value
        val currentCenter = GeoPosition(_mapCenter.value.latitude.toDouble(), _mapCenter.value.longitude.toDouble())

        // Convert the tapped location to a screen pixel point (x, y)
        val tappedScreenPx = geoToScreenPixel(tappedGeo, currentCenter, currentZoom, viewportSize)

        // Define your "Hit Box" in pixels.
        // This stays 24px whether you're looking at a street or a continent.
        val HIT_THRESHOLD_PIXELS = 24.0

        viewModelScope.launch(Dispatchers.Default) {
            // Helper to check proximity in screen space
            fun isNearby(route: Route): Boolean {
                val geoPoints = route.route // Assuming this is List<Point> or List<GeoPosition>
                if (geoPoints.size < 2) return false

                for (i in 0 until geoPoints.size - 1) {
                    // 1. Convert route segment points to screen pixels
                    val p1 = geoToScreenPixel(
                        GeoPosition(geoPoints[i].latitude.toDouble(), geoPoints[i].longitude.toDouble()),
                        currentCenter, currentZoom, viewportSize
                    )
                    val p2 = geoToScreenPixel(
                        GeoPosition(geoPoints[i+1].latitude.toDouble(), geoPoints[i+1].longitude.toDouble()),
                        currentCenter, currentZoom, viewportSize
                    )

                    // 2. Simple AABB (Bounding Box) check in pixels for speed
                    val minX = min(p1.x, p2.x) - HIT_THRESHOLD_PIXELS
                    val maxX = max(p1.x, p2.x) + HIT_THRESHOLD_PIXELS
                    val minY = min(p1.y, p2.y) - HIT_THRESHOLD_PIXELS
                    val maxY = max(p1.y, p2.y) + HIT_THRESHOLD_PIXELS

                    if (tappedScreenPx.x in minX.toInt()..maxX.toInt() &&
                        tappedScreenPx.y in minY.toInt()..maxY.toInt()) {

                        // 3. Precise segment distance check in pixels
                        if (distToSegmentPixels(tappedScreenPx, p1, p2) <= HIT_THRESHOLD_PIXELS) {
                            return true
                        }
                    }
                }
                return false
            }

            if (_isStravaEnabled.value) {
                _nearbyActivities.value = stravaRoutes.value.filter { isNearby(it.value) }.keys.toList()
            }
            if (_isRoutesEnabled.value) {
                _nearbyStoredRoutes.value = storedRoutes.value.filter { isNearby(it.value) }.keys.toList()
            }
        }
    }

    private fun distToSegmentPixels(p: IntOffset, a: IntOffset, b: IntOffset): Double {
        val dx = (b.x - a.x).toDouble()
        val dy = (b.y - a.y).toDouble()
        if (dx == 0.0 && dy == 0.0) return distance(p, a)

        val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)
        val clampedT = t.coerceIn(0.0, 1.0)
        val closestX = a.x + clampedT * dx
        val closestY = a.y + clampedT * dy

        return sqrt((p.x - closestX).pow(2) + (p.y - closestY).pow(2))
    }

    private fun distance(
        p: IntOffset,
        a: IntOffset
    ): Double {
        val dx = (p.x - a.x).toDouble()
        val dy = (p.y - a.y).toDouble()

        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Resets the newly created palette state, called after navigation has been handled.
     */
    fun onPaletteCreationHandled() {
        _newlyCreatedPalette.value = null
    }

    /**
     * Creates a 64-color palette from the visible tiles on the screen using multiplatform-safe APIs.
     * This function reconstructs the current view into a composite ImageBitmap and then analyzes
     * its pixels to extract the most representative colors.
     *
     * @param visibleTiles The list of tiles currently in the viewport.
     * @param tileCache A map containing the loaded ImageBitmaps for tiles.
     * @param viewportSize The current size of the map composable.
     */
    fun createPaletteFromViewport(
        visibleTiles: List<com.paul.infrastructure.service.TileInfo>,
        tileCache: Map<TileId, ImageBitmap?>,
        viewportSize: IntSize
    ) {
        viewModelScope.launch(Dispatchers.Default) { // Use Default dispatcher for CPU-intensive work
            if (viewportSize == IntSize.Zero || visibleTiles.isEmpty()) {
                snackbarHostState.showSnackbar("Map view is not ready.")
                return@launch
            }

            // 1. Reconstruct the view into a single multiplatform ImageBitmap (No changes here)
            val compositeBitmap = ImageBitmap(viewportSize.width, viewportSize.height)
            val canvas = Canvas(compositeBitmap)
            val paint = Paint()

            visibleTiles.forEach { tileInfo ->
                tileCache[tileInfo.id]?.let { tileBitmap ->
                    canvas.drawImage(
                        image = tileBitmap,
                        topLeftOffset = Offset(
                            tileInfo.screenOffset.x.toFloat(),
                            tileInfo.screenOffset.y.toFloat()
                        ),
                        paint = paint
                    )
                }
            }

            // 2. Read the raw pixel data from the composite ImageBitmap (No changes here)
            val pixelCount = viewportSize.width * viewportSize.height
            val pixelArray = IntArray(pixelCount)
            compositeBitmap.readPixels(
                buffer = pixelArray,
                startX = 0,
                startY = 0,
                width = viewportSize.width,
                height = viewportSize.height
            )

            // 3. Perform color quantization with a single call to your centralized function.
            //    This replaces all the manual bit-shifting and frequency counting logic.
            val rgbColors = ColourPaletteConverter.extractDominantColors(
                pixelArray = pixelArray,
                maxColors = 64 // Explicitly set to match the original ".take(64)"
            )

            // 4. Check the result and create the final palette (No changes here)
            if (rgbColors.isEmpty()) {
                snackbarHostState.showSnackbar("Could not generate a palette from the current map view.")
                return@launch
            }

            val newPalette = ColourPalette(
                watchAppPaletteId = 0, // 0 signifies a new, unsaved custom palette
                uniqueId = uuid4().toString(),
                name = "From Map", // Default name for the user to change
                colors = rgbColors,
                isEditable = true
            )
            _newlyCreatedPalette.value = newPalette
            _navigationEvents.emit(MapViewNavigationEvent.NavigateTo(Screen.Settings.route))
        }
    }

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

    fun displayRoute(route: Route, gpxRoute: IRoute) {
        _currentRouteI.value = gpxRoute // Set the route for sending to devices
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
        _currentRouteI.value = null
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

        // max dim means the user is zoomed in, but then the watch shows an area that they cannot see (generally to the left and right of the screen)
        // min dim only shows part of the screen on the watch, but feels more intuative, as the zoom level is similar.
        // it will mean some map tiles are not cached though
        val minDim = min(xDim, yDim)

        return RequestLocationLoad(
            centerGeo.latitude.toFloat(),
            centerGeo.longitude.toFloat(),
            minDim
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
                    val toSend = _watchSendStarted.value!!
                    _watchSendStarted.value = null // clear the overlay so we can see our message
                    connection.send(device, toSend)
                    connection.send(device, CacheCurrentArea())
                    // todo wait for response to say finished? its a very long process though
                    delay(5000) // wait for a bit so users can read message

                }
            } catch (t: Throwable) {
                snackbarHostState.showSnackbar("Failed to start seed on watch")
                Napier.e("Failed to start seed on watch", t) // Log the full exception
            } finally {
                _watchSendStarted.value = null
            }
        }
    }

    fun returnWatchToUsersLocation() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val device = deviceSelector.currentDevice()
                if (device == null) {
                    snackbarHostState.showSnackbar("no devices selected")
                    return@launch
                }

                sendingMessage("Returning watch to users location") {
                    connection.send(device, ReturnToUser())
                }
            } catch (t: Throwable) {
                snackbarHostState.showSnackbar("Failed to return watch to users location")
                Napier.e("Failed to return watch to users location", t) // Log the full exception
            }
        }
    }

    private fun startLocationUpdates() {
        viewModelScope.launch {
            locationService.getLocationFlow().collect { location ->
                _userLocation.value = location

                // If this is the first location update we've received, center the map on it.
                if (!isInitialLocationSet) {
                    _mapCenter.value = Point(
                        latitude = location.position.latitude.toFloat(),
                        longitude = location.position.longitude.toFloat(),
                        0f,
                    )
                    _mapZoom.value = 15f // A good default zoom for user location
                    isInitialLocationSet = true
                }
            }
        }
    }

    fun returnToUsersLocation() {
        viewModelScope.launch {
            _userLocation.value?.let { currentUserLocation ->
                // Center the map on the most recent known location
                _mapCenter.value = Point(
                    latitude = currentUserLocation.position.latitude.toFloat(),
                    longitude = currentUserLocation.position.longitude.toFloat(),
                    0f,
                )

                // Only change the zoom level if the user is currently zoomed out further than our
                // desired "close-up" level. This prevents jarringly zooming out.
                // maybe this should be hard coded to 15 or some other value so we do not zoom right in?
                if (_mapZoom.value < currentTileServer.value.tileLayerMax) {
                    _mapZoom.value = currentTileServer.value.tileLayerMax.toFloat()
                }
            } ?: run {
                // Handle case where location is not yet available
                snackbarHostState.showSnackbar("User location not available yet.")
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
                    delay(1000) // wait for a bit so users can read message
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

        seedingJob = viewModelScope.launch(Dispatchers.IO) {
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
                seedingJob = null // Clear the job reference
                Napier.e("Seeding error", e) // Log the full exception
            } finally {
                _isSeeding.value = false
            }
        }
    }

    fun cancelSeedingArea() {
        seedingJob?.cancel()
    }

    // --- Helper Functions (Needs Implementation) ---

    fun centerMapOn(point: Point) {
        // Logic to update _mapCenter (and potentially _mapZoom)
        _mapCenter.value = point
    }

    fun setMapZoom(z: Float) {
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

    fun previewStoredRoute(routeEntry: RouteEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val route = routeRepository.getRouteI(routeEntry.id)
                if (route == null) {
                    snackbarHostState.showSnackbar("Failed to load route")
                    return@launch
                }
                displayRoute(route.toSummary(snackbarHostState).let { Route(route.name(), it, emptyList()) }, route)
            } catch (t: Throwable) {
                snackbarHostState.showSnackbar("Failed to load route preview")
                Napier.e("Preview failed", t)
            }
        }
    }

    fun sendStoredRouteToDevice(routeEntry: RouteEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val route = routeRepository.getRouteI(routeEntry.id)
                if (route == null) {
                    snackbarHostState.showSnackbar("Failed to load route")
                    return@launch
                }
                sendRoute(route)
            } catch (t: Throwable) {
                snackbarHostState.showSnackbar("Failed to send route")
                Napier.e("Send route failed", t)
            }
        }
    }

    fun openActivityInStrava(id: Long) {
        viewModelScope.launch {
            try {
                // This usually involves calling a method on the repository to get the URL
                // and then using an Intent handler or similar mechanism to open it.
                stravaRepo.openActivityInBrowser(id)
            } catch (t: Throwable) {
                snackbarHostState.showSnackbar("Could not open Strava activity")
                Napier.e("Failed to open Strava", t)
            }
        }
    }

    fun previewActivity(activity: StravaActivity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = stravaRepo.getStreamForActivity(activity.id)
                if (stream == null) {
                    snackbarHostState.showSnackbar("Failed to load activity stream, please do a full delete/resync")
                    return@launch
                }
                displayRoute(stream.toRouteForDevice(activity.name), StaveIRoute(activity, stream))

            } catch (t: Throwable) {
                snackbarHostState.showSnackbar("Failed to load activity preview")
                Napier.e("Preview failed", t)
            }
        }
    }

    fun sendActivityToDevice(activity: StravaActivity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stream = stravaRepo.getStreamForActivity(activity.id)
                if (stream == null) {
                    snackbarHostState.showSnackbar("Failed to load activity stream, please do a full delete/resync")
                    return@launch
                }
                sendRoute(StaveIRoute(activity, stream))

            } catch (t: Throwable) {
                snackbarHostState.showSnackbar("Failed to send activity route")
                Napier.e("Send route failed", t)
            }
        }
    }

    fun sendRouteSync(gpxRoute: IRoute) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                sendRoute(gpxRoute)

            } catch (t: Throwable) {
                snackbarHostState.showSnackbar("Failed to send route")
                Napier.e("Send route failed", t)
            }
        }
    }

    private suspend fun sendRoute(
        gpxRoute: IRoute
    ) {
        SendRoute.sendRoute(
            gpxRoute,
            deviceSelector,
            snackbarHostState,
            connection,
            routeRepository,
            historyRepo,
        )
        { msg, cb ->
            this.sendingMessage(msg, cb)
        }
    }
}