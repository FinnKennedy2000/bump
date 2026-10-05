package com.finn.bump.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finn.bump.data.AppState
import com.finn.bump.data.sync.SyncStatus
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.components.BackRow
import com.finn.bump.ui.components.DatePickerSheet
import com.finn.bump.ui.components.Dot
import com.finn.bump.ui.components.MicroLabel
import com.finn.bump.ui.components.Rule
import com.finn.bump.ui.components.syncStatusWords
import com.finn.bump.ui.theme.LocalBumpExtras
import com.finn.bump.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DueDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK)

/**
 * Where sharing lives once onboarding is over, plus the handful of values that are not
 * derived from the due date.
 *
 * The sharing block leads with the last sync in words: a sync that quietly stopped is
 * the failure that costs you something, and it has to be noticed without being looked for.
 */
@Composable
fun SettingsScreen(
    state: AppState,
    vm: BumpViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pairingEntry by remember { mutableStateOf<SharingEntry?>(null) }
    var editingBaby by remember { mutableStateOf(false) }
    var pickingBirth by remember { mutableStateOf(false) }

    BackHandler(enabled = pairingEntry == null, onBack = onBack)

    pairingEntry?.let { entry ->
        SharingFlow(vm = vm, entry = entry, onDone = { pairingEntry = null }, modifier = modifier)
        return
    }

    val pairing by vm.pairing.collectAsStateWithLifecycle()
    val status by vm.syncStatus.collectAsStateWithLifecycle()

    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(bottom = Spacing.p10),
        ) {
            BackRow("Today", Modifier.padding(horizontal = Spacing.p6), onClick = onBack)
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .padding(horizontal = Spacing.p6)
                    .padding(top = Spacing.p2, bottom = Spacing.p5),
            )

            SharingBlock(
                code = pairing?.toCode(),
                status = status,
                onSyncNow = vm::syncNow,
                onUnpair = vm::unpair,
                onCreate = { pairingEntry = SharingEntry.Create },
                onJoin = { pairingEntry = SharingEntry.Join },
            )

            Section("Pregnancy") {
                SettingRow("Due date", state.dueDate?.format(DueDate) ?: "Not set") {
                    editingBaby = true
                }
                SettingRow("Name", state.babyName.ifBlank { "Not decided" }) {
                    editingBaby = true
                }
                SettingRow("Sex", state.babySex.label) { editingBaby = true }
                SettingRow("Baby arrived", state.bornOn?.format(DueDate) ?: "Not yet") {
                    pickingBirth = true
                }
                if (state.bornOn != null) {
                    TextAction("Clear the birth date") { vm.setBornOn(null) }
                    Rule()
                }
                SettingRow("Pause tracking", if (state.paused) "Paused" else "Off", last = true) {
                    vm.setPaused(!state.paused)
                }
                Text(
                    "Hides the countdown and weekly size on both phones. Nothing is deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

            }

            Advanced(
                baseUrl = vm.baseUrl.collectAsStateWithLifecycle().value,
                editable = pairing == null,
                onBaseUrl = vm::setBaseUrl,
            )
        }
    }

    if (editingBaby) {
        BabyDetailsSheet(state = state, vm = vm, onDismiss = { editingBaby = false })
    }
    if (pickingBirth) {
        DatePickerSheet(
            title = "Baby arrived",
            initial = state.bornOn ?: LocalDate.now(),
            helper = "Ends the countdown on both phones. You can clear it in Settings.",
            selectableUpToToday = true,
            onDismiss = { pickingBirth = false },
            onPicked = {
                vm.setBornOn(it)
                pickingBirth = false
            },
        )
    }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.p3),
    )
}

/** 4d and 4e: the same block, before and after pairing. */
@Composable
private fun SharingBlock(
    code: String?,
    status: SyncStatus,
    onSyncNow: () -> Unit,
    onUnpair: () -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
) {
    val context = LocalContext.current
    val extras = LocalBumpExtras.current

    Rule()
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.p6, vertical = Spacing.p5),
        verticalArrangement = Arrangement.spacedBy(Spacing.p3),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MicroLabel("Sharing")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
            ) {
                Dot(
                    when (status) {
                        SyncStatus.Off -> extras.faint
                        is SyncStatus.Failed -> MaterialTheme.colorScheme.error
                        else -> extras.test
                    }
                )
                Text(
                    syncStatusWords(status),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (status is SyncStatus.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        if (code == null) {
            Text(
                "Only this phone has the record. Sharing puts a second phone on the same one.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.p2)) {
                SmallButton("Start sharing", Modifier.weight(1f), filled = true, onClick = onCreate)
                SmallButton("I have a code", Modifier.weight(1f), onClick = onJoin)
            }
        } else {
            Text(
                "This phone is sharing with one other. Send the code again if they lose it.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, extras.rule, MaterialTheme.shapes.small)
                    .padding(Spacing.p3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.p3),
            ) {
                Column(Modifier.weight(1f)) {
                    MicroLabel("Sharing code")
                    Text(
                        code,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SmallButton("Copy") { copySharingCode(context, code) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.p2)) {
                SmallButton(
                    "Send to partner",
                    Modifier.weight(1f),
                    filled = true,
                ) { sendSharingCode(context, code) }
                SmallButton("Sync now", onClick = onSyncNow)
            }
            // Unpairing leaves the local document alone, so this is not a destructive
            // action however it reads — this phone simply stops sending and receiving.
            Text(
                "Stop sharing on this phone",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onUnpair)
                    .padding(top = Spacing.p1),
            )
        }
    }
    Rule()
}

/**
 * The server address, kept out of the way.
 *
 * Onboarding uses the default and says nothing about it; this is here so a redeploy of
 * the backend is an edit rather than a rebuild. Locked while paired: the address is part
 * of the code the other phone holds, so changing it under a live pairing only breaks it.
 */
@Composable
private fun Advanced(baseUrl: String, editable: Boolean, onBaseUrl: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }

    var draft by remember(baseUrl) { mutableStateOf(baseUrl) }

    Section("Advanced") {
        // Saved on collapse rather than per keystroke: this is a DataStore write, and a
        // half-typed URL is not an address anyone wants persisted.
        SettingRow("Server", if (open) "" else baseUrl, last = true) {
            if (open && draft.isNotBlank()) onBaseUrl(draft)
            open = !open
        }
        if (open) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                enabled = editable,
                singleLine = true,
                label = { Text("Server") },
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.p2),
            )
            Text(
                if (editable) {
                    "Only used when starting sharing from this phone."
                } else {
                    "Stop sharing to change this — the address is part of the code your " +
                        "partner already has."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.p3),
            )
        }
    }
}

@Composable
private fun Section(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.p6).padding(top = Spacing.p5)) {
        MicroLabel(label, Modifier.padding(bottom = Spacing.p1))
        content()
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    last: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(vertical = Spacing.p3),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            // A server address is longer than the row: it gives way to the label rather
            // than running off the edge.
            modifier = Modifier.weight(1f, fill = false).padding(start = Spacing.p4),
        )
    }
    if (!last) Rule()
}

/** The 44dp action used inside the sharing block, where a 52dp block would dominate. */
@Composable
private fun SmallButton(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(44.dp)
            .then(
                if (filled) {
                    Modifier.background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                } else {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        MaterialTheme.shapes.small,
                    )
                }
            )
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = Spacing.p4),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (filled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
