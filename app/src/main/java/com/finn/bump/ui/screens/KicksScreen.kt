package com.finn.bump.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.finn.bump.data.AppState
import com.finn.bump.data.KickSession
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.components.MicroLabel
import com.finn.bump.ui.components.Rule
import com.finn.bump.ui.theme.LocalBumpExtras
import com.finn.bump.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Started = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
private val Past = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.UK)

/**
 * Counting movements, as an action rather than a destination: you open it, use it for a
 * few minutes, and close it. It used to hold a quarter of the navigation.
 *
 * Deliberately no target count and no interpretation of the result — that is a clinical
 * judgement, and an app that implied otherwise would be worse than one that just keeps
 * the tally.
 */
@Composable
fun KicksScreen(
    state: AppState,
    vm: BumpViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)

    var startedAt by remember { mutableLongStateOf(0L) }
    var kicks by remember { mutableIntStateOf(0) }
    var elapsed by remember { mutableLongStateOf(0L) }
    val running = startedAt != 0L
    val extras = LocalBumpExtras.current

    LaunchedEffect(running) {
        while (running) {
            elapsed = System.currentTimeMillis() - startedAt
            delay(500)
        }
    }

    fun reset() {
        startedAt = 0L
        kicks = 0
        elapsed = 0L
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.p6)
                .padding(bottom = Spacing.p7),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.p2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MicroLabel("Counting movements")
                Box(
                    Modifier
                        .size(30.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        .clickable(onClickLabel = "Close", onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(top = Spacing.p6),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (running) formatDuration(elapsed) else "0:00",
                    style = MaterialTheme.typography.displaySmall,
                )
                MicroLabel(
                    if (running) {
                        "Started ${Instant.ofEpochMilli(startedAt).atZone(ZoneId.systemDefault()).format(Started)}"
                    } else {
                        "Tap to start"
                    }
                )
            }

            Box(
                Modifier
                    .padding(top = Spacing.p7)
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f)
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable {
                        if (!running) startedAt = System.currentTimeMillis()
                        kicks++
                    }
                    .semantics {
                        contentDescription = "Record a movement. Counted $kicks so far."
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$kicks",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    MicroLabel(
                        if (kicks == 1) "kick" else "kicks",
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "tap each movement",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.p2),
                    )
                }
            }

            if (running) {
                Row(
                    Modifier.fillMaxWidth().padding(top = Spacing.p7),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                MaterialTheme.shapes.small,
                            )
                            .clickable { reset() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Discard",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.shapes.small,
                            )
                            .clickable {
                                vm.addKickSession(
                                    KickSession(
                                        startedAtMillis = startedAt,
                                        durationMillis = System.currentTimeMillis() - startedAt,
                                        kicks = kicks,
                                    )
                                )
                                reset()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Save session",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            val recent = state.kickSessions.sortedByDescending { it.startedAtMillis }.take(3)
            if (recent.isNotEmpty()) {
                MicroLabel("Recent", Modifier.padding(top = Spacing.p7, bottom = Spacing.p1))
                recent.forEach { session ->
                    Rule()
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Spacing.p3),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            "${session.kicks} in ${formatDuration(session.durationMillis)}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            Instant.ofEpochMilli(session.startedAtMillis)
                                .atZone(ZoneId.systemDefault()).format(Past),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                "Counting movements is for your own reassurance. If you notice a change in " +
                    "your baby's usual pattern, contact your midwife straight away.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
