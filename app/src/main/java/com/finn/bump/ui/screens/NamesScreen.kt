package com.finn.bump.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finn.bump.data.AppState
import com.finn.bump.data.BabySex
import com.finn.bump.data.awaitingMe
import com.finn.bump.data.shortlist
import com.finn.bump.domain.Culture
import com.finn.bump.domain.NameGroup
import com.finn.bump.domain.NameIdea
import com.finn.bump.domain.Verdict
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.components.Rule
import com.finn.bump.ui.theme.LocalBumpExtras
import com.finn.bump.ui.theme.Spacing

internal val DISPLAY_SIZE = 52.sp
internal val DISPLAY_LINE = 56.sp

internal enum class NamesTab(val label: String) { Swipe("Swipe"), List("Our list") }

internal fun NameGroup.kind() = when (this) {
    NameGroup.GIRL -> "Girl"
    NameGroup.BOY -> "Boy"
    NameGroup.NEUTRAL -> "Neutral"
}

internal fun NameGroup.label() = when (this) {
    NameGroup.GIRL -> "Girls"
    NameGroup.BOY -> "Boys"
    NameGroup.NEUTRAL -> "Neutral"
}

internal fun Verdict.word() = when (this) {
    Verdict.YES -> "yes"
    Verdict.MAYBE -> "maybe"
    Verdict.NO -> "no"
}

/** "Scottish, from the isle of Iona": the origin and, when known, the meaning. */
internal fun NameIdea.about() = listOf(origin, meaning).filter { it.isNotBlank() }.joinToString(", ")

/**
 * Finding a name together. Each of you swipes through the list on your own phone — right
 * for yes, left for no, up for maybe — without seeing the other's answers. A name you
 * both say yes to is a match, and the shared list is everything either of you is keen on.
 *
 * The state both tabs touch lives here: the deck's filter (so "Answer" on the list can set
 * it on the Swipe tab) and the match moment, which takes over the whole area below the title.
 */
@Composable
fun NamesScreen(
    state: AppState,
    vm: BumpViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val me by vm.voter.collectAsStateWithLifecycle()
    val paired = vm.pairing.collectAsStateWithLifecycle().value != null

    // Seen on open and as new votes sync in while it is open, so nothing here is announced later.
    LaunchedEffect(state.nameVotes) { vm.namesSeen() }

    // Asked here, the first time it means something: paired, and looking at names.
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val context = LocalContext.current
    LaunchedEffect(paired) {
        if (paired && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    var tab by rememberSaveable { mutableStateOf(NamesTab.Swipe) }
    var group by rememberSaveable {
        mutableStateOf(
            when (state.babySex) {
                BabySex.BOY -> NameGroup.BOY
                BabySex.GIRL -> NameGroup.GIRL
                else -> NameGroup.NEUTRAL
            }
        )
    }
    var picked by rememberSaveable { mutableStateOf<Culture?>(null) }
    var partnerOnly by rememberSaveable { mutableStateOf(false) }
    var match by remember { mutableStateOf<NameIdea?>(null) }
    BackHandler(enabled = match != null) { match = null }

    val list = me?.let { shortlist(state.nameVotes, it) }.orEmpty()
    val awaiting = me?.let { awaitingMe(state.nameVotes, it) }.orEmpty()

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = Spacing.p6),
        ) {
            Row(
                Modifier.fillMaxWidth().height(56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Baby names", style = MaterialTheme.typography.titleMedium)
                Box(
                    Modifier.size(44.dp).clickable(onClickLabel = "Close", onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            val voter = me ?: return@Column
            val moment = match
            if (moment != null) {
                MatchMoment(
                    moment,
                    onList = { match = null; tab = NamesTab.List },
                    onKeep = { match = null },
                )
                return@Column
            }

            Tabs(tab, list.size, onSelect = { tab = it })
            when (tab) {
                NamesTab.Swipe -> Swipe(
                    state, vm, voter,
                    group, onGroup = { group = it },
                    picked, onPicked = { picked = it },
                    partnerOnly, onPartnerOnly = { partnerOnly = it },
                    onMatch = { match = it },
                )
                NamesTab.List -> OurList(
                    list, awaiting.size, vm,
                    addGroup = group,
                    onAnswer = {
                        awaiting.firstOrNull()?.let { group = it.group }
                        picked = null
                        partnerOnly = true
                        tab = NamesTab.Swipe
                    },
                )
            }
        }
    }
}

/** Two text tabs with a clay underline on the active one. */
@Composable
private fun Tabs(tab: NamesTab, listCount: Int, onSelect: (NamesTab) -> Unit) {
    val clay = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.p6)) {
            NamesTab.entries.forEach { t ->
                val on = t == tab
                Row(
                    Modifier
                        .height(44.dp)
                        .clickable(onClickLabel = t.label) { onSelect(t) }
                        .semantics { selected = on }
                        .drawBehind {
                            if (on) drawRect(clay, Offset(0f, size.height - 2.dp.toPx()), Size(size.width, 2.dp.toPx()))
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
                ) {
                    Text(
                        t.label,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (t == NamesTab.List && listCount > 0) {
                        Text(
                            "$listCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalBumpExtras.current.label,
                        )
                    }
                }
            }
        }
        Rule()
    }
}

/** Both said yes. Replaces the tabs until it is answered; a name is only ever announced by you swiping it. */
@Composable
private fun MatchMoment(idea: NameIdea, onList: () -> Unit, onKeep: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.p2),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.width(48.dp).height(2.dp).background(MaterialTheme.colorScheme.primary))
            Text(
                idea.name,
                style = MaterialTheme.typography.displayMedium.copy(fontSize = DISPLAY_SIZE, lineHeight = DISPLAY_LINE),
                modifier = Modifier.padding(top = Spacing.p6),
            )
            Text(
                "You both said yes.",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.p4),
            )
            idea.about().takeIf { it.isNotEmpty() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.p2),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = Spacing.p8),
            horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
        ) {
            ActionButton("See our list", Modifier.weight(1f), filled = false, onClick = onList)
            ActionButton("Keep swiping", Modifier.weight(1.2f), filled = true, onClick = onKeep)
        }
    }
}

/** A 56dp thumb-zone button: outlined, or filled clay for the answer you want to give. */
@Composable
internal fun ActionButton(
    label: String,
    modifier: Modifier,
    filled: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .height(56.dp)
            .then(
                if (filled) Modifier.background(MaterialTheme.colorScheme.primary, shape)
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, shape)
            )
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A 44dp-high segmented control, shared by the filters sheet, the list filter, the open row and the add sheet. */
@Composable
internal fun <T> Seg(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val extras = LocalBumpExtras.current
    Row(
        modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(if (on) extras.selected else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = label(option)) { if (!on) onSelect(option) }
                    .semantics { this.selected = on },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (on) extras.onSelected else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
