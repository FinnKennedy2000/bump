package com.finn.bump.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.finn.bump.data.AppState
import com.finn.bump.domain.MilestoneKind
import com.finn.bump.domain.MilestoneWindow
import com.finn.bump.domain.Pregnancy
import com.finn.bump.domain.on
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.components.MedicalDisclaimer
import com.finn.bump.ui.components.MoonCard
import com.finn.bump.ui.theme.LocalMoonExtras
import com.finn.bump.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val MonthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK)
private val DayTitle = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)
private val AppointmentTime = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)

@Composable
fun CalendarScreen(
    state: AppState,
    vm: BumpViewModel,
    modifier: Modifier = Modifier,
) {
    val dueDate = state.dueDate ?: return
    val today = LocalDate.now()

    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    var prefill by remember { mutableStateOf<Prefill?>(null) }

    // Cheap enough to recompute per due date (20 entries), and it can never drift.
    val windows = remember(dueDate) { vm.milestones.forPregnancy(dueDate) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.p4),
            verticalArrangement = Arrangement.spacedBy(Spacing.p3),
        ) {
            MonthHeader(
                month = month,
                onPrev = { month = month.minusMonths(1) },
                onNext = { month = month.plusMonths(1) },
                onToday = { month = YearMonth.from(today); selected = today },
            )

            MonthGrid(
                month = month,
                today = today,
                dueDate = dueDate,
                selected = selected,
                onSelect = { selected = it },
                marks = { date -> marksFor(date, state, windows, dueDate) },
            )

            Legend()

            DayDetail(
                date = selected,
                state = state,
                windows = windows,
                dueDate = dueDate,
                onBook = { prefill = it },
            )

            MedicalDisclaimer(Modifier.padding(bottom = Spacing.p6))
        }
    }

    prefill?.let {
        AddEntrySheet(
            tab = LogTab.Appointments,
            state = state,
            vm = vm,
            prefill = it,
            onDismiss = { prefill = null },
        )
    }
}

/** Which coloured dots a day cell shows. Order is stable so cells don't jitter. */
private class DayMarks(val milestoneKinds: List<MilestoneKind>, val hasAppointment: Boolean, val hasLog: Boolean) {
    val isEmpty: Boolean get() = milestoneKinds.isEmpty() && !hasAppointment && !hasLog
}

private fun marksFor(
    date: LocalDate,
    state: AppState,
    windows: List<MilestoneWindow>,
    dueDate: LocalDate,
): DayMarks = DayMarks(
    milestoneKinds = windows.on(date).map { it.milestone.kind }.distinct(),
    hasAppointment = state.appointments.any { it.atMillis.toLocalDate() == date },
    hasLog = state.weights.any { it.date == date } ||
        state.notes.any { it.createdAtMillis.toLocalDate() == date },
)

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.p4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
        }
        Text(
            text = month.atDay(1).format(MonthTitle),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
        }
    }
    TextButton(onClick = onToday) { Text("Today") }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    dueDate: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    marks: (LocalDate) -> DayMarks,
) {
    // Monday-first, matching UK convention and NHS notes.
    val weekdays = remember { (1..7).map { java.time.DayOfWeek.of(it) } }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.p1)) {
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { d ->
                Text(
                    text = d.getDisplayName(TextStyle.NARROW, Locale.UK),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        val first = month.atDay(1)
        // How many blanks before the 1st: Monday=1, so a Wednesday 1st leaves 2 blanks.
        val lead = first.dayOfWeek.value - 1
        val cells = lead + month.lengthOfMonth()
        val rows = (cells + 6) / 7

        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val dayIndex = row * 7 + col - lead
                    Box(Modifier.weight(1f)) {
                        if (dayIndex in 0 until month.lengthOfMonth()) {
                            val date = first.plusDays(dayIndex.toLong())
                            DayCell(
                                date = date,
                                isToday = date == today,
                                isDue = date == dueDate,
                                isSelected = date == selected,
                                marks = marks(date),
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isDue: Boolean,
    isSelected: Boolean,
    marks: DayMarks,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val background = when {
        isSelected -> scheme.primary
        isDue -> scheme.primaryContainer
        else -> Color.Transparent
    }
    val content = when {
        isSelected -> scheme.onPrimary
        isToday -> scheme.primary
        else -> scheme.onSurface
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(Spacing.p1)
            .background(background, MaterialTheme.shapes.small)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(1.dp, scheme.primary, MaterialTheme.shapes.small)
                } else Modifier
            )
            .clickable(
                onClick = onClick,
                onClickLabel = date.format(DayTitle),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isDue) FontWeight.Bold else FontWeight.Normal,
                color = content,
            )
            if (!marks.isEmpty) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    marks.milestoneKinds.take(2).forEach { Dot(it.color()) }
                    if (marks.hasAppointment) Dot(scheme.primary)
                    if (marks.hasLog) Dot(scheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(Modifier.size(4.dp).background(color, CircleShape))
}

@Composable
private fun MilestoneKind.color(): Color = when (this) {
    MilestoneKind.SCAN -> MaterialTheme.colorScheme.secondary
    MilestoneKind.APPOINTMENT -> LocalMoonExtras.current.info
    MilestoneKind.TEST -> LocalMoonExtras.current.success
    MilestoneKind.CLASS -> LocalMoonExtras.current.accentPink
    MilestoneKind.ADMIN -> LocalMoonExtras.current.warning
}

private val MilestoneKind.label: String get() = when (this) {
    MilestoneKind.SCAN -> "Scan"
    MilestoneKind.APPOINTMENT -> "Appointment"
    MilestoneKind.TEST -> "Test"
    MilestoneKind.CLASS -> "Class"
    MilestoneKind.ADMIN -> "Admin"
}

@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
    ) {
        MilestoneKind.entries.forEach { kind ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.p1),
            ) {
                Dot(kind.color())
                Text(
                    kind.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DayDetail(
    date: LocalDate,
    state: AppState,
    windows: List<MilestoneWindow>,
    dueDate: LocalDate,
    onBook: (Prefill) -> Unit,
) {
    val week = Pregnancy.week(dueDate, date)
    val day = Pregnancy.dayOfWeek(dueDate, date)
    val due = date == dueDate

    Text(
        text = date.format(DayTitle),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = Spacing.p2),
    )
    Text(
        text = if (due) "Due date · $week+$day" else "$week+$day",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val appointments = state.appointments
        .filter { it.atMillis.toLocalDate() == date }
        .sortedBy { it.atMillis }

    appointments.forEach { a ->
        MoonCard {
            Text(a.title, style = MaterialTheme.typography.titleMedium)
            Text(
                Instant.ofEpochMilli(a.atMillis)
                    .atZone(ZoneId.systemDefault())
                    .format(AppointmentTime),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (a.note.isNotBlank()) {
                Text(a.note, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    val onThisDay = windows.on(date)
    onThisDay.forEach { w ->
        MilestoneCard(w) { onBook(Prefill(w.milestone.title, date)) }
    }

    if (appointments.isEmpty() && onThisDay.isEmpty()) {
        Text(
            "Nothing on this day.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (onThisDay.isNotEmpty()) {
        Text(
            "Typical NHS schedule — your midwife will confirm your actual dates.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun MilestoneCard(window: MilestoneWindow, onBook: (() -> Unit)? = null) {
    MoonCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
        ) {
            Dot(window.milestone.kind.color())
            Text(
                window.milestone.weekLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(window.milestone.title, style = MaterialTheme.typography.titleMedium)
        Text(window.milestone.detail, style = MaterialTheme.typography.bodyMedium)
        if (onBook != null) {
            TextButton(onClick = onBook, modifier = Modifier.align(Alignment.End)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Add to my appointments")
            }
        }
    }
}
