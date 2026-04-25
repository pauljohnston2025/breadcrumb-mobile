package com.paul.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.paul.infrastructure.repositories.ITileRepository
import com.paul.infrastructure.service.GeoPosition
import com.paul.infrastructure.service.geoToScreenPixel
import com.paul.protocol.todevice.Route
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tan

@Composable
fun RouteMiniMap(
    route: Route?,
    tileRepository: ITileRepository,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFFFC4C02),
) {
    // Local state to store bitmaps fetched from the repo
    // This avoids using a global tileCache while allowing the Canvas to draw asynchronously loaded data
    val localBitmaps = remember { mutableStateMapOf<String, ImageBitmap>() }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.LightGray.copy(alpha = 0.1f))
    ) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        if (width <= 0 || height <= 0 || route == null || route.route.isEmpty()) return@BoxWithConstraints

        val viewportSize = IntSize(width, height)

        // 1. Calculate Center and Zoom to fit the route bounds
        val mapParams = remember(route, viewportSize) {
            val lats = route.route.map { it.latitude.toDouble() }
            val lons = route.route.map { it.longitude.toDouble() }
            val minLat = lats.min(); val maxLat = lats.max()
            val minLon = lons.min(); val maxLon = lons.max()

            val center = GeoPosition((minLat + maxLat) / 2.0, (minLon + maxLon) / 2.0)

            val tileSize = 256.0
            val padding = 1.25

            val latRadMin = minLat * PI / 180.0
            val latRadMax = maxLat * PI / 180.0
            val mapHeightFull = ln(tan(latRadMax) + 1.0 / cos(latRadMax)) - ln(tan(latRadMin) + 1.0 / cos(latRadMin))

            val zoomV = log2(height / (max(0.00001, mapHeightFull) * tileSize / (2 * PI)))
            val zoomH = log2(width / (max(0.00001, maxLon - minLon) * (tileSize / 360.0)))

            val bestZoom = (minOf(zoomV, zoomH) - (padding - 1.0)).toFloat().coerceIn(2f, 18f)
            val integerZoom = bestZoom.roundToInt().coerceIn(2, 18)

            Triple(center, bestZoom, integerZoom)
        }

        val centerGeo = mapParams.first
        val localZoom = mapParams.second
        val integerZoom = mapParams.third

        // 2. Determine visible tiles
        val visibleTiles = remember(centerGeo, integerZoom, viewportSize) {
            calculateVisibleTiles(centerGeo, integerZoom, viewportSize, "minimap")
        }

        // 3. Handle Suspend Loading: Fetch tiles from repo and update local state
        LaunchedEffect(visibleTiles) {
            visibleTiles.forEach { tileInfo ->
                if (!localBitmaps.containsKey(tileInfo.id.toString())) {
                    launch {
                        val data = tileRepository.getTile(tileInfo.id.x, tileInfo.id.y, tileInfo.id.z)
                        if (data.first == 200 && data.second != null) {
                            byteArrayToImageBitmap(data.second!!)?.let { bitmap ->
                                localBitmaps[tileInfo.id.toString()] = bitmap
                            }
                        }
                    }
                }
            }
        }

        // 4. Draw Logic mirroring MapTilerComposable
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scale = 2.0.pow((localZoom - integerZoom).toDouble()).toFloat()

            withTransform({
                // Scaling around the center keeps the path and tiles locked together
                scale(scale, scale, pivot = Offset(size.width / 2f, size.height / 2f))
            }) {
                // Draw Tiles from local state
                visibleTiles.forEach { tileInfo ->
                    localBitmaps[tileInfo.id.toString()]?.let { bitmap ->
                        drawImage(
                            image = bitmap,
                            dstOffset = tileInfo.screenOffset,
                            dstSize = tileInfo.size
                        )
                    }
                }

                // Draw Route Path (calculated at integerZoom to match tiles)
                val path = Path()
                route.route.forEachIndexed { index, point ->
                    val pos = geoToScreenPixel(
                        GeoPosition(point.latitude.toDouble(), point.longitude.toDouble()),
                        centerGeo,
                        integerZoom.toFloat(),
                        viewportSize
                    )
                    if (index == 0) path.moveTo(pos.x.toFloat(), pos.y.toFloat())
                    else path.lineTo(pos.x.toFloat(), pos.y.toFloat())
                }

                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(
                        width = 3.dp.toPx() / scale,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            }
        }
    }
}