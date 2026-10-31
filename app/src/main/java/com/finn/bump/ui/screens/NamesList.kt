package com.finn.bump.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finn.bump.data.ShortlistEntry
import com.finn.bump.data.listSections
import com.finn.bump.domain.NameGroup
import com.finn.bump.domain.Verdict
import com.finn.bump.ui.BumpViewModel
import com.finn.bump.ui.components.Rule
import com.finn.bump.ui.theme.LocalBumpExtras
import com.finn.bump.ui.theme.Spacing

private val MarkColumn = 56.dp

/** One lazy-list row: a section heading with its column labels, or a name. */
private sealed interface ListRow {
    data class Heading(val title: String, val count: Int) : ListRow
    data class Name(val entry: ShortlistEntry) : ListRow
}

@Composable
internal fun OurList(
    list: List<ShortlistEntry>,
    awaitingCount: Int,
    vm: BumpViewModel,
    addGroup: NameGroup,
    onAnswer: () -> Unit,
) {
    val fetched by vm.fetched.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf<NameGroup?>(null) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }

    val sections = remember(list, filter) { listSections(list, filter) }
    val rows = buildList {
        if (sections.bothKeen.isNotEmpty()) {
            add(ListRow.Heading("Both keen", sections.bothKeen.size))
            sections.bothKeen.forEach { add(ListRow.Name(it)) }
        }
        if (sections.onlyOne.isNotEmpty()) {
            add(ListRow.Heading("Only one of you", sections.onlyOne.size))
            sections.onlyOne.forEach { add(ListRow.Name(it)) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (awaitingCount > 0) {
            Row(
                Modifier
                    .padding(top = Spacing.p4)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, LocalBumpExtras.current.rule, RoundedCornerShape(16.dp))
                    .padding(Spacing.p4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.p4),
            ) {
                Text(
                    if (awaitingCount == 1) "1 name your partner likes is waiting for you"
                    else "$awaitingCount names your partner likes are waiting for you",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Box(
                    Modifier
                        .height(44.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "Answer your partner's picks", onClick = onAnswer)
                        .padding(horizontal = Spacing.p4),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Answer", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Seg(
            listOf<NameGroup?>(null) + NameGroup.entries, filter, { filter = it },
            label = { it?.label() ?: "All" },
            modifier = Modifier.padding(top = Spacing.p4),
        )

        if (rows.isEmpty()) {
            Text(
                if (list.isEmpty()) "Nothing yet. Swipe right on a name, or add one below."
                else "Nothing here yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(top = Spacing.p6),
            )
        } else {
            LazyColumn(Modifier.weight(1f).padding(top = Spacing.p2)) {
                items(rows, key = { r ->
                    when (r) {
                        is ListRow.Heading -> "h:${r.title}"
                        is ListRow.Name -> "n:${r.entry.name.lowercase()}"
                    }
                }) { r ->
                    when (r) {
                        is ListRow.Heading -> SectionHead(r.title, r.count)
                        is ListRow.Name -> {
                            val e = r.entry
                            val key = e.name.lowercase()
                            NameRow(
                                e,
                                about = (vm.names.find(e.name) ?: fetched.firstOrNull { it.name.equals(e.name, true) }),
                                open = open == key,
                                onToggle = { open = if (open == key) null else key },
                                onAnswer = { vm.vote(e.name, e.group, it) },
                            )
                        }
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.p3, bottom = Spacing.p8)
                .height(56.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .clickable(onClickLabel = "Add a name") { adding = true },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.p2)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Text("Add a name", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (adding) {
        AddNameSheet(
            addGroup,
            onAdd = { name, group, verdict -> vm.vote(name, group, verdict) },
            onDismiss = { adding = false },
        )
    }
}

/** "Both keen  6" with the You / Partner column labels over the answer marks. */
@Composable
private fun SectionHead(title: String, count: Int) {
    val label = LocalBumpExtras.current.label
    Row(Modifier.fillMaxWidth().padding(top = Spacing.p4, bottom = Spacing.p1), verticalAlignment = Alignment.Bottom) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.p2)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("$count", style = MaterialTheme.typography.labelMedium, color = label)
        }
        listOf("You", "Partner").forEach {
            Text(
                it,
                Modifier.width(MarkColumn),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
                color = label,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun NameRow(
    e: ShortlistEntry,
    about: com.finn.bump.domain.NameIdea?,
    open: Boolean,
    onToggle: () -> Unit,
    onAnswer: (Verdict) -> Unit,
) {
    val clay = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // Only the origin until the row is open, then the meaning too: the closed list stays calm.
    val detail = listOfNotNull(
        e.group.kind(),
        about?.origin?.takeIf { it.isNotBlank() },
        about?.meaning?.takeIf { open && it.isNotBlank() },
    ).joinToString(", ")
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (open) Modifier
                    .padding(vertical = Spacing.p1)
                    .background(MaterialTheme.colorScheme.surface, shape)
                    .border(1.dp, LocalBumpExtras.current.rule, shape)
                else Modifier
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clickable(onClickLabel = if (open) "Close" else "Change your answer for ${e.name}", onClick = onToggle)
                .padding(vertical = Spacing.p2, horizontal = if (open) Spacing.p2 else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(e.name, style = MaterialTheme.typography.titleSmall.copy(fontSize = 28.sp, lineHeight = 32.sp))
                Text(
                    buildAnnotatedString {
                        if (e.isMatch) {
                            withStyle(SpanStyle(color = clay, fontWeight = FontWeight.SemiBold)) { append("Match") }
                            append(" · ")
                        }
                        append(detail)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
            Mark("You", e.mine)
            Mark("Partner", e.theirs)
        }
        if (open) {
            Seg(
                listOf(Verdict.NO, Verdict.MAYBE, Verdict.YES), e.mine, onAnswer,
                label = { it.word().replaceFirstChar { c -> c.uppercase() } },
                modifier = Modifier.padding(start = Spacing.p2, end = Spacing.p2, bottom = Spacing.p2),
            )
        } else {
            Rule()
        }
    }
}

/**
 * One answer, drawn: a filled dot is yes, a ring with its left half filled is maybe, a short
 * dash is no, a tiny dot is not answered. The description carries the same, so colour is never the only cue.
 */
@Composable
private fun Mark(who: String, v: Verdict?) {
    val clay = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val faint = LocalBumpExtras.current.faint
    Box(
        Modifier
            .width(MarkColumn)
            .height(44.dp)
            .semantics { contentDescription = if (v == null) "$who has not answered" else "$who said ${v.word()}" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val u = size.width / 20f
            val c = Offset(10f * u, 10f * u)
            when (v) {
                Verdict.YES -> drawCircle(clay, 8f * u, c)
                Verdict.MAYBE -> {
                    drawCircle(clay, 7f * u, c, style = Stroke(2f * u))
                    drawArc(clay, 90f, 180f, true, Offset(3f * u, 3f * u), Size(14f * u, 14f * u))
                }
                Verdict.NO -> drawLine(muted, Offset(5f * u, 10f * u), Offset(15f * u, 10f * u), 2f * u, StrokeCap.Round)
                null -> drawCircle(faint, 2f * u, c)
            }
        }
    }
}

// ---- Add a name -------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddNameSheet(
    initialGroup: NameGroup,
    onAdd: (String, NameGroup, Verdict) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf(initialGroup) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun add(verdict: Verdict) {
        if (draft.isNotBlank()) {
            onAdd(draft.trim().replaceFirstChar { it.uppercase() }, group, verdict)
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = Spacing.p6).padding(bottom = Spacing.p8)) {
            Text("Add a name", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleSmall.copy(fontSize = 24.sp, lineHeight = 30.sp),
                placeholder = { Text("Name", style = MaterialTheme.typography.titleSmall.copy(fontSize = 24.sp)) },
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add(Verdict.YES) }),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.p6).focusRequester(focus),
            )
            Text(
                "It is a",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.p6, bottom = Spacing.p2),
            )
            Seg(NameGroup.entries, group, { group = it }, label = { if (it == NameGroup.NEUTRAL) "Neutral" else "${it.kind()}'s name" })
            Row(Modifier.fillMaxWidth().padding(top = Spacing.p6), horizontalArrangement = Arrangement.spacedBy(Spacing.p2)) {
                ActionButton("Maybe", Modifier.weight(1f), filled = false, enabled = draft.isNotBlank()) { add(Verdict.MAYBE) }
                ActionButton("Yes, add it", Modifier.weight(2f), filled = true, enabled = draft.isNotBlank()) { add(Verdict.YES) }
            }
            Text(
                "Your partner will see it in their swipe deck.",
                style = MaterialTheme.typography.bodySmall,
                color = LocalBumpExtras.current.label,
                modifier = Modifier.padding(top = Spacing.p2),
            )
        }
    }
}
