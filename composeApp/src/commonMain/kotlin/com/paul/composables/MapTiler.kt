package com.paul.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Watch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds // <-- IMPORT THIS FOR CLIPPING
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.paul.infrastructure.service.GeoPosition
import com.paul.infrastructure.service.TileId
import com.paul.infrastructure.service.TileInfo
import com.paul.infrastructure.service.calculateNewCenter
import com.paul.infrastructure.service.geoToScreenPixel
import com.paul.infrastructure.service.latLonToTileXY
import com.paul.infrastructure.service.screenPixelToGeo
import com.paul.infrastructure.service.worldPixelToGeo
import com.paul.protocol.todevice.Point
import com.paul.protocol.todevice.Route
import com.paul.viewmodels.MapViewModel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

// Constant to define how many extra zoom levels to allow visually past the last tile layer
private const val OVERZOOM_LEVELS = 3f

@Composable
fun MapTilerComposable(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel,
    viewportSize: IntSize,
    onViewportSizeChange: (IntSize) -> Unit,
    routeToDisplay: Route? = null,
    routeColor: Color = Color.Blue,
    routeStrokeWidth: Float = 5f,
    fitToBoundsPaddingPercent: Float = 0.1f
) {
    // --- State from ViewModel ---
    val vmMapCenter by viewModel.mapCenter.collectAsState()
    val vmZoom by viewModel.mapZoom.collectAsState()
    val tilServer by viewModel.tileServerRepository.currentServerFlow().collectAsState()
    val tileCache by viewModel.tileCacheState.collectAsState()

    // --- Local State for Gestures ---
    var localCenterGeo by remember { mutableStateOf(GeoPosition(vmMapCenter.latitude.toDouble(), vmMapCenter.longitude.toDouble())) }
    var localZoom by remember { mutableStateOf(vmZoom) }

    // --- Derived State ---
    val minZoom = remember { tilServer.tileLayerMin.toFloat() }
    val maxZoom = remember { tilServer.tileLayerMax.toFloat() }

    // *** CHANGE 1: OVERZOOM LOGIC ***
    // The integerZoom is used for fetching tiles. It MUST be clamped to the actual tile layers available.
    val integerZoom = remember(localZoom, minZoom, maxZoom) {
        localZoom.roundToInt().coerceIn(minZoom.toInt(), maxZoom.toInt())
    }

    // --- Effects to sync local state if ViewModel is updated externally ---
    LaunchedEffect(vmMapCenter) {
        localCenterGeo = GeoPosition(vmMapCenter.latitude.toDouble(), vmMapCenter.longitude.toDouble())
    }
    LaunchedEffect(vmZoom) {
        localZoom = vmZoom
    }

    // --- Tile Calculation & Fetching ---
    val visibleTiles by remember(localCenterGeo, integerZoom, viewportSize) {
        derivedStateOf {
            if (viewportSize == IntSize.Zero) emptyList() else {
                calculateVisibleTiles(localCenterGeo, integerZoom, viewportSize, tilServer.id)
            }
        }
    }

    LaunchedEffect(visibleTiles) {
        viewModel.requestTilesForViewport(visibleTiles.map { it.id }.toSet())
    }

    // --- Fit to Route Effect ---
    LaunchedEffect(routeToDisplay, viewportSize) {
        // This effect logic remains the same
        if (routeToDisplay != null && routeToDisplay.route.isNotEmpty() && viewportSize != IntSize.Zero) {
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

            if (minLat == maxLat && minLon == maxLon) {
                viewModel.setMapZoom((vmZoom + 1).coerceIn(minZoom, maxZoom + OVERZOOM_LEVELS))
                return@LaunchedEffect
            }

            val targetCenterGeo = GeoPosition(minLat + (maxLat - minLat) / 2.0, minLon + (maxLon - minLon) / 2.0)
            var targetZoom = maxZoom
            while (targetZoom > minZoom) {
                val topLeftScreen = geoToScreenPixel(GeoPosition(maxLat, minLon), targetCenterGeo, targetZoom, viewportSize)
                val bottomRightScreen = geoToScreenPixel(GeoPosition(minLat, maxLon), targetCenterGeo, targetZoom, viewportSize)
                val routePixelWidth = abs(bottomRightScreen.x - topLeftScreen.x)
                val routePixelHeight = abs(bottomRightScreen.y - topLeftScreen.y)
                val paddedViewportWidth = viewportSize.width * (1f - fitToBoundsPaddingPercent * 2)
                val paddedViewportHeight = viewportSize.height * (1f - fitToBoundsPaddingPercent * 2)

                if (routePixelWidth <= paddedViewportWidth && routePixelHeight <= paddedViewportHeight) break
                targetZoom -= 0.1f
            }

            launch(Dispatchers.Main.immediate) {
                viewModel.centerMapOn(Point(targetCenterGeo.latitude.toFloat(), targetCenterGeo.longitude.toFloat(), 0f))
                viewModel.setMapZoom(targetZoom.coerceIn(minZoom, maxZoom + OVERZOOM_LEVELS))
            }
        }
    }

    Box(
        modifier = modifier
            .onSizeChanged { onViewportSizeChange(it) }
            .background(Color.LightGray)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.pressed }) {
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()
                            val centroid = event.calculateCentroid()

                            // *** CHANGE 1: OVERZOOM LOGIC ***
                            // The visual zoom can now go past the max tile layer up to the OVERZOOM_LEVELS limit.
                            val newZoom = (localZoom + (ln(zoom) / ln(2.0f))).coerceIn(minZoom, maxZoom + OVERZOOM_LEVELS)

                            val pannedCenterScreenPixel = Offset(size.width / 2f, size.height / 2f) - pan
                            val pannedCenterGeo = screenPixelToGeo(
                                screenPixel = IntOffset(pannedCenterScreenPixel.x.roundToInt(), pannedCenterScreenPixel.y.roundToInt()),
                                mapCenterGeo = localCenterGeo,
                                zoom = localZoom,
                                viewportSize = size
                            )
                            val geoUnderCentroid = screenPixelToGeo(
                                screenPixel = IntOffset(centroid.x.roundToInt(), centroid.y.roundToInt()),
                                mapCenterGeo = pannedCenterGeo,
                                zoom = localZoom,
                                viewportSize = size
                            )
                            val finalNewCenterGeo = calculateNewCenter(
                                targetGeo = geoUnderCentroid,
                                targetScreenPx = centroid,
                                newZoom = newZoom,
                                viewportSize = size
                            )

                            localZoom = newZoom
                            localCenterGeo = finalNewCenterGeo
                        }
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })

                    viewModel.setMapZoom(localZoom)
                    viewModel.centerMapOn(Point(localCenterGeo.latitude.toFloat(), localCenterGeo.longitude.toFloat(), 0f))
                }
            }
    ) {
        // *** CHANGE 2: RESPECT CONTAINER BOUNDARIES ***
        // The clipToBounds() modifier ensures that the canvas content (tiles, route)
        // will not be drawn outside of its specified layout area.
        Canvas(modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
        ) {
            val scale = 2.0.pow((localZoom - integerZoom).toDouble()).toFloat()

            withTransform({
                scale(scale, scale, pivot = Offset(size.width / 2f, size.height / 2f))
            }) {
                // --- Draw Tiles ---
                visibleTiles.forEach { tileInfo ->
                    tileCache[tileInfo.id]?.let { imageBitmap ->
                        drawImage(image = imageBitmap, dstOffset = tileInfo.screenOffset, dstSize = tileInfo.size)
                    } ?: run {
                        drawRect(
                            color = Color.DarkGray.copy(alpha = 0.5f),
                            topLeft = Offset(tileInfo.screenOffset.x.toFloat(), tileInfo.screenOffset.y.toFloat()),
                            size = androidx.compose.ui.geometry.Size(tileInfo.size.width.toFloat(), tileInfo.size.height.toFloat())
                        )
                    }
                }

                // --- Draw Route ---
                routeToDisplay?.let { route ->
                    if (route.route.size >= 2) {
                        val path = Path()
                        val startPoint = geoToScreenPixel(GeoPosition(route.route.first().latitude.toDouble(), route.route.first().longitude.toDouble()), localCenterGeo, integerZoom.toFloat(), viewportSize)
                        path.moveTo(startPoint.x.toFloat(), startPoint.y.toFloat())
                        route.route.drop(1).forEach { point ->
                            val screenPoint = geoToScreenPixel(GeoPosition(point.latitude.toDouble(), point.longitude.toDouble()), localCenterGeo, integerZoom.toFloat(), viewportSize)
                            path.lineTo(screenPoint.x.toFloat(), screenPoint.y.toFloat())
                        }
                        drawPath(path = path, color = routeColor, style = Stroke(width = routeStrokeWidth / scale, cap = StrokeCap.Round))
                    }
                }
            }
        }

        // --- UI Controls ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        ) {
            // ... (The buttons section is updated to respect the new overzoom limit)
            Button(
                onClick = {
                    if (viewportSize != IntSize.Zero) {
                        val topLeftGeo = screenPixelToGeo(IntOffset(0, 0), localCenterGeo, localZoom, viewportSize)
                        val bottomRightGeo = screenPixelToGeo(IntOffset(viewportSize.width, viewportSize.height), localCenterGeo, localZoom, viewportSize)
                        viewModel.showLocationOnWatch(centerGeo = localCenterGeo, topLeftGeo = topLeftGeo, bottomRightGeo = bottomRightGeo)
                    } else {
                        Napier.d("Viewport size not available yet.")
                    }
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy((-2).dp)) {
                    Icon(Icons.Default.Watch, contentDescription = "Show on watch", modifier = Modifier.size(17.dp))
                    Icon(Icons.Default.RemoveRedEye, contentDescription = "Show on watch", modifier = Modifier.size(17.dp))
                }
            }

            Button(
                onClick = {
                    // *** CHANGE 1: OVERZOOM LOGIC ***
                    val newZoom = (localZoom + 1f).coerceIn(minZoom, maxZoom + OVERZOOM_LEVELS)
                    localZoom = newZoom
                    viewModel.setMapZoom(newZoom)
                },
                enabled = localZoom < maxZoom + OVERZOOM_LEVELS
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }
            Button(
                onClick = {
                    // The minimum zoom doesn't change
                    val newZoom = (localZoom - 1f).coerceIn(minZoom, maxZoom + OVERZOOM_LEVELS)
                    localZoom = newZoom
                    viewModel.setMapZoom(newZoom)
                },
                enabled = localZoom > minZoom
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }
        }
    }
}

// This private helper function remains unchanged.
private fun calculateVisibleTiles(
    mapCenterGeo: GeoPosition,
    zoom: Int,
    viewportSize: IntSize,
    serverId: String
): List<TileInfo> {
    if (viewportSize == IntSize.Zero) return emptyList()
    val zoomF = zoom.toFloat()
    val tiles = mutableListOf<TileInfo>()
    val topLeftGeo = screenPixelToGeo(IntOffset(0, 0), mapCenterGeo, zoomF, viewportSize)
    val bottomRightGeo = screenPixelToGeo(IntOffset(viewportSize.width, viewportSize.height), mapCenterGeo, zoomF, viewportSize)
    val (minTileX, minTileY) = latLonToTileXY(topLeftGeo.latitude, topLeftGeo.longitude, zoom)
    val (maxTileX, maxTileY) = latLonToTileXY(bottomRightGeo.latitude, bottomRightGeo.longitude, zoom)
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
            val screenOffset = geoToScreenPixel(tileTopLeftGeo, mapCenterGeo, zoomF, viewportSize)
            tiles.add(TileInfo(id = tileId, screenOffset = screenOffset))
        }
    }
    return tiles
}