package com.finn.bump.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.finn.bump.data.AppState
import com.finn.bump.data.Appointment
import com.finn.bump.data.KickSession
import com.finn.bump.data.Logged
import com.finn.bump.data.Note
import com.finn.bump.data.WeightEntry
import com.finn.bump.domain.Pregnancy
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.components.DeleteRow
import com.finn.bump.ui.components.EmptyState
import com.finn.bump.ui.components.MicroLabel
import com.finn.bump.ui.components.PaperCard
import com.finn.bump.ui.components.Rule
import com.finn.bump.ui.components.WeightChart
import com.finn.bump.ui.theme.LocalBumpExtras
import com.finn.bump.ui.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Clock = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
private val DayNumber = DateTimeFormatter.ofPattern("d", Locale.UK)
private val MonthShort = DateTimeFormatter.ofPattern("MMM", Locale.UK)

/** What the Add button can create. Kick sessions are saved by the counter, not typed in. */
internal enum class LogTab(val label: String) {
    Appointments("Appointments"),
    Weight("Weight"),
    Notes("Notes"),
}

private enum class Filter(val label: String) {
    All("All"),
    Appointments("Appointments"),
    Weight("Weight"),
    Notes("Notes"),
    Kicks("Kicks"),
}

/**
 * Everything you have written down, in one column in date order.
 *
 * The old screen kept three separate lists behind tabs, which meant a week's weight, note
 * and appointment never appeared near each other. The chips filter one list rather than
 * switching between three.
 */
@Composable
fun LogScreen(
    state: AppState,
    vm: BumpViewModel,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    // Held here so an undo survives the row that was just deleted.
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(Filter.All) }
    var adding by remember { mutableStateOf<LogTab?>(null) }

    val entries = remember(state, filter) { entries(state, filter) }
    val now = System.currentTimeMillis()
    val (upcoming, earlier) = entries.partition { it.millis > now }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.p6),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.p1, bottom = Spacing.p3),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Log", style = MaterialTheme.typography.headlineSmall)
                AddButton { adding = defaultAddFor(filter) }
            }

            Chips(filter) { filter = it }

            if (filter == Filter.All || filter == Filter.Weight) {
                WeightSummary(state, Modifier.padding(top = Spacing.p4))
            }

            if (entries.isEmpty()) {
                EmptyState(emptyMessage(filter))
            }

            if (upcoming.isNotEmpty()) {
                MicroLabel("Upcoming", Modifier.padding(top = Spacing.p5, bottom = Spacing.p1))
                upcoming.sortedBy { it.millis }.forEach { Entry(it, vm, snackbar, scope) }
            }
            if (earlier.isNotEmpty()) {
                MicroLabel("Earlier", Modifier.padding(top = Spacing.p5, bottom = Spacing.p1))
                earlier.sortedByDescending { it.millis }.forEach { Entry(it, vm, snackbar, scope) }
            }

            Box(Modifier.height(Spacing.p8))
        }
    }

    adding?.let { tab ->
        AddEntrySheet(tab = tab, state = state, vm = vm, onDismiss = { adding = null })
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    Row(
        Modifier
            .height(36.dp)
            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
            .clickable(onClickLabel = "Add an entry", onClick = onClick)
            .padding(horizontal = Spacing.p3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.p1),
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            "Add",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(selected: Filter, onSelect: (Filter) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.p1),
        verticalArrangement = Arrangement.spacedBy(Spacing.p1),
    ) {
        Filter.entries.forEach { f ->
            val on = f == selected
            Box(
                Modifier
                    .background(
                        if (on) LocalBumpExtras.current.selected else Color.Transparent,
                        MaterialTheme.shapes.small,
                    )
                    .then(
                        if (on) Modifier
                        else Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            MaterialTheme.shapes.small,
                        )
                    )
                    .clickable(enabled = !on) { onSelect(f) }
                    .padding(horizontal = Spacing.p3, vertical = 6.dp),
            ) {
                Text(
                    f.label,
                    style = if (on) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.bodySmall
                    },
                    color = if (on) {
                        LocalBumpExtras.current.onSelected
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** Where you are now and how far that is from where you started. */
@Composable
private fun WeightSummary(state: AppState, modifier: Modifier = Modifier) {
    val sorted = state.weights.sortedBy { it.date }
    if (sorted.size < 2) return
    val first = sorted.first()
    val latest = sorted.last()
    val delta = latest.kg - first.kg
    val extras = LocalBumpExtras.current
    val due = state.dueDate

    PaperCard(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                MicroLabel("Weight")
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "%.1f".format(Locale.UK, latest.kg),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        " kg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "%+.1f kg".format(Locale.UK, delta),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (delta >= 0) extras.gain else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (due != null) {
                    Text(
                        "since week ${Pregnancy.week(due, first.date)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        WeightChart(state.weights)
        if (due != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MicroLabel("Week ${Pregnancy.week(due, first.date)}")
                MicroLabel("Week ${Pregnancy.week(due, latest.date)}")
            }
        }
    }
}

/** One logged thing, placed on the timeline by its own date. */
private class LogRow(val millis: Long, val date: LocalDate, val item: Logged)

private fun entries(state: AppState, filter: Filter): List<LogRow> = buildList {
    if (filter == Filter.All || filter == Filter.Appointments) {
        state.appointments.forEach { add(LogRow(it.atMillis, it.atMillis.toLocalDate(), it)) }
    }
    if (filter == Filter.All || filter == Filter.Weight) {
        state.weights.forEach {
            add(LogRow(it.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), it.date, it))
        }
    }
    if (filter == Filter.All || filter == Filter.Notes) {
        state.notes.forEach { add(LogRow(it.createdAtMillis, it.createdAtMillis.toLocalDate(), it)) }
    }
    if (filter == Filter.All || filter == Filter.Kicks) {
        state.kickSessions.forEach {
            add(LogRow(it.startedAtMillis, it.startedAtMillis.toLocalDate(), it))
        }
    }
}

private fun defaultAddFor(filter: Filter): LogTab = when (filter) {
    Filter.Weight -> LogTab.Weight
    Filter.Notes -> LogTab.Notes
    else -> LogTab.Appointments
}

private fun emptyMessage(filter: Filter): String = when (filter) {
    Filter.Appointments -> "No appointments yet. Add your scans and midwife visits here."
    Filter.Weight -> "No weight entries yet."
    Filter.Notes -> "No notes yet. Jot down how you're feeling, or anything worth remembering."
    Filter.Kicks -> "No kick sessions yet. Start one from Today."
    Filter.All -> "Nothing logged yet."
}

@Composable
private fun Entry(
    row: LogRow,
    vm: BumpViewModel,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
) {
    val extras = LocalBumpExtras.current
    val item = row.item

    Rule()
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.p3),
        horizontalArrangement = Arrangement.spacedBy(Spacing.p3),
    ) {
        Column(Modifier.width(38.dp)) {
            Text(row.date.format(DayNumber), style = MaterialTheme.typography.titleSmall)
            MicroLabel(row.date.format(MonthShort))
        }
        Column(Modifier.weight(1f)) {
            when (item) {
                is Appointment -> {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        Instant.ofEpochMilli(item.atMillis).atZone(ZoneId.systemDefault())
                            .format(Clock),
                        style = MaterialTheme.typography.labelLarge,
                        color = extras.appointment,
                    )
                    if (item.note.isNotBlank()) {
                        Text(
                            item.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is WeightEntry -> {
                    MicroLabel("Weight")
                    Text(
                        "%.1f kg".format(Locale.UK, item.kg),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }

                is Note -> {
                    MicroLabel("Note · week ${item.week}")
                    Text(item.text, style = MaterialTheme.typography.bodyLarge)
                }

                is KickSession -> {
                    MicroLabel("Kicks")
                    Text(
                        "${item.kicks} in ${formatDuration(item.durationMillis)}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            DeleteRow(
                label = "Delete entry",
                undoMessage = "Entry deleted",
                onDelete = { vm.delete(item) },
                onUndo = { vm.restore(item) },
                snackbar = snackbar,
                scope = scope,
            )
        }
    }
}

/** The week a note belongs to, so notes read back in pregnancy order rather than calendar order. */
internal fun currentWeek(state: AppState): Int =
    state.dueDate?.let { Pregnancy.week(it, LocalDate.now()) } ?: 0
