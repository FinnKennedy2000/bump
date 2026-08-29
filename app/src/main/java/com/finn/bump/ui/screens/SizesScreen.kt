package com.finn.bump.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.finn.bump.data.AppState
import com.finn.bump.domain.Pregnancy
import com.finn.bump.domain.WeekInfo
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.components.MedicalDisclaimer
import com.finn.bump.ui.components.MoonCard
import com.finn.bump.ui.theme.Spacing
import java.time.LocalDate

/** How the length bar is drawn. */
private enum class Scale(val label: String) {
    /** Every week on one scale, so scrolling shows the growth. */
    Relative("To scale"),

    /** Real centimetres on the glass, as far as the screen allows. */
    Actual("Actual size"),
}

/**
 * The whole size journey, week 3 to week 40.
 *
 * The emoji are a garnish; the measured bar is the point. Two thirds of the weeks have
 * an emoji that genuinely depicts the fruit or vegetable named, and the rest would need
 * a wrong one, so the bar is what every row has in common.
 */
@Composable
fun SizesScreen(
    state: AppState,
    vm: BumpViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val due = state.dueDate ?: return
    val currentWeek = Pregnancy.week(due, LocalDate.now())
    val weeks = remember(vm.weeks) { vm.weeks.measured }
    val maxCm = remember(vm.weeks) { vm.weeks.maxLengthCm }

    var scale by remember { mutableStateOf(Scale.Relative) }

    // Open on the week you are actually in, one row up so it doesn't hug the header.
    val startIndex = weeks.indexOfFirst { it.week >= currentWeek }
        .let { if (it < 0) weeks.lastIndex else it }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (startIndex - 1).coerceAtLeast(0),
    )

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(end = Spacing.p4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Size week by week", style = MaterialTheme.typography.titleLarge)
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.p4),
                horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
            ) {
                Scale.entries.forEach { s ->
                    FilterChip(
                        selected = scale == s,
                        onClick = { scale = s },
                        label = { Text(s.label) },
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.p4),
                verticalArrangement = Arrangement.spacedBy(Spacing.p3),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = Spacing.p3,
                    bottom = Spacing.p6,
                ),
            ) {
                items(weeks, key = { it.week }) { info ->
                    WeekSizeCard(
                        info = info,
                        isCurrent = info.week == currentWeek,
                        maxCm = maxCm,
                        scale = scale,
                    )
                }
                item {
                    Column {
                        Text(
                            if (scale == Scale.Actual) {
                                "Actual size uses your screen's reported pixel density, which " +
                                    "is close but not exact. Longer weeks run off the edge."
                            } else {
                                "Every week drawn on the same scale, so week 40 fills the width."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MedicalDisclaimer(Modifier.padding(top = Spacing.p3))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekSizeCard(
    info: WeekInfo,
    isCurrent: Boolean,
    maxCm: Double,
    scale: Scale,
) {
    val cm = info.lengthCm ?: return

    Box(
        Modifier
            .fillMaxWidth()
            .then(
                if (isCurrent) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.shapes.medium,
                    )
                } else Modifier
            )
    ) {
        MoonCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Week ${info.week}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                )
                if (isCurrent) {
                    Text(
                        "You are here",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.p3),
            ) {
                if (info.emoji != null) {
                    Text(info.emoji, fontSize = 32.sp)
                }
                Text(
                    info.size,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            LengthBar(cm = cm, maxCm = maxCm, scale = scale)

            Text(
                info.figures,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A measured bar with end caps, so it reads as a length rather than as a progress bar.
 *
 * In [Scale.Actual] the width is real centimetres, derived from the display's reported
 * physical density. `xdpi` is approximate on many devices and the bar is capped at the
 * card width, so the screen says so rather than claiming precision it does not have.
 */
@Composable
private fun LengthBar(cm: Double, maxCm: Double, scale: Scale) {
    val density = LocalDensity.current
    val xdpi = LocalContext.current.resources.displayMetrics.xdpi
    val available = LocalConfiguration.current.screenWidthDp.dp - Spacing.p12 - Spacing.p4

    val target: Dp = when (scale) {
        Scale.Relative -> available * (cm / maxCm).toFloat()
        Scale.Actual -> with(density) { (cm / 2.54 * xdpi).toFloat().toDp() }
    }
    val clipped = target > available
    val width = if (clipped) available else target

    val colour = MaterialTheme.colorScheme.primary

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.p1)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Left cap, the run, then a right cap unless it ran off the card.
            Box(Modifier.width(2.dp).height(14.dp).background(colour))
            Box(
                Modifier
                    .width((width - 4.dp).coerceAtLeast(0.dp))
                    .height(4.dp)
                    .background(colour, CircleShape)
            )
            if (!clipped) {
                Box(Modifier.width(2.dp).height(14.dp).background(colour))
            }
        }
        if (clipped) {
            Text(
                "longer than the screen",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
