package com.finn.bump.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.finn.bump.data.AppState
import com.finn.bump.data.KickSession
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.components.DeleteRow
import com.finn.bump.ui.components.EmptyState
import com.finn.bump.ui.components.MoonCard
import com.finn.bump.ui.components.SectionTitle
import com.finn.bump.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Counts and times movements. Deliberately no target count and no interpretation of the
 * result — that is a clinical judgement, and an app that implied otherwise would be
 * worse than one that just keeps the tally.
 */
@Composable
fun KicksScreen(
    state: AppState,
    vm: BumpViewModel,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    var startedAt by remember { mutableLongStateOf(0L) }
    var kicks by remember { mutableIntStateOf(0) }
    var elapsed by remember { mutableLongStateOf(0L) }
    val running = startedAt != 0L

    LaunchedEffect(running) {
        while (running) {
            elapsed = System.currentTimeMillis() - startedAt
            delay(500)
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.p4),
            verticalArrangement = Arrangement.spacedBy(Spacing.p3),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (running) formatDuration(elapsed) else "Tap to start counting",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = Spacing.p4),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable {
                        if (!running) startedAt = System.currentTimeMillis()
                        kicks++
                    }
                    .semantics {
                        contentDescription = "Record a kick. Counted $kicks so far."
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$kicks", style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text(
                        if (kicks == 1) "kick" else "kicks",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (running) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        onClick = { startedAt = 0L; kicks = 0; elapsed = 0L },
                        modifier = Modifier.weight(1f),
                    ) { Text("Discard") }

                    Button(
                        onClick = {
                            vm.addKickSession(
                                KickSession(
                                    startedAtMillis = startedAt,
                                    durationMillis = System.currentTimeMillis() - startedAt,
                                    kicks = kicks,
                                )
                            )
                            startedAt = 0L; kicks = 0; elapsed = 0L
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Save session") }
                }
            }

            SectionTitle("Past sessions", Modifier.fillMaxWidth())

            if (state.kickSessions.isEmpty()) {
                EmptyState("No sessions yet. Tap the circle each time you feel a movement.")
            } else {
                state.kickSessions
                    .sortedByDescending { it.startedAtMillis }
                    .forEach { session ->
                        MoonCard {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "${session.kicks} in ${formatDuration(session.durationMillis)}",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    Instant.ofEpochMilli(session.startedAtMillis)
                                        .atZone(ZoneId.systemDefault())
                                        .format(DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.UK)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            DeleteRow(
                                label = "Delete session",
                                onDelete = { vm.delete(session) },
                                onUndo = { vm.restore(session) },
                                snackbar = snackbar,
                                undoMessage = "Session deleted",
                            )
                        }
                    }
            }

            Text(
                "Counting movements is for your own reassurance. " +
                    "If you notice a change in your baby's usual pattern, contact your midwife straight away.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = Spacing.p6),
            )
        }
    }
}

internal fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes >= 60) {
        "%d h %02d m".format(Locale.UK, minutes / 60, minutes % 60)
    } else {
        "%d:%02d".format(Locale.UK, minutes, seconds)
    }
}
