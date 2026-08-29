package com.finn.bump.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.finn.bump.data.WeightEntry
import java.util.Locale

/**
 * A sparkline over the weight entries.
 *
 * Hand-drawn on a Canvas rather than pulled from a chart library: it is one polyline
 * with two labels, and no charting dependency is smaller than that.
 */
@Composable
fun WeightChart(entries: List<WeightEntry>, modifier: Modifier = Modifier) {
    if (entries.size < 2) return

    val sorted = entries.sortedBy { it.date }
    val values = sorted.map { it.kg }
    val min = values.min()
    val max = values.max()
    // A flat series would divide by zero; give it an arbitrary band so it draws mid-height.
    val span = (max - min).takeIf { it > 0.0001 } ?: 1.0

    val line = MaterialTheme.colorScheme.primary

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val stepX = if (sorted.size > 1) size.width / (sorted.size - 1) else 0f
            val path = Path()
            sorted.forEachIndexed { i, entry ->
                val x = i * stepX
                val y = size.height - (((entry.kg - min) / span).toFloat() * size.height)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = line, style = Stroke(width = 4f, cap = StrokeCap.Round))
            sorted.forEachIndexed { i, entry ->
                val x = i * stepX
                val y = size.height - (((entry.kg - min) / span).toFloat() * size.height)
                drawCircle(line, radius = 6f, center = Offset(x, y))
            }
        }

        Row(Modifier.fillMaxWidth()) {
            Text(
                "%.1f kg".format(Locale.UK, min),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                "%.1f kg".format(Locale.UK, max),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
