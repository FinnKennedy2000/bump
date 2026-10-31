package com.finn.bump.ui.components

import android.util.TypedValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.finn.bump.R

/**
 * A baby's hand and foot at true physical size: each drawable is drawn exactly as tall as
 * the stated length, converted through the real screen density. If the pair is wider than
 * the space, both shrink by the same factor and the caption says so.
 * Artwork is a generic hand and sole, not a model of this baby; see README Credits.
 */
@Composable
fun LifeSizeHandFootArt(handMm: Float?, footMm: Float?, modifier: Modifier = Modifier) {
    if (handMm == null && footMm == null) return
    val metrics = LocalContext.current.resources.displayMetrics
    val density = LocalDensity.current
    // Viewport of both drawables is cropped to the artwork, so aspect ratio is width / height.
    val handPainter = painterResource(R.drawable.baby_hand)
    val footPainter = painterResource(R.drawable.baby_foot)
    fun mmPx(mm: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, mm, metrics)
    fun aspect(p: androidx.compose.ui.graphics.painter.Painter) =
        p.intrinsicSize.let { it.width / it.height }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gapPx = with(density) { 24.dp.toPx() }
        val items = listOfNotNull(
            handMm?.let { Triple("Hand", it, handPainter) },
            footMm?.let { Triple("Foot", it, footPainter) },
        )
        val natural = items.sumOf { (_, mm, p) -> (mmPx(mm) * aspect(p)).toDouble() } +
            gapPx * (items.size - 1)
        val scale = minOf(1f, constraints.maxWidth / natural.toFloat())
        val scaled = scale < 1f
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            items.forEach { (name, mm, painter) ->
                val h = mmPx(mm) * scale
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.size(
                            with(density) { (h * aspect(painter)).toDp() },
                            with(density) { h.toDp() },
                        ),
                    )
                    Text(
                        "$name ≈ ${mm.toInt()} mm · life size" + if (scaled) " (scaled)" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
