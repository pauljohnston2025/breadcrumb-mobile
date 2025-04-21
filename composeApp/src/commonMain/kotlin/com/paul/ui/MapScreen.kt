import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.* // Or Material3
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.paul.protocol.todevice.Route
import com.paul.viewmodels.MapViewModel

@Composable
fun MapScreen(viewModel: MapViewModel) {
    val currentRoute by viewModel.currentRoute.collectAsState()
    val isSeeding by viewModel.isSeeding.collectAsState()
    val seedingProgress by viewModel.seedingProgress.collectAsState()
    val seedingError by viewModel.seedingError.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {

        // --- Map View Area ---
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // *** Placeholder for the actual Map Library Composable ***
            MapLibraryComposable(
                modifier = Modifier.fillMaxSize(),
                tileProvider = { x, y, z -> viewModel.provideTileData(x, y, z) }, // May not work this way
                routeToDisplay = currentRoute, // Pass the route data
                // Other parameters: initial center, zoom, gesture callbacks etc.
            )

            // Optional: Button to clear route display
            if (currentRoute != null) {
                Button(
                    onClick = { viewModel.clearRoute() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Text("Clear Route")
                }
            }
        }

        // --- Seeding Controls / Status ---
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (isSeeding) {
                Text("Downloading map area...")
                LinearProgressIndicator(
                    progress = seedingProgress,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            } else {
                // Add Button or UI to trigger seeding
                Button(onClick = {
                    // Get bounds and zoom from user input or map viewport
                    // Example fixed values:
                    viewModel.startSeedingArea(
                        minLat = 51.0f, maxLat = 52.0f, minLon = -1.0f, maxLon = 0.0f,
                        minZoom = 10, maxZoom = 14
                    )
                }) {
                    Text("Download Map Area")
                }
            }
            seedingError?.let {
                Text("Error: $it", color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
            }
        }

        // --- Elevation Profile Area ---
        currentRoute?.let { route ->
            Spacer(modifier = Modifier.height(8.dp))
            ElevationProfileChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp) // Example height
                    .padding(horizontal=16.dp),
                route = route
            )
            Spacer(modifier = Modifier.height(8.dp))
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


// *** Placeholder - Replace with actual Charting Library Composable ***
@Composable
fun ElevationProfileChart(
    modifier: Modifier = Modifier,
    route: Route
) {
    // Use a KMP charting library (e.g., Kandy, Plotly.kt wrappers, or custom drawing)
    // Calculate distance along the route for the X-axis.
    // Use point.altitude for the Y-axis.
    // Render the chart (e.g., Line chart).

    Box(modifier = modifier.border(1.dp, Color.Gray)) {
        Text(
            "Elevation Profile Placeholder\nPoints: ${route.route.size}",
            modifier = Modifier.align(Alignment.Center)
        )
    }
    // Implementation requires calculating cumulative distance between points.
    // You'll need a distance function (Haversine formula for lat/lon).
}