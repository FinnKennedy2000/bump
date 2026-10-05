package com.finn.bump.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.finn.bump.data.AppState
import com.finn.bump.data.ChecklistItem
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.components.MicroLabel
import com.finn.bump.ui.components.Rule
import com.finn.bump.ui.theme.Spacing

/**
 * The hospital bag: one list on both phones, so whoever is packing can tick things off
 * and the other sees it. What goes on it is yours to decide — the app suggests nothing.
 */
@Composable
fun ChecklistScreen(
    state: AppState,
    vm: BumpViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    var draft by rememberSaveable { mutableStateOf("") }

    fun add() {
        if (draft.isNotBlank()) {
            vm.addChecklistItem(ChecklistItem(text = draft))
            draft = ""
        }
    }

    val (packed, toPack) = state.checklist.partition { it.done }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.p6)
                .padding(bottom = Spacing.p7),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.p2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MicroLabel("Hospital bag")
                Box(
                    Modifier
                        .size(30.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        .clickable(onClickLabel = "Close", onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                }
            }

            Text(
                if (state.checklist.isEmpty()) {
                    "Nothing on the list yet"
                } else {
                    "${packed.size} of ${state.checklist.size} packed"
                },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = Spacing.p5),
            )
            Text(
                "One list on both phones. Tick things off as they go in the bag.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.p1),
            )

            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.p5),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text("Add something") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    // Done adds and keeps the keyboard up, for typing a list in one go.
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    Modifier
                        .height(56.dp)
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                        .clickable(onClickLabel = "Add to the list") { add() }
                        .padding(horizontal = Spacing.p4),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Add", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                }
            }

            Column(Modifier.padding(top = Spacing.p5)) {
                toPack.forEach { ItemRow(it, vm) }
            }
            if (packed.isNotEmpty()) {
                MicroLabel("Packed", Modifier.padding(top = Spacing.p6, bottom = Spacing.p1))
                packed.forEach { ItemRow(it, vm) }
            }
        }
    }
}

@Composable
private fun ItemRow(item: ChecklistItem, vm: BumpViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (item.done) "Mark as not packed" else "Mark as packed") {
                vm.setChecked(item, !item.done)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.done,
            onCheckedChange = { vm.setChecked(item, it) },
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
        )
        Text(
            item.text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(vertical = Spacing.p3),
        )
        Box(
            Modifier
                .size(44.dp)
                .clickable(onClickLabel = "Remove ${item.text}") { vm.delete(item) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove ${item.text}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
    Rule()
}
