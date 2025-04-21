package com.paul.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.paul.infrastructure.service.GeoPosition
import com.paul.infrastructure.service.TileId
import com.paul.infrastructure.service.TileInfo
import com.paul.infrastructure.service.geoToScreenPixel
import com.paul.infrastructure.service.latLonToTileXY
import com.paul.infrastructure.service.screenPixelToGeo
import com.paul.infrastructure.service.worldPixelToGeo
import com.paul.protocol.todevice.Route
import com.paul.viewmodels.MapViewModel
import kotlinx.coroutines.*
import kotlin.math.roundToInt

@Composable
fun MapTilerComposable(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel, // Inject or get ViewModel instance
    minZoom: Int = 1,
    maxZoom: Int = 18,
    onMapCenterChange: (GeoPosition) -> Unit, // Callback to update ViewModel state
    onZoomChange: (Int) -> Unit,             // Callback to update ViewModel state
    routeToDisplay: Route? = null,
    routeColor: Color = Color.Blue,
    routeStrokeWidth: Float = 5f
) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    // Read cache and loading state from ViewModel
    val tileCache by viewModel.tileCacheState.collectAsState()
    // val loadingTiles by viewModel.loadingTilesState.collectAsState() // If exposing loading state

    // Calculate visible tiles based on props passed from VM (or local state if managing here)
    val mapCenter by viewModel.mapCenter.collectAsState()
    val mapCenterGeo = GeoPosition(mapCenter.latitude.toDouble(), mapCenter.longitude.toDouble())
    val zoomLevel by viewModel.mapZoom.collectAsState()
    val visibleTiles by remember(mapCenterGeo, zoomLevel, viewportSize) { // Key calculation
        derivedStateOf {
            if (viewportSize == IntSize.Zero) emptyList() else {
                calculateVisibleTiles(mapCenterGeo, zoomLevel, viewportSize)
            }
        }
    }

    // Effect to *request* tiles from ViewModel
    LaunchedEffect(visibleTiles) {
        val visibleIds = visibleTiles.map { it.id }.toSet()
        viewModel.requestTilesForViewport(visibleIds)
    }

    Box(
        modifier = modifier
            .onSizeChanged { viewportSize = it }
            .background(Color.LightGray)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val currentCenterScreen =
                        IntOffset(viewportSize.width / 2, viewportSize.height / 2)
                    val draggedToScreen = currentCenterScreen - IntOffset(
                        dragAmount.x.roundToInt(),
                        dragAmount.y.roundToInt()
                    )
                    val newCenter =
                        screenPixelToGeo(draggedToScreen, mapCenterGeo, zoomLevel, viewportSize)
                    onMapCenterChange(newCenter) // Update ViewModel's center state
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            clipRect {
                // --- Draw Tiles ---
                visibleTiles.forEach { tileInfo ->
                    val tileId = tileInfo.id
                    val imageBitmap = tileCache[tileId] // Read from VM's cache state

                    if (tileCache.containsKey(tileId)) {
                        if (imageBitmap != null) {
                            drawImage(
                                image = imageBitmap,
                                dstOffset = tileInfo.screenOffset,
                                dstSize = tileInfo.size
                            )
                        } else { /* Draw Error */
                            drawRect(
                                color = Color.DarkGray.copy(alpha = 0.5f),
                                topLeft = Offset(tileInfo.screenOffset.x.toFloat(), tileInfo.screenOffset.y.toFloat()),
                                size = androidx.compose.ui.geometry.Size(tileInfo.size.width.toFloat(), tileInfo.size.height.toFloat())
                                // You could draw a small spinner graphic here too
                            )
                        }
                    } else { // Check internal loading state if needed, or just show background
                        // If exposing loading state from VM:
                        // if (loadingTiles.contains(tileId)) { /* Draw Loading */ }
                        // Otherwise, just let background show through
                    }
                }
                // --- Draw Route ---
                routeToDisplay?.let { route ->
                    if (route.route.size >= 2) {
                        val path = Path()
                        val startPoint = geoToScreenPixel(
                            geo = GeoPosition(
                                route.route.first().latitude.toDouble(),
                                route.route.first().longitude.toDouble()
                            ),
                            mapCenterGeo = mapCenterGeo,
                            zoom = zoomLevel,
                            viewportSize = viewportSize
                        )
                        path.moveTo(startPoint.x.toFloat(), startPoint.y.toFloat())
                        route.route.drop(1).forEach { point ->
                            val screenPoint = geoToScreenPixel(
                                geo = GeoPosition(
                                    point.latitude.toDouble(),
                                    point.longitude.toDouble()
                                ),
                                mapCenterGeo = mapCenterGeo,
                                zoom = zoomLevel,
                                viewportSize = viewportSize
                            )
                            path.lineTo(screenPoint.x.toFloat(), screenPoint.y.toFloat())
                        }
                        drawPath(
                            path = path,
                            color = routeColor,
                            style = Stroke(width = routeStrokeWidth)
                        )
                    }
                }
            }
        }

        // --- Simple Zoom Buttons ---
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onZoomChange((zoomLevel + 1).coerceIn(minZoom, maxZoom)) },
                enabled = zoomLevel < maxZoom
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Zoom In")
            }
            Button(
                onClick = { onZoomChange((zoomLevel - 1).coerceIn(minZoom, maxZoom)) },
                enabled = zoomLevel > minZoom
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Zoom Out")
            }
        }
    }
}


// Helper function to calculate which tiles are visible
private fun calculateVisibleTiles(
    mapCenterGeo: GeoPosition,
    zoom: Int,
    viewportSize: IntSize
): List<TileInfo> {
    if (viewportSize == IntSize.Zero) return emptyList()

    val tiles = mutableListOf<TileInfo>()

    // Get the geo coordinates of the corners of the viewport
    val topLeftGeo = screenPixelToGeo(IntOffset(0, 0), mapCenterGeo, zoom, viewportSize)
    val bottomRightGeo = screenPixelToGeo(
        IntOffset(viewportSize.width, viewportSize.height),
        mapCenterGeo,
        zoom,
        viewportSize
    )

    // Convert corner geo coordinates to tile indices
    val (minTileX, minTileY) = latLonToTileXY(topLeftGeo.latitude, topLeftGeo.longitude, zoom)
    val (maxTileX, maxTileY) = latLonToTileXY(
        bottomRightGeo.latitude,
        bottomRightGeo.longitude,
        zoom
    )

    // Expand slightly to cover edges fully, clamp to valid tile range
    val n = 1 shl zoom
    val buffer = 1 // Load 1 extra tile around the edges
    val startX = (minTileX - buffer).coerceAtLeast(0)
    val startY = (minTileY - buffer).coerceAtLeast(0)
    val endX = (maxTileX + buffer).coerceAtMost(n - 1)
    val endY = (maxTileY + buffer).coerceAtMost(n - 1)

    for (x in startX..endX) {
        for (y in startY..endY) {
            val tileId = TileId(x, y, zoom)
            // Calculate where this tile's top-left corner should be on the screen
            val tileTopLeftGeo =
                worldPixelToGeo(x.toDouble() / n, y.toDouble() / n) // Approx geo for tile corner
            val screenOffset = geoToScreenPixel(tileTopLeftGeo, mapCenterGeo, zoom, viewportSize)

            tiles.add(TileInfo(id = tileId, screenOffset = screenOffset))
        }
    }
    return tiles
}