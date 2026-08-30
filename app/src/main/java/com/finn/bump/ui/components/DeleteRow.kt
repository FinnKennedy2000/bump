package com.finn.bump.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Delete with undo. The item is removed immediately and handed back if the snackbar's
 * Undo is tapped — the caller holds the original, so restoring is exact rather than
 * reconstructed.
 *
 * [scope] belongs to the screen, not to this row. Deleting removes the row from
 * composition on the next frame, which would cancel a scope remembered here and take the
 * undo snackbar with it — losing the only safety net on a delete.
 */
@Composable
fun DeleteRow(
    label: String,
    undoMessage: String,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = {
                onDelete()
                scope.launch {
                    val result = snackbar.showSnackbar(
                        message = undoMessage,
                        actionLabel = "Undo",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) onUndo()
                }
            },
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            Text("Delete", color = MaterialTheme.colorScheme.error)
        }
    }
}
