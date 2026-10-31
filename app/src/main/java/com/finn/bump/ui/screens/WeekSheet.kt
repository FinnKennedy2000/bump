package com.finn.bump.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.finn.bump.data.Photo
import com.finn.bump.domain.Pregnancy
import com.finn.bump.domain.WeekInfo
import com.finn.bump.domain.WeekTable
import com.finn.bump.domain.formatLength
import com.finn.bump.domain.formatWeight
import com.finn.bump.ui.components.MedicalDisclaimer
import com.finn.bump.ui.components.MicroLabel
import com.finn.bump.ui.components.PhotoImage
import com.finn.bump.ui.components.PhotoViewer
import com.finn.bump.ui.components.Rule
import com.finn.bump.ui.components.ScaleBar
import com.finn.bump.ui.components.SegmentedToggle
import com.finn.bump.ui.components.FetusPicture
import com.finn.bump.ui.components.LifeSizeHandFootArt
import com.finn.bump.ui.components.WeekMark
import com.finn.bump.ui.theme.LocalBumpExtras
import com.finn.bump.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Day = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

/** How the length bar is drawn. */
private enum class Scale(val label: String) {
    /** Every week against week 40, so the bar reads as growth. */
    Relative("To scale"),

    /** Real centimetres on the glass, as far as the screen allows. */
    Actual("Actual size"),
}

/**
 * One week, in full. This is what the old Sizes screen was for: it now opens over
 * whatever you were looking at and closes again, instead of being a 38-row list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeekSheet(
    week: Int,
    currentWeek: Int,
    weeks: WeekTable,
    dueDate: LocalDate,
    onWeek: (Int) -> Unit,
    onDismiss: () -> Unit,
    photos: List<Photo> = emptyList(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val info = weeks[week]
    val extras = LocalBumpExtras.current
    var scale by remember { mutableStateOf(Scale.Relative) }
    var viewing by remember { mutableStateOf<Photo?>(null) }
    val thisWeeks = photos.filter { Pregnancy.week(dueDate, it.date) == week }.sortedBy { it.date }

    val start = dueDate.minusDays((Pregnancy.TERM_DAYS - week * 7).toLong())

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.p6)
                .padding(bottom = Spacing.p8),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                MicroLabel(
                    if (week == currentWeek) "Week $week · this week" else "Week $week",
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "${start.format(Day)} – ${start.plusDays(6).format(Day)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.p1),
                horizontalArrangement = Arrangement.spacedBy(Spacing.p4),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(info.size, style = MaterialTheme.typography.headlineLarge)
                    Row(
                        Modifier.padding(top = Spacing.p3),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.p6),
                    ) {
                        info.lengthCm?.let {
                            Figure(info.measureLabel.uppercase(), formatLength(it))
                        }
                        info.weightG?.let { Figure("WEIGHT", formatWeight(it)) }
                    }
                }
                Box(
                    Modifier
                        .size(108.dp)
                        .border(1.dp, extras.rule, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    WeekMark(week, 80.dp)
                }
            }

            info.lengthCm?.let { cm ->
                SegmentedToggle(
                    options = Scale.entries,
                    selected = scale,
                    onSelect = { scale = it },
                    label = { it.label },
                    modifier = Modifier.padding(top = Spacing.p5),
                )

                Rule(Modifier.padding(top = Spacing.p5))
                LengthToScale(cm, weeks.maxLengthCm, scale, info)
                Rule()
            }

            Text(
                info.blurb,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = Spacing.p5),
            )

            if (week >= 5) {
                MicroLabel("What baby looks like now", Modifier.padding(top = Spacing.p5, bottom = Spacing.p2))
                FetusPicture(week, Modifier.fillMaxWidth())
                if (info.looks.isNotBlank()) {
                    Text(
                        info.looks,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = Spacing.p3),
                    )
                }
                Text(
                    "An illustration of typical features, not a scan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.p1),
                )
            }

            if (info.handMm != null || info.footMm != null) {
                MicroLabel("Hands and feet, life size", Modifier.padding(top = Spacing.p5, bottom = Spacing.p2))
                LifeSizeHandFootArt(info.handMm?.toFloat(), info.footMm?.toFloat())
            }

            if (thisWeeks.isNotEmpty()) {
                MicroLabel("Your photos", Modifier.padding(top = Spacing.p5, bottom = Spacing.p2))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
                ) {
                    thisWeeks.forEach { photo ->
                        PhotoImage(
                            blob = photo.blob,
                            maxPx = 300,
                            contentDescription = photo.caption.ifBlank { "Photo from week $week" },
                            modifier = Modifier
                                .size(96.dp)
                                .clip(MaterialTheme.shapes.small)
                                .clickable(onClickLabel = "Open photo") { viewing = photo },
                        )
                    }
                }
            }

            GrowthCurve(weeks, week, Modifier.padding(top = Spacing.p6))

            Rule(Modifier.padding(top = Spacing.p5))
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.p4),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StepWeek(week - 1, back = true, enabled = week > 3, onWeek = onWeek)
                StepWeek(week + 1, back = false, enabled = week < weeks.lastWeek, onWeek = onWeek)
            }

            MedicalDisclaimer(Modifier.padding(top = Spacing.p5))
        }
    }

    // View only: edits and deletes live in the Log, where there is a snackbar for undo.
    viewing?.let { PhotoViewer(it, dueDate, onDismiss = { viewing = null }) }
}

@Composable
private fun Figure(label: String, value: String) {
    Column {
        MicroLabel(label)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * The length bar plus the caption that says what scale it is on.
 *
 * In [Scale.Actual] the width is real centimetres, derived from the display's reported
 * physical density. `xdpi` is approximate on many devices and the bar is capped at the
 * screen width, so the caption says so rather than claiming precision it does not have.
 */
@Composable
private fun LengthToScale(cm: Double, maxCm: Double, scale: Scale, info: WeekInfo) {
    val density = LocalDensity.current
    val xdpi = LocalContext.current.resources.displayMetrics.xdpi
    val available = LocalConfiguration.current.screenWidthDp.dp - Spacing.p12
    val actual = with(density) { (cm / 2.54 * xdpi).toFloat().toDp() }

    val fraction = when (scale) {
        Scale.Relative -> (cm / maxCm).toFloat()
        Scale.Actual -> (actual / available).coerceAtMost(1f)
    }

    Column(Modifier.padding(vertical = Spacing.p5)) {
        ScaleBar(fraction, thickness = 4.dp, capHeight = 18.dp)
        MicroLabel(
            when {
                scale == Scale.Actual && actual >= available ->
                    "Actual size — longer than the screen"

                scale == Scale.Actual -> "Actual size — ${info.measureLabel}"

                else -> "${info.measureLabel} · week ${Pregnancy.WEEKS_AT_TERM} = " +
                    "${formatLength(maxCm)} = full width"
            },
            Modifier.padding(top = Spacing.p3),
        )
    }
}

/**
 * Length against week, with this week marked.
 *
 * The step at week 21 is real: lengths are crown to rump up to week 20 and crown to heel
 * after it. Smoothing it would be drawing a baby that does not exist.
 */
@Composable
private fun GrowthCurve(weeks: WeekTable, week: Int, modifier: Modifier = Modifier) {
    val points = remember(weeks) { weeks.measured.filter { it.week >= 8 } }
    if (points.size < 2) return

    val line = LocalBumpExtras.current.faint
    val mark = MaterialTheme.colorScheme.primary
    val maxCm = points.maxOf { it.lengthCm!! }
    val first = points.first().week
    val span = (points.last().week - first).toFloat()

    Column(modifier) {
        MicroLabel("Growth, week $first to ${points.last().week}")
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(top = Spacing.p2)
        ) {
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = (p.week - first) / span * size.width
                val y = size.height - (p.lengthCm!! / maxCm).toFloat() * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = line, style = Stroke(width = 3f, cap = StrokeCap.Round))

            points.firstOrNull { it.week >= week }?.let { here ->
                val x = (here.week - first) / span * size.width
                val y = size.height - (here.lengthCm!! / maxCm).toFloat() * size.height
                drawLine(mark, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
                drawCircle(mark, radius = 9f, center = Offset(x, y))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(first, 20, 30, points.last().week).forEach { MicroLabel("$it") }
        }
    }
}

@Composable
private fun StepWeek(week: Int, back: Boolean, enabled: Boolean, onWeek: (Int) -> Unit) {
    if (!enabled) {
        Box(Modifier.size(1.dp))
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { onWeek(week) },
    ) {
        if (back) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
        Text("Week $week", style = MaterialTheme.typography.labelLarge)
        if (!back) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
