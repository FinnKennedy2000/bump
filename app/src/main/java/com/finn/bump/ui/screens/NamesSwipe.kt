package com.finn.bump.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finn.bump.data.AppState
import com.finn.bump.data.PARTNER_SUGGESTED
import com.finn.bump.data.awaitingMe
import com.finn.bump.data.hasOnlineNames
import com.finn.bump.data.nameQueue
import com.finn.bump.domain.Culture
import com.finn.bump.domain.NameGroup
import com.finn.bump.domain.NameIdea
import com.finn.bump.domain.Verdict
import com.finn.bump.domain.cultureOf
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.theme.LocalBumpExtras
import com.finn.bump.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The deck. One quiet pill says what it is showing; everything about that (group, origin,
 * partner's picks, going online for more) lives in a sheet behind it.
 */
@Composable
internal fun Swipe(
    state: AppState,
    vm: BumpViewModel,
    me: String,
    group: NameGroup,
    onGroup: (NameGroup) -> Unit,
    picked: Culture?,
    onPicked: (Culture?) -> Unit,
    partnerOnly: Boolean,
    onPartnerOnly: (Boolean) -> Unit,
    onMatch: (NameIdea) -> Unit,
) {
    val fetched by vm.fetched.collectAsStateWithLifecycle()
    val fetchStatus by vm.fetchStatus.collectAsStateWithLifecycle()
    // A chip for every culture this group already has names in, plus every one the internet
    // can supply — otherwise an origin with no bundled names (Slavic neutral, say) could never
    // be picked and so never fetched. Cultures with neither get no chip.
    val cultures = remember(group, fetched) {
        (vm.names.all + fetched).filter { it.group == group }.map { cultureOf(it.origin) }.toSet()
            .let { present -> Culture.entries.filter { it in present || hasOnlineNames(it) } }
    }
    // Partner-typed names have no origin, so the partner's picks and an origin exclude each other.
    val culture = picked?.takeIf { it in cultures && !partnerOnly }
    val awaiting = remember(state.nameVotes, me, group) { awaitingMe(state.nameVotes, me).filter { it.group == group } }
    val queue = remember(state.nameVotes, me, group, fetched, culture, partnerOnly) {
        nameQueue(
            vm.names.all + fetched, state.nameVotes, me, group, culture,
            only = if (partnerOnly) awaiting.mapTo(HashSet()) { it.name.lowercase() } else null,
        )
    }
    // The deck is running low: top it up once per visit (a failure waits for the button, so
    // an offline phone does not retry in a loop). Network only happens here, on this screen.
    // Not for the partner's picks: the internet has none of those.
    var autoTried by remember(culture) { mutableStateOf(false) }
    LaunchedEffect(queue.size <= 3, autoTried, culture, partnerOnly) {
        if (queue.size <= 3 && !autoTried && !partnerOnly) {
            autoTried = true
            vm.fetchMoreNames(state.nameVotes, culture)
        }
    }
    var lastVoted by rememberSaveable { mutableStateOf<String?>(null) }
    var lastVerdict by rememberSaveable { mutableStateOf<Verdict?>(null) }
    var sheet by rememberSaveable { mutableStateOf(false) }

    fun decide(idea: NameIdea, verdict: Verdict) {
        vm.vote(idea.name, idea.group, verdict)
        lastVoted = idea.name
        lastVerdict = verdict
        val partnerSaidYes = state.nameVotes.any {
            it.voter != me && !it.deleted && it.verdict == Verdict.YES && it.name.equals(idea.name, ignoreCase = true)
        }
        if (verdict == Verdict.YES && partnerSaidYes) onMatch(idea)
    }

    val top = queue.firstOrNull()
    val scope = rememberCoroutineScope()
    val offset = remember(top?.name) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val away = with(LocalDensity.current) { 600.dp.toPx() }

    fun fling(verdict: Verdict) {
        val idea = top ?: return
        scope.launch {
            val target = when (verdict) {
                Verdict.YES -> Offset(away, offset.value.y)
                Verdict.NO -> Offset(-away, offset.value.y)
                Verdict.MAYBE -> Offset(offset.value.x, -away)
            }
            offset.animateTo(target, tween(220))
            decide(idea, verdict)
        }
    }

    Column(Modifier.fillMaxSize()) {
        ContextPill(
            group.label(),
            if (partnerOnly) "Partner's picks" else culture?.label ?: "All origins",
            onClick = { sheet = true },
        )

        Box(
            Modifier.fillMaxWidth().weight(1f).padding(top = Spacing.p6, bottom = Spacing.p2),
            contentAlignment = Alignment.Center,
        ) {
            if (top == null) {
                EmptyDeck(
                    message = when {
                        fetchStatus == "" -> "Fetching more names…"
                        partnerOnly -> "You have answered every name your partner likes."
                        // The online source only says male or female, so it never supplies neutral names.
                        group == NameGroup.NEUTRAL && culture != null && hasOnlineNames(culture) ->
                            "Online ${culture.label} names are girls' or boys' names. Pick Girls or Boys to swipe them."
                        culture != null -> "No more ${culture.label} names in this list."
                        else -> "That's every ${group.label().lowercase()} name so far."
                    },
                    fetching = fetchStatus == "",
                    onFetch = { vm.fetchMoreNames(state.nameVotes, culture) },
                    onFilter = { sheet = true },
                )
            } else {
                // The next card waits underneath, a little smaller, so the deck reads as a deck.
                queue.getOrNull(1)?.let { next ->
                    NameCard(next, Modifier.graphicsLayer { scaleX = 0.94f; scaleY = 0.94f; translationY = 36f })
                }
                SwipeCard(top, offset, onFling = ::fling)
            }
        }

        fetchStatus?.takeIf { it.isNotEmpty() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.p2),
            )
        }

        // Offered only while the vote is still the one made here; changed on the list since,
        // there is nothing for this undo to undo.
        val stillAsVoted = lastVoted?.let { n ->
            state.nameVotes.none { it.voter == me && !it.deleted && it.name.equals(n, true) && it.verdict != lastVerdict }
        } ?: false
        val undoName = lastVoted?.takeIf { stillAsVoted }
        val undoVerdict = lastVerdict
        if (undoName != null && undoVerdict != null) {
            UndoBar(undoVerdict, undoName) {
                vm.unvote(undoName, undoVerdict)
                lastVoted = null
            }
        }

        if (top != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.p4, bottom = Spacing.p8),
                horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
            ) {
                ActionButton("No", Modifier.weight(1f), filled = false) { fling(Verdict.NO) }
                ActionButton("Maybe", Modifier.weight(1f), filled = false) { fling(Verdict.MAYBE) }
                ActionButton("Yes", Modifier.weight(1.2f), filled = true) { fling(Verdict.YES) }
            }
        } else {
            Box(Modifier.height(Spacing.p8))
        }
    }

    if (sheet) {
        FilterSheet(
            group, onGroup,
            cultures, culture, onPicked = { onPartnerOnly(false); onPicked(it) },
            partnerCount = awaiting.size,
            partnerOnly = partnerOnly,
            onPartnerOnly = { onPartnerOnly(it) },
            fetchStatus = fetchStatus,
            onFetch = { vm.fetchMoreNames(state.nameVotes, culture) },
            onDismiss = { sheet = false },
        )
    }
}

/** The one outlined pill that says what the deck shows and opens the filters. */
@Composable
private fun ContextPill(first: String, second: String, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .padding(top = Spacing.p4)
            .fillMaxWidth()
            .height(44.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
            .clickable(onClickLabel = "Change what you are shown") { onClick() }
            .semantics { contentDescription = "Showing $first, $second. Change what you are shown." }
            .padding(horizontal = Spacing.p4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
    ) {
        Text(first, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        Text(second, style = MaterialTheme.typography.bodyLarge, color = muted, modifier = Modifier.weight(1f))
        Canvas(Modifier.size(20.dp)) {
            val w = 1.75.dp.toPx()
            val u = size.width / 24f
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
                drawLine(muted, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), w, StrokeCap.Round)
            line(4f, 7f, 14f, 7f); line(18f, 7f, 20f, 7f); line(4f, 17f, 6f, 17f); line(10f, 17f, 20f, 17f)
            drawCircle(muted, 2f * u, Offset(16f * u, 7f * u), style = Stroke(w))
            drawCircle(muted, 2f * u, Offset(8f * u, 17f * u), style = Stroke(w))
        }
    }
}

@Composable
private fun EmptyDeck(message: String, fetching: Boolean, onFetch: () -> Unit, onFilter: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextAction(if (fetching) "Fetching…" else "Find more names online", enabled = !fetching, onClick = onFetch)
        TextAction("Change what you see", onClick = onFilter)
    }
}

/** A 44dp clay text button. */
@Composable
internal fun TextAction(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.height(44.dp).clickable(enabled = enabled, onClickLabel = text, onClick = onClick).padding(horizontal = Spacing.p3),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Maybe to Rosie   Undo", slim, above the answer buttons. */
@Composable
private fun UndoBar(verdict: Verdict, name: String, onUndo: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .border(1.dp, LocalBumpExtras.current.rule, RoundedCornerShape(12.dp))
            .padding(start = Spacing.p4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "${verdict.word().replaceFirstChar { it.uppercase() }} to $name",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextAction("Undo", onClick = onUndo)
    }
}

// ---- Filters sheet -----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    group: NameGroup,
    onGroup: (NameGroup) -> Unit,
    cultures: List<Culture>,
    culture: Culture?,
    onPicked: (Culture?) -> Unit,
    partnerCount: Int,
    partnerOnly: Boolean,
    onPartnerOnly: (Boolean) -> Unit,
    fetchStatus: String?,
    onFetch: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.p6).padding(bottom = Spacing.p8),
        ) {
            Text("What to show", style = MaterialTheme.typography.headlineSmall)

            SheetHeading("Names for")
            Seg(NameGroup.entries, group, onGroup, label = { it.label() })

            SheetHeading("Where they come from")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.p2)) {
                Chip("Any", culture == null && !partnerOnly) { onPicked(null) }
                cultures.forEach { c -> Chip(c.label, culture == c) { onPicked(c) } }
            }

            if (partnerCount > 0) {
                SheetHeading("Only")
                Chip("Your partner's picks to answer · $partnerCount", partnerOnly) { onPartnerOnly(!partnerOnly) }
            }

            ActionButton("Show names", Modifier.fillMaxWidth().padding(top = Spacing.p6), filled = true, onClick = onDismiss)
            TextAction(
                if (fetchStatus == "") "Fetching…" else "Find more names online",
                Modifier.fillMaxWidth().padding(top = Spacing.p2),
                enabled = fetchStatus != "",
                onClick = onFetch,
            )
            fetchStatus?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                "Needs internet. Online names come as girls' or boys' names.",
                style = MaterialTheme.typography.bodySmall,
                color = LocalBumpExtras.current.label,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.p2),
            )
        }
    }
}

@Composable
private fun SheetHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.p6, bottom = Spacing.p2),
    )
}

/** A 44dp pill chip; the one that is on takes the clay tint. */
@Composable
private fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .padding(bottom = Spacing.p2)
            .height(44.dp)
            .background(if (on) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent, shape)
            .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape)
            .clickable(onClickLabel = text, onClick = onClick)
            .semantics { selected = on }
            .padding(horizontal = Spacing.p4),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal),
            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---- The card ---------------------------------------------------------------------------

/** The top card, following the finger. The buttons below it do the same, for one hand or a screen reader. */
@Composable
private fun SwipeCard(
    idea: NameIdea,
    offset: Animatable<Offset, AnimationVector2D>,
    onFling: (Verdict) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { 110.dp.toPx() }
    NameCard(
        idea,
        Modifier
            .graphicsLayer {
                translationX = offset.value.x
                translationY = offset.value.y
                rotationZ = offset.value.x / 40f
            }
            .pointerInput(idea.name) {
                detectDragGestures(
                    onDragEnd = {
                        val o = offset.value
                        when {
                            o.x > threshold -> onFling(Verdict.YES)
                            o.x < -threshold -> onFling(Verdict.NO)
                            o.y < -threshold && abs(o.y) > abs(o.x) -> onFling(Verdict.MAYBE)
                            else -> scope.launch { offset.animateTo(Offset.Zero, spring()) }
                        }
                    },
                    onDrag = { change, drag ->
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + drag) }
                    },
                )
            },
        stamp = when {
            offset.value.x > threshold / 3 -> Stamp("Yes", (offset.value.x / threshold).coerceIn(0f, 1f))
            offset.value.x < -threshold / 3 -> Stamp("No", (-offset.value.x / threshold).coerceIn(0f, 1f))
            offset.value.y < -threshold / 3 -> Stamp("Maybe", (-offset.value.y / threshold).coerceIn(0f, 1f))
            else -> null
        },
    )
}

private class Stamp(val word: String, val strength: Float)

@Composable
private fun NameCard(idea: NameIdea, modifier: Modifier = Modifier, stamp: Stamp? = null) {
    val suggested = idea.origin == PARTNER_SUGGESTED
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
            .border(1.dp, LocalBumpExtras.current.rule, RoundedCornerShape(24.dp))
            .semantics { contentDescription = "${idea.name}, ${idea.about()}" },
    ) {
        Column(Modifier.align(Alignment.CenterStart).padding(Spacing.p8)) {
            Text(
                idea.name,
                style = MaterialTheme.typography.displayMedium.copy(fontSize = DISPLAY_SIZE, lineHeight = DISPLAY_LINE),
            )
            if (!suggested) idea.about().takeIf { it.isNotEmpty() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.p4),
                )
            }
            if (suggested) Text(
                PARTNER_SUGGESTED,
                style = MaterialTheme.typography.bodySmall,
                color = LocalBumpExtras.current.label,
                modifier = Modifier.padding(top = Spacing.p4),
            )
        }
        stamp?.let {
            Text(
                it.word,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = Spacing.p5)
                    .graphicsLayer { alpha = it.strength }
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = it.strength), MaterialTheme.shapes.small)
                    .padding(horizontal = Spacing.p3, vertical = Spacing.p1),
            )
        }
    }
}
