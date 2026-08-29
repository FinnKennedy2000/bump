package com.finn.bump.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.finn.bump.data.AppState
import com.finn.bump.domain.Pregnancy
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.components.DeleteRow
import com.finn.bump.ui.components.EmptyState
import com.finn.bump.ui.components.MoonCard
import com.finn.bump.ui.components.WeightChart
import com.finn.bump.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal enum class LogTab(val label: String) {
    Appointments("Appointments"),
    Weight("Weight"),
    Notes("Notes"),
}

@Composable
fun LogScreen(
    state: AppState,
    vm: BumpViewModel,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(LogTab.Appointments) }
    var adding by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.p4),
            verticalArrangement = Arrangement.spacedBy(Spacing.p3),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.p4),
                horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
            ) {
                LogTab.entries.forEach { t ->
                    FilterChip(
                        selected = tab == t,
                        onClick = { tab = t },
                        label = { Text(t.label) },
                    )
                }
            }

            OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  Add ${tab.label.dropLast(1).lowercase(Locale.UK)}")
            }

            when (tab) {
                LogTab.Appointments -> Appointments(state, vm, snackbar)
                LogTab.Weight -> Weights(state, vm, snackbar)
                LogTab.Notes -> Notes(state, vm, snackbar)
            }

            Column(Modifier.padding(bottom = Spacing.p8)) {}
        }
    }

    if (adding) {
        AddEntrySheet(
            tab = tab,
            state = state,
            vm = vm,
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun Appointments(state: AppState, vm: BumpViewModel, snackbar: SnackbarHostState) {
    if (state.appointments.isEmpty()) {
        EmptyState("No appointments yet. Add your scans and midwife visits here.")
        return
    }
    val now = System.currentTimeMillis()
    val (upcoming, past) = state.appointments.sortedBy { it.atMillis }.partition { it.atMillis >= now }

    (upcoming + past.reversed()).forEach { a ->
        MoonCard {
            Text(a.title, style = MaterialTheme.typography.titleMedium)
            Text(
                Instant.ofEpochMilli(a.atMillis).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.UK)),
                style = MaterialTheme.typography.bodyMedium,
                color = if (a.atMillis >= now) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (a.note.isNotBlank()) {
                Text(a.note, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DeleteRow(
                label = "Delete appointment ${a.title}",
                undoMessage = "Appointment deleted",
                onDelete = { vm.delete(a) },
                onUndo = { vm.restore(a) },
                snackbar = snackbar,
            )
        }
    }
}

@Composable
private fun Weights(state: AppState, vm: BumpViewModel, snackbar: SnackbarHostState) {
    if (state.weights.isEmpty()) {
        EmptyState("No weight entries yet.")
        return
    }

    MoonCard { WeightChart(state.weights) }

    state.weights.sortedByDescending { it.date }.forEach { w ->
        MoonCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("%.1f kg".format(Locale.UK, w.kg), style = MaterialTheme.typography.titleMedium)
                Text(
                    w.date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DeleteRow(
                label = "Delete weight entry",
                undoMessage = "Entry deleted",
                onDelete = { vm.delete(w) },
                onUndo = { vm.restore(w) },
                snackbar = snackbar,
            )
        }
    }
}

@Composable
private fun Notes(state: AppState, vm: BumpViewModel, snackbar: SnackbarHostState) {
    if (state.notes.isEmpty()) {
        EmptyState("No notes yet. Jot down how you're feeling, or anything worth remembering.")
        return
    }

    state.notes.sortedByDescending { it.createdAtMillis }.forEach { n ->
        MoonCard {
            Text(
                "Week ${n.week} · " + Instant.ofEpochMilli(n.createdAtMillis)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(n.text, style = MaterialTheme.typography.bodyLarge)
            DeleteRow(
                label = "Delete note",
                undoMessage = "Note deleted",
                onDelete = { vm.delete(n) },
                onUndo = { vm.restore(n) },
                snackbar = snackbar,
            )
        }
    }
}

/** The week a note belongs to, so notes read back in pregnancy order rather than calendar order. */
internal fun currentWeek(state: AppState): Int =
    state.dueDate?.let { Pregnancy.week(it, LocalDate.now()) } ?: 0
