package com.finn.bump.ui.components

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finn.bump.data.Bump
import com.finn.bump.data.Photo
import com.finn.bump.domain.Pregnancy
import com.finn.bump.ui.theme.LocalBumpExtras
import com.finn.bump.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PhotoDay = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.UK)

/**
 * Decoded photos, by blob and size. Bounded by bytes rather than count because a thumbnail
 * and a full-screen decode differ in size by two orders of magnitude.
 *
 * ponytail: one process-wide cache sized for a few dozen thumbnails and a couple of full
 * views. Fine for a few hundred photos; a real image loader (Coil) earns its place only if
 * scrolling a much larger gallery ever stutters.
 */
private val decoded = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

/**
 * A stored photo, or a placeholder when its bytes are not on this phone. Usually they are
 * on their way from the other phone, and this redraws by itself when the download lands.
 */
@Composable
fun PhotoImage(
    blob: String,
    maxPx: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val files = Bump.photoFiles(LocalContext.current)
    val changes by files.changes.collectAsStateWithLifecycle()
    val key = "$blob@$maxPx"
    val bitmap by produceState(decoded.get(key), key, changes) {
        if (value == null) {
            value = files.decode(blob, maxPx)?.asImageBitmap()?.also { decoded.put(key, it) }
        }
    }

    Box(modifier.background(LocalBumpExtras.current.track), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription, Modifier.fillMaxSize(), contentScale = contentScale)
        } else {
            Icon(
                Icons.Outlined.HourglassEmpty,
                contentDescription = "Photo not on this phone yet",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * One photo, full screen, with its date and caption.
 *
 * [onEdit] and [delete] are optional because the viewer opens from places that cannot
 * offer them — the week sheet has no snackbar to carry an undo, and a delete without undo
 * is not one this app makes.
 */
@Composable
fun PhotoViewer(
    photo: Photo,
    dueDate: LocalDate,
    onDismiss: () -> Unit,
    onEdit: ((date: LocalDate, caption: String) -> Unit)? = null,
    delete: (@Composable () -> Unit)? = null,
) {
    var caption by remember(photo.id) { mutableStateOf(photo.caption) }
    var date by remember(photo.id) { mutableStateOf(photo.date) }
    var pickingDate by remember { mutableStateOf(false) }
    val changed = caption.trim() != photo.caption || date != photo.date

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.p6),
        ) {
            BackRow("Close", onClick = onDismiss)

            PhotoImage(
                blob = photo.blob,
                maxPx = 2048,
                contentDescription = photo.caption.ifBlank { "Photo" },
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.p2)
                    .height(440.dp),
            )

            MicroLabel(
                "Week ${Pregnancy.week(dueDate, date)} · ${date.format(PhotoDay)}",
                Modifier.padding(top = Spacing.p4),
            )

            if (onEdit == null) {
                if (photo.caption.isNotBlank()) {
                    Text(photo.caption, style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = Spacing.p2))
                }
            } else {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("Caption") },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.p2),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { pickingDate = true }) { Text("Change date") }
                    TextButton(enabled = changed, onClick = { onEdit(date, caption) }) { Text("Save") }
                }
            }

            delete?.invoke()
        }
    }

    if (pickingDate) {
        DatePickerSheet(
            title = "When was it taken?",
            initial = date,
            onDismiss = { pickingDate = false },
            onPicked = { pickingDate = false; date = it },
        )
    }
}
