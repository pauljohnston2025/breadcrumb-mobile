package com.paul.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.composables.MapTilerComposable
import com.paul.infrastructure.service.GeoPosition
import com.paul.protocol.todevice.Point
import com.paul.protocol.todevice.Route
import com.paul.viewmodels.MapViewModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun MapScreen(viewModel: MapViewModel) {
    val currentRoute by viewModel.currentRoute.collectAsState()
    val isSeeding by viewModel.isSeeding.collectAsState()
    val seedingProgress by viewModel.seedingProgress.collectAsState()
    val seedingError by viewModel.seedingError.collectAsState()
    // Collect the new state for profile visibility
    val isElevationProfileVisible by viewModel.isElevationProfileVisible.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {

        // --- Map View Area ---
        Box(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()) {
            MapTilerComposable(
                modifier = Modifier.fillMaxSize(), // Or Modifier.weight(1f).fillMaxWidth() etc.
                initialCenter = GeoPosition( // Optional: Pass initial center from ViewModel
                    viewModel.mapCenter.collectAsState().value.latitude.toDouble(),
                    viewModel.mapCenter.collectAsState().value.longitude.toDouble()
                ),
                initialZoom = viewModel.mapZoom.collectAsState().value.roundToInt(), // Optional
                tileProvider = { x, y, z ->
                    // Call your ViewModel's function
                    viewModel.provideTileData(x, y, z)
                },
                routeToDisplay = currentRoute // Pass the route state
                // Customize routeColor, zoom levels etc. if needed
            )

            // --- Map Overlays / Buttons ---
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd) // Group buttons top-right
                    .padding(8.dp)
            ) {
                // Optional: Button to clear route display
                if (currentRoute != null) {
                    Button(onClick = { viewModel.clearRoute() }) {
                        Text("Clear Route")
                    }
                    Spacer(Modifier.height(8.dp)) // Space between buttons
                    // --- Toggle Elevation Profile Button ---
                    // Use OutlinedButton or IconButton for less emphasis than Clear maybe
                    OutlinedButton(
                        onClick = { viewModel.toggleElevationProfileVisibility() }
                    ) {
                        Icon(
                            Icons.Default.MoreVert, // Chart icon
                            contentDescription = if (isElevationProfileVisible) "Hide Elevation Profile" else "Show Elevation Profile",
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(if (isElevationProfileVisible) "Hide Elevation Profile" else "Show Elevation Profile")
                    }
                }
            }
            // You could also place the profile toggle button elsewhere, e.g., BottomStart:
            /*
            if (currentRoute != null) {
                Button(
                    onClick = { viewModel.toggleElevationProfileVisibility() },
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                ) {
                    Text(if (isElevationProfileVisible) "Hide Profile" else "Show Profile")
                }
            }
            */
        } // --- End of Map Box ---


        // --- Seeding Controls / Status ---
        // (Keep this section as is)
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (isSeeding) { /* ... Seeding UI ... */
            } else { /* ... Download Button ... */
            }
            seedingError?.let { /* ... Error text ... */ }
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
