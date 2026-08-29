package com.finn.bump.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.finn.bump.ui.components.DatePickerSheet
import com.finn.bump.ui.components.MedicalDisclaimer
import com.finn.bump.ui.theme.Spacing
import java.time.LocalDate

/**
 * Shown whenever no due date is set. Two ways in, because people know one or the other:
 * the due date from a scan, or the first day of the last period.
 */
@Composable
fun OnboardingScreen(
    onDueDate: (LocalDate) -> Unit,
    onLmp: (LocalDate) -> Unit,
) {
    var picking by remember { mutableStateOf<Mode?>(null) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.p6),
            verticalArrangement = Arrangement.spacedBy(Spacing.p4),
        ) {
            Spacer(Modifier.weight(1f))

            Text("Bump", style = MaterialTheme.typography.displaySmall)
            Text(
                "Track the weeks, the size, and how long is left.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { picking = Mode.Due },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("I know the due date") }

            OutlinedButton(
                onClick = { picking = Mode.Lmp },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Use my last period instead") }

            MedicalDisclaimer()
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
            helper = "The due date is that date plus 280 days — the standard 40 weeks.",
            initial = LocalDate.now().minusMonths(2),
            selectableUpToToday = true,
            onDismiss = { picking = null },
            onPicked = { picking = null; onLmp(it) },
        )

        null -> Unit
    }
}

private enum class Mode { Due, Lmp }
