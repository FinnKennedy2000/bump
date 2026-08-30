package com.finn.bump.data.sync

import com.finn.bump.data.BumpRepository
import com.finn.bump.data.merge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * What the status line on the Now screen shows.
 *
 * [Failed] carries its reason rather than collapsing to a generic "sync error" because the
 * failures differ in what the user should do: no network is wait, a 401 is re-pair. A sync
 * that fails silently is the worst outcome this feature has — you would believe your
 * partner had seen an appointment they never received — so failures are always surfaced.
 */
sealed interface SyncStatus {
    data object Off : SyncStatus
    data object Syncing : SyncStatus
    data class Synced(val atMillis: Long) : SyncStatus
    data class Failed(val reason: String) : SyncStatus
}

/**
 * Pull, merge, push — the whole sync loop.
 *
 * Offline-first: every write lands in DataStore first and is complete without the network.
 * This only reconciles. A failure here therefore loses nothing; the next trigger tries again.
 */
class Syncer(
    private val repo: BumpRepository,
    private val settings: SyncSettings,
    private val client: SyncClient = SyncClient(Json { ignoreUnknownKeys = true; encodeDefaults = true }),
) {

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Off)
    val status: StateFlow<SyncStatus> = _status

    /** Two triggers can fire at once (a write while a resume-sync is in flight). Serialise. */
    private val lock = Mutex()

    suspend fun createSpace(baseUrl: String): Result<Pairing> = runCatching {
        client.createSpace(baseUrl).also { settings.save(it) }
    }.onSuccess { sync() }

    suspend fun join(pairing: Pairing): Result<Unit> = runCatching {
        // Prove the code works before saving it, so a typo fails here rather than silently
        // leaving the phone paired to a space it can never read.
        client.pull(pairing)
        settings.save(pairing)
    }.onSuccess { sync() }

    suspend fun unpair() {
        settings.clear()
        _status.value = SyncStatus.Off
    }

    suspend fun sync() {
        val pairing = settings.pairing.first()
        if (pairing == null) {
            _status.value = SyncStatus.Off
            return
        }

        lock.withLock {
            _status.value = SyncStatus.Syncing
            try {
                syncOnce(pairing, allowRetry = true)
                _status.value = SyncStatus.Synced(System.currentTimeMillis())
            } catch (e: CancellationException) {
                // Not a failure: the screen went away, or a newer sync superseded this one.
                // Swallowing it here would break structured concurrency and report a
                // cancelled refresh as a broken one.
                throw e
            } catch (e: Exception) {
                // Otherwise deliberately broad. Anything thrown here — no network, a proxy
                // serving an HTML error page, a response this build cannot parse — has the
                // same consequence for the user and the same remedy: show it, try again.
                // Letting it escape would crash the app over a failed background refresh.
                _status.value = SyncStatus.Failed(e.message ?: "Network unavailable")
            }
        }
    }

    /**
     * One pull-merge-push round.
     *
     * A [VersionConflict] means the other phone pushed between our pull and our push. That
     * is expected, not an error: re-pull and merge again, once. Retrying more than once
     * would be a spin loop against a partner actively typing; the next trigger picks it up.
     */
    private suspend fun syncOnce(pairing: Pairing, allowRetry: Boolean) {
        val remote = client.pull(pairing)
        val local = repo.rawState.first()
        val merged = merge(local, remote.state)

        // Write the merge locally first. If the push then fails, this phone still gained
        // the partner's entries — a one-way sync beats none.
        if (merged != local) repo.replace(merged)

        if (merged == remote.state) return  // nothing of ours to send

        try {
            client.push(pairing, remote.version, merged)
        } catch (e: VersionConflict) {
            if (!allowRetry) throw e
            syncOnce(pairing, allowRetry = false)
        }
    }
}
