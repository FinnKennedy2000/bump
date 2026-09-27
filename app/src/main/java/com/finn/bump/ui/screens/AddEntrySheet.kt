package com.finn.bump.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.finn.bump.data.AppState
import com.finn.bump.data.Appointment
import com.finn.bump.data.Note
import com.finn.bump.data.Photo
import com.finn.bump.data.WeightEntry
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.components.DatePickerSheet
import com.finn.bump.ui.components.OutlineButton
import com.finn.bump.ui.components.PhotoImage
import com.finn.bump.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Prefilled title and date, used when a calendar milestone becomes a real appointment. */
internal data class Prefill(val title: String, val date: LocalDate)

/**
 * One sheet for every entry type. They share a shape — a couple of fields and a date —
 * so near-identical sheets would be several places to fix the same bug.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddEntrySheet(
    tab: LogTab,
    state: AppState,
    vm: BumpViewModel,
    onDismiss: () -> Unit,
    prefill: Prefill? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(prefill?.title.orEmpty()) }
    var note by remember { mutableStateOf("") }
    var kg by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(prefill?.date ?: LocalDate.now()) }
    var time by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var pickingDate by remember { mutableStateOf(false) }
    var blob by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }

    // The system Photo Picker: no storage permission, and the app only ever sees the one
    // picture chosen.
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            importing = true
            importError = null
            vm.importPhoto(uri) { result ->
                importing = false
                result.onSuccess { blob = it }
                    .onFailure { importError = it.message ?: "That picture could not be opened" }
            }
        }
    }
    val pick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }

    val kgValue = kg.replace(',', '.').toDoubleOrNull()
    val canSave = when (tab) {
        LogTab.Appointments -> title.isNotBlank()
        // A plausibility bound, not a medical one: it only catches slips like a
        // misplaced decimal point, which would otherwise wreck the chart's scale.
        LogTab.Weight -> kgValue != null && kgValue in 20.0..300.0
        LogTab.Notes -> note.isNotBlank()
        LogTab.Photos -> blob != null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = Spacing.p4)
                .padding(bottom = Spacing.p8)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.p3),
        ) {
            Text(
                "Add ${tab.label.dropLast(1).lowercase(Locale.UK)}",
                style = MaterialTheme.typography.titleLarge,
            )

            when (tab) {
                LogTab.Appointments -> {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("What is it?") },
                        placeholder = { Text("20 week scan") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { pickingDate = true }) {
                        Text(date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.UK)))
                    }
                    TimeRow(time) { time = it }
                }

                LogTab.Weight -> {
                    OutlinedTextField(
                        value = kg,
                        onValueChange = { kg = it },
                        label = { Text("Weight (kg)") },
                        singleLine = true,
                        isError = kg.isNotBlank() && !canSave,
                        supportingText = if (kg.isNotBlank() && !canSave) {
                            { Text("Enter a weight between 20 and 300 kg") }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { pickingDate = true }) {
                        Text(date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.UK)))
                    }
                }

                LogTab.Notes -> OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("How are things?") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )

                LogTab.Photos -> {
                    blob?.let {
                        PhotoImage(
                            blob = it,
                            maxPx = 1024,
                            contentDescription = "The chosen photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                        )
                    }
                    OutlineButton(
                        when {
                            importing -> "Opening…"
                            blob == null -> "Choose a photo"
                            else -> "Choose a different photo"
                        },
                        onClick = { if (!importing) pick() },
                    )
                    importError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Caption (optional)") },
                        placeholder = { Text("20 week scan") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { pickingDate = true }) {
                        Text(date.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.UK)))
                    }
                }
            }

            Button(
                enabled = canSave,
                onClick = {
                    when (tab) {
                        LogTab.Appointments -> vm.addAppointment(
                            Appointment(
                                title = title.trim(),
                                atMillis = date.atTime(time)
                                    .atZone(ZoneId.systemDefault())
                                    .toInstant()
                                    .toEpochMilli(),
                                note = note.trim(),
                            )
                        )

                        LogTab.Weight -> vm.addWeight(
                            WeightEntry(date = date, kg = kgValue!!)
                        )

                        LogTab.Notes -> vm.addNote(
                            Note(
                                createdAtMillis = System.currentTimeMillis(),
                                week = currentWeek(state),
                                text = note.trim(),
                            )
                        )

                        LogTab.Photos -> vm.addPhoto(
                            Photo(date = date, caption = note, blob = blob!!)
                        )
                    }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }

    if (pickingDate) {
        DatePickerSheet(
            title = "Pick a date",
            initial = date,
            onDismiss = { pickingDate = false },
            onPicked = { pickingDate = false; date = it },
        )
    }
}

/** Hour/minute stepping. M3 has a TimePicker, but this is two taps instead of a dialog. */
@Composable
private fun TimeRow(time: LocalTime, onChange: (LocalTime) -> Unit) {
    Column {
        Text("Time", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
        ) {
            TextButton(onClick = { onChange(time.minusMinutes(30)) }) { Text("−30m") }
            Text(
                time.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = Spacing.p3),
            )
            TextButton(onClick = { onChange(time.plusMinutes(30)) }) { Text("+30m") }
        }
    }
}
