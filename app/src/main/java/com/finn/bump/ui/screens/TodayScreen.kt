package com.finn.bump.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finn.bump.data.AppState
import com.finn.bump.data.Contact
import com.finn.bump.data.measuredOn
import com.finn.bump.data.displayName
import com.finn.bump.domain.MilestoneWindow
import com.finn.bump.domain.Pregnancy
import com.finn.bump.domain.nextFrom
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.components.Dot
import com.finn.bump.ui.components.MedicalDisclaimer
import com.finn.bump.ui.components.MicroLabel
import com.finn.bump.ui.components.Rule
import com.finn.bump.ui.components.SyncStatusRow
import com.finn.bump.ui.components.ScaleBar
import com.finn.bump.ui.components.WeekMark
import com.finn.bump.ui.theme.LocalBumpExtras
import com.finn.bump.ui.theme.Spacing
import com.finn.bump.ui.theme.color
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val Today = DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)
private val DueDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)
private val BornOn = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK)
private val AppointmentAt = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.UK)

/**
 * The one screen that answers "where am I?".
 *
 * Everything on it is derived from the due date: the week, the size, the countdown. The
 * three actions at the bottom are the only things that write, and each of them opens and
 * closes rather than living in the navigation.
 */
@Composable
fun TodayScreen(
    state: AppState,
    vm: BumpViewModel,
    onCountKicks: () -> Unit,
    onContractions: () -> Unit,
    onBag: () -> Unit,
    onEveryWeek: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val due = state.dueDate ?: return
    val today = LocalDate.now()
    val week = Pregnancy.week(due, state.measuredOn(today))
    val day = Pregnancy.dayOfWeek(due, state.measuredOn(today))
    val info = vm.weeks[week]

    var editing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf<LogTab?>(null) }
    var sheetWeek by remember { mutableStateOf<Int?>(null) }
    val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.p6)
                    .padding(top = Spacing.p2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.p1),
                    modifier = Modifier.clickable(onClickLabel = "Edit baby details") {
                        editing = true
                    },
                ) {
                    Text(
                        state.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
                MicroLabel(today.format(Today))
            }

            if (state.paused || state.bornOn != null) {
                Ended(state, due, Modifier.padding(horizontal = Spacing.p6).padding(top = Spacing.p5))
            } else {
                // The week, set as one measured figure: the number, the day, the unit.
                Column(Modifier.padding(horizontal = Spacing.p6).padding(top = Spacing.p3)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$week", style = MaterialTheme.typography.displayLarge)
                        Text(
                            "+$day",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = Spacing.p2, bottom = 6.dp),
                        )
                        MicroLabel(
                            "weeks",
                            Modifier.padding(start = Spacing.p2, bottom = 12.dp),
                        )
                    }
                    Text(
                        listOfNotNull(
                            trimesterName(Pregnancy.trimester(week)),
                            state.babySex.headline,
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Countdown(due, today, Modifier.padding(Spacing.p6, Spacing.p5, Spacing.p6, 0.dp))

                SizeBand(
                    info = vm.weeks[week],
                    maxCm = vm.weeks.maxLengthCm,
                    onOpenWeek = { sheetWeek = week },
                    onEveryWeek = onEveryWeek,
                    modifier = Modifier.padding(top = Spacing.p5),
                )

                Text(
                    info.blurb,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = Spacing.p6).padding(top = Spacing.p5),
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.p6)
                        .padding(top = Spacing.p4),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
                ) {
                    QuietAction("Count kicks", Modifier.weight(1f), onCountKicks)
                    QuietAction("Contractions", Modifier.weight(1f), onContractions)
                    QuietAction("Hospital bag", Modifier.weight(1f), onBag)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.p6)
                        .padding(top = Spacing.p2),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
                ) {
                    QuietAction("Add note", Modifier.weight(1f)) { adding = LogTab.Notes }
                    QuietAction("Weight", Modifier.weight(1f)) { adding = LogTab.Weight }
                    QuietAction("Photo", Modifier.weight(1f)) { adding = LogTab.Photos }
                }

                Next(state, vm, due, today, Modifier.padding(horizontal = Spacing.p6))
            }

            WhoToCall(
                contacts = state.contacts,
                onAdd = onSettings,
                modifier = Modifier.padding(horizontal = Spacing.p6).padding(top = Spacing.p5),
            )

            SyncStatusRow(
                status = syncStatus,
                onClick = onSettings,
                modifier = Modifier
                    .padding(horizontal = Spacing.p6)
                    .padding(top = Spacing.p5),
            )

            MedicalDisclaimer(
                Modifier
                    .padding(horizontal = Spacing.p6)
                    .padding(top = Spacing.p3, bottom = Spacing.p8)
            )
        }
    }

    if (editing) {
        BabyDetailsSheet(state = state, vm = vm, onDismiss = { editing = false })
    }
    adding?.let { tab ->
        AddEntrySheet(tab = tab, state = state, vm = vm, onDismiss = { adding = null })
    }
    sheetWeek?.let { w ->
        WeekSheet(
            week = w,
            currentWeek = week,
            weeks = vm.weeks,
            dueDate = due,
            onWeek = { sheetWeek = it },
            onDismiss = { sheetWeek = null },
            photos = state.photos,
        )
    }
}

@Composable
private fun Countdown(due: LocalDate, today: LocalDate, modifier: Modifier = Modifier) {
    val extras = LocalBumpExtras.current
    val toGo = Pregnancy.daysToGo(due, today)
    val progress = Pregnancy.progress(due, today)

    Column(modifier) {
        Rule()
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.p3, bottom = Spacing.p2),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(countdownText(toGo), style = MaterialTheme.typography.titleLarge)
            Text(
                due.format(DueDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // A 3dp rule that fills as the weeks pass — the same hairline vocabulary as
        // everything else, rather than a Material progress bar.
        Row(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(extras.track, MaterialTheme.shapes.extraSmall)
        ) {
            Box(
                Modifier
                    .weight(progress.coerceAtLeast(0.001f))
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall)
            )
            Spacer(Modifier.weight((1f - progress).coerceAtLeast(0.001f)))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MicroLabel("${(progress * 100).roundToInt()}% through")
            MicroLabel("Week ${Pregnancy.WEEKS_AT_TERM}")
        }
    }
}

/**
 * Today once the pregnancy view has ended: the birth, frozen where it happened, or a
 * pause that explains nothing and asks nothing. Neither shows a countdown or a size.
 */
@Composable
private fun Ended(state: AppState, due: LocalDate, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.p2)) {
        val born = state.bornOn
        if (born != null && !state.paused) {
            MicroLabel("Born")
            Text(born.format(BornOn), style = MaterialTheme.typography.headlineMedium)
            Text(
                "At ${Pregnancy.week(due, born)}+${Pregnancy.dayOfWeek(due, born)} weeks",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("Tracking is paused", style = MaterialTheme.typography.titleLarge)
            Text(
                "The countdown and weekly size are hidden on both phones. Everything you " +
                    "have logged is kept. Turn it back on in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The numbers you entered, one tap from dialling. It opens the dialler rather than
 * calling, so a stray tap costs nothing and the app needs no call permission.
 */
@Composable
private fun WhoToCall(contacts: List<Contact>, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier) {
        MicroLabel("Who to call")
        if (contacts.isEmpty()) {
            Text(
                "Add your midwife, triage and labour ward numbers",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onAdd)
                    .padding(vertical = Spacing.p3),
            )
            return
        }
        contacts.forEachIndexed { i, c ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Call ${c.label}") { dial(context, c.number) }
                    .padding(vertical = Spacing.p3),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(c.label, style = MaterialTheme.typography.titleMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
                ) {
                    Text(
                        c.number,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        Icons.Filled.Call,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (i < contacts.lastIndex) Rule()
        }
    }
}

internal fun dial(context: Context, number: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
    } catch (e: ActivityNotFoundException) {
        // A phone always has a dialler; a tablet may not. Say so rather than do nothing.
        Toast.makeText(context, "No app here can make calls", Toast.LENGTH_SHORT).show()
    }
}

/**
 * The size band: the drawn mark, the name, the figures, and the length drawn against
 * week 40. The only raised surface on the screen, because it is the one thing you look
 * at rather than read.
 */
@Composable
private fun SizeBand(
    info: com.finn.bump.domain.WeekInfo,
    maxCm: Double,
    onOpenWeek: () -> Unit,
    onEveryWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = LocalBumpExtras.current

    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClickLabel = "Open week ${info.week}", onClick = onOpenWeek),
    ) {
        Rule()
        Column(Modifier.padding(Spacing.p6, Spacing.p5)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MicroLabel("About the size of")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onEveryWeek),
                ) {
                    Text(
                        "Every week",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.p2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.p3),
            ) {
                Box(
                    Modifier
                        .size(76.dp)
                        .border(1.dp, extras.rule, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    WeekMark(info.week, 56.dp)
                }
                Column {
                    Text(info.size, style = MaterialTheme.typography.headlineMedium)
                    if (info.compactFigures.isNotEmpty()) {
                        Text(
                            info.compactFigures,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            info.lengthCm?.let { cm ->
                ScaleBar(
                    fraction = (cm / maxCm).toFloat(),
                    modifier = Modifier.padding(top = Spacing.p4),
                )
                MicroLabel(
                    "To scale — week ${Pregnancy.WEEKS_AT_TERM} fills the width",
                    Modifier.padding(top = Spacing.p2),
                )
            }
        }
        Rule()
    }
}

@Composable
private fun QuietAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(44.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * What is coming: your next booked appointment, then the next thing the NHS schedule
 * suggests. Two lines, because a third would be a list and this is a summary.
 */
@Composable
private fun Next(
    state: AppState,
    vm: BumpViewModel,
    due: LocalDate,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val extras = LocalBumpExtras.current
    val now = System.currentTimeMillis()
    val appointment = state.appointments.filter { it.atMillis >= now }.minByOrNull { it.atMillis }
    val milestone: MilestoneWindow? = remember(due, today) {
        vm.milestones.forPregnancy(due).nextFrom(today)
    }
    if (appointment == null && milestone == null) return

    Column(modifier.padding(top = Spacing.p5)) {
        MicroLabel("Next")
        appointment?.let { a ->
            NextRow(
                dot = extras.appointment,
                title = a.title,
                detail = listOfNotNull(
                    Instant.ofEpochMilli(a.atMillis).atZone(ZoneId.systemDefault())
                        .format(AppointmentAt),
                    a.note.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                divider = milestone != null,
            )
        }
        milestone?.let { w ->
            NextRow(
                dot = w.milestone.kind.color(),
                title = w.milestone.title,
                detail = "${w.milestone.weekLabel} · typical NHS timing",
                divider = false,
            )
        }
    }
}

@Composable
private fun NextRow(dot: Color, title: String, detail: String, divider: Boolean) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = Spacing.p3),
            horizontalArrangement = Arrangement.spacedBy(Spacing.p3),
        ) {
            Dot(dot, modifier = Modifier.padding(top = 7.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (divider) Rule()
    }
}

internal fun trimesterName(trimester: Int): String = when (trimester) {
    1 -> "First trimester"
    2 -> "Second trimester"
    else -> "Third trimester"
}

internal fun countdownText(daysToGo: Int): String = when {
    daysToGo > 1 -> "$daysToGo days to go"
    daysToGo == 1 -> "1 day to go"
    daysToGo == 0 -> "Due today"
    abs(daysToGo) == 1 -> "1 day overdue"
    else -> "${abs(daysToGo)} days overdue"
}
