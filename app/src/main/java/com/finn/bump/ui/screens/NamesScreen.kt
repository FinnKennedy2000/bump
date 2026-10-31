package com.finn.bump.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.ContextCompat
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finn.bump.data.AppState
import com.finn.bump.data.BabySex
import com.finn.bump.data.ShortlistEntry
import com.finn.bump.data.nameQueue
import com.finn.bump.data.shortlist
import com.finn.bump.domain.NameGroup
import com.finn.bump.domain.NameIdea
import com.finn.bump.domain.Verdict
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.components.MicroLabel
import com.finn.bump.ui.components.Rule
import com.finn.bump.ui.components.SegmentedToggle
import com.finn.bump.ui.theme.LocalBumpExtras
import com.finn.bump.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class NamesTab(val label: String) { Discover("Discover"), List("Our list") }

private fun NameGroup.heading() = when (this) {
    NameGroup.GIRL -> "Girl names"
    NameGroup.BOY -> "Boy names"
    NameGroup.NEUTRAL -> "Neutral names"
}

private fun NameGroup.label() = when (this) {
    NameGroup.GIRL -> "Girls"
    NameGroup.BOY -> "Boys"
    NameGroup.NEUTRAL -> "Neutral"
}

/**
 * Finding a name together. Each of you swipes through the list on your own phone — right
 * for yes, left for no, up for maybe — without seeing the other's answers. A name you
 * both say yes to is a match, and the shared list is everything either of you is keen on.
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
    var tab by rememberSaveable { mutableStateOf(NamesTab.Discover) }
    var group by rememberSaveable {
        mutableStateOf(
            when (state.babySex) {
                BabySex.BOY -> NameGroup.BOY
                BabySex.GIRL -> NameGroup.GIRL
                else -> NameGroup.NEUTRAL
            }
        )
    }
    val list = me?.let { shortlist(state.nameVotes, it) }.orEmpty()

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .padding(horizontal = Spacing.p6)
                .padding(bottom = Spacing.p5),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.p2),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MicroLabel("Baby names")
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

            SegmentedToggle(
                options = NamesTab.entries,
                selected = tab,
                onSelect = { tab = it },
                label = { t -> if (t == NamesTab.List && list.isNotEmpty()) "${t.label} (${list.size})" else t.label },
                modifier = Modifier.padding(top = Spacing.p4),
            )

            val voter = me ?: return@Column
            when (tab) {
                NamesTab.Discover -> Discover(state, vm, voter, group, onGroup = { group = it })
                NamesTab.List -> OurList(list, vm)
            }
        }
    }
}

// ---- Discover ---------------------------------------------------------------------------

@Composable
private fun Discover(
    state: AppState,
    vm: BumpViewModel,
    me: String,
    group: NameGroup,
    onGroup: (NameGroup) -> Unit,
) {
    val fetched by vm.fetched.collectAsStateWithLifecycle()
    val fetchStatus by vm.fetchStatus.collectAsStateWithLifecycle()
    val queue = remember(state.nameVotes, me, group, fetched) {
        nameQueue(vm.names.all + fetched, state.nameVotes, me, group)
    }
    // The deck is running low: top it up once per visit (a failure waits for the button, so
    // an offline phone does not retry in a loop). Network only happens here, on this screen.
    var autoTried by remember { mutableStateOf(false) }
    LaunchedEffect(queue.size <= 3, autoTried) {
        if (queue.size <= 3 && !autoTried) {
            autoTried = true
            vm.fetchMoreNames(state.nameVotes)
        }
    }
    var match by remember { mutableStateOf<String?>(null) }
    var lastVoted by rememberSaveable { mutableStateOf<String?>(null) }
    var lastVerdict by rememberSaveable { mutableStateOf<Verdict?>(null) }

    LaunchedEffect(match) {
        if (match != null) {
            delay(3500)
            match = null
        }
    }

    fun decide(idea: NameIdea, verdict: Verdict) {
        vm.vote(idea.name, idea.group, verdict)
        lastVoted = idea.name
        lastVerdict = verdict
        val partnerSaidYes = state.nameVotes.any {
            it.voter != me && !it.deleted && it.verdict == Verdict.YES && it.name.equals(idea.name, ignoreCase = true)
        }
        if (verdict == Verdict.YES && partnerSaidYes) match = idea.name
    }

    Column(Modifier.fillMaxSize()) {
        SegmentedToggle(
            options = NameGroup.entries,
            selected = group,
            onSelect = onGroup,
            label = { it.label() },
            modifier = Modifier.padding(top = Spacing.p3),
        )

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

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = Spacing.p5),
            contentAlignment = Alignment.Center,
        ) {
            if (top == null) {
                Text(
                    if (fetchStatus == "") "Fetching more names…"
                    else "That's every ${group.label().lowercase()} name so far. Tap More names, or add your own on Our list.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                // The next card waits underneath, a little smaller, so the deck reads as a deck.
                queue.getOrNull(1)?.let { next ->
                    NameCard(next, Modifier.graphicsLayer { scaleX = 0.94f; scaleY = 0.94f; translationY = 36f })
                }
                SwipeCard(top, offset, onFling = ::fling)
            }

            match?.let { name ->
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                        .padding(horizontal = Spacing.p5, vertical = Spacing.p3),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("You both like", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                    Text(name, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        if (top != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = Spacing.p5),
                horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
            ) {
                VoteButton("No", Modifier.weight(1f), filled = false) { fling(Verdict.NO) }
                VoteButton("Maybe", Modifier.weight(1f), filled = false) { fling(Verdict.MAYBE) }
                VoteButton("Yes", Modifier.weight(1f), filled = true) { fling(Verdict.YES) }
            }
        }

        fetchStatus?.takeIf { it.isNotEmpty() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.p2),
            )
        }
        Text(
            if (fetchStatus == "") "Fetching…" else "More names",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clickable(enabled = fetchStatus != "", onClickLabel = "Fetch more names from the internet") {
                    vm.fetchMoreNames(state.nameVotes)
                }
                .padding(Spacing.p2),
        )

        // Offered only while the vote is still the one made here; changed on the list since,
        // there is nothing for this undo to undo.
        val stillAsVoted = lastVoted?.let { n ->
            state.nameVotes.none { it.voter == me && !it.deleted && it.name.equals(n, true) && it.verdict != lastVerdict }
        } ?: false
        lastVoted?.takeIf { stillAsVoted }?.let { name ->
            Text(
                "Undo $name",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable {
                        lastVerdict?.let { vm.unvote(name, it) }
                        lastVoted = null
                    }
                    .padding(Spacing.p2),
            )
        }
    }
}

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
    val extras = LocalBumpExtras.current
    Box(
        modifier
            .fillMaxWidth()
            .height(340.dp)
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .border(1.dp, extras.rule, MaterialTheme.shapes.medium)
            .semantics { contentDescription = "${idea.name}, ${idea.origin}, meaning ${idea.meaning}" },
    ) {
        Column(
            Modifier.align(Alignment.Center).padding(Spacing.p6),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MicroLabel(idea.origin)
            Text(
                idea.name,
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.p3),
            )
            if (idea.meaning.isNotBlank()) Text(
                idea.meaning,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.p3),
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

@Composable
private fun VoteButton(label: String, modifier: Modifier, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .height(52.dp)
            .then(
                if (filled) {
                    Modifier.background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                }
            )
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---- Our list ---------------------------------------------------------------------------

@Composable
private fun OurList(list: List<ShortlistEntry>, vm: BumpViewModel) {
    var draft by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf(NameGroup.NEUTRAL) }

    fun add() {
        if (draft.isNotBlank()) {
            vm.vote(draft.trim().replaceFirstChar { it.uppercase() }, group, Verdict.YES)
            draft = ""
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "Every name either of you likes, split into girls, boys and neutral. Names your partner likes show up here as soon as they sync; tap Say yes to agree.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.p4),
        )

        // One section per kind of name, matches first within each (shortlist already sorts them so).
        NameGroup.entries.forEach { g ->
            val inGroup = list.filter { it.group == g }
            if (inGroup.isNotEmpty()) {
                MicroLabel(g.heading(), Modifier.padding(top = Spacing.p5, bottom = Spacing.p1))
                inGroup.forEach { Entry(it, vm) }
            }
        }
        if (list.isEmpty()) {
            Text(
                "Nothing yet. Swipe right on a name in Discover, or add one below.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = Spacing.p5),
            )
        }

        MicroLabel("Add a name of your own", Modifier.padding(top = Spacing.p6, bottom = Spacing.p2))
        SegmentedToggle(options = NameGroup.entries, selected = group, onSelect = { group = it }, label = { it.label() })
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.p2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                label = { Text("Name") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            // Same height as the field and its label's notch, so the two read as one row.
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                    .clickable(onClickLabel = "Add to the list") { add() }
                    .padding(horizontal = Spacing.p5),
                contentAlignment = Alignment.Center,
            ) {
                Text("Add", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun Entry(e: ShortlistEntry, vm: BumpViewModel) {
    val meaning = vm.names.find(e.name)
    Row(
        Modifier.fillMaxWidth().padding(vertical = Spacing.p3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.name, style = MaterialTheme.typography.headlineSmall)
            if (e.isMatch) {
                Text("You both like it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            meaning?.let {
                Text(
                    "${it.origin}, ${it.meaning}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.padding(top = Spacing.p1), horizontalArrangement = Arrangement.spacedBy(Spacing.p3)) {
                Answer("You", e.mine)
                Answer("Partner", e.theirs)
            }
        }
        // Your own answer is the one you can change here: tap to swap yes and maybe.
        if (e.mine == null || e.mine == Verdict.NO) {
            // A name only your partner has judged: answer it straight from the list, yes or maybe.
            MaybeChip { vm.vote(e.name, e.group, Verdict.MAYBE) }
            Spacer(Modifier.width(Spacing.p2))
        }
        VerdictChip(e.mine) {
            vm.vote(e.name, e.group, if (e.mine == Verdict.YES) Verdict.MAYBE else Verdict.YES)
        }
        Box(
            Modifier
                .size(44.dp)
                .clickable(onClickLabel = "Say no to ${e.name}") { vm.vote(e.name, e.group, Verdict.NO) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Say no to ${e.name}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
    Rule()
}

@Composable
private fun Answer(who: String, v: Verdict?) {
    Text(
        "$who: ${v?.word() ?: "not yet"}",
        style = MaterialTheme.typography.labelMedium,
        color = if (v == Verdict.YES) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MaybeChip(onClick: () -> Unit) {
    Box(
        Modifier
            .height(32.dp)
            .border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
            .clickable(onClickLabel = "Say maybe", onClick = onClick)
            .padding(horizontal = Spacing.p3),
        contentAlignment = Alignment.Center,
    ) {
        Text("Maybe", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun VerdictChip(v: Verdict?, onClick: () -> Unit) {
    val yes = v == Verdict.YES
    Box(
        Modifier
            .height(32.dp)
            .background(if (yes) MaterialTheme.colorScheme.primary else Color.Transparent, MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
            .clickable(onClickLabel = if (yes) "Change to maybe" else "Change to yes", onClick = onClick)
            .padding(horizontal = Spacing.p3),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            when (v) {
                Verdict.YES -> "Yes"
                Verdict.MAYBE -> "Maybe"
                else -> "Say yes"
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (yes) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        )
    }
}

private fun Verdict.word() = when (this) {
    Verdict.YES -> "yes"
    Verdict.MAYBE -> "maybe"
    Verdict.NO -> "no"
}
