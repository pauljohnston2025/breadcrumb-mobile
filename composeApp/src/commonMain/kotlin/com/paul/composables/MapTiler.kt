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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paul.infrastructure.service.GeoPosition
import com.paul.infrastructure.service.TileId
import com.paul.infrastructure.service.TileInfo
import com.paul.infrastructure.service.UserLocation
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
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.material.icons.filled.Colorize
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    // Create and remember Paint objects for drawing the angle text
    val textPaint = remember {
        Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 30f // Pixel size, adjust as needed
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }
    val textBackgroundPaint = remember {
        Paint().apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }
    val textBackgroundStrokePaint = remember {
        Paint().apply {
            color = android.graphics.Color.DKGRAY
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
    }

    val vmMapCenter by viewModel.mapCenter.collectAsState()
    val vmZoom by viewModel.mapZoom.collectAsState()
    val userLocation by viewModel.userLocation.collectAsState()
    val tilServer by viewModel.tileServerRepository.currentServerFlow().collectAsState()
    val tileCache by viewModel.tileCacheState.collectAsState()

    var localCenterGeo by remember {
        mutableStateOf(
            GeoPosition(
                vmMapCenter.latitude.toDouble(),
                vmMapCenter.longitude.toDouble()
            )
        )
    }
    var localZoom by remember { mutableStateOf(vmZoom) }

    val minZoom = remember { tilServer.tileLayerMin.toFloat() }
    val maxZoom = remember { tilServer.tileLayerMax.toFloat() }
    val integerZoom = remember(localZoom, minZoom, maxZoom) {
        localZoom.roundToInt().coerceIn(minZoom.toInt(), maxZoom.toInt())
    }

    // *** THE FIX IS HERE (Part 1): Remember the route we have already centered on. ***
    var centeredRoute by remember { mutableStateOf<Route?>(null) }

    LaunchedEffect(vmMapCenter) {
        localCenterGeo =
            GeoPosition(vmMapCenter.latitude.toDouble(), vmMapCenter.longitude.toDouble())
    }
    LaunchedEffect(vmZoom) {
        localZoom = vmZoom
    }
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
        // *** THE FIX IS HERE (Part 2): Add a condition to only center on a NEW route. ***
        if (routeToDisplay != null && routeToDisplay != centeredRoute && viewportSize != IntSize.Zero) {
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

            val targetCenterGeo =
                GeoPosition(minLat + (maxLat - minLat) / 2.0, minLon + (maxLon - minLon) / 2.0)
            var targetZoom = maxZoom
            while (targetZoom > minZoom) {
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
                val paddedViewportWidth = viewportSize.width * (1f - fitToBoundsPaddingPercent * 2)
                val paddedViewportHeight =
                    viewportSize.height * (1f - fitToBoundsPaddingPercent * 2)
                if (routePixelWidth <= paddedViewportWidth && routePixelHeight <= paddedViewportHeight) break
                targetZoom -= 0.1f
            }

            launch(Dispatchers.Main.immediate) {
                viewModel.centerMapOn(
                    Point(
                        targetCenterGeo.latitude.toFloat(),
                        targetCenterGeo.longitude.toFloat(),
                        0f
                    )
                )
                viewModel.setMapZoom(targetZoom.coerceIn(minZoom, maxZoom + OVERZOOM_LEVELS))
            }
            // *** THE FIX IS HERE (Part 3): Once we have centered, record the route. ***
            centeredRoute = routeToDisplay

        } else if (routeToDisplay == null) {
            // If the route is cleared, reset our tracker so a new route can be centered later.
            centeredRoute = null
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
                            val newZoom = (localZoom + (ln(zoom) / ln(2.0f))).coerceIn(
                                minZoom,
                                maxZoom + OVERZOOM_LEVELS
                            )
                            val pannedCenterScreenPixel =
                                Offset(size.width / 2f, size.height / 2f) - pan
                            val pannedCenterGeo = screenPixelToGeo(
                                screenPixel = IntOffset(
                                    pannedCenterScreenPixel.x.roundToInt(),
                                    pannedCenterScreenPixel.y.roundToInt()
                                ),
                                mapCenterGeo = localCenterGeo, zoom = localZoom, viewportSize = size
                            )
                            val geoUnderCentroid = screenPixelToGeo(
                                screenPixel = IntOffset(
                                    centroid.x.roundToInt(),
                                    centroid.y.roundToInt()
                                ),
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
                    viewModel.centerMapOn(
                        Point(
                            localCenterGeo.latitude.toFloat(),
                            localCenterGeo.longitude.toFloat(),
                            0f
                        )
                    )
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            val scale = 2.0.pow((localZoom - integerZoom).toDouble()).toFloat()
            withTransform({
                scale(scale, scale, pivot = Offset(size.width / 2f, size.height / 2f))
            }) {
                visibleTiles.forEach { tileInfo ->
                    tileCache[tileInfo.id]?.let { imageBitmap ->
                        drawImage(
                            image = imageBitmap,
                            dstOffset = tileInfo.screenOffset,
                            dstSize = tileInfo.size
                        )
                    } ?: run {
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
                        )
                    }
                }
                routeToDisplay?.let { route ->
                    if (route.route.size >= 2) {
                        val path = Path()
                        val startPoint = geoToScreenPixel(
                            GeoPosition(
                                route.route.first().latitude.toDouble(),
                                route.route.first().longitude.toDouble()
                            ), localCenterGeo, integerZoom.toFloat(), viewportSize
                        )
                        path.moveTo(startPoint.x.toFloat(), startPoint.y.toFloat())
                        route.route.drop(1).forEach { point ->
                            val screenPoint = geoToScreenPixel(
                                GeoPosition(
                                    point.latitude.toDouble(),
                                    point.longitude.toDouble()
                                ), localCenterGeo, integerZoom.toFloat(), viewportSize
                            )
                            path.lineTo(screenPoint.x.toFloat(), screenPoint.y.toFloat())
                        }
                        drawPath(
                            path = path,
                            color = routeColor,
                            style = Stroke(width = routeStrokeWidth / scale, cap = StrokeCap.Round)
                        )

                        // ... inside the Canvas composable, after drawing the blue route path ...

                        routeToDisplay?.let { route ->
                            // Draw direction arrows and angle text for each turn
                            route.directions.forEach { direction ->
                                val turnIndex = direction.routeIndex
                                if (turnIndex > 0 && turnIndex < route.route.size) {
                                    val turnPointGeo = route.route[turnIndex]
                                    val prevPointGeo = route.route[turnIndex - 1]

                                    val turnScreenPoint = geoToScreenPixel(
                                        GeoPosition(turnPointGeo.latitude.toDouble(), turnPointGeo.longitude.toDouble()),
                                        localCenterGeo, integerZoom.toFloat(), viewportSize
                                    ).let { Offset(it.x.toFloat(), it.y.toFloat()) }

                                    val prevScreenPoint = geoToScreenPixel(
                                        GeoPosition(prevPointGeo.latitude.toDouble(), prevPointGeo.longitude.toDouble()),
                                        localCenterGeo, integerZoom.toFloat(), viewportSize
                                    ).let { Offset(it.x.toFloat(), it.y.toFloat()) }

                                    val dx = turnScreenPoint.x - prevScreenPoint.x
                                    val dy = turnScreenPoint.y - prevScreenPoint.y
                                    val incomingBearingOnScreen = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat()

                                    val finalAngleDeg = incomingBearingOnScreen + direction.angleDeg

                                    // --- 1. Draw the Arrow ---
                                    // The arrow is now defined with its BASE at (0,0) and pointing to the right.
                                    val arrowLength = 35f / scale
                                    val arrowPath = Path().apply {
                                        val headWidth = arrowLength / 2.5f
                                        val headLength = arrowLength / 2f

                                        // Main tail line of the arrow
                                        moveTo(0f, 0f)      // Base of the tail
                                        lineTo(arrowLength, 0f) // Tip of the arrow

                                        // The two lines that form the arrowhead
                                        moveTo(arrowLength - headLength, -headWidth)
                                        lineTo(arrowLength, 0f)
                                        lineTo(arrowLength - headLength, headWidth)
                                    }

                                    // The transformation logic is the same, but now it correctly positions the arrow's base.
                                    withTransform({
                                        translate(left = turnScreenPoint.x, top = turnScreenPoint.y)
                                        rotate(degrees = finalAngleDeg, pivot = Offset.Zero)
                                    }) {
                                        drawPath(
                                            path = arrowPath,
                                            color = Color.Red,
                                            style = Stroke(
                                                width = 7f / scale,
                                                cap = StrokeCap.Round
                                            )
                                        )
                                    }

                                    // --- 2. Draw the Angle Text ---
//                                    drawIntoCanvas { canvas ->
//                                        textPaint.textSize = 30f / scale
//                                        textBackgroundStrokePaint.strokeWidth = 2f / scale
//
//                                        val angleText = direction.angleDeg.roundToInt().toString() + "°"
//                                        val textBounds = Rect()
//                                        textPaint.getTextBounds(angleText, 0, angleText.length, textBounds)
//
//                                        // Position the text just off the tip of the arrow.
//                                        // The position is calculated from the base (turnScreenPoint) along the arrow's angle.
//                                        val textOffset = arrowLength + 10f // Place it slightly beyond the arrow's tip
//                                        val angleRad = Math.toRadians(finalAngleDeg.toDouble())
//                                        val textX = turnScreenPoint.x + textOffset * cos(angleRad).toFloat()
//                                        val textY = turnScreenPoint.y + textOffset * sin(angleRad).toFloat() + textBounds.height() / 2f
//
//                                        // Draw a background for readability
//                                        val padding = 8f
//                                        canvas.nativeCanvas.drawRoundRect(
//                                            textX - textBounds.width() / 2f - padding,
//                                            textY - textBounds.height() - padding,
//                                            textX + textBounds.width() / 2f + padding,
//                                            textY + padding,
//                                            10f, 10f, // corner radius
//                                            textBackgroundPaint
//                                        )
//                                        canvas.nativeCanvas.drawRoundRect(
//                                            textX - textBounds.width() / 2f - padding,
//                                            textY - textBounds.height() - padding,
//                                            textX + textBounds.width() / 2f + padding,
//                                            textY + padding,
//                                            10f, 10f, // corner radius
//                                            textBackgroundStrokePaint
//                                        )
//
//                                        // Draw the actual text
//                                        canvas.nativeCanvas.drawText(
//                                            angleText,
//                                            textX,
//                                            textY,
//                                            textPaint
//                                        )
//                                    }
                                }
                            }
                        }
                    }
                }
            }
            userLocation?.let { loc ->
                val screenPos = geoToScreenPixel(
                    geo = loc.position,
                    mapCenterGeo = localCenterGeo,
                    zoom = localZoom,
                    viewportSize = viewportSize
                )
                val circleRadius = 20f
                val arrowSize = 22f // Half the base width of the arrow
                val screenOffset = Offset(screenPos.x.toFloat(), screenPos.y.toFloat())
                loc.bearing?.let { nonNullBearing ->
                    // Convert bearing to radians. Subtract 90 degrees (PI/2) to align 0 degrees with the top.
                    val angleRad = Math.toRadians(nonNullBearing - 90.0).toFloat()

                    // Calculate the arrow's center position on the edge of the circle
                    // The arrow will be placed just outside the main circle.
                    val arrowDistanceFromCenter = circleRadius + arrowSize / 2f

                    val arrowCenterX = screenOffset.x + arrowDistanceFromCenter * cos(angleRad)
                    val arrowCenterY = screenOffset.y + arrowDistanceFromCenter * sin(angleRad)
                    val arrowCenter = Offset(arrowCenterX, arrowCenterY)

                    // Use withTransform to isolate translation and rotation for the arrow
                    withTransform({
                        // First, translate the canvas to the arrow's target position
                        translate(left = arrowCenter.x, top = arrowCenter.y)
                        // Then, rotate around this new origin (0,0) which is now the arrow's center
                        rotate(degrees = nonNullBearing, pivot = Offset.Zero)
                    }) {
                        // Define the arrow path relative to its own center (0,0)
                        val arrowPath = Path().apply {
                            val halfHeight =
                                arrowSize * 1.5f // Make the arrow taller than it is wide
                            moveTo(0f, -halfHeight / 2) // Tip of the arrow
                            lineTo(-arrowSize, halfHeight / 2) // Bottom left
                            lineTo(arrowSize, halfHeight / 2)  // Bottom right
                            close()
                        }
                        drawPath(path = arrowPath, color = Color(0xFFFF5500))
                    }
                }

                // Draw the central circle
                drawCircle(color = Color(0xFFFF5500), radius = circleRadius, center = screenOffset)
                drawCircle(
                    color = Color.White,
                    radius = circleRadius,
                    center = screenOffset,
                    style = Stroke(width = 3f)
                )
            }
        }
        ZoomLevelIndicator(
            zoom = localZoom,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        ) {
            Button(
                onClick = {
                    if (viewportSize != IntSize.Zero) {
                        viewModel.createPaletteFromViewport(
                            visibleTiles = visibleTiles,
                            tileCache = tileCache,
                            viewportSize = viewportSize
                        )
                    } else {
                        Napier.d("Viewport size not available yet.")
                    }
                }
            ) {
                Icon(Icons.Default.Colorize, contentDescription = "Create Palette from Map")
            }

            Button(
                onClick = {
                    if (viewportSize != IntSize.Zero) {
                        val topLeftGeo = screenPixelToGeo(
                            IntOffset(0, 0),
                            localCenterGeo,
                            localZoom,
                            viewportSize
                        )
                        val bottomRightGeo = screenPixelToGeo(
                            IntOffset(viewportSize.width, viewportSize.height),
                            localCenterGeo,
                            localZoom,
                            viewportSize
                        )
                        viewModel.showLocationOnWatch(
                            centerGeo = localCenterGeo,
                            topLeftGeo = topLeftGeo,
                            bottomRightGeo = bottomRightGeo
                        )
                    } else {
                        Napier.d("Viewport size not available yet.")
                    }
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((-2).dp)
                ) {
                    Icon(
                        Icons.Default.Watch,
                        contentDescription = "Show on watch",
                        modifier = Modifier.size(17.dp)
                    )
                    Icon(
                        Icons.Default.RemoveRedEye,
                        contentDescription = "Show on watch",
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            Button(
                onClick = {
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

@Composable
private fun ZoomLevelIndicator(
    zoom: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = "Zoom: %.1f".format(zoom), color = Color.White, fontSize = 12.sp)
    }
}

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
    val bottomRightGeo = screenPixelToGeo(
        IntOffset(viewportSize.width, viewportSize.height),
        mapCenterGeo,
        zoomF,
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
            val screenOffset =
                geoToScreenPixel(tileTopLeftGeo, mapCenterGeo, zoomF, viewportSize)
            tiles.add(TileInfo(id = tileId, screenOffset = screenOffset))
        }
    }
    return tiles
}