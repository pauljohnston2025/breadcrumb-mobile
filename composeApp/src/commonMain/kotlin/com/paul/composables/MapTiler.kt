package com.paul.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.paul.infrastructure.service.GeoPosition
import com.paul.infrastructure.service.TileId
import com.paul.infrastructure.service.TileInfo
import com.paul.infrastructure.service.geoToScreenPixel
import com.paul.infrastructure.service.getScaleFactor
import com.paul.infrastructure.service.latLonToTileXY
import com.paul.infrastructure.service.screenPixelToGeo
import com.paul.infrastructure.service.worldPixelToGeo
import com.paul.protocol.todevice.Point
import com.paul.protocol.todevice.Route
import com.paul.viewmodels.MapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun MapTilerComposable(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel,
    viewportSize: IntSize,
    onViewportSizeChange: (IntSize) -> Unit,
    routeToDisplay: Route? = null,
    routeColor: Color = Color.Blue,
    routeStrokeWidth: Float = 5f,
    fitToBoundsPaddingPercent: Float = 0.1f // e.g., 10% padding around the route
) {
    // Read state from ViewModel
    val initialMapCenterPoint by viewModel.mapCenter.collectAsState()
    val mapCenterPoint by viewModel.mapCenter.collectAsState()
    val currentZoomLevel by viewModel.mapZoom.collectAsState()
    val tilServer by viewModel.tileServerRepository.currentServerFlow().collectAsState()
    val minZoom = tilServer.tileLayerMin
    val maxZoom = tilServer.tileLayerMax

    // --- LOCAL state for map center during interaction ---
    var localMapCenterGeo by remember {
        mutableStateOf(
            GeoPosition(
                initialMapCenterPoint.latitude.toDouble(),
                initialMapCenterPoint.longitude.toDouble()
            )
        )
    }

    // Effect to update LOCAL state if ViewModel state changes externally
    LaunchedEffect(initialMapCenterPoint) {
        val vmGeo = GeoPosition(
            initialMapCenterPoint.latitude.toDouble(),
            initialMapCenterPoint.longitude.toDouble()
        )
        // Basic reset. Consider adding a threshold check if needed.
        if (localMapCenterGeo != vmGeo) { // Avoid self-update from onDragEnd
            localMapCenterGeo = vmGeo
        }
    }
    val viewModelMapCenterGeo = remember(initialMapCenterPoint) {
        GeoPosition(
            initialMapCenterPoint.latitude.toDouble(),
            initialMapCenterPoint.longitude.toDouble()
        )
    }

    val tileCache by viewModel.tileCacheState.collectAsState()

    val visibleTiles by remember(viewModelMapCenterGeo, currentZoomLevel, viewportSize) {
        derivedStateOf {
            if (viewportSize == IntSize.Zero) emptyList() else {
                calculateVisibleTiles(
                    viewModelMapCenterGeo,
                    currentZoomLevel,
                    viewportSize,
                    viewModel.tileServerRepository.currentServerFlow().value.id
                )
            }
        }
    }

    // Effect to request tiles from ViewModel
    LaunchedEffect(visibleTiles) {
        val visibleIds = visibleTiles.map { it.id }.toSet()
        viewModel.requestTilesForViewport(visibleIds)
    }

    // === Effect to Zoom/Center on Route Change ===
    LaunchedEffect(routeToDisplay, viewportSize) {
        // Run calculation only when we have a route AND the viewport has been measured
        if (routeToDisplay != null && routeToDisplay.route.isNotEmpty() && viewportSize != IntSize.Zero) {
            // 1. Calculate Bounding Box
            var minLat = routeToDisplay.route.first().latitude.toDouble()
            var maxLat = minLat
            var minLon = routeToDisplay.route.first().longitude.toDouble()
            var maxLon = minLon

            routeToDisplay.route.drop(1).forEach { point ->
                minLat = min(minLat, point.latitude.toDouble())
                maxLat = max(maxLat, point.latitude.toDouble())
                minLon = min(minLon, point.longitude.toDouble())
                maxLon = max(maxLon, point.longitude.toDouble())
            }

            // Handle single point case (just use VM center, maybe zoom in?)
            if (minLat == maxLat && minLon == maxLon) {
                viewModel.setMapZoom(
                    (currentZoomLevel + 2).coerceIn(
                        minZoom,
                        maxZoom
                    )
                ) // Example: Zoom in 2 levels
                return@LaunchedEffect // Exit effect
            }

            // 2. Calculate Target Center (already done mostly in VM, but recalculate for precision)
            val targetCenterLat = minLat + (maxLat - minLat) / 2.0
            val targetCenterLon = minLon + (maxLon - minLon) / 2.0
            val targetCenterGeo = GeoPosition(targetCenterLat, targetCenterLon)

            // 3. Calculate Target Zoom
            var targetZoom = maxZoom // Start checking from max zoom level
            while (targetZoom > minZoom) {
                val scale = getScaleFactor(targetZoom)

                // Calculate pixel coordinates of bounds relative to the *target center*
                val topLeftScreen = geoToScreenPixel(
                    GeoPosition(maxLat, minLon),
                    targetCenterGeo,
                    targetZoom,
                    viewportSize
                )
                val bottomRightScreen = geoToScreenPixel(
                    GeoPosition(minLat, maxLon),
                    targetCenterGeo,
                    targetZoom,
                    viewportSize
                )

                val routePixelWidth = abs(bottomRightScreen.x - topLeftScreen.x)
                val routePixelHeight = abs(bottomRightScreen.y - topLeftScreen.y)

                // Check if it fits within the viewport with padding
                val paddedViewportWidth = viewportSize.width * (1f - fitToBoundsPaddingPercent * 2)
                val paddedViewportHeight =
                    viewportSize.height * (1f - fitToBoundsPaddingPercent * 2)

                if (routePixelWidth <= paddedViewportWidth && routePixelHeight <= paddedViewportHeight) {
                    // This zoom level fits, break the loop
                    break
                }
                targetZoom-- // Try the next lower zoom level
            }
            targetZoom = targetZoom.coerceIn(minZoom, maxZoom) // Ensure it's within bounds


            // 4. Apply the calculated Center and Zoom via ViewModel functions
            // Use Dispatchers.Main if state updates require it, though StateFlow should handle it
            launch(Dispatchers.Main.immediate) {
                viewModel.centerMapOn(
                    Point(
                        targetCenterGeo.latitude.toFloat(),
                        targetCenterGeo.longitude.toFloat(),
                        0f
                    )
                )
                viewModel.setMapZoom(targetZoom)
            }
        }
    } // === End of Zoom/Center Effect ===


    Box(
        modifier = modifier
            .onSizeChanged { onViewportSizeChange(it) }
            .background(Color.LightGray)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        // Update VM only on drag end
                        viewModel.centerMapOn(
                            Point(
                                localMapCenterGeo.latitude.toFloat(), // Use local state
                                localMapCenterGeo.longitude.toFloat(), // Use local state
                                0f
                            )
                        )
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // Update local state for immediate feedback (using previous local state approach)
                        val currentCenterScreen =
                            IntOffset(viewportSize.width / 2, viewportSize.height / 2)
                        val draggedToScreen = currentCenterScreen - IntOffset(
                            dragAmount.x.roundToInt(),
                            dragAmount.y.roundToInt()
                        )
                        val newCenter = screenPixelToGeo(
                            draggedToScreen,
                            localMapCenterGeo,
                            currentZoomLevel,
                            viewportSize
                        )
                        // --- Update LOCAL state directly ---
                        localMapCenterGeo = newCenter

                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            clipRect {
                // --- Draw Tiles ---
                visibleTiles.forEach { tileInfo ->
                    val tileId = tileInfo.id
                    val imageBitmap = tileCache[tileId]
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
                                topLeft = Offset(
                                    tileInfo.screenOffset.x.toFloat(),
                                    tileInfo.screenOffset.y.toFloat()
                                ),
                                size = androidx.compose.ui.geometry.Size(
                                    tileInfo.size.width.toFloat(),
                                    tileInfo.size.height.toFloat()
                                )
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
                        // Use viewModelMapCenterGeo and currentZoomLevel from VM state
                        val startPoint = geoToScreenPixel(
                            GeoPosition(
                                route.route.first().latitude.toDouble(),
                                route.route.first().longitude.toDouble()
                            ), viewModelMapCenterGeo, currentZoomLevel, viewportSize
                        )
                        path.moveTo(startPoint.x.toFloat(), startPoint.y.toFloat())
                        route.route.drop(1).forEach { point ->
                            val screenPoint = geoToScreenPixel(
                                GeoPosition(
                                    point.latitude.toDouble(),
                                    point.longitude.toDouble()
                                ), viewModelMapCenterGeo, currentZoomLevel, viewportSize
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
        Row( /* ... */) {
            Button(
                onClick = {
                    viewModel.setMapZoom(
                        (currentZoomLevel + 1).coerceIn(
                            minZoom,
                            maxZoom
                        )
                    )
                },
                enabled = currentZoomLevel < maxZoom
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Zoom In")
            }
            Button(
                onClick = {
                    viewModel.setMapZoom(
                        (currentZoomLevel - 1).coerceIn(
                            minZoom,
                            maxZoom
                        )
                    )
                },
                enabled = currentZoomLevel > minZoom
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Zoom Out")
            }
        }
    }
}

// Helper function calculateVisibleTiles needs slight adjustment if it uses the *local* screenOffset calculation
// It should probably just return List<TileId> and the drawing loop calculates offset.
// OR ensure it uses the VM state passed in. Let's assume it uses the passed state:
private fun calculateVisibleTiles(
    mapCenterGeo: GeoPosition, // Use state from VM
    zoom: Int,                 // Use state from VM
    viewportSize: IntSize,
    serverId: String
): List<TileInfo> { // Keep returning TileInfo for now
    // ... (implementation remains the same, calculates offset based on passed center/zoom) ...
    if (viewportSize == IntSize.Zero) return emptyList()
    val tiles = mutableListOf<TileInfo>()
    val topLeftGeo = screenPixelToGeo(IntOffset(0, 0), mapCenterGeo, zoom, viewportSize)
    val bottomRightGeo = screenPixelToGeo(
        IntOffset(viewportSize.width, viewportSize.height),
        mapCenterGeo,
        zoom,
        viewportSize
    )
    val (minTileX, minTileY) = latLonToTileXY(topLeftGeo.latitude, topLeftGeo.longitude, zoom)
    val (maxTileX, maxTileY) = latLonToTileXY(
        bottomRightGeo.latitude,
        bottomRightGeo.longitude,
        zoom
    )
    val n = 1 shl zoom
    val buffer = 1
    val startX = (minTileX - buffer).coerceAtLeast(0)
    val startY = (minTileY - buffer).coerceAtLeast(0)
    val endX = (maxTileX + buffer).coerceAtMost(n - 1)
    val endY = (maxTileY + buffer).coerceAtMost(n - 1)
    for (x in startX..endX) {
        for (y in startY..endY) {
            val tileId = TileId(x, y, zoom, serverId)
            val tileTopLeftGeo = worldPixelToGeo(x.toDouble() / n, y.toDouble() / n)
            // This screenOffset is calculated based on the *current* VM state
            val screenOffset = geoToScreenPixel(tileTopLeftGeo, mapCenterGeo, zoom, viewportSize)
            tiles.add(
                TileInfo(
                    id = tileId,
                    screenOffset = screenOffset
                )
            ) // Include offset here for drawing
        }
    }
    return tiles
}
