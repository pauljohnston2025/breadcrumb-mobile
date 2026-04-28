package com.paul.ui

import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.Surface
import androidx.compose.material.IconButton
import androidx.compose.material.Divider
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Watch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.paul.composables.LoadingOverlay
import com.paul.composables.MapTilerComposable
import com.paul.composables.mapButtonStyle
import com.paul.domain.RouteEntry
import com.paul.domain.ServerType
import com.paul.infrastructure.service.GeoPosition
import com.paul.infrastructure.service.screenPixelToGeo
import com.paul.protocol.todevice.Point
import com.paul.protocol.todevice.Route
import com.paul.viewmodels.MapViewModel
import io.github.aakira.napier.Napier
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import com.paul.infrastructure.connectiq.IConnection.Companion.LIGHT_WEIGHT_BREADCRUMB_DATAFIELD_ID
import com.paul.infrastructure.connectiq.IConnection.Companion.ULTRA_LIGHT_BREADCRUMB_DATAFIELD_ID
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
private fun WatchSendDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cache current area to watch") },
        text = {
            Text("Note some watches do not support this. The datafield must be open for this to work.")
        },
        confirmButton = {
            Button(
                onClick = { onConfirm() },
                enabled = true,
            ) { Text("Confirm") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

internal const val URL_TAG =
    "URL_ATTRIBUTION_TAG" // Internal tag for identifying clickable URL parts

@Composable
fun MapScreen(viewModel: MapViewModel) {
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
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val watchSendStarted by viewModel.watchSendStarted.collectAsState()
    val connectIqAppId by viewModel.connection.connectIqAppIdFlow().collectAsState()

    val isWatchFeatureDisabled = remember(connectIqAppId) {
        connectIqAppId == LIGHT_WEIGHT_BREADCRUMB_DATAFIELD_ID ||
                connectIqAppId == ULTRA_LIGHT_BREADCRUMB_DATAFIELD_ID
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
                    route = currentRoute!! // Use non-null assertion or smart cast
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
                    isWatchFeatureDisabled = isWatchFeatureDisabled
                )

                if (nearbyActivities.isNotEmpty() || nearbyStoredRoutes.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center) // Change from Alignment.BottomCenter
                            .padding(24.dp)
                            .fillMaxWidth(0.85f)    // Don't take the full width so it looks like a dialog
                            .zIndex(100f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Nearby Items",
                                    style = MaterialTheme.typography.h6,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { viewModel.clearNearbyActivities() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                                items(nearbyActivities) { activity ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        elevation = 2.dp,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp)
                                        ) {
                                            // 1. Activity Title at the top
                                            Text(
                                                text = activity.name,
                                                style = MaterialTheme.typography.subtitle1,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // 2. Bottom section: Icon/Type on left, All Controls on right
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                // Left Side: Icon and Type
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = activity.getActivityIcon(),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colors.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = activity.type ?: "Unknown",
                                                        style = MaterialTheme.typography.caption,
                                                        color = LocalContentColor.current.copy(alpha = 0.7f)
                                                    )
                                                }

                                                // Right Side: Action Buttons (Grouped)
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    // Preview/Location Button
                                                    IconButton(
                                                        onClick = {
                                                            viewModel.previewActivity(
                                                                activity
                                                            )
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.LocationOn,
                                                            contentDescription = "Preview",
                                                            modifier = Modifier.size(20.dp),
                                                            tint = MaterialTheme.colors.primary
                                                        )
                                                    }

                                                    // Send to Watch Button
                                                    IconButton(
                                                        onClick = {
                                                            viewModel.sendActivityToDevice(
                                                                activity
                                                            )
                                                        },
                                                        enabled = !isWatchFeatureDisabled,
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.PlayArrow,
                                                            contentDescription = "Send to Device",
                                                            modifier = Modifier.size(20.dp),
                                                            tint = Color(0xFF4CAF50),
                                                        )
                                                    }

                                                    // Strava Link Button (Restored)
                                                    IconButton(
                                                        onClick = {
                                                            viewModel.openActivityInStrava(
                                                                activity.id
                                                            )
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.OpenInNew,
                                                            contentDescription = "Open in Strava",
                                                            modifier = Modifier.size(20.dp),
                                                            tint = MaterialTheme.colors.primary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                items(nearbyStoredRoutes) { routeEntry ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        elevation = 2.dp,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp)
                                        ) {
                                            Text(
                                                text = routeEntry.name,
                                                style = MaterialTheme.typography.subtitle1,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Directions,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colors.primary
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = routeEntry.type.name,
                                                        style = MaterialTheme.typography.caption,
                                                        color = LocalContentColor.current.copy(alpha = 0.7f)
                                                    )
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    IconButton(
                                                        onClick = {
                                                            viewModel.previewStoredRoute(routeEntry)
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.LocationOn,
                                                            contentDescription = "Preview",
                                                            modifier = Modifier.size(20.dp),
                                                            tint = MaterialTheme.colors.primary
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            viewModel.sendStoredRouteToDevice(routeEntry)
                                                        },
                                                        enabled = !isWatchFeatureDisabled,
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.PlayArrow,
                                                            contentDescription = "Send to Device",
                                                            modifier = Modifier.size(20.dp),
                                                            tint = Color(0xFF4CAF50),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
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

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (currentRoute != null) {
                        if (currentRouteI != null) {
                            Button(
                                modifier = mapButtonStyle,
                                onClick = { currentRouteI?.let { viewModel.sendRouteSync(it) } }
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Send route to device",
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                    tint = Color(0xFF4CAF50) // Matching the green used in the list item
                                )
                            }
                        }
                        Button(modifier = mapButtonStyle, onClick = { viewModel.clearRoute() }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear route",
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                        }
                        Button(
                            modifier = mapButtonStyle,
                            onClick = { viewModel.toggleElevationProfileVisibility() },
                        ) {
                            Icon(
                                Icons.Default.Terrain,
                                contentDescription = if (isElevationProfileVisible) "Hide Elevation Profile" else "Show Elevation Profile",
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                                tint = if (isElevationProfileVisible) LocalContentColor.current.copy(
                                    alpha = 1f
                                ) else LocalContentColor.current.copy(alpha = 0.5f)
                            )
                        }
                    }
                    if (!isWatchFeatureDisabled) {
                        Button(
                            modifier = mapButtonStyle,
                            onClick = {
                                viewModel.returnWatchToUsersLocation()
                            },
                            enabled = true
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                // Negative spacing causes overlap. Adjust the value as needed.
                                // -8.dp means the second icon will be pulled 8.dp to the left.
                                horizontalArrangement = Arrangement.spacedBy((-2).dp)
                            ) {
                                Icon(
                                    Icons.Default.Watch,
                                    contentDescription = "Return Watch To Users Location",
                                    modifier = Modifier.size(17.dp) // Slightly smaller icon so that the button is the same size as the other ones
                                )
                                Icon(
                                    Icons.Default.MyLocation,
                                    contentDescription = "Return Watch To Users Location",
                                    modifier = Modifier.size(17.dp) // Slightly smaller icon so that the button is the same size as the other ones
                                )
                            }
                        }
                    }

                    Button(
                        modifier = mapButtonStyle,
                        onClick = {
                            viewModel.returnToUsersLocation()
                        },
                        enabled = true
                    ) {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = "Return To Users Location",
                        )
                    }
                }
            } // --- End of Map Box ---


            // --- Seeding Controls / Status ---
            // This is the start of the corrected section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween // This will push the items to the edges
            ) {
                // This Column will contain the part of the UI that changes based on isSeeding
                Column(
                    modifier = Modifier.weight(
                        1f,
                        fill = false
                    ) // Use weight to allow the watch button to have its own space
                ) {
                    if (isSeeding) {
                        // --- Seeding is in Progress ---
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Downloading,
                                contentDescription = "Caching map area",
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Caching (z: $zSeedingProgress)...", maxLines = 1)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = seedingProgress,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.cancelSeedingArea() },
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = MaterialTheme.colors.error,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "Stop Caching"
                                )
                            }
                        }
                    } else {
                        // --- Seeding is NOT in progress ---
                        Button(
                            onClick = {
                                if (viewportSize != IntSize.Zero) {
                                    val centerGeo = GeoPosition(
                                        viewModel.mapCenter.value.latitude.toDouble(),
                                        viewModel.mapCenter.value.longitude.toDouble()
                                    )
                                    val currentZoom = viewModel.mapZoom.value
                                    val topLeftGeo = screenPixelToGeo(
                                        IntOffset(0, 0),
                                        centerGeo,
                                        currentZoom,
                                        viewportSize
                                    )
                                    val bottomRightGeo = screenPixelToGeo(
                                        IntOffset(
                                            viewportSize.width,
                                            viewportSize.height
                                        ), centerGeo, currentZoom, viewportSize
                                    )
                                    val tilServer =
                                        viewModel.tileServerRepository.currentServerFlow().value
                                    val minSeedZoom = viewModel.mapZoom.value.roundToInt()
                                        .coerceIn(tilServer.tileLayerMin, tilServer.tileLayerMax)
                                    val maxSeedZoom = tilServer.tileLayerMax

                                    viewModel.startSeedingArea(
                                        minLat = bottomRightGeo.latitude.toFloat(),
                                        maxLat = topLeftGeo.latitude.toFloat(),
                                        minLon = topLeftGeo.longitude.toFloat(),
                                        maxLon = bottomRightGeo.longitude.toFloat(),
                                        minZoom = minSeedZoom,
                                        maxZoom = maxSeedZoom
                                    )
                                } else {
                                    Napier.d("Viewport size not available yet for seeding.")
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Download map area",
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Text("Download Current Map Area")
                        }
                    }
                }

                if (!isWatchFeatureDisabled) {
                    Button(
                        onClick = {
                            if (viewportSize != IntSize.Zero) {
                                val centerGeo = GeoPosition(
                                    viewModel.mapCenter.value.latitude.toDouble(),
                                    viewModel.mapCenter.value.longitude.toDouble()
                                )
                                val currentZoom = viewModel.mapZoom.value
                                val topLeftGeo = screenPixelToGeo(
                                    IntOffset(0, 0),
                                    centerGeo,
                                    currentZoom,
                                    viewportSize
                                )
                                val bottomRightGeo = screenPixelToGeo(
                                    IntOffset(viewportSize.width, viewportSize.height),
                                    centerGeo,
                                    currentZoom,
                                    viewportSize
                                )
                                viewModel.startSeedingAreaToWatch(
                                    centerGeo = centerGeo,
                                    topLeftGeo = topLeftGeo,
                                    bottomRightGeo = bottomRightGeo,
                                )
                            } else {
                                Napier.d("Viewport size not available yet.")
                            }
                        },
                    ) {
                        Icon(
                            Icons.Default.Watch,
                            contentDescription = "Download map area on watch",
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Download map area on watch",
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                    }
                }
            }
        }

        SendingFileOverlay(
            sendingMessage = viewModel.sendingFile
        )

        LoadingOverlay(
            isLoading = isGeneratingPalette,
            loadingText = "Generating Color Palette..."
        )
    }
}


// Data class to hold calculated chart bounds and data
private data class ChartData(
    val points: List<Pair<Float, Float>> = emptyList(), // (distance, altitude)
    val minAltitude: Float = 0f,
    val maxAltitude: Float = 0f,
    val totalDistance: Float = 0f
)

@OptIn(ExperimentalTextApi::class) // Still needed for TextMeasurer/drawText at the top level
@Composable
fun ElevationProfileChart(
    modifier: Modifier = Modifier,
    route: Route,
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

// Helper function to format distance (e.g., to km)
private fun formatDistance(distanceMeters: Float): String {
    return if (distanceMeters < 1000) {
        "${distanceMeters.roundToInt()} m"
    } else {
        "${"%.1f".format(distanceMeters / 1000f)} km"
    }
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
