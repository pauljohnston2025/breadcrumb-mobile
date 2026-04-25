import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.paul.composables.byteArrayToImageBitmap
import com.paul.infrastructure.repositories.ITileRepository
import com.paul.protocol.todevice.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.tan

@Composable
fun RouteMiniMap(
    route: Route?,
    tileRepository: ITileRepository,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFFFC4C02),
) {
    val points = route?.route ?: emptyList()
    var tileBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    // 1. Calculate Bounds and Zoom level
    val routeData = remember(points) {
        if (points.isEmpty()) return@remember null
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLng = points.minOf { it.longitude }
        val maxLng = points.maxOf { it.longitude }

        val dLat = (maxLat - minLat).toDouble().coerceAtLeast(0.0001)
        val dLng = (maxLng - minLng).toDouble().coerceAtLeast(0.0001)

        // Pick zoom based on bounding box size
        val maxDiff = max(dLat, dLng)
        val zoom = floor(log2(360.0 / maxDiff)).toInt().coerceIn(10, 14)

        object {
            val centerLat = (minLat + maxLat) / 2.0
            val centerLng = (minLng + maxLng) / 2.0
            val zoom = zoom
            val minLat = minLat
            val minLng = minLng
            val rangeLat = dLat
            val rangeLng = dLng
        }
    }

    // 2. Fetch only the necessary tile directly from Repository
    LaunchedEffect(routeData) {
        routeData?.let { data ->
            withContext(Dispatchers.IO) {
                val n = 2.0.pow(data.zoom)
                val xtile = ((data.centerLng + 180.0) / 360.0 * n).toInt()
                val ytile = ((1.0 - ln(tan(Math.toRadians(data.centerLat)) + (1.0 / cos(Math.toRadians(data.centerLat)))) / PI) / 2.0 * n).toInt()

                val result = tileRepository.getTile(xtile, ytile, data.zoom)
                result.second?.let { bytes ->
                    tileBitmap = byteArrayToImageBitmap(bytes)
                }
            }
        }
    }

    Box(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(Color.LightGray.copy(alpha = 0.1f))) {
        // Tile Layer
        tileBitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Polyline Layer
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            routeData?.let { data ->
                val path = Path()
                val canvasRatio = size.width / size.height
                val routeRatio = data.rangeLng / data.rangeLat

                val scale = if (routeRatio > canvasRatio) {
                    size.width / data.rangeLng.toFloat()
                } else {
                    size.height / data.rangeLat.toFloat()
                }

                val offsetX = (size.width - (data.rangeLng.toFloat() * scale)) / 2f
                val offsetY = (size.height - (data.rangeLat.toFloat() * scale)) / 2f

                points.forEachIndexed { index, point ->
                    val x = ((point.longitude - data.minLng).toFloat() * scale) + offsetX
                    val y = size.height - (((point.latitude - data.minLat).toFloat() * scale) + offsetY)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                drawPath(path = path, color = lineColor, style = Stroke(width = 3.dp.toPx()))
            }
        }
    }
}