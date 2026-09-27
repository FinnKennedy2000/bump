package com.finn.bump.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.finn.bump.domain.Pregnancy
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.components.DatePickerSheet
import com.finn.bump.ui.components.MedicalDisclaimer
import com.finn.bump.ui.components.MicroLabel
import com.finn.bump.ui.components.OutlineButton
import com.finn.bump.ui.components.PrimaryButton
import com.finn.bump.ui.theme.LocalBumpExtras
import com.finn.bump.ui.theme.Spacing
import java.time.LocalDate

/**
 * Two steps: the date, then sharing.
 *
 * The date has two ways in, because people know one or the other — the due date from a
 * scan, or the first day of the last period. Sharing follows because whether a second
 * phone is involved is a day-one question, and it is skippable because plenty of the
 * time it is not.
 *
 * Nothing is written until the flow finishes. That is what lets the join path work: a
 * phone that joins its partner's record must not stamp its own freshly-picked date over
 * the details already in there — see [BumpViewModel.finishOnboarding].
 */
@Composable
fun OnboardingScreen(vm: BumpViewModel) {
    var picked by rememberSaveable { mutableStateOf<LocalDate?>(null) }

    picked?.let { date ->
        SharingFlow(
            vm = vm,
            entry = SharingEntry.Offer,
            stepLabel = "Step 2 of 2",
            onDone = { vm.finishOnboarding(date) },
        )
        return
    }

    DueDateStep(
        onDueDate = { picked = it },
        onLmp = { picked = Pregnancy.dueDateFromLmp(it) },
    )
}

@Composable
private fun DueDateStep(
    onDueDate: (LocalDate) -> Unit,
    onLmp: (LocalDate) -> Unit,
) {
    var picking by remember { mutableStateOf<Mode?>(null) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = Spacing.p7)
                .padding(bottom = Spacing.p8),
        ) {
            Row(
                Modifier.padding(top = Spacing.p12),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
            ) {
                MicroLabel("Bump", color = MaterialTheme.colorScheme.primary)
                MicroLabel("Step 1 of 2")
            }

            Spacer(Modifier.weight(1f))

            Text(
                "One date is all\nit needs.",
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                "The week, the size and the countdown are all worked out from your due " +
                    "date. Nothing about the pregnancy is stored beyond that one value.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.p5),
            )
            WeekRuler(Modifier.padding(top = Spacing.p6))

            Spacer(Modifier.weight(1f))

            PrimaryButton("I know my due date") { picking = Mode.Due }
            OutlineButton("Use my last period instead", Modifier.padding(top = Spacing.p2)) {
                picking = Mode.Lmp
            }
            MedicalDisclaimer(Modifier.padding(top = Spacing.p4))
        }
    }

    when (picking) {
        Mode.Due -> DatePickerSheet(
            title = "When is the due date?",
            initial = LocalDate.now().plusMonths(6),
            onDismiss = { picking = null },
            onPicked = { picking = null; onDueDate(it) },
        )

        Mode.Lmp -> DatePickerSheet(
            title = "First day of your last period",
            helper = "The due date is that date plus ${Pregnancy.TERM_DAYS} days — " +
                "the standard ${Pregnancy.WEEKS_AT_TERM} weeks.",
            initial = LocalDate.now().minusMonths(2),
            selectableUpToToday = true,
            onDismiss = { picking = null },
            onPicked = { picking = null; onLmp(it) },
        )

        null -> Unit
    }
}

/** Forty weeks as forty ticks. The only ornament in the app, and it is the subject. */
@Composable
private fun WeekRuler(modifier: Modifier = Modifier) {
    val extras = LocalBumpExtras.current
    Row(
        modifier.height(40.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        (1..Pregnancy.WEEKS_AT_TERM).forEach { week ->
            val (height, color) = when {
                week == 1 -> 22.dp to MaterialTheme.colorScheme.primary
                week % 5 == 0 -> 14.dp to extras.faint
                else -> 8.dp to MaterialTheme.colorScheme.outline
            }
            Box(Modifier.width(1.dp).height(height).background(color))
        }
        MicroLabel(
            "${Pregnancy.WEEKS_AT_TERM} weeks",
            Modifier.padding(start = Spacing.p2, bottom = 2.dp),
        )
    }
}

private enum class Mode { Due, Lmp }
