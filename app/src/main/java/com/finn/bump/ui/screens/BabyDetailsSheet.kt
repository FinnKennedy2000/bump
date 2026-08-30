package com.finn.bump.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.finn.bump.data.AppState
import com.finn.bump.data.BabySex
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.components.DatePickerSheet
import com.finn.bump.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val BabySex.label: String get() = when (this) {
    BabySex.UNKNOWN -> "Don't know yet"
    BabySex.GIRL -> "Girl"
    BabySex.BOY -> "Boy"
    BabySex.SURPRISE -> "Staying a surprise"
}

/** How the sex reads on Today, or null while there is nothing to say. */
internal val BabySex.headline: String? get() = when (this) {
    BabySex.UNKNOWN -> null
    BabySex.GIRL -> "A girl"
    BabySex.BOY -> "A boy"
    BabySex.SURPRISE -> "Staying a surprise"
}

/**
 * Everything you learn about the baby as you go: the name, what you call the bump
 * meanwhile, the sex, and the due date once a scan corrects it.
 *
 * All of it is optional and all of it is editable — a due date from an early guess and
 * a name settled at 30 weeks are the same kind of fact, just arriving at different times.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun BabyDetailsSheet(
    state: AppState,
    vm: BumpViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(state.babyName) }
    var nickname by remember { mutableStateOf(state.babyNickname) }
    var sex by remember { mutableStateOf(state.babySex) }
    var dueDate by remember { mutableStateOf(state.dueDate) }
    var pickingDate by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = Spacing.p4)
                .padding(bottom = Spacing.p8)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.p3),
        ) {
            Text("Your baby", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("Once you've decided") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("Bump name") },
                placeholder = { Text("What you call them meanwhile") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "Sex",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.p2)) {
                BabySex.entries.forEach { option ->
                    FilterChip(
                        selected = sex == option,
                        onClick = { sex = option },
                        label = { Text(option.label) },
                    )
                }
            }

            Text(
                "Due date",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.p2),
            )
            TextButton(onClick = { pickingDate = true }) {
                Text(
                    dueDate?.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.UK))
                        ?: "Not set",
                )
            }
            Text(
                "Changing this moves every week, countdown and milestone in the app. " +
                    "Worth updating if a scan gave you a different date.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    vm.setBabyDetails(name, nickname, sex)
                    // Only write the date if it actually moved — setDueDate refreshes
                    // the widget, and a no-op refresh on every save is wasted work.
                    dueDate?.takeIf { it != state.dueDate }?.let(vm::setDueDate)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }

    if (pickingDate) {
        DatePickerSheet(
            title = "When is the due date?",
            initial = dueDate ?: LocalDate.now().plusMonths(6),
            onDismiss = { pickingDate = false },
            onPicked = { pickingDate = false; dueDate = it },
        )
    }
}
