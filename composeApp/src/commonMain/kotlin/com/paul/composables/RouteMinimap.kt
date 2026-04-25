// ui/components/RouteMiniMap.kt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isEmpty
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.paul.protocol.todevice.Route
import com.paul.protocol.todevice.Point
import kotlin.text.forEachIndexed
import kotlin.text.maxOf
import kotlin.text.minOf
import kotlin.text.toDouble
import kotlin.text.toFloat

@Composable
fun RouteMiniMap(
    route: Route?,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Float = 6f
) {
    // The property in your generated protocol is 'point', not 'points'
    val points = route?.route ?: emptyList()

    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        )
        return
    }

    val bounds = remember(points) {
        val minLat = points.minOf { it.latitude }
        val maxLat = points.maxOf { it.latitude }
        val minLng = points.minOf { it.longitude }
        val maxLng = points.maxOf { it.longitude }

        val dLat = (maxLat - minLat).toDouble().coerceAtLeast(0.0001)
        val dLng = (maxLng - minLng).toDouble().coerceAtLeast(0.0001)

        object {
            val minLat = minLat
            val minLng = minLng
            val rangeLat = dLat
            val rangeLng = dLng
            val aspectRatio = dLng / dLat
        }
    }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(6.dp)
    ) {
        val path = Path()

        // Calculate scaling to maintain aspect ratio within the canvas
        val canvasRatio = size.width / size.height
        val scale: Float
        val offsetX: Float
        val offsetY: Float

        if (bounds.aspectRatio > canvasRatio) {
            // Wide route: fill width, center vertically
            scale = size.width / bounds.rangeLng.toFloat()
            offsetX = 0f
            offsetY = (size.height - (bounds.rangeLat.toFloat() * scale)) / 2f
        } else {
            // Tall route: fill height, center horizontally
            scale = size.height / bounds.rangeLat.toFloat()
            offsetY = 0f
            offsetX = (size.width - (bounds.rangeLng.toFloat() * scale)) / 2f
        }

        points.forEachIndexed { index, point ->
            val x = ((point.longitude - bounds.minLng).toFloat() * scale) + offsetX
            // Latitude is inverted in UI coordinates (Y increases downwards)
            val y = size.height - (((point.latitude - bounds.minLat).toFloat() * scale) + offsetY)

            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = strokeWidth)
        )
    }
}