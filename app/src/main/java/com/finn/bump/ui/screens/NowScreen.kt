package com.finn.bump.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.finn.bump.data.AppState
import com.finn.bump.data.displayName
import com.finn.bump.domain.Pregnancy
import com.finn.bump.domain.nextFrom
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.components.MedicalDisclaimer
import com.finn.bump.ui.components.MoonCard
import com.finn.bump.ui.components.SectionTitle
import androidx.compose.runtime.remember
import com.finn.bump.ui.theme.LocalMoonExtras
import com.finn.bump.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun NowScreen(state: AppState, vm: BumpViewModel, modifier: Modifier = Modifier) {
    val due = state.dueDate ?: return
    val today = LocalDate.now()
    val weeks = vm.weeks

    val week = Pregnancy.week(due, today)
    val day = Pregnancy.dayOfWeek(due, today)
    val toGo = Pregnancy.daysToGo(due, today)
    val info = weeks[week]
    var editing by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.p4),
            verticalArrangement = Arrangement.spacedBy(Spacing.p3),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.p1),
                modifier = Modifier
                    .padding(top = Spacing.p4)
                    .clickable(onClickLabel = "Edit baby details") { editing = true },
            ) {
                Text(
                    text = state.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }

            Text(
                text = "Week $week · Day $day",
                style = MaterialTheme.typography.displaySmall,
            )

            Text(
                text = listOfNotNull(
                    "Trimester ${Pregnancy.trimester(week)}",
                    state.babySex.headline,
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            MoonCard {
                Text("About the size of", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(info.size, style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary)

                val measure = if (info.measuresCrownToHeel) "head to heel" else "head to bottom"
                val length = info.lengthCm?.let { "%.1f cm $measure".format(Locale.UK, it) }
                val weight = info.weightG?.let { formatWeight(it) }
                val figures = listOfNotNull(length, weight)
                if (figures.isNotEmpty()) {
                    Text(
                        figures.joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            MoonCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(countdownText(toGo), style = MaterialTheme.typography.titleLarge)
                    Text(
                        due.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { Pregnancy.progress(due, today) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionTitle("This week")
            MoonCard {
                Text(info.blurb, style = MaterialTheme.typography.bodyLarge)
            }

            NextAppointment(state)

            val nextMilestone = remember(due, today) {
                vm.milestones.forPregnancy(due).nextFrom(today)
            }
            if (nextMilestone != null) {
                SectionTitle("Coming up")
                MilestoneCard(nextMilestone)
            }

            MedicalDisclaimer(Modifier.padding(vertical = Spacing.p6))
        }
    }

    if (editing) {
        BabyDetailsSheet(state = state, vm = vm, onDismiss = { editing = false })
    }
}


@Composable
private fun NextAppointment(state: AppState) {
    val now = System.currentTimeMillis()
    val next = state.appointments.filter { it.atMillis >= now }.minByOrNull { it.atMillis } ?: return
    val extras = LocalMoonExtras.current

    SectionTitle("Next appointment")
    MoonCard {
        Text(next.title, style = MaterialTheme.typography.titleMedium)
        Text(
            Instant.ofEpochMilli(next.atMillis)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.UK)),
            style = MaterialTheme.typography.bodyMedium,
            color = extras.info,
            fontWeight = FontWeight.Bold,
        )
        if (next.note.isNotBlank()) {
            Text(
                next.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatWeight(grams: Int): String =
    if (grams < 1000) "$grams g" else "%.2f kg".format(Locale.UK, grams / 1000.0)

private fun countdownText(daysToGo: Int): String = when {
    daysToGo > 1 -> "$daysToGo days to go"
    daysToGo == 1 -> "1 day to go"
    daysToGo == 0 -> "Due today"
    abs(daysToGo) == 1 -> "1 day overdue"
    else -> "${abs(daysToGo)} days overdue"
}
