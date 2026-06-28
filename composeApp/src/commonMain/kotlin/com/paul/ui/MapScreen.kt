package com.paul.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.paul.composables.LoadingOverlay
import com.paul.composables.MapTilerComposable
import com.paul.composables.RouteMiniMap
import com.paul.composables.mapButtonStyle
import com.paul.domain.RouteEntry
import com.paul.domain.ServerType
import com.paul.domain.StravaActivity
import com.paul.domain.TileServerInfo
import com.paul.infrastructure.connectiq.IConnection.Companion.LIGHT_WEIGHT_BREADCRUMB_DATAFIELD_ID
import com.paul.infrastructure.connectiq.IConnection.Companion.ULTRA_LIGHT_BREADCRUMB_DATAFIELD_ID
import com.paul.infrastructure.repositories.ITileRepository
import com.paul.infrastructure.repositories.RouteRepository
import com.paul.infrastructure.repositories.TileServerRepo
import com.paul.infrastructure.service.GeoPosition
import com.paul.infrastructure.service.formatDistance
import com.paul.infrastructure.service.screenPixelToGeo
import com.paul.protocol.todevice.Point
import com.paul.protocol.todevice.RequestLocationLoad
import com.paul.protocol.todevice.Route
import com.paul.viewmodels.MapViewModel
import io.github.aakira.napier.Napier
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

const val URL_TAG = "URL"

@Composable
fun MapScreen(
    viewModel: MapViewModel,
) {
    val isGeneratingPalette by viewModel.isGeneratingPalette.collectAsState()

    BackHandler(enabled = viewModel.sendingFile.value != "" && !isGeneratingPalette) {
        // prevent back handler when we are trying to do things, todo cancel the job we are trying to do
    }

    val isStravaEnabled by viewModel.isStravaEnabled.collectAsState()
    val nearbyActivities by viewModel.nearbyActivities.collectAsState()
    val nearbyStoredRoutes by viewModel.nearbyStoredRoutes.collectAsState()
    val currentRoute by viewModel.currentRoute.collectAsState()
    val currentRouteI by viewModel.currentRouteI.collectAsState()
    val isSeeding by viewModel.isSeeding.collectAsState()
    val seedingProgress by viewModel.seedingProgress.collectAsState()
    val zSeedingProgress by viewModel.zSeedingProgress.collectAsState()
    val seedingError by viewModel.seedingError.collectAsState()
    // Collect the new state for profile visibility
    val isElevationProfileVisible by viewModel.isElevationProfileVisible.collectAsState()
    val hoveredDistance by viewModel.hoveredDistance.collectAsState()

    val migrationStatus by viewModel.migrationService.migrationStatus.collectAsState()
    val isMigrating by viewModel.migrationService.isMigrating.collectAsState()

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val watchSendStarted by viewModel.watchSendStarted.collectAsState()
    val connectIqAppId by viewModel.connection.connectIqAppIdFlow().collectAsState()

    val isWatchFeatureDisabled = remember(connectIqAppId) {
        viewModel.excludedApps.contains(connectIqAppId)
    }

    // Box allows stacking the sending overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val boxScope = this // Capture BoxScope explicitly

        if (watchSendStarted != null && !isWatchFeatureDisabled) {
            WatchSendDialog(
                onConfirm = { viewModel.confirmWatchLocationLoad() },
                onDismiss = { viewModel.cancelWatchLocationLoad() }
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            StravaMapFilterSection(viewModel)

            if (isMigrating) {
                Text(
                    text = migrationStatus ?: "",
                    style = MaterialTheme.typography.caption.copy(color = MaterialTheme.colors.primary),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colors.primary.copy(alpha = 0.1f))
                        .padding(vertical = 4.dp)
                )
            }

            if (currentRoute != null) {
                Text(
                    text = currentRoute!!.name,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally)
                )
            }

            // --- Elevation Profile Area ---
            // Conditionally display based on BOTH route existence AND visibility state
            if (currentRoute != null && isElevationProfileVisible) {
                Spacer(modifier = Modifier.height(8.dp))
                ElevationProfileChart(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp) // Example height
                        .padding(horizontal = 16.dp),
                    route = currentRoute!!, // Use non-null assertion or smart cast
                    hoveredDistance = hoveredDistance,
                    onHoveredDistanceChange = { viewModel.setHoveredDistance(it) }
                )
                Spacer(modifier = Modifier.height(8.dp)) // Padding at the bottom
            }

            // --- Map View Area ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                MapTilerComposable(
                    modifier = Modifier.fillMaxSize(), // Or Modifier.weight(1f).fillMaxWidth() etc.
                    viewModel = viewModel,
                    viewportSize = viewportSize,
                    onViewportSizeChange = { viewportSize = it }, // Update the state
                    routeToDisplay = currentRoute,
                    isWatchFeatureDisabled = isWatchFeatureDisabled,
                    hoveredDistance = hoveredDistance
                )

                if (nearbyActivities.isNotEmpty() || nearbyStoredRoutes.isNotEmpty()) {
                    val tileServer by viewModel.tileServerRepository.currentServerFlow()
                        .collectAsState(TileServerRepo.defaultTileServer)

                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center) // Change from Alignment.BottomCenter
                            .padding(24.dp)
                            .fillMaxWidth(0.95f)    // Take more width for the side-by-side layout
                            .zIndex(100f),
                        shape = RoundedCornerShape(12.dp),
                        elevation = 8.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Nearby Routes",
                                    style = MaterialTheme.typography.h6,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { viewModel.clearNearbyActivities() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                            LazyColumn(modifier = Modifier.heightIn(max = 450.dp)) {
                                items(nearbyActivities) { activity ->
                                    NearbyActivityListItem(
                                        activity = activity,
                                        tileRepository = viewModel.tileRepository,
                                        tileServer = tileServer,
                                        onPreviewClick = { viewModel.previewActivity(activity) },
                                        onSendClick = { viewModel.sendActivityToDevice(activity) },
                                        onStravaClick = { viewModel.openActivityInStrava(activity.id) }
                                    )
                                    Divider(color = Color.LightGray.copy(alpha = 0.2f))
                                }
                                items(nearbyStoredRoutes) { routeEntry ->
                                    NearbyRouteListItem(
                                        routeEntry = routeEntry,
                                        routeRepo = viewModel.routeRepository,
                                        tileRepository = viewModel.tileRepository,
                                        tileServer = tileServer,
                                        onPreviewClick = { viewModel.previewStoredRoute(routeEntry) },
                                        onSendClick = { viewModel.sendStoredRouteToDevice(routeEntry) }
                                    )
                                    Divider(color = Color.LightGray.copy(alpha = 0.2f))
                                }
                            }
                        }
                    }
                }

                val tilServer =
                    viewModel.tileServerRepository.currentServerFlow().collectAsState()
                val serverType = tilServer.value.serverType
                KmpMapAttributionDisplay(
                    serverType = serverType,
                    modifier = with(boxScope) { // Make BoxScope explicit for .align
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp)
                    }
                )

                // --- Floating Action Buttons (Example layout) ---
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentRoute != null) {
                        // Play Button: Send route to watch
                        Button(
                            modifier = mapButtonStyle,
                            onClick = { viewModel.sendRouteSync(currentRouteI!!) },
                            enabled = true
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Send to Device")
                        }

                        // Close Button: Clear current route
                        Button(
                            modifier = mapButtonStyle,
                            onClick = { viewModel.clearRoute() }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Route")
                        }

                        // Elevation Profile Toggle Button
                        Button(
                            modifier = mapButtonStyle,
                            onClick = { viewModel.toggleElevationProfileVisibility() },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terrain,
                                contentDescription = if (isElevationProfileVisible) "Hide Elevation" else "Show Elevation",
                                tint = if (isElevationProfileVisible) LocalContentColor.current.copy(
                                    alpha = 0.5f
                                ) else LocalContentColor.current
                            )
                        }
                    }

                    // Watch and Crosshair: Return to User Command
                    if (!isWatchFeatureDisabled) {
                        Button(
                            modifier = mapButtonStyle,
                            onClick = { viewModel.returnWatchToUsersLocation() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy((-2).dp)
                            ) {
                                Icon(
                                    Icons.Default.Watch,
                                    contentDescription = "Return watch to user",
                                    modifier = Modifier.size(17.dp)
                                )
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = "Return watch to user",
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    // Crosshair: Center phone map on user
                    Button(
                        modifier = mapButtonStyle,
                        onClick = { viewModel.returnToUsersLocation() }
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = "Center on My Location")
                    }

                    if (!isWatchFeatureDisabled) {
                        Button(
                            modifier = mapButtonStyle,
                            onClick = {
                                if (viewportSize != IntSize.Zero) {
                                    val topLeftGeo = screenPixelToGeo(
                                        IntOffset(0, 0),
                                        GeoPosition(
                                            viewModel.mapCenter.value.latitude.toDouble(),
                                            viewModel.mapCenter.value.longitude.toDouble()
                                        ),
                                        viewModel.mapZoom.value,
                                        viewportSize
                                    )
                                    val bottomRightGeo = screenPixelToGeo(
                                        IntOffset(viewportSize.width, viewportSize.height),
                                        GeoPosition(
                                            viewModel.mapCenter.value.latitude.toDouble(),
                                            viewModel.mapCenter.value.longitude.toDouble()
                                        ),
                                        viewModel.mapZoom.value,
                                        viewportSize
                                    )
                                    viewModel.startSeedingAreaToWatch(
                                        centerGeo = GeoPosition(
                                            viewModel.mapCenter.value.latitude.toDouble(),
                                            viewModel.mapCenter.value.longitude.toDouble()
                                        ),
                                        topLeftGeo = topLeftGeo,
                                        bottomRightGeo = bottomRightGeo
                                    )
                                } else {
                                    Napier.v("Viewport size not available yet.")
                                }
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy((-2).dp)
                            ) {
                                Icon(
                                    Icons.Default.Watch,
                                    contentDescription = "Download area to watch",
                                    modifier = Modifier.size(17.dp)
                                )
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "Download area to watch",
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    Button(
                        modifier = mapButtonStyle,
                        onClick = {
                            if (viewportSize != IntSize.Zero) {
                                val currentCenter = GeoPosition(
                                    viewModel.mapCenter.value.latitude.toDouble(),
                                    viewModel.mapCenter.value.longitude.toDouble()
                                )
                                val currentZoom = viewModel.mapZoom.value
                                val topLeftGeo = screenPixelToGeo(
                                    IntOffset(0, 0), currentCenter, currentZoom, viewportSize
                                )
                                val bottomRightGeo = screenPixelToGeo(
                                    IntOffset(viewportSize.width, viewportSize.height),
                                    currentCenter,
                                    currentZoom,
                                    viewportSize
                                )

                                viewModel.startSeedingArea(
                                    minLat = min(
                                        topLeftGeo.latitude.toFloat(),
                                        bottomRightGeo.latitude.toFloat()
                                    ),
                                    maxLat = max(
                                        topLeftGeo.latitude.toFloat(),
                                        bottomRightGeo.latitude.toFloat()
                                    ),
                                    minLon = min(
                                        topLeftGeo.longitude.toFloat(),
                                        bottomRightGeo.longitude.toFloat()
                                    ),
                                    maxLon = max(
                                        topLeftGeo.longitude.toFloat(),
                                        bottomRightGeo.longitude.toFloat()
                                    ),
                                    minZoom = viewModel.currentTileServer.value.tileLayerMin,
                                    maxZoom = viewModel.currentTileServer.value.tileLayerMax
                                )
                            }
                        }
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Download Area")
                    }
                }
            }
        }

        if (isSeeding) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Downloading Map Area", style = MaterialTheme.typography.h6)
                    Text(
                        "Downloading zoom level $zSeedingProgress",
                        style = MaterialTheme.typography.body2
                    )

                    LinearProgressIndicator(
                        progress = seedingProgress,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        "${(seedingProgress * 100).roundToInt()}% Complete",
                        style = MaterialTheme.typography.caption
                    )

                    Button(onClick = { viewModel.cancelSeedingArea() }) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Cancel")
                    }
                }
            }
        }

        if (isGeneratingPalette) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Extracting Palette...", color = Color.White)
                    }
                }
            }
        }

        LoadingOverlay(
            isLoading = viewModel.sendingFile.value.isNotEmpty(),
            loadingText = viewModel.sendingFile.value
        )
    }
}

@Composable
fun WatchSendDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download to Watch") },
        text = { Text("Are you sure you want to start caching map tiles for this area on your Garmin device? This process can take a significant amount of time and uses device battery.") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun NearbyActivityListItem(
    activity: StravaActivity,
    tileRepository: ITileRepository,
    tileServer: TileServerInfo,
    onPreviewClick: () -> Unit,
    onSendClick: () -> Unit,
    onStravaClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Gray.copy(alpha = 0.1f))
        ) {
            RouteMiniMap(
                route = activity.summaryToRoute(),
                tileRepository = tileRepository,
                modifier = Modifier.fillMaxSize(),
                tileServer,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = activity.name,
                style = MaterialTheme.typography.subtitle2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = activity.getActivityIcon(),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.Gray
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = (activity.type ?: "Activity").uppercase(),
                    style = MaterialTheme.typography.overline,
                    color = Color.Gray
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = " • ${formatDistance(activity.distance)}",
                    style = MaterialTheme.typography.overline,
                    color = Color.Gray
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = activity.startDate.toLocalDateTime(TimeZone.currentSystemDefault())
                        .toString().substringBefore("T"),
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray.copy(0.7f)
                )

                Spacer(Modifier.weight(1f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPreviewClick, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colors.primary.copy(0.7f)
                        )
                    }
                    IconButton(onClick = onSendClick, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.PlayArrow,
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = Color(0xFF4CAF50)
                        )
                    }
                    IconButton(onClick = onStravaClick, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.OpenInNew,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colors.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyRouteListItem(
    routeEntry: RouteEntry,
    routeRepo: RouteRepository,
    tileRepository: ITileRepository,
    tileServer: TileServerInfo,
    onPreviewClick: () -> Unit,
    onSendClick: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val routeDetail by produceState<Route?>(initialValue = null, routeEntry.id) {
        value = routeRepo.getRouteEntrySummary(routeEntry, snackbarHostState)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Gray.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            routeDetail?.let { activeRoute ->
                RouteMiniMap(
                    route = activeRoute,
                    tileRepository = tileRepository,
                    modifier = Modifier.fillMaxSize(),
                    tileServer,
                )
            } ?: run {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = routeEntry.name.ifBlank { "<No Name>" },
                style = MaterialTheme.typography.subtitle2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Directions,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = Color.Gray
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = routeEntry.type.name,
                    style = MaterialTheme.typography.overline,
                    color = Color.Gray
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = " • ${formatDistance(routeEntry.distanceMeters)}",
                    style = MaterialTheme.typography.overline,
                    color = Color.Gray
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = routeEntry.createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
                        .toString().substringBefore("T"),
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray.copy(0.7f)
                )

                Spacer(Modifier.weight(1f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPreviewClick, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.LocationOn,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colors.primary.copy(0.7f)
                        )
                    }
                    IconButton(onClick = onSendClick, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.PlayArrow,
                            null,
                            modifier = Modifier.size(20.dp),
                            tint = Color(0xFF4CAF50)
                        )
                    }
                }
            }
        }
    }
}

private data class ChartData(
    val points: List<Pair<Float, Float>> = emptyList(), // (distance, altitude)
    val minAltitude: Float = 0f,
    val maxAltitude: Float = 0f,
    val totalDistance: Float = 0f
)

@OptIn(ExperimentalTextApi::class)
@Composable
fun ElevationProfileChart(
    modifier: Modifier = Modifier,
    route: Route,
    hoveredDistance: Float? = null,
    onHoveredDistanceChange: (Float?) -> Unit = {},
    lineColor: Color = MaterialTheme.colors.primary,
    strokeWidth: Float = 4f,
    gridColor: Color = Color.Gray,
    gridStrokeWidth: Float = 3f,
    labelTextColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
    labelTextSize: TextUnit = 10.sp,
    numHorizontalGridLines: Int = 5,
    numVerticalGridLines: Int = 5,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 16.dp,
    labelPadding: Dp = 4.dp
) {
    val chartData: ChartData = remember(route.route) {
        processRouteForChart(route.route)
    }

    val density = LocalDensity.current
    val horizontalPaddingPx = with(density) { horizontalPadding.toPx() }
    val verticalPaddingPx = with(density) { verticalPadding.toPx() }
    val labelPaddingPx = with(density) { labelPadding.toPx() }

    // === Get TextMeasurer here in the Composable scope ===
    val textMeasurer = rememberTextMeasurer()

    Column(modifier = modifier) {

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(chartData, horizontalPaddingPx) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val availableWidth = size.width - 2 * horizontalPaddingPx
                            if (availableWidth > 0) {
                                val distance = ((offset.x - horizontalPaddingPx) / availableWidth) * chartData.totalDistance
                                onHoveredDistanceChange(distance.coerceIn(0f, chartData.totalDistance))
                            }
                        },
                        onDrag = { change, _ ->
                            val availableWidth = size.width - 2 * horizontalPaddingPx
                            if (availableWidth > 0) {
                                val currentX = change.position.x
                                val distance = ((currentX - horizontalPaddingPx) / availableWidth) * chartData.totalDistance
                                onHoveredDistanceChange(distance.coerceIn(0f, chartData.totalDistance))
                            }
                        },
                        onDragEnd = { onHoveredDistanceChange(null) },
                        onDragCancel = { onHoveredDistanceChange(null) }
                    )
                }
                .pointerInput(chartData, horizontalPaddingPx) {
                    detectTapGestures(
                        onPress = { offset ->
                            val availableWidth = size.width - 2 * horizontalPaddingPx
                            if (availableWidth > 0) {
                                val distance = ((offset.x - horizontalPaddingPx) / availableWidth) * chartData.totalDistance
                                onHoveredDistanceChange(distance.coerceIn(0f, chartData.totalDistance))
                            }
                            try {
                                awaitRelease()
                            } finally {
                                onHoveredDistanceChange(null)
                            }
                        }
                    )
                }
        ) { // Canvas lambda provides DrawScope
            if (chartData.points.size >= 2 && chartData.totalDistance > 0f) {
                // Pass the textMeasurer instance to drawGridLines
                drawGridLines(
                    drawScope = this,
                    textMeasurer = textMeasurer, // Pass the measurer
                    data = chartData,
                    gridColor = gridColor,
                    strokeWidth = gridStrokeWidth,
                    labelTextColor = labelTextColor,
                    labelTextSize = labelTextSize,
                    numHorizontalLines = numHorizontalGridLines,
                    numVerticalLines = numVerticalGridLines,
                    horizontalPadding = horizontalPaddingPx,
                    verticalPadding = verticalPaddingPx,
                    labelPadding = labelPaddingPx
                )

                // drawElevationLine does not need the textMeasurer
                drawElevationLine(
                    drawScope = this,
                    data = chartData,
                    lineColor = lineColor,
                    strokeWidth = strokeWidth,
                    horizontalPadding = horizontalPaddingPx,
                    verticalPadding = verticalPaddingPx
                )

                // Draw Hover indicator
                hoveredDistance?.let { dist ->
                    val availableWidth = size.width - 2 * horizontalPaddingPx
                    val xScale = availableWidth / chartData.totalDistance
                    val x = horizontalPaddingPx + (dist * xScale)

                    // Find elevation at this distance (interpolate)
                    val elevation = interpolateElevation(chartData.points, dist)

                    val altitudeRange = chartData.maxAltitude - chartData.minAltitude
                    val availableHeight = size.height - 2 * verticalPaddingPx
                    val yScale = if (altitudeRange > 0) availableHeight / altitudeRange else 0f
                    val y = if (altitudeRange > 0) {
                        size.height - verticalPaddingPx - ((elevation - chartData.minAltitude) * yScale)
                    } else {
                        verticalPaddingPx + availableHeight / 2f
                    }

                    // Draw vertical line
                    drawLine(
                        color = lineColor.copy(alpha = 0.5f),
                        start = Offset(x, verticalPaddingPx),
                        end = Offset(x, size.height - verticalPaddingPx),
                        strokeWidth = 2f
                    )

                    // Draw dot at elevation
                    drawCircle(
                        color = lineColor,
                        radius = 6f,
                        center = Offset(x, y)
                    )

                    // Draw tooltip
                    val tooltipText = "${formatDistance(dist)} / ${formatAltitude(elevation)}"
                    val textLayoutResult = textMeasurer.measure(
                        AnnotatedString(tooltipText),
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    val tooltipPadding = 8f
                    val tooltipWidth = textLayoutResult.size.width + tooltipPadding * 2
                    val tooltipHeight = textLayoutResult.size.height + tooltipPadding * 2
                    
                    var tooltipX = x - tooltipWidth / 2
                    var tooltipY = y - tooltipHeight - 16f
                    
                    // Keep tooltip on screen
                    tooltipX = tooltipX.coerceIn(0f, size.width - tooltipWidth)
                    if (tooltipY < 0) tooltipY = y + 16f

                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.7f),
                        topLeft = Offset(tooltipX, tooltipY),
                        size = androidx.compose.ui.geometry.Size(tooltipWidth, tooltipHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                    )

                    drawText(
                        textLayoutResult,
                        topLeft = Offset(tooltipX + tooltipPadding, tooltipY + tooltipPadding)
                    )
                }
            }
        }

        // --- Labels Below Canvas ---
        if (chartData.points.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp) // Space between canvas and labels
                    .padding(horizontal = horizontalPadding), // Align labels roughly with chart padding
                horizontalArrangement = Arrangement.SpaceBetween // Space out labels
            ) {
                // Display Total Distance
                Text(
                    text = "Dist: ${formatDistance(chartData.totalDistance)}",
                    style = MaterialTheme.typography.caption
                )
                // Display Max Altitude
                Text(
                    text = "Max Alt: ${formatAltitude(chartData.maxAltitude)}",
                    style = MaterialTheme.typography.caption
                )
            }
        }
    }
}

private fun interpolateElevation(points: List<Pair<Float, Float>>, distance: Float): Float {
    if (points.isEmpty()) return 0f
    if (distance <= points.first().first) return points.first().second
    if (distance >= points.last().first) return points.last().second

    // Find the two points to interpolate between
    for (i in 0 until points.size - 1) {
        val p1 = points[i]
        val p2 = points[i + 1]
        if (distance >= p1.first && distance <= p2.first) {
            val fraction = (distance - p1.first) / (p2.first - p1.first)
            return p1.second + fraction * (p2.second - p1.second)
        }
    }
    return points.last().second
}

// Helper function to format altitude
private fun formatAltitude(altitudeMeters: Float): String {
    return "${altitudeMeters.roundToInt()} m"
}


@OptIn(ExperimentalTextApi::class) // Still needed for drawText parameter type
private fun drawGridLines(
    drawScope: DrawScope,
    textMeasurer: TextMeasurer, // Receive the TextMeasurer instance
    data: ChartData,
    gridColor: Color,
    strokeWidth: Float,
    labelTextColor: Color,
    labelTextSize: TextUnit,
    numHorizontalLines: Int,
    numVerticalLines: Int,
    horizontalPadding: Float,
    verticalPadding: Float,
    labelPadding: Float
) {
    with(drawScope) { // Use DrawScope receiver
        val altitudeRange = data.maxAltitude - data.minAltitude
        val availableWidth = size.width - 2 * horizontalPadding
        val availableHeight = size.height - 2 * verticalPadding

        if (availableWidth <= 0 || availableHeight <= 0) return

        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

        // --- Draw Horizontal Grid Lines & Labels (Altitude) ---
        if (numHorizontalLines > 1 && altitudeRange > 0) {
            val altitudeStep = altitudeRange / (numHorizontalLines - 1)
            val yScale = availableHeight / altitudeRange

            for (i in 0 until numHorizontalLines) {
                val currentAltitude = data.minAltitude + i * altitudeStep
                val y =
                    size.height - verticalPadding - ((currentAltitude - data.minAltitude) * yScale)
                // Draw Grid Line
                drawLine(
                    color = gridColor,
                    start = Offset(horizontalPadding, y),
                    end = Offset(size.width - horizontalPadding, y),
                    strokeWidth = strokeWidth,
                    pathEffect = dashEffect
                )

                // Prepare and Draw Label - Use the passed textMeasurer
                val labelText = formatAltitude(currentAltitude)
                val textLayoutResult = textMeasurer.measure(
                    AnnotatedString(labelText),
                    style = TextStyle(
                        color = labelTextColor,
                        fontSize = labelTextSize,
                    )
                )
                val labelX = horizontalPadding - labelPadding - textLayoutResult.size.width
                val labelY = y - textLayoutResult.size.height / 2f
                drawText(textLayoutResult, topLeft = Offset(labelX.coerceAtLeast(0f), labelY))
            }
        } else if (numHorizontalLines > 0) { // Handle flat case
            val y = verticalPadding + availableHeight / 2f
            val currentAltitude = data.minAltitude
            // Draw Grid Line
            drawLine(
                color = gridColor,
                start = Offset(horizontalPadding, y),
                end = Offset(size.width - horizontalPadding, y),
                strokeWidth = strokeWidth,
                pathEffect = dashEffect
            )
            // Prepare and Draw Label
            val labelText = formatAltitude(currentAltitude)
            val textLayoutResult = textMeasurer.measure(
                AnnotatedString(labelText), style = TextStyle(
                    color = labelTextColor,
                    fontSize = labelTextSize,
                )
            )
            val labelX = horizontalPadding - labelPadding - textLayoutResult.size.width
            val labelY = y - textLayoutResult.size.height / 2f
            drawText(textLayoutResult, topLeft = Offset(labelX.coerceAtLeast(0f), labelY))
        }

        // --- Draw Vertical Grid Lines & Labels (Distance) ---
        if (numVerticalLines > 1 && data.totalDistance > 0) {
            val distanceStep = data.totalDistance / (numVerticalLines - 1)
            val xScale = availableWidth / data.totalDistance

            for (i in 0 until numVerticalLines) {
                val currentDistance = i * distanceStep
                val x = horizontalPadding + (currentDistance * xScale)
                // Draw Grid Line
                drawLine(
                    color = gridColor,
                    start = Offset(x, verticalPadding),
                    end = Offset(x, size.height - verticalPadding),
                    strokeWidth = strokeWidth,
                    pathEffect = dashEffect
                )

                // Prepare and Draw Label
                if (i > 0 || numHorizontalLines <= 1) {
                    val labelText = formatDistance(currentDistance)
                    val textLayoutResult = textMeasurer.measure(
                        AnnotatedString(labelText),
                        style = TextStyle(
                            color = labelTextColor,
                            fontSize = labelTextSize,
                        )
                    )
                    val labelX = x - textLayoutResult.size.width / 2f
                    val labelY = size.height - verticalPadding + labelPadding
                    drawText(
                        textLayoutResult,
                        topLeft = Offset(
                            labelX.coerceIn(
                                0f,
                                size.width - textLayoutResult.size.width
                            ), labelY
                        )
                    )
                }
            }
        }
    }
}

// Helper function to perform the drawing logic within a DrawScope
private fun drawElevationLine(
    drawScope: DrawScope, // Receive DrawScope from Canvas
    data: ChartData,
    lineColor: Color,
    strokeWidth: Float,
    horizontalPadding: Float,
    verticalPadding: Float
) {
    with(drawScope) { // Use DrawScope receiver for easy access to size etc.

        val altitudeRange = data.maxAltitude - data.minAltitude
        val availableWidth = size.width - 2 * horizontalPadding
        val availableHeight = size.height - 2 * verticalPadding

        // Handle edge cases where drawing isn't feasible
        if (availableWidth <= 0 || availableHeight <= 0) return

        // Calculate scaling factors
        val xScale = availableWidth / data.totalDistance

        // Handle flat elevation case to avoid division by zero
        val yScale = if (altitudeRange > 0) {
            availableHeight / altitudeRange
        } else {
            0f // If flat, scale is 0, points will be drawn at mid-height
        }

        // Create the path object to draw the line
        val path = Path()

        // Move to the starting point
        val firstPoint = data.points.first()
        val startX = horizontalPadding // Start at the left padding edge
        val startY = if (altitudeRange > 0) {
            // Map altitude to inverted Y pixel coordinate within padded area
            size.height - verticalPadding - ((firstPoint.second - data.minAltitude) * yScale)
        } else {
            // If flat, draw at the vertical center of the padded area
            verticalPadding + availableHeight / 2f
        }
        path.moveTo(startX, startY)

        // Add lines to subsequent points
        data.points.drop(1).forEach { point ->
            val x = horizontalPadding + (point.first * xScale)
            val y = if (altitudeRange > 0) {
                size.height - verticalPadding - ((point.second - data.minAltitude) * yScale)
            } else {
                verticalPadding + availableHeight / 2f // Keep drawing horizontally if flat
            }
            path.lineTo(
                x.coerceIn(horizontalPadding, size.width - horizontalPadding), // Clamp X
                y.coerceIn(verticalPadding, size.height - verticalPadding)
            )     // Clamp Y
        }

        // Draw the path onto the canvas
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round // Makes line ends look nicer
            )
        )
    }
}


// --- Helper function to calculate the profile data and bounds ---
private fun processRouteForChart(points: List<Point>): ChartData {
    if (points.size < 2) {
        return ChartData() // Return empty data if not enough points
    }

    val profile = mutableListOf<Pair<Float, Float>>()
    var cumulativeDistance = 0.0f
    var minAlt = points[0].altitude
    var maxAlt = points[0].altitude

    // Add the starting point
    profile.add(Pair(cumulativeDistance, points[0].altitude))

    // Iterate through the rest of the points
    for (i in 1 until points.size) {
        val p1 = points[i - 1]
        val p2 = points[i]
        val distance = haversineDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
        cumulativeDistance += distance
        profile.add(Pair(cumulativeDistance, p2.altitude))

        // Update min/max altitude
        minAlt = min(minAlt, p2.altitude)
        maxAlt = max(maxAlt, p2.altitude)
    }

    return ChartData(
        points = profile,
        minAltitude = minAlt,
        maxAltitude = maxAlt,
        totalDistance = cumulativeDistance
    )
}

// --- Haversine distance calculation (keep this function) ---
// Returns distance in meters
private fun haversineDistance(lat1: Float, lon1: Float, lat2: Float, lon2: Float): Float {
    val R = 6371e3 // Earth radius in meters
    val phi1 = Math.toRadians(lat1.toDouble())
    val phi2 = Math.toRadians(lat2.toDouble())
    val deltaPhi = Math.toRadians((lat2 - lat1).toDouble())
    val deltaLambda = Math.toRadians((lon2 - lon1).toDouble())

    val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
            cos(phi1) * cos(phi2) *
            sin(deltaLambda / 2) * sin(deltaLambda / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return (R * c).toFloat()
}

data class AttributionPart(
    val text: String,
    val url: String? = null // URL is optional
)

fun getAttributionParts(serverType: ServerType): List<AttributionPart> {
    val texts = serverType.attributionText()
    val links = serverType.attributionLink()

    return texts.mapIndexedNotNull { index, text ->
        if (text.isNotBlank()) { // Only include if text is not blank
            val url = links.getOrNull(index)?.takeIf { it.isNotBlank() }
            AttributionPart(text, url)
        } else {
            null // Filter out parts with blank text
        }
    }
}

@Composable
fun KmpMapAttributionDisplay(
    serverType: ServerType,
    modifier: Modifier = Modifier,
    separator: String = " ",
    defaultTextColor: Color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f), // M3 color
    linkTextColor: Color = MaterialTheme.colors.primary, // M3 color
    fontSize: TextUnit = 10.sp,
    linkTextDecoration: TextDecoration? = TextDecoration.Underline
) {
    val attributionParts = getAttributionParts(serverType)

    if (attributionParts.isEmpty()) {
        // No valid attribution text to display
        return
    }

    val uriHandler = LocalUriHandler.current
    val annotatedString = buildAnnotatedString {
        attributionParts.forEachIndexed { index, part ->
            if (part.url != null) {
                // This part is clickable
                pushStringAnnotation(tag = URL_TAG, annotation = part.url)
                withStyle(
                    style = SpanStyle(
                        color = linkTextColor,
                        textDecoration = linkTextDecoration,
                        fontSize = fontSize
                    )
                ) {
                    append(part.text)
                }
                pop() // Important to pop the annotation
            } else {
                // This part is plain text
                withStyle(
                    style = SpanStyle(
                        color = defaultTextColor,
                        fontSize = fontSize
                    )
                ) {
                    append(part.text)
                }
            }

            // Add separator if this is not the last part and there are more parts
            if (index < attributionParts.size - 1) {
                withStyle(style = SpanStyle(color = defaultTextColor, fontSize = fontSize)) {
                    append(separator)
                }
            }
        }
    }

    if (annotatedString.text.isBlank()) return // Double check if anything was actually appended

    @Suppress("DEPRECATION")
    ClickableText(
        text = annotatedString,
        modifier = modifier,
        style = MaterialTheme.typography.body1.copy(fontSize = fontSize), // Base style
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = URL_TAG, start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    try {
                        uriHandler.openUri(annotation.item) // annotation.item is the URL
                    } catch (e: Exception) {
                        // Handle potential errors opening URI (e.g., invalid URI, no browser)
                        // Log.e("KmpMapAttribution", "Could not open URI: ${annotation.item}", e)
                        println("Error opening URI: ${annotation.item} - ${e.message}")
                    }
                }
        }
    )
}

@Composable
fun StravaMapFilterSection(viewModel: MapViewModel) {
    val stravaRepo = viewModel.stravaRepo
    val isStravaEnabled by viewModel.isStravaEnabled.collectAsState()

    val currentPage by stravaRepo.currentPage.collectAsState(0L)
    val maxPages by stravaRepo.maxPages.collectAsState(1L) // Collect max pages
    val currentRange by stravaRepo.currentRange.collectAsState()
    val totalFiltered by stravaRepo.activitiesByDateRangeAndPage.collectAsState(emptyList())

    var showDatePicker by remember { mutableStateOf(false) }

    if (isStravaEnabled) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp), // Slightly more padding
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                text = "Strava:",
                style = MaterialTheme.typography.caption.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )

            // 1. Compact Date Clickable
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colors.primary.copy(alpha = 0.05f)) // Subtle hint
                    .clickable { showDatePicker = true }
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DateRange,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colors.primary
                )
                Spacer(Modifier.width(6.dp))
                val start =
                    currentRange.start.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                val end =
                    currentRange.endInclusive.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                Text(
                    text = "${start} - ${end}",
                    style = MaterialTheme.typography.caption.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 2. Pagination Controls
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Show current page / total pages
                Text(
                    text = "Pg ${currentPage + 1}/${maxPages.coerceAtLeast(1)} (${totalFiltered.size})",
                    style = MaterialTheme.typography.caption.copy(fontSize = 12.sp),
                    color = Color.Gray
                )

                IconButton(
                    onClick = { stravaRepo.setPage(currentPage - 1) },
                    enabled = currentPage > 0,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronLeft,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = if (currentPage > 0) MaterialTheme.colors.primary else Color.Gray
                    )
                }

                IconButton(
                    onClick = { stravaRepo.setPage(currentPage + 1) },
                    // CAP: Disable if we are on the last page
                    enabled = (currentPage + 1) < maxPages,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.ChevronRight,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = if ((currentPage + 1) < maxPages) MaterialTheme.colors.primary else Color.Gray
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        StravaDateRangePicker(
            initialRange = currentRange,
            onDismiss = { showDatePicker = false },
            onDateRangeSelected = { start, end ->
                stravaRepo.setDateRange(start, end)
                showDatePicker = false
            }
        )
    }
}
