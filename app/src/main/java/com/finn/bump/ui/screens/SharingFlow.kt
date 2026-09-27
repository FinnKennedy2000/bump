package com.finn.bump.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finn.bump.data.sync.Pairing
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.components.BackRow
import com.finn.bump.ui.components.Dot
import com.finn.bump.ui.components.MicroLabel
import com.finn.bump.ui.components.OutlineButton
import com.finn.bump.ui.components.PaperCard
import com.finn.bump.ui.components.PrimaryButton
import com.finn.bump.ui.components.QuietButton
import com.finn.bump.ui.theme.LocalBumpExtras
import com.finn.bump.ui.theme.Spacing

/** Where the flow starts. Onboarding offers the choice; Settings has already made it. */
enum class SharingEntry { Offer, Create, Join }

/**
 * Pairing, full screen: one phone creates the shared space, the other joins with the code.
 *
 * It is the last step of onboarding and the same screens again from Settings, rather than
 * a bottom sheet hidden behind a status line — whether a second phone is involved is a
 * day-one question, not something to discover by tapping the right thing.
 *
 * There are no accounts because there are two people who already trust each other. The
 * code carries server, space and token in one string, so nobody types it: the actions are
 * send and paste, and the copy button exists to keep it out of screenshots.
 */
@Composable
fun SharingFlow(
    vm: BumpViewModel,
    entry: SharingEntry,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    stepLabel: String? = null,
) {
    val context = LocalContext.current
    val pairing by vm.pairing.collectAsStateWithLifecycle()

    var step by remember { mutableStateOf(entry) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Creating is a network call with nothing to decide, so the Create entry fires it on
    // arrival and the screen it lands on is the code.
    val create: () -> Unit = {
        busy = true
        error = null
        vm.createSpace { result ->
            busy = false
            error = result.exceptionOrNull()?.userMessage()
            if (result.isSuccess) step = SharingEntry.Create
        }
        Unit
    }

    LaunchedEffect(Unit) { if (entry == SharingEntry.Create) create() }

    // Back out of the join, or out of the flow. Leaving is never destructive: nothing
    // here is saved until the space is created or joined.
    BackHandler {
        if (step == SharingEntry.Join && entry == SharingEntry.Offer) {
            error = null
            step = SharingEntry.Offer
        } else {
            onDone()
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.p7)
                .padding(bottom = Spacing.p8),
        ) {
            when (step) {
                SharingEntry.Offer -> Offer(
                    stepLabel = stepLabel,
                    busy = busy,
                    onStart = create,
                    onJoin = { error = null; step = SharingEntry.Join },
                    onSkip = onDone,
                )

                SharingEntry.Create -> Code(
                    stepLabel = stepLabel,
                    pairing = pairing,
                    busy = busy,
                    onRetry = create,
                    onSend = { pairing?.let { sendSharingCode(context, it.toCode()) } },
                    onCopy = { pairing?.let { copySharingCode(context, it.toCode()) } },
                    onDone = onDone,
                )

                SharingEntry.Join -> Join(
                    busy = busy,
                    // Offer is the only screen behind this one; entering straight into
                    // the join from Settings means back leaves the flow entirely.
                    onBack = {
                        error = null
                        if (entry == SharingEntry.Offer) step = SharingEntry.Offer else onDone()
                    },
                    onJoin = { code ->
                        busy = true
                        error = null
                        vm.joinSpace(code) { result ->
                            busy = false
                            error = result.exceptionOrNull()?.userMessage()
                            if (result.isSuccess) onDone()
                        }
                    },
                )
            }

            error?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = Spacing.p3),
                )
            }
        }
    }
}

/** 4a — the offer. Two phones, one record, and a way past it. */
@Composable
private fun Offer(
    stepLabel: String?,
    busy: Boolean,
    onStart: () -> Unit,
    onJoin: () -> Unit,
    onSkip: () -> Unit,
) {
    StepLabel(stepLabel)

    Spacer(Modifier.height(Spacing.p10))

    Text("Two phones,\none record.", style = MaterialTheme.typography.displayMedium)
    Text(
        "Sharing keeps both phones on the same record. Everything still works offline; " +
            "changes catch up when there's a connection.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.p5),
    )

    TwoPhones(Modifier.padding(top = Spacing.p6))

    Spacer(Modifier.height(Spacing.p10))

    PrimaryButton("Start sharing", enabled = !busy, onClick = onStart)
    OutlineButton(
        "I have a code from my partner",
        Modifier.padding(top = Spacing.p2),
        onClick = onJoin,
    )
    QuietButton("Not now", Modifier.padding(top = Spacing.p1), onClick = onSkip)
    Caution(
        "The code is the whole key. Anyone who has it can read and change your record.",
    )
}

/** The picture the words are making: two handsets, one number between them. */
@Composable
private fun TwoPhones(modifier: Modifier = Modifier) {
    val extras = LocalBumpExtras.current
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.p3),
    ) {
        Handset("Yours")
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.p1),
        ) {
            MicroLabel("One record", color = MaterialTheme.colorScheme.primary)
            Box(Modifier.fillMaxWidth().height(1.dp).background(extras.track))
        }
        Handset("Theirs")
    }
}

@Composable
private fun Handset(label: String) {
    Column(
        Modifier
            .width(58.dp)
            .height(88.dp)
            .border(1.dp, LocalBumpExtras.current.faint, MaterialTheme.shapes.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("25", style = MaterialTheme.typography.titleSmall)
        MicroLabel(label)
    }
}

/** 4b — the code, once the space exists. */
@Composable
private fun Code(
    stepLabel: String?,
    pairing: Pairing?,
    busy: Boolean,
    onRetry: () -> Unit,
    onSend: () -> Unit,
    onCopy: () -> Unit,
    onDone: () -> Unit,
) {
    StepLabel(stepLabel)

    Spacer(Modifier.height(Spacing.p8))

    if (pairing == null) {
        // Either the create call is still in flight, or it failed and the error is
        // printed below. Never a dead screen with nothing to press.
        Text(
            if (busy) "Starting…" else "Sharing hasn't started yet.",
            style = MaterialTheme.typography.displayMedium,
        )
        if (!busy) {
            PrimaryButton(
                "Try again",
                Modifier.padding(top = Spacing.p6),
                onClick = onRetry,
            )
            QuietButton("Not now", Modifier.padding(top = Spacing.p1), onClick = onDone)
        }
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
    ) {
        Dot(LocalBumpExtras.current.test, 7.dp)
        MicroLabel("Sharing is on")
    }

    Text(
        "Send this to\nyour partner.",
        style = MaterialTheme.typography.displayMedium,
        modifier = Modifier.padding(top = Spacing.p4),
    )

    PaperCard(Modifier.padding(top = Spacing.p5)) {
        MicroLabel("Bump sharing code")
        // Selectable so the code can still be lifted by hand if the share sheet is
        // unavailable, which is the whole point of it being one string.
        SelectionContainer {
            Text(pairing.toCode(), style = MaterialTheme.typography.bodySmall)
        }
    }

    Text(
        "Send it in a private message — not a group chat, and not a screenshot. Nobody " +
            "needs to type it out.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.p4),
    )

    Spacer(Modifier.height(Spacing.p8))

    PrimaryButton("Send to your partner", onClick = onSend)
    OutlineButton("Copy code", Modifier.padding(top = Spacing.p2), onClick = onCopy)
    QuietButton("Done — I'll send it later", Modifier.padding(top = Spacing.p1), onClick = onDone)
    Caution("The code stays in Settings, so you can send it again any time.")
}

/** 4c — the other side of the same pairing. */
@Composable
private fun Join(busy: Boolean, onBack: () -> Unit, onJoin: (String) -> Unit) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }

    BackRow("Back", Modifier.padding(top = Spacing.p3), onClick = onBack)

    Spacer(Modifier.height(Spacing.p8))

    Text("Paste their code.", style = MaterialTheme.typography.displayMedium)
    Text(
        "Your partner starts sharing on their phone and sends you a code. Paste it here " +
            "and both phones read the same record.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.p4),
    )

    OutlinedTextField(
        value = code,
        onValueChange = { code = it },
        label = { Text("Their code") },
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.p5),
    )

    // The code arrives as a message on this phone, so the gesture is paste, not type.
    OutlineButton("Paste from clipboard", Modifier.padding(top = Spacing.p2)) {
        pastedText(context)?.let { code = it }
    }

    Spacer(Modifier.height(Spacing.p8))

    PrimaryButton(
        if (busy) "Joining…" else "Join their record",
        enabled = !busy && code.isNotBlank(),
    ) { onJoin(code) }
    Caution(
        "Joining replaces nothing: both phones' entries are combined, and the most " +
            "recent edit of anything wins.",
    )
}

@Composable
private fun StepLabel(label: String?) {
    if (label == null) return
    Row(
        Modifier.padding(top = Spacing.p6),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
    ) {
        repeat(2) {
            Box(
                Modifier
                    .width(26.dp)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        MicroLabel(label)
    }
}

@Composable
private fun Caution(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
        modifier = Modifier.padding(top = Spacing.p3),
    )
}

private fun Throwable.userMessage(): String = message ?: "Couldn't reach the server"

/** Shared with Settings, which offers the same two actions on the same code. */
internal fun copySharingCode(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Bump sharing code", text))
}

private fun pastedText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()?.ifBlank { null }
}

/** The system share sheet: the code has to leave this phone somehow, and this is how. */
internal fun sendSharingCode(context: Context, code: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, code)
    }
    context.startActivity(Intent.createChooser(intent, "Send your Bump sharing code"))
}
