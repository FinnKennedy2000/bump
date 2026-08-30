package com.finn.bump.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finn.bump.data.sync.DEFAULT_BASE_URL
import com.finn.bump.data.sync.Pairing
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.components.PaperCard
import com.finn.bump.ui.theme.Spacing

/**
 * Pairing: one phone creates the shared space, the other joins with the code.
 *
 * There are no accounts because there are two people who already trust each other. The
 * code is the whole credential, so the copy button exists to keep it out of screenshots
 * and into a private message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SyncSheet(vm: BumpViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    val pairing by vm.pairing.collectAsStateWithLifecycle()

    var baseUrl by remember { mutableStateOf(DEFAULT_BASE_URL) }
    var joinCode by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = Spacing.p4)
                .padding(bottom = Spacing.p8)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(Spacing.p3),
        ) {
            Text("Share with your partner", style = MaterialTheme.typography.titleLarge)

            val current = pairing
            if (current != null) {
                Text(
                    "This phone is sharing. Send the code below to the other phone — " +
                        "anyone with it can read and change your record, so send it privately.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                PaperCard {
                    Text(
                        current.toCode(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.p2)) {
                    Button(onClick = { copyToClipboard(context, current.toCode()) }) {
                        Text("Copy code")
                    }
                    OutlinedButton(onClick = { vm.syncNow() }) { Text("Sync now") }
                }

                // Unpairing leaves the local document alone, so this is not a destructive
                // action however it reads — this phone simply stops sending and receiving.
                TextButton(onClick = { vm.unpair() }) { Text("Stop sharing on this phone") }
            } else {
                Text(
                    "Sharing keeps both phones on the same record. Everything still works " +
                        "offline; changes catch up when there's a connection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("Server") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = {
                        busy = true; error = null
                        vm.createSpace(baseUrl.trim()) { result ->
                            busy = false
                            error = result.exceptionOrNull()?.userMessage()
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Start sharing from this phone") }

                Text(
                    "or, if your partner already started:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = joinCode,
                    onValueChange = { joinCode = it },
                    label = { Text("Paste their code") },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedButton(
                    onClick = {
                        busy = true; error = null
                        vm.joinSpace(joinCode) { result ->
                            busy = false
                            error = result.exceptionOrNull()?.userMessage()
                        }
                    },
                    enabled = !busy && joinCode.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Join their record") }
            }

            error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Text(
                "Joining replaces nothing: both phones' entries are combined, and the most " +
                    "recent edit of anything wins.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Throwable.userMessage(): String = message ?: "Couldn't reach the server"

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Bump sharing code", text))
}
