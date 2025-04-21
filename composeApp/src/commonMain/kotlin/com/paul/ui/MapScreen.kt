package com.paul.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons // Import icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.paul.protocol.todevice.Point // Make sure Point is imported if needed by chart
import com.paul.protocol.todevice.Route
import com.paul.viewmodels.MapViewModel
import kotlin.math.* // For Haversine

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
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // *** Placeholder for the actual Map Library Composable ***
            MapLibraryComposable(
                modifier = Modifier.fillMaxSize(),
                tileProvider = { x, y, z -> viewModel.provideTileData(x, y, z) },
                routeToDisplay = currentRoute,
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
                            contentDescription = if (isElevationProfileVisible) "Hide Profile" else "Show Profile",
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(if (isElevationProfileVisible) "Hide Profile" else "Show Profile")
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
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (isSeeding) { /* ... Seeding UI ... */ }
            else { /* ... Download Button ... */ }
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
                    .padding(horizontal=16.dp),
                route = currentRoute!! // Use non-null assertion or smart cast
            )
            Spacer(modifier = Modifier.height(8.dp)) // Padding at the bottom
        }
    }
}

// *** Placeholder - Replace with actual Map Library Composable ***
@Composable
fun MapLibraryComposable(
    modifier: Modifier = Modifier,
    tileProvider: suspend (x: Int, y: Int, z: Int) -> ByteArray?, // How tiles are fetched
    routeToDisplay: Route? // Route to draw
    // ... other state like viewport, callbacks ...
) {
    // This is where you integrate the chosen KMP map library (MapLibre, Moko, etc.)
    // The implementation heavily depends on the library.
    // - You'll configure its tile source based on tileServerInfo (maybe URL template or custom provider).
    // - You'll use the library's API to draw a polyline using routeToDisplay.points coordinates.
    // - You'll handle map interactions (zoom, pan).

    Box(modifier = modifier.background(Color.LightGray)) { // Simple placeholder
        Text(
            "Map View Placeholder",
            modifier = Modifier.align(Alignment.Center)
        )
        // Draw route placeholder (very basic)
        routeToDisplay?.let {
            // Use library's line drawing feature here
            Text("Drawing route: ${it.name}", Modifier.align(Alignment.BottomStart).padding(4.dp))
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

@Composable
fun ElevationProfileChart(
    modifier: Modifier = Modifier,
    route: Route,
    lineColor: Color = MaterialTheme.colors.primary, // Allow customizing color
    strokeWidth: Float = 4f, // Allow customizing line thickness
    horizontalPadding: Float = 16f, // Padding inside the canvas (in pixels)
    verticalPadding: Float = 16f
) {
    // Calculate distance-altitude pairs and bounds, memoizing based on route points
    val chartData: ChartData = remember(route.route) {
        processRouteForChart(route.route)
    }

    // Use Canvas for custom drawing
    Canvas(modifier = modifier.fillMaxSize()) {
        // Draw the chart if we have enough data
        if (chartData.points.size >= 2 && chartData.totalDistance > 0f) {
            drawElevationLine(
                drawScope = this, // Pass the drawing scope
                data = chartData,
                lineColor = lineColor,
                strokeWidth = strokeWidth,
                horizontalPadding = horizontalPadding,
                verticalPadding = verticalPadding
            )
        } else {
            // Optional: Draw a placeholder text if no data or not enough points
            // This part requires access to Text drawing on Canvas which is more complex.
            // For simplicity, keep the text outside or rely on the calling composable
            // to handle the "no data" case if needed. You could also draw simple
            // shapes or lines as a placeholder within the canvas.
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
            path.lineTo(x.coerceIn(horizontalPadding, size.width - horizontalPadding), // Clamp X
                y.coerceIn(verticalPadding, size.height - verticalPadding))     // Clamp Y
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
