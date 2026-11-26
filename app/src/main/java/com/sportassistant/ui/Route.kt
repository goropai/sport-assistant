package com.sportassistant.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.sportassistant.track.LatLon
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

fun parsePath(path: String): List<LatLon> =
    path.split(';').mapNotNull { pair ->
        val (lat, lon) = pair.split(',').takeIf { it.size == 2 } ?: return@mapNotNull null
        val la = lat.toDoubleOrNull() ?: return@mapNotNull null
        val lo = lon.toDoubleOrNull() ?: return@mapNotNull null
        LatLon(la, lo)
    }

/**
 * Offline route preview: the track fitted into the box, no map tiles.
 * Longitude is scaled by cos(latitude) so the shape is not stretched.
 */
@Composable
fun RouteCanvas(points: List<LatLon>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    Box(modifier.padding(8.dp)) {
        Canvas(Modifier.matchParentSize()) {
            if (points.size < 2) return@Canvas
            val midLat = points.sumOf { it.lat } / points.size
            val k = cos(Math.toRadians(midLat))
            val xs = points.map { it.lon * k }
            val ys = points.map { -it.lat } // screen y grows downward
            val minX = xs.min(); val maxX = xs.max()
            val minY = ys.min(); val maxY = ys.max()
            val w = max(maxX - minX, 1e-9)
            val h = max(maxY - minY, 1e-9)
            val scale = min(size.width / w, size.height / h)
            val offX = (size.width - w * scale) / 2
            val offY = (size.height - h * scale) / 2
            fun at(i: Int) = Offset(((xs[i] - minX) * scale + offX).toFloat(), ((ys[i] - minY) * scale + offY).toFloat())

            val line = Path().apply {
                moveTo(at(0).x, at(0).y)
                for (i in 1 until points.size) lineTo(at(i).x, at(i).y)
            }
            drawPath(line, lineColor, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(Color(0xFF2E7D32), 9f, at(0))
            drawCircle(Color(0xFFC62828), 9f, at(points.lastIndex))
        }
    }
}
