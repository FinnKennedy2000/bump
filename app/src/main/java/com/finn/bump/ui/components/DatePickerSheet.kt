package com.finn.bump.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.finn.bump.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * M3's date picker in a bottom sheet.
 *
 * The picker works in UTC millis while the app works in [LocalDate], so both
 * conversions pin to UTC. Going through the device zone would shift the date by a day
 * for anyone east or west of UTC at the wrong hour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(
    title: String,
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit,
    helper: String? = null,
    selectableUpToToday: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val todayUtc = LocalDate.now().toEpochDay() * MILLIS_PER_DAY

    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.toEpochDay() * MILLIS_PER_DAY,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                !selectableUpToToday || utcTimeMillis <= todayUtc
        },
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = Spacing.p4, vertical = Spacing.p2)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (helper != null) {
                Text(
                    helper,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.p1),
                )
            }

            DatePicker(state = pickerState, title = null, headline = null, showModeToggle = false)

            Button(
                onClick = {
                    pickerState.selectedDateMillis?.let { onPicked(it.toLocalDateUtc()) }
                },
                enabled = pickerState.selectedDateMillis != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.p6),
            ) { Text("Save") }
        }
    }
}

private const val MILLIS_PER_DAY = 86_400_000L

private fun Long.toLocalDateUtc(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
