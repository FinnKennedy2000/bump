package com.finn.bump.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finn.bump.data.AppState
import com.finn.bump.domain.Contractions
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.components.MicroLabel
import com.finn.bump.ui.components.Rule
import com.finn.bump.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Clock = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

/** How far back the list reaches: one labour, not every practice twinge from last week. */
private const val SHOWN_MILLIS = 12 * Contractions.HOUR

/**
 * Timing contractions: tap when one starts, tap when it eases. Each one is saved as it
 * ends, so nothing rides on a session being finished, and the other phone sees them live.
 *
 * Like the kick counter it keeps the numbers and judges none of them. There is no "time
 * to go in" — that is a conversation with your maternity unit, so the one thing offered
 * beyond the times is the number to ring them on.
 */
@Composable
fun ContractionsScreen(
    state: AppState,
    vm: BumpViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)

    // A phone that sleeps between contractions has to be woken with wet hands at 3am.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val startedAt by vm.contractionStartedAt.collectAsStateWithLifecycle()
    val lastTimed by vm.lastTimed.collectAsStateWithLifecycle()
    var now by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    val running = startedAt != 0L
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    val recent = state.contractions.filter { it.startedAtMillis >= now - SHOWN_MILLIS }
    val timed = Contractions.timed(recent.map { it.startedAtMillis to it.durationMillis })
    val summary = Contractions.summary(recent.map { it.startedAtMillis to it.durationMillis }, now)
    val lastStart = recent.maxOfOrNull { it.startedAtMillis }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.p6)
                .padding(bottom = Spacing.p7),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.p2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MicroLabel("Timing contractions")
                Box(
                    Modifier
                        .size(30.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        .clickable(onClickLabel = "Close", onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                }
            }

            Column(
                Modifier.fillMaxWidth().padding(top = Spacing.p6),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    when {
                        running -> formatDuration(now - startedAt)
                        lastStart != null -> formatDuration(now - lastStart)
                        else -> "0:00"
                    },
                    style = MaterialTheme.typography.displaySmall,
                )
                MicroLabel(
                    when {
                        running -> "Started ${at(startedAt)}"
                        lastStart != null -> "Since the last one started"
                        else -> "Tap when one starts"
                    }
                )
            }

            Box(
                Modifier
                    .padding(top = Spacing.p6)
                    .fillMaxWidth(0.66f)
                    .aspectRatio(1f)
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(
                        if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
                    )
                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable { if (running) vm.endContraction() else vm.startContraction() }
                    .semantics {
                        contentDescription = if (running) "Contraction has ended" else "Contraction has started"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (running) "It's eased" else "It's started",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        if (running) "tap when it ends" else "tap as one begins",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.p1),
                    )
                }
            }

            if (running) {
                Text(
                    "Started by mistake? Cancel this one",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { vm.cancelContraction() }
                        .padding(Spacing.p3),
                )
            }

            summary?.let { s ->
                Row(
                    Modifier.fillMaxWidth().padding(top = Spacing.p6),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Figure("${s.count}", "in the last hour")
                    Figure(short(s.averageDurationMillis), "average length")
                    Figure(s.averageGapMillis?.let(::short) ?: "–", "average gap")
                }
            }

            if (timed.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = Spacing.p6, bottom = Spacing.p1),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MicroLabel("Timed")
                    // Only the one this phone just timed: on a shared list "the latest" could
                    // be one the other phone synced in a moment ago.
                    if (lastTimed != null && state.contractions.any { it.id == lastTimed?.id }) {
                        Text(
                            "Remove the one just timed",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { vm.removeLastTimed() },
                        )
                    }
                }
                timed.asReversed().forEach { c ->
                    Rule()
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Spacing.p3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(at(c.startedAtMillis), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text(
                            "lasted ${short(c.durationMillis)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1.2f),
                        )
                        Text(
                            c.gapMillis?.let { "${short(it)} after" } ?: "first",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.2f),
                        )
                    }
                }
            }

            Text(
                "This keeps the times. It doesn't tell you when to go in — your maternity " +
                    "unit decides that with you on the phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.p7),
            )
            state.contacts.firstOrNull()?.let { c ->
                Row(
                    Modifier
                        .padding(top = Spacing.p3)
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        .clickable(onClickLabel = "Call ${c.label}") { dial(context, c.number) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Call,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "Call ${c.label}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = Spacing.p2),
                    )
                }
            }
        }
    }
}

@Composable
private fun Figure(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun at(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(Clock)

/** "45 s", "4 m 10 s", "1 h 05 m": short enough for a column, unambiguous to read. */
private fun short(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 60 -> "$s s"
        s < 3600 -> if (s % 60 == 0L) "${s / 60} m" else "${s / 60} m ${s % 60} s"
        else -> "%d h %02d m".format(Locale.UK, s / 3600, (s % 3600) / 60)
    }
}
