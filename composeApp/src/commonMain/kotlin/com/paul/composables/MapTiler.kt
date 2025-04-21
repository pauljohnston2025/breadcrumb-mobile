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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.unit.dp
import com.paul.infrastructure.service.GeoPosition
import com.paul.infrastructure.service.TileId
import com.paul.infrastructure.service.TileInfo
import com.paul.infrastructure.service.geoToScreenPixel
import com.paul.infrastructure.service.latLonToTileXY
import com.paul.infrastructure.service.screenPixelToGeo
import com.paul.infrastructure.service.worldPixelToGeo
import com.paul.protocol.todevice.Route
import kotlinx.coroutines.*
import kotlin.math.roundToInt

@Composable
fun MapTilerComposable(
    modifier: Modifier = Modifier,
    initialCenter: GeoPosition = GeoPosition(51.5, -0.1), // Default: London
    initialZoom: Int = 10,
    minZoom: Int = 1,
    maxZoom: Int = 18,
    tileProvider: suspend (x: Int, y: Int, z: Int) -> ByteArray?,
    routeToDisplay: Route? = null,
    routeColor: Color = Color.Blue,
    routeStrokeWidth: Float = 5f
) {
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var mapCenterGeo by remember { mutableStateOf(initialCenter) }
    var zoomLevel by remember { mutableStateOf(initialZoom.coerceIn(minZoom, maxZoom)) }

    // Simple in-memory cache for tiles
    val tileCache = remember { mutableMapOf<TileId, ByteArray?>() }
    // State to track tiles currently being loaded
    val loadingTiles = remember { mutableStateMapOf<TileId, Boolean>() }

    val coroutineScope = rememberCoroutineScope()

    // Derived state to calculate visible tiles based on current state
    val visibleTiles by remember {
        derivedStateOf {
            if (viewportSize == IntSize.Zero) emptyList() else {
                calculateVisibleTiles(mapCenterGeo, zoomLevel, viewportSize)
            }
        }
    }

    // Effect to launch tile fetching when visible tiles change
    LaunchedEffect(visibleTiles) {
        visibleTiles.forEach { tileInfo ->
            val tileId = tileInfo.id
            if (!tileCache.containsKey(tileId) && !loadingTiles.containsKey(tileId)) {
                loadingTiles[tileId] = true // Mark as loading
                launch(Dispatchers.IO) { // Use IO dispatcher for fetching
                    try {
                        val data = tileProvider(tileId.x, tileId.y, tileId.z)
                        // Conversion happens implicitly via rememberByteArrayToImageBitmap
                        // Store null if fetch failed but don't retry immediately
                        tileCache[tileId] = data  // Store raw data (bitmap created later)
                    } catch (e: Exception) {
                        println("Error fetching tile $tileId: ${e.message}")
                        tileCache[tileId] = null // Mark as failed (won't retry this session)
                    } finally {
                        loadingTiles.remove(tileId) // Mark as not loading anymore
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { viewportSize = it }
            .background(Color.LightGray) // Background for empty areas
            .pointerInput(Unit) { // Handle Panning
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    // Convert drag pixels to Geo coordinate shift
                    val currentCenterScreen = IntOffset(viewportSize.width / 2, viewportSize.height / 2)
                    val draggedToScreen = currentCenterScreen - IntOffset(dragAmount.x.roundToInt(), dragAmount.y.roundToInt())

                    mapCenterGeo = screenPixelToGeo(
                        screenPixel = draggedToScreen,
                        mapCenterGeo = mapCenterGeo, // Use current center as reference
                        zoom = zoomLevel,
                        viewportSize = viewportSize
                    )
                }
            }
        // Optional: Add detectTapGestures for double-tap zoom
        // Optional: Add detectTransformGestures for pinch-zoom (more complex)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            clipRect { // Prevent drawing tiles/routes outside the canvas bounds

                // --- Draw Tiles ---
                visibleTiles.forEach { tileInfo ->
                    val tileData = tileCache[tileInfo.id]
                    // Remember the bitmap conversion here
                    val imageBitmap = byteArrayToImageBitmap(data = tileData)

                    if (imageBitmap != null) {
                        drawImage(
                            image = imageBitmap,
                            dstOffset = tileInfo.screenOffset,
                            dstSize = tileInfo.size
                        )
                    } else if (loadingTiles.containsKey(tileInfo.id)) {
                        // Optional: Draw a loading indicator placeholder
                        drawRect(
                            color = Color.DarkGray.copy(alpha = 0.5f),
                            topLeft = Offset(tileInfo.screenOffset.x.toFloat(), tileInfo.screenOffset.y.toFloat()),
                            size = androidx.compose.ui.geometry.Size(tileInfo.size.width.toFloat(), tileInfo.size.height.toFloat())
                            // You could draw a small spinner graphic here too
                        )
                    } else {
                        // Optional: Draw an error placeholder if fetch failed (tileCache[id] is null but not loading)
                        drawRect(
                            color = Color.Red.copy(alpha = 0.2f),
                            topLeft = Offset(tileInfo.screenOffset.x.toFloat(), tileInfo.screenOffset.y.toFloat()),
                            size = androidx.compose.ui.geometry.Size(tileInfo.size.width.toFloat(), tileInfo.size.height.toFloat())
                        )
                    }
                }

                // --- Draw Route ---
                routeToDisplay?.let { route ->
                    if (route.route.size >= 2) {
                        val path = Path()
                        val startPoint = geoToScreenPixel(
                            geo = GeoPosition(route.route.first().latitude.toDouble(), route.route.first().longitude.toDouble()),
                            mapCenterGeo = mapCenterGeo,
                            zoom = zoomLevel,
                            viewportSize = viewportSize
                        )
                        path.moveTo(startPoint.x.toFloat(), startPoint.y.toFloat())

                        route.route.drop(1).forEach { point ->
                            val screenPoint = geoToScreenPixel(
                                geo = GeoPosition(point.latitude.toDouble(), point.longitude.toDouble()),
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
            } // End clipRect
        }

        // --- Simple Zoom Buttons ---
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { zoomLevel = (zoomLevel + 1).coerceIn(minZoom, maxZoom) }, enabled = zoomLevel < maxZoom) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }
            Button(onClick = { zoomLevel = (zoomLevel - 1).coerceIn(minZoom, maxZoom) }, enabled = zoomLevel > minZoom) {
                Icon(Icons.Default.Close, contentDescription = "Zoom Out")
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
    val bottomRightGeo = screenPixelToGeo(IntOffset(viewportSize.width, viewportSize.height), mapCenterGeo, zoom, viewportSize)

    // Convert corner geo coordinates to tile indices
    val (minTileX, minTileY) = latLonToTileXY(topLeftGeo.latitude, topLeftGeo.longitude, zoom)
    val (maxTileX, maxTileY) = latLonToTileXY(bottomRightGeo.latitude, bottomRightGeo.longitude, zoom)

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
            val tileTopLeftGeo = worldPixelToGeo(x.toDouble() / n, y.toDouble() / n) // Approx geo for tile corner
            val screenOffset = geoToScreenPixel(tileTopLeftGeo, mapCenterGeo, zoom, viewportSize)

            tiles.add(TileInfo(id = tileId, screenOffset = screenOffset))
        }
    }
    return tiles
}