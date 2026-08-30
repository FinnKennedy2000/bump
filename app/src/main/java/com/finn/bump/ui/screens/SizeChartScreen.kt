package com.finn.bump.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.finn.bump.domain.WeekInfo
import com.finn.bump.domain.WeekTable
import com.finn.bump.ui.components.MicroLabel
import com.finn.bump.ui.components.WeekMark
import com.finn.bump.ui.theme.LocalBumpExtras
import com.finn.bump.ui.theme.Spacing
import java.time.LocalDate

/**
 * Every week from 3 to 40 as its drawn mark. The old Sizes screen was a list of 38 rows
 * nobody scrolled twice; as a grid it is one screenful you can read at a glance, and each
 * mark opens that week's sheet.
 */
@Composable
internal fun SizeChartScreen(
    weeks: WeekTable,
    currentWeek: Int,
    dueDate: LocalDate,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val drawn = remember(weeks) { (3..weeks.lastWeek).map { weeks[it] } }
    var sheetWeek by remember { mutableStateOf<Int?>(null) }

    // Open near the week you are in rather than at week 3.
    val gridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = (currentWeek - 3 - 4).coerceIn(0, drawn.lastIndex),
    )

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.p6)
                    .padding(top = Spacing.p2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClickLabel = "Back", onClick = onBack),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "Journey",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MicroLabel("Week $currentWeek", color = MaterialTheme.colorScheme.primary)
            }

            Column(Modifier.padding(Spacing.p6, Spacing.p3, Spacing.p6, Spacing.p4)) {
                Text("Size chart", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Every week from 3 to ${weeks.lastWeek}. Tap a week for its measurements.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                state = gridState,
                modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.p6),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(bottom = Spacing.p8),
            ) {
                items(drawn, key = { it.week }) { info ->
                    ChartTile(info, info.week == currentWeek) { sheetWeek = info.week }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Population averages for orientation only. Lengths are head to bottom " +
                            "up to week 20 and head to heel from week 21.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.p4),
                    )
                }
            }
        }
    }

    sheetWeek?.let { w ->
        WeekSheet(
            week = w,
            currentWeek = currentWeek,
            weeks = weeks,
            dueDate = dueDate,
            onWeek = { sheetWeek = it },
            onDismiss = { sheetWeek = null },
        )
    }
}

@Composable
private fun ChartTile(info: WeekInfo, isCurrent: Boolean, onClick: () -> Unit) {
    val extras = LocalBumpExtras.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClickLabel = "Week ${info.week}", onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(
                    if (isCurrent) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    MaterialTheme.shapes.small,
                )
                .border(
                    1.dp,
                    if (isCurrent) MaterialTheme.colorScheme.primary else extras.rule,
                    MaterialTheme.shapes.small,
                ),
            contentAlignment = Alignment.Center,
        ) {
            WeekMark(info.week, 44.dp)
        }
        Text(
            info.size,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.p1),
        )
        MicroLabel(
            "Wk ${info.week}",
            color = if (isCurrent) MaterialTheme.colorScheme.primary else null,
        )
    }
}
