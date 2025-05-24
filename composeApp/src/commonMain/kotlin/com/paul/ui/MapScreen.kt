package com.paul.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.MyLocation
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.paul.composables.MapTilerComposable
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

@Composable
fun MapScreen(viewModel: MapViewModel, navController: NavController) {
    BackHandler {
        if (viewModel.sendingFile.value != "") {
            // prevent back handler when we are trying to do things, todo cancel the job we are trying to do
            return@BackHandler
        }

        navController.popBackStack()
    }

    val currentRoute by viewModel.currentRoute.collectAsState()
    val isSeeding by viewModel.isSeeding.collectAsState()
    val seedingProgress by viewModel.seedingProgress.collectAsState()
    val zSeedingProgress by viewModel.zSeedingProgress.collectAsState()
    val seedingError by viewModel.seedingError.collectAsState()
    // Collect the new state for profile visibility
    val isElevationProfileVisible by viewModel.isElevationProfileVisible.collectAsState()
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val watchSendStarted by viewModel.watchSendStarted.collectAsState()

    // Box allows stacking the sending overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        watchSendStarted?.let {
            WatchSendDialog(
                onConfirm = { viewModel.confirmWatchLocationLoad() },
                onDismiss = { viewModel.cancelWatchLocationLoad() }
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {

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
                    routeToDisplay = currentRoute
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    if (currentRoute != null) {
                        Button(onClick = { viewModel.clearRoute() }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear route",
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                        }
                        Button(
                            onClick = { viewModel.toggleElevationProfileVisibility() }
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
                    Button(
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

                    Button(
                        onClick = {
                                viewModel.returnToUsersLocation()
                        },
                        enabled = true
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = "Return To Users Location",
                            )
                        }
                    }
                }
            } // --- End of Map Box ---


            // --- Seeding Controls / Status ---
            // (Keep this section as is)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                if (isSeeding) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Downloading,
                            contentDescription = "Caching map area",
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Text("Caching map area (zlayer: $zSeedingProgress)...") // Updated text slightly
                    }
                    LinearProgressIndicator(
                        progress = seedingProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                } else {
                    // Add Button or UI to trigger seeding
                    // --- Cache Current Area Button ---
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                // Get bounds and zoom from user input or map viewport
                                // Example fixed values:
                                if (viewportSize != IntSize.Zero) {
                                    // 1. Get current map state from ViewModel
                                    val centerPoint = viewModel.mapCenter.value
                                    val centerGeo = GeoPosition(
                                        centerPoint.latitude.toDouble(),
                                        centerPoint.longitude.toDouble()
                                    )
                                    val currentZoom = viewModel.mapZoom.value

                                    // 2. Calculate viewport corners' geographic coordinates
                                    val topLeftGeo =
                                        screenPixelToGeo(
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

                                    // 3. Determine zoom range (e.g., current to current + 2, or up to max)
                                    //    Make sure minZoom is at least the current zoom level
                                    val tilServer =
                                        viewModel.tileServerRepository.currentServerFlow().value
                                    val minSeedZoom = tilServer.tileLayerMin
                                    val maxSeedZoom = tilServer.tileLayerMax

                                    // 4. Call ViewModel function
                                    viewModel.startSeedingArea(
                                        minLat = bottomRightGeo.latitude.toFloat(), // Min lat is bottom
                                        maxLat = topLeftGeo.latitude.toFloat(),     // Max lat is top
                                        minLon = topLeftGeo.longitude.toFloat(),    // Min lon is left
                                        maxLon = bottomRightGeo.longitude.toFloat(), // Max lon is right
                                        minZoom = minSeedZoom,
                                        maxZoom = maxSeedZoom
                                    )
                                } else {
                                    // Optional: Show a message if size is not ready
                                    // scope.launch { snackbarHostState.showSnackbar("Map not ready yet") }
                                    Napier.d("Viewport size not available yet.")
                                }
                            },
//                        modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Download map area",
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Text("Download Current Map Area")

                        }

                        Button(
                            onClick = {
                                // Get bounds and zoom from user input or map viewport
                                // Example fixed values:
                                if (viewportSize != IntSize.Zero) {
                                    // 1. Get current map state from ViewModel
                                    val centerPoint = viewModel.mapCenter.value
                                    val centerGeo = GeoPosition(
                                        centerPoint.latitude.toDouble(),
                                        centerPoint.longitude.toDouble()
                                    )
                                    val currentZoom = viewModel.mapZoom.value

                                    // 2. Calculate viewport corners' geographic coordinates
                                    val topLeftGeo =
                                        screenPixelToGeo(
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
                                    // Optional: Show a message if size is not ready
                                    // scope.launch { snackbarHostState.showSnackbar("Map not ready yet") }
                                    Napier.d("Viewport size not available yet.")
                                }
                            },
//                        modifier = Modifier.align(Alignment.CenterHorizontally)
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

        }

        SendingFileOverlay(
            sendingMessage = viewModel.sendingFile
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
