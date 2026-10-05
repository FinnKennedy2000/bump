package com.finn.bump.data

/**
 * Reconcile this phone's copy with the server's.
 *
 * Pure Kotlin with no Android imports, so it is unit tested on the JVM. It lives on the
 * client rather than the server on purpose: the server stores the document as an opaque
 * blob, which means these rules exist once, here, instead of in two languages drifting
 * apart.
 *
 * The rule is last-write-wins per item. It is the right rule for this app because the two
 * people using it do not edit the same appointment at the same moment — they add different
 * things, from different rooms, and mostly while one of them is offline. What matters is
 * that nothing is lost and nothing comes back from the dead, and per-item stamps give both.
 *
 * ponytail: two ceilings, both fine at two users who talk to each other. Last-write-wins
 * silently discards the losing edit of a genuine conflict — if both phones retitle the same
 * appointment while offline, one title is gone with no trace. And "last" means by device
 * clock, so a phone whose clock is badly wrong wins or loses every race regardless of when
 * the edit really happened; both handsets use network time, which makes this theoretical
 * rather than absent. The upgrade path for either is a CRDT or a conflict-surfacing UI.
 */
fun merge(local: AppState, remote: AppState): AppState {
    // The details are one group with one stamp, so they move together. Ties go to local:
    // the local copy is the edit the user just made, and preferring it keeps the merge
    // deterministic rather than dependent on which side was fetched first.
    val details = if (remote.detailsUpdatedAt > local.detailsUpdatedAt) remote else local

    // Copying the winner, rather than listing its fields, means a detail added later
    // travels with the group without anyone remembering to add it here.
    return details.copy(
        appointments = mergeItems(local.appointments, remote.appointments),
        weights = mergeItems(local.weights, remote.weights),
        notes = mergeItems(local.notes, remote.notes),
        kickSessions = mergeItems(local.kickSessions, remote.kickSessions),
        photos = mergeItems(local.photos, remote.photos),
        contacts = mergeItems(local.contacts, remote.contacts),
        contractions = mergeItems(local.contractions, remote.contractions),
        checklist = mergeItems(local.checklist, remote.checklist),
    )
}

/**
 * Union by [Logged.id], keeping whichever copy was written last.
 *
 * Tombstones take part as ordinary items, so a delete beats an older edit and an edit
 * beats an older delete — which is what restoring a mistakenly deleted entry has to mean.
 *
 * Order is local-then-new-remote rather than sorted: every screen sorts for itself, and
 * imposing an order here would be a second, competing opinion about it.
 */
private fun <T : Logged> mergeItems(local: List<T>, remote: List<T>): List<T> {
    val byId = local.associateByTo(LinkedHashMap()) { it.id }
    for (item in remote) {
        val mine = byId[item.id]
        if (mine == null || item.updatedAt > mine.updatedAt) byId[item.id] = item
    }
    return byId.values.toList()
}

/** The document as the UI should see it: tombstones are an implementation detail of sync. */
fun AppState.withoutTombstones(): AppState = copy(
    appointments = appointments.filterNot { it.deleted },
    weights = weights.filterNot { it.deleted },
    notes = notes.filterNot { it.deleted },
    kickSessions = kickSessions.filterNot { it.deleted },
    photos = photos.filterNot { it.deleted },
    contacts = contacts.filterNot { it.deleted },
    contractions = contractions.filterNot { it.deleted },
    checklist = checklist.filterNot { it.deleted },
)

/** Which photo files have to move, and which way. */
data class PhotoTransfers(val upload: Set<String>, val download: Set<String>)

/**
 * The photo half of a sync round: after the documents are merged, copy each live photo's
 * bytes to whichever side lacks them.
 *
 * Tombstoned photos are left alone in both directions — nobody needs to see them, and the
 * bytes already on either side stay put so an undo still has them. A blob neither side
 * holds is in neither set: the phone that took it has not uploaded it yet, and a later
 * round will pick it up.
 */
fun photoTransfers(state: AppState, onPhone: Set<String>, onServer: Set<String>): PhotoTransfers {
    val live = state.photos.filterNot { it.deleted }.mapTo(HashSet()) { it.blob }
    return PhotoTransfers(
        upload = live.filterTo(HashSet()) { it in onPhone && it !in onServer },
        download = live.filterTo(HashSet()) { it in onServer && it !in onPhone },
    )
}
