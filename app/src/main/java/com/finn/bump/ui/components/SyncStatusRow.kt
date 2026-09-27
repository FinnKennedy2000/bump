package com.finn.bump.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.finn.bump.data.sync.SyncStatus
import com.finn.bump.ui.theme.Spacing
import java.util.concurrent.TimeUnit

/**
 * One line saying whether the other phone has seen your entries.
 *
 * It is always visible when sharing is on, including when everything is fine. A status
 * that only appears on failure teaches people to ignore it; the whole point is that a
 * sync which quietly stopped working is noticed before it matters.
 */
@Composable
fun SyncStatusRow(status: SyncStatus, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (text, isError) = when (status) {
        SyncStatus.Off -> "Not shared — tap to share with your partner" to false
        SyncStatus.Syncing -> "Syncing…" to false
        is SyncStatus.Synced -> "Synced ${relative(status.atMillis)}" to false
        is SyncStatus.Failed -> "Not synced: ${status.reason} — will retry" to true
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.p2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.p2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The same state in three or four words, for the Settings block where the label beside
 * it already says "Sharing" and the long sentence would only repeat it.
 */
fun syncStatusWords(status: SyncStatus): String = when (status) {
    SyncStatus.Off -> "Off"
    SyncStatus.Syncing -> "Syncing…"
    is SyncStatus.Synced -> "Synced ${relative(status.atMillis)}"
    is SyncStatus.Failed -> "Not synced"
}

private fun relative(millis: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - millis)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        else -> "${minutes / (60 * 24)}d ago"
    }
}
