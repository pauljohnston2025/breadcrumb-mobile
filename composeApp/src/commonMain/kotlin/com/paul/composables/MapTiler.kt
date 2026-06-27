package com.paul.composables

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Route
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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import breadcrumb.composeapp.generated.resources.Res
import breadcrumb.composeapp.generated.resources.strava
import com.paul.domain.PaletteMappingMode
import com.paul.domain.SegmentInfo
import com.paul.domain.SegmentType
import com.paul.infrastructure.service.GeoPosition
import com.paul.infrastructure.service.TileId
import com.paul.infrastructure.service.TileInfo
import com.paul.infrastructure.service.calculateNewCenter
import com.paul.infrastructure.service.geoToScreenPixel
import com.paul.infrastructure.service.geoToWorldPixel
import com.paul.infrastructure.service.getScaleFactor
import com.paul.infrastructure.service.latLonToTileXY
import com.paul.infrastructure.service.screenPixelToGeo
import com.paul.infrastructure.service.worldPixelToGeo
import com.paul.protocol.todevice.Point
import com.paul.protocol.todevice.Route
import com.paul.viewmodels.MapViewModel
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val OVERZOOM_LEVELS = 3f
val mapButtonStyle = Modifier.size(width = 60.dp, height = 35.dp)

@Composable
fun MapTilerComposable(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel,
    viewportSize: IntSize,
    onViewportSizeChange: (IntSize) -> Unit,
    routeToDisplay: Route? = null,
    routeColor: Color = Color.Blue,
    routeStrokeWidth: Float = 8f,
    fitToBoundsPaddingPercent: Float = 0.1f,
    isWatchFeatureDisabled: Boolean,
    hoveredDistance: Float? = null
) {
    val isStravaEnabled by viewModel.isStravaEnabled.collectAsState()
    val stravaRoutes by viewModel.stravaRoutes.collectAsState()
    val isRoutesEnabled by viewModel.isRoutesEnabled.collectAsState()
    val storedRoutes by viewModel.storedRoutes.collectAsState()
    val overlayTiles by viewModel.overlayCacheState.collectAsState()

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
    val routeSettings by viewModel.routeRepository.currentSettingsFlow()
        .collectAsStateWithLifecycle()

    var showMappingModeDialog by remember { mutableStateOf(false) }

    var localCenterGeo by remember {
        mutableStateOf(
            GeoPosition(
                vmMapCenter.latitude.toDouble(), vmMapCenter.longitude.toDouble()
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
    val visibleTiles by remember(localCenterGeo, integerZoom, localZoom, viewportSize) {
        derivedStateOf {
            if (viewportSize == IntSize.Zero) emptyList() else {
                calculateVisibleTiles(localCenterGeo, integerZoom, localZoom, viewportSize, tilServer.id)
            }
        }
    }
    val visibleOverlayTiles by remember(localCenterGeo, localZoom, viewportSize) {
        derivedStateOf {
            if (viewportSize == IntSize.Zero) emptyList() else {
                val indexLevel = com.paul.infrastructure.repositories.SpatialIndexRepository.SPATIAL_INDEX_ZOOM_LEVELS
                    .filter { it >= localZoom.toInt() }
                    .minOrNull() ?: com.paul.infrastructure.repositories.SpatialIndexRepository.SPATIAL_INDEX_ZOOM_LEVELS.max()
                
                calculateVisibleTiles(localCenterGeo, indexLevel, localZoom, viewportSize, "overlay")
            }
        }
    }

    LaunchedEffect(visibleTiles, viewportSize) {
        if (viewportSize != IntSize.Zero) {
            viewModel.requestTilesForViewport(
                visibleTiles.map { it.id }.toSet(),
                localCenterGeo,
                localZoom,
                viewportSize
            )
        }
    }

    // --- Fit to Route Effect ---
    LaunchedEffect(routeToDisplay, viewportSize) {
        // *** THE FIX IS HERE (Part 2): Add a condition to only center on a NEW route. ***
        val hasPoints = !routeToDisplay?.route.isNullOrEmpty()
        if (hasPoints && routeToDisplay != null && routeToDisplay != centeredRoute && viewportSize != IntSize.Zero) {
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
                    GeoPosition(maxLat, minLon), targetCenterGeo, targetZoom, viewportSize
                )
                val bottomRightScreen = geoToScreenPixel(
                    GeoPosition(minLat, maxLon), targetCenterGeo, targetZoom, viewportSize
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
                        targetCenterGeo.latitude.toFloat(), targetCenterGeo.longitude.toFloat(), 0f
                    )
                )
                viewModel.setMapZoom(targetZoom.coerceIn(minZoom, maxZoom + OVERZOOM_LEVELS))
            }
            // *** THE FIX IS HERE (Part 3): Once we have centered, record the route. ***
            centeredRoute = routeToDisplay

        } else if (routeToDisplay == null || !hasPoints) {
            // If the route is cleared, reset our tracker so a new route can be centered later.
            centeredRoute = null
        }
    }

    if (showMappingModeDialog) {
        MappingModeSelectionDialog(
            onDismissRequest = { showMappingModeDialog = false },
            onModeSelected = { mode ->
                showMappingModeDialog = false
                viewModel.createPaletteFromViewport(
                    visibleTiles = visibleTiles,
                    tileCache = tileCache,
                    viewportSize = viewportSize,
                    mappingMode = mode
                )
            }
        )
    }

    Box(
        modifier = modifier
            .onSizeChanged { onViewportSizeChange(it) }
            .background(Color.LightGray)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    // Check the state inside the callback instead of using it as a key
                    if (isStravaEnabled || isRoutesEnabled) {
                        val tappedGeo = screenPixelToGeo(
                            IntOffset(
                                offset.x.roundToInt(),
                                offset.y.roundToInt()
                            ),
                            localCenterGeo,
                            localZoom, // Use localZoom for better accuracy than integerZoom
                            size
                        )
                        viewModel.findNearbyActivities(tappedGeo, viewportSize)
                    }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    do {
                        // 1. Get the event ONCE per loop
                        val event = awaitPointerEvent()

                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()
                        val centroid = event.calculateCentroid()

                        // 2. Only consume/calculate if actual movement occurred
                        val isMoving = pan != Offset.Zero || zoom != 1f

                        if (event.changes.any { it.pressed }) {
                            // Calculate new zoom level
                            val newZoom = (localZoom + (ln(zoom) / ln(2.0f))).coerceIn(
                                minZoom, maxZoom + OVERZOOM_LEVELS
                            )

                            // Calculate where the center is now after the pan
                            val pannedCenterScreenPixel =
                                Offset(size.width / 2f, size.height / 2f) - pan

                            val pannedCenterGeo = screenPixelToGeo(
                                screenPixel = IntOffset(
                                    pannedCenterScreenPixel.x.roundToInt(),
                                    pannedCenterScreenPixel.y.roundToInt()
                                ),
                                mapCenterGeo = localCenterGeo,
                                zoom = localZoom,
                                viewportSize = size
                            )

                            // Handle zooming relative to the fingers (centroid)
                            val geoUnderCentroid = screenPixelToGeo(
                                screenPixel = IntOffset(
                                    centroid.x.roundToInt(), centroid.y.roundToInt()
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

                            // Update local state for smooth drawing
                            localZoom = newZoom
                            localCenterGeo = finalNewCenterGeo

                            // 3. ONLY consume if we actually moved.
                            // This allows the Tap detector in the other block to work.
                            if (isMoving) {
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // Sync the ViewModel once the user lifts their fingers
                    viewModel.setMapZoom(localZoom)
                    viewModel.centerMapOn(
                        Point(
                            localCenterGeo.latitude.toFloat(),
                            localCenterGeo.longitude.toFloat(),
                            0f
                        )
                    )
                }
            }) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            visibleTiles.forEach { tileInfo ->
                tileCache[tileInfo.id]?.let { imageBitmap ->
                    drawImage(
                        image = imageBitmap,
                        dstOffset = tileInfo.screenOffset,
                        dstSize = tileInfo.size
                    )
                } ?: run {
                    drawRect(
                        color = Color.DarkGray.copy(alpha = 0.5f), topLeft = Offset(
                            tileInfo.screenOffset.x.toFloat(), tileInfo.screenOffset.y.toFloat()
                        ), size = androidx.compose.ui.geometry.Size(
                            tileInfo.size.width.toFloat(), tileInfo.size.height.toFloat()
                        )
                    )
                }
            }

            visibleOverlayTiles.forEach { tileInfo ->
                overlayTiles[tileInfo.id]?.let { imageBitmap ->
                    drawImage(
                        image = imageBitmap,
                        dstOffset = tileInfo.screenOffset,
                        dstSize = tileInfo.size
                    )
                }
            }

            routeToDisplay?.let { route ->
                if (route.route.size >= 2) {
                    val path = Path()
                    route.route.forEachIndexed { index, point ->
                        val screenPos = geoToScreenPixel(
                            GeoPosition(point.latitude.toDouble(), point.longitude.toDouble()),
                            localCenterGeo,
                            localZoom,
                            viewportSize
                        )
                        val offset = Offset(screenPos.x.toFloat(), screenPos.y.toFloat())
                        if (index == 0) path.moveTo(offset.x, offset.y)
                        else path.lineTo(offset.x, offset.y)
                    }
                    drawPath(
                        path = path,
                        color = routeColor,
                        style = Stroke(width = routeStrokeWidth, cap = StrokeCap.Round)
                    )
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
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(text = "Zoom: %.1f".format(localZoom), color = Color.White, fontSize = 12.sp)
            }

            if (routeSettings.showRoutePoints && routeToDisplay != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Points: ${routeToDisplay.route.size}",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }

            Button(
                modifier = mapButtonStyle,
                onClick = { viewModel.toggleStrava(!isStravaEnabled) },
                colors = ButtonDefaults.buttonColors(
                    // Match the background of your other buttons
                    backgroundColor = if (isStravaEnabled) MaterialTheme.colors.primary else MaterialTheme.colors.primary.copy(
                        alpha = 0.8f
                    ),
                    contentColor = Color.Unspecified // Prevents automatic tinting
                ),
            ) {
                Image(
                    painter = painterResource(Res.drawable.strava),
                    contentDescription = "Toggle Strava",
                    modifier = Modifier
                        .clip(CircleShape), // Makes the orange PNG round
                    contentScale = ContentScale.Fit,
                )
            }

            Button(
                modifier = mapButtonStyle,
                onClick = { viewModel.toggleStoredRoutes(!isRoutesEnabled) },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (isRoutesEnabled) MaterialTheme.colors.primary else MaterialTheme.colors.primary.copy(
                        alpha = 0.8f
                    ),
                ),
            ) {
                Icon(
                    Icons.Default.Route,
                    contentDescription = "Toggle Stored Routes",
                    tint = if (isRoutesEnabled) Color.White else LocalContentColor.current
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                modifier = mapButtonStyle,
                onClick = {
                    if (viewportSize != IntSize.Zero) {
                        showMappingModeDialog = true
                    } else {
                        Napier.v("Viewport size not available yet.")
                    }
                }) {
                Icon(Icons.Default.Colorize, contentDescription = "Create Palette from Map")
            }

            if (!isWatchFeatureDisabled) {
                Button(
                    modifier = mapButtonStyle,
                    onClick = {
                        if (viewportSize != IntSize.Zero) {
                            val topLeftGeo = screenPixelToGeo(
                                IntOffset(0, 0), localCenterGeo, localZoom, viewportSize
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
                            Napier.v("Viewport size not available yet.")
                        }
                    }) {
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
            }
            Button(
                modifier = mapButtonStyle,
                onClick = {
                    val newZoom = (localZoom + 1f).coerceIn(minZoom, maxZoom + OVERZOOM_LEVELS)
                    localZoom = newZoom
                    viewModel.setMapZoom(newZoom)
                }, enabled = localZoom < maxZoom + OVERZOOM_LEVELS
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In")
            }
            Button(
                modifier = mapButtonStyle,
                onClick = {
                    val newZoom = (localZoom - 1f).coerceIn(minZoom, maxZoom + OVERZOOM_LEVELS)
                    localZoom = newZoom
                    viewModel.setMapZoom(newZoom)
                }, enabled = localZoom > minZoom
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
            }
        }
    }
}

private fun findPointAtDistance(points: List<Point>, targetDistance: Float): Point? {
    if (points.isEmpty()) return null
    if (targetDistance <= 0) return points.first()

    var cumulativeDistance = 0f
    for (i in 0 until points.size - 1) {
        val p1 = points[i]
        val p2 = points[i + 1]
        val dist = haversineDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
        if (cumulativeDistance + dist >= targetDistance) {
            val fraction = (targetDistance - cumulativeDistance) / dist
            return Point(
                p1.latitude + fraction * (p2.latitude - p1.latitude),
                p1.longitude + fraction * (p2.longitude - p1.longitude),
                p1.altitude + fraction * (p2.altitude - p1.altitude)
            )
        }
        cumulativeDistance += dist
    }
    return points.last()
}

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

@Composable
fun MappingModeSelectionDialog(
    onDismissRequest: () -> Unit,
    onModeSelected: (PaletteMappingMode) -> Unit
) {
    var selectedMode by remember { mutableStateOf(PaletteMappingMode.NEAREST_NEIGHBOR) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Select Mapping Mode") },
        text = {
            Column {
                Text("Choose how colors will be mapped on the device. This choice also affects how the palette is generated.")
                Spacer(modifier = Modifier.height(16.dp))
                PaletteMappingMode.entries.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = mode }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = selectedMode == mode, onClick = { selectedMode = mode })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (mode) {
                                PaletteMappingMode.NEAREST_NEIGHBOR -> "Nearest (RGB)"
                                PaletteMappingMode.CIELAB -> "Perceptual (CIELAB)"
                                PaletteMappingMode.ORDERED_BY_BRIGHTNESS -> "Brightness (Gradient)"
                                PaletteMappingMode.PALETTE_REMAP -> "Palette Remap"
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onModeSelected(selectedMode) }) {
                Text("Generate")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

fun calculateVisibleTiles(
    mapCenterGeo: GeoPosition, tileZoom: Int, viewportZoom: Float, viewportSize: IntSize, serverId: String
): List<TileInfo> {
    if (viewportSize == IntSize.Zero) return emptyList()
    val tiles = mutableListOf<TileInfo>()
    val topLeftGeo = screenPixelToGeo(IntOffset(0, 0), mapCenterGeo, viewportZoom, viewportSize)
    val bottomRightGeo = screenPixelToGeo(
        IntOffset(viewportSize.width, viewportSize.height), mapCenterGeo, viewportZoom, viewportSize
    )
    val (minTileX, minTileY) = latLonToTileXY(topLeftGeo.latitude, topLeftGeo.longitude, tileZoom)
    val (maxTileX, maxTileY) = latLonToTileXY(
        bottomRightGeo.latitude, bottomRightGeo.longitude, tileZoom
    )
    val n = 1 shl tileZoom
    val buffer = 1
    val startX = (minTileX - buffer).coerceAtLeast(0)
    val startY = (minTileY - buffer).coerceAtLeast(0)
    val endX = (maxTileX + buffer).coerceAtMost(n - 1)
    val endY = (maxTileY + buffer).coerceAtMost(n - 1)
    
    val scaleFactor = 2.0.pow((viewportZoom - tileZoom).toDouble())
    val tileSizeOnScreen = (256 * scaleFactor).roundToInt()
    val tileSize = IntSize(tileSizeOnScreen, tileSizeOnScreen)

    for (x in startX..endX) {
        for (y in startY..endY) {
            val tileId = TileId(x, y, tileZoom, serverId)
            val tileTopLeftGeo = worldPixelToGeo(x.toDouble() / n, y.toDouble() / n)
            val screenOffset = geoToScreenPixel(tileTopLeftGeo, mapCenterGeo, viewportZoom, viewportSize)
            tiles.add(TileInfo(id = tileId, screenOffset = screenOffset, size = tileSize))
        }
    }
    return tiles
}
