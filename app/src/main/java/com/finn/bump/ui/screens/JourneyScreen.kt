package com.finn.bump.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.finn.bump.data.AppState
import com.finn.bump.domain.MilestoneKind
import com.finn.bump.domain.MilestoneWindow
import com.finn.bump.domain.Pregnancy
import com.finn.bump.domain.WeekInfo
import com.finn.bump.domain.on
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.components.Dot
import com.finn.bump.ui.components.MedicalDisclaimer
import com.finn.bump.ui.components.MicroLabel
import com.finn.bump.ui.components.PaperCard
import com.finn.bump.ui.components.Rule
import com.finn.bump.ui.components.RuledHeading
import com.finn.bump.ui.components.ScaleBar
import com.finn.bump.ui.components.SegmentedToggle
import com.finn.bump.ui.components.WeekMark
import com.finn.bump.ui.theme.LocalBumpExtras
import com.finn.bump.ui.theme.Spacing
import com.finn.bump.ui.theme.color
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val MonthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK)
private val DayTitle = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK)
private val Clock = DateTimeFormatter.ofPattern("HH:mm", Locale.UK)
private val Range = DateTimeFormatter.ofPattern("d MMM", Locale.UK)

/** Timeline is the default: a pregnancy runs in weeks. The month grid is for picking a date. */
private enum class View(val label: String) { Timeline("Timeline"), Month("Month") }

@Composable
fun JourneyScreen(
    state: AppState,
    vm: BumpViewModel,
    onEveryWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val due = state.dueDate ?: return
    val today = LocalDate.now()
    val currentWeek = Pregnancy.week(due, today)
    var view by remember { mutableStateOf(View.Timeline) }
    var sheetWeek by remember { mutableStateOf<Int?>(null) }
    var prefill by remember { mutableStateOf<Prefill?>(null) }

    // 20-odd entries, cheap to resolve and impossible to leave stale.
    val windows = remember(due) { vm.milestones.forPregnancy(due) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier.fillMaxSize()) {
            Column(Modifier.padding(Spacing.p6, Spacing.p1, Spacing.p6, Spacing.p4)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Journey", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Size chart",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onEveryWeek),
                    )
                }
                SegmentedToggle(
                    options = View.entries,
                    selected = view,
                    onSelect = { view = it },
                    label = { it.label },
                    modifier = Modifier.padding(top = Spacing.p3),
                )
            }

            when (view) {
                View.Timeline -> Timeline(
                    vm = vm,
                    due = due,
                    currentWeek = currentWeek,
                    windows = windows,
                    state = state,
                    onOpenWeek = { sheetWeek = it },
                )

                View.Month -> MonthView(
                    state = state,
                    due = due,
                    today = today,
                    windows = windows,
                    onBook = { prefill = it },
                )
            }
        }
    }

    sheetWeek?.let { w ->
        WeekSheet(
            week = w,
            currentWeek = currentWeek,
            weeks = vm.weeks,
            dueDate = due,
            onWeek = { sheetWeek = it },
            onDismiss = { sheetWeek = null },
            photos = state.photos,
        )
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

// ---------------------------------------------------------------- timeline

/**
 * The pregnancy as one spine: every week from 3 to 40, grouped by trimester, with the
 * measured size as the thing that runs down it. This is where the old Sizes list went.
 */
@Composable
private fun Timeline(
    vm: BumpViewModel,
    due: LocalDate,
    currentWeek: Int,
    windows: List<MilestoneWindow>,
    state: AppState,
    onOpenWeek: (Int) -> Unit,
) {
    val weeks = remember(vm.weeks) { (3..vm.weeks.lastWeek).map { vm.weeks[it] } }
    val maxCm = vm.weeks.maxLengthCm
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (currentWeek - 3).coerceIn(0, weeks.lastIndex),
    )
    val byWeek = remember(windows) { windows.groupBy { it.milestone.fromWeek } }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.p8),
    ) {
        items(
            count = weeks.size,
            key = { weeks[it].week },
        ) { index ->
            val info = weeks[index]
            val trimester = Pregnancy.trimester(info.week)
            val startsTrimester = index == 0 ||
                Pregnancy.trimester(weeks[index - 1].week) != trimester

            if (startsTrimester) {
                RuledHeading(
                    label = trimesterName(trimester),
                    trailing = trimesterRange(trimester),
                    modifier = Modifier
                        .padding(horizontal = Spacing.p6)
                        .padding(top = if (index == 0) 0.dp else Spacing.p5, bottom = Spacing.p3),
                )
            }

            TimelineRow(
                info = info,
                maxCm = maxCm,
                isCurrent = info.week == currentWeek,
                isPast = info.week < currentWeek,
                due = due,
                milestones = byWeek[info.week].orEmpty(),
                onOpenWeek = onOpenWeek,
            )
        }
        item {
            MedicalDisclaimer(
                Modifier.padding(horizontal = Spacing.p6).padding(top = Spacing.p4)
            )
        }
    }
}

@Composable
private fun TimelineRow(
    info: WeekInfo,
    maxCm: Double,
    isCurrent: Boolean,
    isPast: Boolean,
    due: LocalDate,
    milestones: List<MilestoneWindow>,
    onOpenWeek: (Int) -> Unit,
) {
    val extras = LocalBumpExtras.current
    val start = due.minusDays((Pregnancy.TERM_DAYS - info.week * 7).toLong())

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Week ${info.week}") { onOpenWeek(info.week) }
            .padding(start = Spacing.p6, end = Spacing.p6, bottom = Spacing.p5)
            .alpha(if (isPast) 0.55f else 1f),
    ) {
        Text(
            "${info.week}",
            style = MaterialTheme.typography.titleSmall,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.End,
            modifier = Modifier.width(30.dp),
        )
        // The spine: a hairline down the column with this week's node on it.
        Box(Modifier.width(20.dp).padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.width(1.dp).height(if (isCurrent) 120.dp else 60.dp).background(extras.rule))
            Box(
                Modifier
                    .size(if (isCurrent) 11.dp else 7.dp)
                    .background(
                        if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
                        CircleShape,
                    )
                    .then(
                        if (isCurrent) Modifier
                        else Modifier.border(1.dp, extras.faint, CircleShape)
                    )
            )
        }

        Column(Modifier.weight(1f).padding(start = Spacing.p2)) {
            if (isCurrent) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
                ) {
                    MicroLabel("This week", color = MaterialTheme.colorScheme.primary)
                    Text(
                        "${start.format(Range)} – ${start.plusDays(6).format(Range)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
                modifier = Modifier.padding(top = if (isCurrent) Spacing.p1 else 0.dp),
            ) {
                WeekMark(info.week, if (isCurrent) 46.dp else 34.dp)
                Text(
                    info.size,
                    style = if (isCurrent) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                )
            }

            info.lengthCm?.let { cm ->
                ScaleBar(
                    fraction = (cm / maxCm).toFloat(),
                    modifier = Modifier.padding(top = Spacing.p2),
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else extras.faint,
                    thickness = if (isCurrent) 3.dp else 2.dp,
                    capHeight = if (isCurrent) 13.dp else 11.dp,
                )
                Text(
                    info.compactFigures,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.p1),
                )
            }

            if (isCurrent) {
                Text(
                    info.blurb,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Spacing.p2),
                )
            }

            if (milestones.isNotEmpty()) {
                if (isCurrent) {
                    milestones.forEach {
                        MilestoneCard(it, Modifier.padding(top = Spacing.p3))
                    }
                } else {
                    MilestoneChips(milestones, Modifier.padding(top = Spacing.p2))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MilestoneChips(milestones: List<MilestoneWindow>, modifier: Modifier = Modifier) {
    val extras = LocalBumpExtras.current
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.p1),
        verticalArrangement = Arrangement.spacedBy(Spacing.p1),
    ) {
        milestones.forEach { w ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.p1),
                modifier = Modifier
                    .border(1.dp, extras.rule, MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = Spacing.p2, vertical = Spacing.p1),
            ) {
                Dot(w.milestone.kind.color(), 5.dp)
                Text(
                    w.milestone.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = w.milestone.kind.textColor(),
                )
            }
        }
    }
}

private fun trimesterRange(trimester: Int): String = when (trimester) {
    1 -> "3–12"
    2 -> "13–27"
    else -> "28–40"
}

// ---------------------------------------------------------------- month

@Composable
private fun MonthView(
    state: AppState,
    due: LocalDate,
    today: LocalDate,
    windows: List<MilestoneWindow>,
    onBook: (Prefill) -> Unit,
) {
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.p6),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = Spacing.p3),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(month.atDay(1).format(MonthTitle), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.p1)) {
                StepBox(Icons.Filled.ChevronLeft, "Previous month") { month = month.minusMonths(1) }
                StepBox(Icons.Filled.ChevronRight, "Next month") { month = month.plusMonths(1) }
            }
        }

        MonthGrid(
            month = month,
            today = today,
            due = due,
            selected = selected,
            onSelect = { selected = it },
            marks = { date -> marksFor(date, state, windows) },
        )

        Legend(Modifier.padding(top = Spacing.p4, bottom = Spacing.p3))
        Rule()

        DayDetail(
            date = selected,
            state = state,
            windows = windows,
            due = due,
            onBook = onBook,
        )

        MedicalDisclaimer(Modifier.padding(top = Spacing.p4, bottom = Spacing.p8))
    }
}

@Composable
private fun StepBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(32.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp))
    }
}

/** Which coloured dots a day cell shows. Order is stable so cells don't jitter. */
private class DayMarks(
    val milestoneKinds: List<MilestoneKind>,
    val hasAppointment: Boolean,
    val hasLog: Boolean,
) {
    val isEmpty: Boolean get() = milestoneKinds.isEmpty() && !hasAppointment && !hasLog
}

private fun marksFor(
    date: LocalDate,
    state: AppState,
    windows: List<MilestoneWindow>,
): DayMarks = DayMarks(
    milestoneKinds = windows.on(date).map { it.milestone.kind }.distinct(),
    hasAppointment = state.appointments.any { it.atMillis.toLocalDate() == date },
    hasLog = state.weights.any { it.date == date } ||
        state.notes.any { it.createdAtMillis.toLocalDate() == date } ||
        state.kickSessions.any { it.startedAtMillis.toLocalDate() == date },
)

internal fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    due: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    marks: (LocalDate) -> DayMarks,
) {
    // Monday-first, matching UK convention and NHS notes.
    val weekdays = remember { (1..7).map { DayOfWeek.of(it) } }

    Column {
        Row(Modifier.fillMaxWidth().padding(bottom = Spacing.p1)) {
            weekdays.forEach { d ->
                MicroLabel(
                    d.getDisplayName(TextStyle.NARROW, Locale.UK),
                    Modifier.weight(1f),
                )
            }
        }

        val first = month.atDay(1)
        // How many blanks before the 1st: Monday=1, so a Wednesday 1st leaves 2 blanks.
        val lead = first.dayOfWeek.value - 1
        val rows = (lead + month.lengthOfMonth() + 6) / 7

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
                                isDue = date == due,
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
    val extras = LocalBumpExtras.current
    val background = when {
        isSelected -> scheme.primary
        isDue -> scheme.primaryContainer
        else -> Color.Transparent
    }
    val content = when {
        isSelected -> scheme.onPrimary
        isToday || isDue -> scheme.primary
        else -> scheme.onBackground
    }

    Box(
        Modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .background(background, MaterialTheme.shapes.small)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(1.dp, scheme.primary, MaterialTheme.shapes.small)
                } else Modifier
            )
            .clickable(onClick = onClick, onClickLabel = date.format(DayTitle)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = content,
            )
            if (!marks.isEmpty) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    marks.milestoneKinds.take(2).forEach {
                        Dot(if (isSelected) scheme.onPrimary else it.color(), 4.dp)
                    }
                    if (marks.hasAppointment) {
                        Dot(if (isSelected) scheme.onPrimary else extras.appointment, 4.dp)
                    }
                    if (marks.hasLog) {
                        Dot(if (isSelected) scheme.onPrimary else extras.label, 4.dp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(modifier: Modifier = Modifier) {
    val extras = LocalBumpExtras.current
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.p3),
        verticalArrangement = Arrangement.spacedBy(Spacing.p1),
    ) {
        val entries = listOf(
            "Scan" to extras.scan,
            "Appointment" to extras.appointment,
            "Test" to extras.test,
            "Admin" to extras.admin,
            "Your log" to extras.label,
        )
        entries.forEach { (label, color) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.p1),
            ) {
                Dot(color, 5.dp)
                MicroLabel(label)
            }
        }
    }
}

@Composable
private fun DayDetail(
    date: LocalDate,
    state: AppState,
    windows: List<MilestoneWindow>,
    due: LocalDate,
    onBook: (Prefill) -> Unit,
) {
    val week = Pregnancy.week(due, date)
    val day = Pregnancy.dayOfWeek(due, date)

    Row(
        Modifier.fillMaxWidth().padding(top = Spacing.p4),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(date.format(DayTitle), style = MaterialTheme.typography.titleSmall)
        Text(
            if (date == due) "Due date · $week+$day" else "$week+$day",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    val appointments = state.appointments
        .filter { it.atMillis.toLocalDate() == date }
        .sortedBy { it.atMillis }

    appointments.forEach { a ->
        PaperCard(Modifier.padding(top = Spacing.p3)) {
            Text(a.title, style = MaterialTheme.typography.titleMedium)
            Text(
                Instant.ofEpochMilli(a.atMillis).atZone(ZoneId.systemDefault()).format(Clock),
                style = MaterialTheme.typography.labelLarge,
                color = LocalBumpExtras.current.appointment,
            )
            if (a.note.isNotBlank()) {
                Text(
                    a.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    val onThisDay = windows.on(date)
    onThisDay.forEach { w ->
        MilestoneCard(w, Modifier.padding(top = Spacing.p3)) {
            onBook(Prefill(w.milestone.title, date))
        }
    }

    if (appointments.isEmpty() && onThisDay.isEmpty()) {
        Text(
            "Nothing on this day.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.p3),
        )
    }

    if (onThisDay.isNotEmpty()) {
        Text(
            "Typical NHS schedule — your midwife will confirm your actual dates.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.p3),
        )
    }
}

/**
 * A milestone as an object you can act on — the one place a card still earns its border,
 * because turning a suggestion into a real appointment is a decision.
 *
 * Nothing here is booked. It only becomes data when [onBook] is taken.
 */
@Composable
internal fun MilestoneCard(
    window: MilestoneWindow,
    modifier: Modifier = Modifier,
    onBook: (() -> Unit)? = null,
) {
    PaperCard(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
        ) {
            Dot(window.milestone.kind.color())
            MicroLabel("${window.milestone.kind.label} · ${window.milestone.weekLabel}")
        }
        Text(window.milestone.title, style = MaterialTheme.typography.titleMedium)
        Text(
            window.milestone.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onBook != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.p1),
                modifier = Modifier.clickable(onClick = onBook).padding(top = Spacing.p1),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    "Add to my appointments",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

internal val MilestoneKind.label: String get() = when (this) {
    MilestoneKind.SCAN -> "Scan"
    MilestoneKind.APPOINTMENT -> "Appointment"
    MilestoneKind.TEST -> "Test"
    MilestoneKind.CLASS -> "Class"
    MilestoneKind.ADMIN -> "Admin"
}

/** The kind's colour, swapped for a readable variant where the dot hue is too light for text. */
@Composable
private fun MilestoneKind.textColor(): Color {
    val extras = LocalBumpExtras.current
    return if (this == MilestoneKind.ADMIN) extras.adminText else color()
}
