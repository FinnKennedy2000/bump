package com.finn.bump.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.finn.bump.ui.theme.LocalBumpExtras
import com.finn.bump.ui.theme.Spacing

/**
 * A hairline. The redesign separates with rules rather than stacking bordered cards, so
 * this is the most-used component in the app.
 */
@Composable
fun Rule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalBumpExtras.current.rule)
    )
}

/** The uppercase micro-label that titles every band. */
@Composable
fun MicroLabel(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color ?: LocalBumpExtras.current.label,
        modifier = modifier,
    )
}

/** A rule with a label on the left and an optional figure on the right. */
@Composable
fun RuledHeading(label: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.p3),
    ) {
        MicroLabel(label)
        Rule(Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelSmall,
                color = LocalBumpExtras.current.label,
            )
        }
    }
}

/**
 * The one boxed surface left: white on paper, hairline border, no shadow.
 *
 * Reserved for things that are genuinely a separate object — a milestone you can act on,
 * the weight summary. Everything else is separated by a [Rule].
 */
@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
            .border(1.dp, LocalBumpExtras.current.rule, MaterialTheme.shapes.small)
            .padding(Spacing.p4),
        verticalArrangement = Arrangement.spacedBy(Spacing.p2),
        content = content,
    )
}

@Composable
fun Dot(color: Color, size: Dp = 6.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(color, CircleShape))
}

/**
 * A measured length, drawn with end caps so it reads as a distance rather than progress.
 *
 * [fraction] is the share of the full width the length occupies; the caller decides
 * whether that is "this week against week 40" or real centimetres.
 */
@Composable
fun ScaleBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    thickness: Dp = 3.dp,
    capHeight: Dp = 15.dp,
) {
    val clipped = fraction >= 1f
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(1.dp).height(capHeight).background(color))
        Box(
            Modifier
                .weight(fraction.coerceIn(0.01f, 1f))
                .height(thickness)
                .background(color)
        )
        if (!clipped) {
            Box(Modifier.width(1.dp).height(capHeight).background(color))
            Box(
                Modifier
                    .weight((1f - fraction).coerceAtLeast(0.001f))
                    .height(1.dp)
                    .background(LocalBumpExtras.current.track)
            )
        }
    }
}

/** A bordered two-or-more-way switch: the selected segment is ink, the rest are quiet. */
@Composable
fun <T> SegmentedToggle(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(1.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        if (on) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                        MaterialTheme.shapes.extraSmall,
                    )
                    .clickable(enabled = !on) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = if (on) {
                        MaterialTheme.typography.labelLarge
                    } else {
                        MaterialTheme.typography.bodySmall
                    },
                    color = if (on) {
                        MaterialTheme.colorScheme.background
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth().padding(Spacing.p6),
    )
}

/**
 * Permanent and non-dismissible. The app shows population averages and general
 * information; it must never read as clinical advice, regardless of who is using it.
 *
 * Set at the secondary tier rather than the design doc's faintest grey: it is prose that
 * has to be readable, so it holds the same 4.5:1 floor as any other body text.
 */
@Composable
fun MedicalDisclaimer(modifier: Modifier = Modifier) {
    Text(
        text = "General information only — not medical advice. " +
            "Talk to your midwife or doctor about your pregnancy.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    )
}
