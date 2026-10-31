package com.finn.bump.ui

import android.app.Application
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.io.File
import java.io.OutputStream
import com.finn.bump.data.FetchedNames
import com.finn.bump.domain.NameIdea
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.finn.bump.data.Appointment
import com.finn.bump.data.AppState
import com.finn.bump.data.BabySex
import com.finn.bump.data.ChecklistItem
import com.finn.bump.data.Contact
import com.finn.bump.data.Contraction
import com.finn.bump.data.NameVote
import com.finn.bump.domain.NameGroup
import com.finn.bump.notify.cancelNameChecks
import com.finn.bump.notify.markNamesSeen
import com.finn.bump.notify.scheduleNameChecks
import com.finn.bump.domain.Verdict
import com.finn.bump.data.writeExport
import com.finn.bump.data.Bump
import com.finn.bump.data.KickSession
import com.finn.bump.data.Logged
import com.finn.bump.data.Note
import com.finn.bump.data.Photo
import com.finn.bump.data.WeightEntry
import com.finn.bump.data.sync.DEFAULT_BASE_URL
import com.finn.bump.data.sync.Pairing
import com.finn.bump.data.sync.SyncStatus
import com.finn.bump.data.sync.SyncSettings
import com.finn.bump.domain.Pregnancy
import com.finn.bump.widget.refreshBumpWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import java.time.LocalDate

class BumpViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Bump.repository(app)
    private val sync = Bump.syncer(app)
    private val syncSettings = SyncSettings(app)
    val weeks = Bump.weekTable(app)
    val milestones = Bump.milestoneTable(app)

    val syncStatus: StateFlow<SyncStatus> = sync.status

    val pairing: StateFlow<Pairing?> = syncSettings.pairing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The server a new space would be created on. Only editable while unpaired. */
    val baseUrl: StateFlow<String> = syncSettings.baseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_BASE_URL)

    fun setBaseUrl(url: String) = viewModelScope.launch { syncSettings.setBaseUrl(url.trim()) }

    /**
     * `null` while the first read is in flight, which is what lets the UI hold a blank
     * frame instead of flashing onboarding at someone who already has a due date set.
     */
    val state: StateFlow<AppState?> =
        repo.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setDueDate(date: LocalDate) = launch {
        repo.setDueDate(date)
        refreshBumpWidget(getApplication())
    }

    /** Called on resume, and by "Sync now" in Settings. */
    fun syncNow() = viewModelScope.launch { syncAndRedraw() }

    fun createSpace(onResult: (Result<Pairing>) -> Unit) =
        viewModelScope.launch { onResult(sync.createSpace(syncSettings.baseUrl.first())) }

    fun joinSpace(code: String, onResult: (Result<Unit>) -> Unit) =
        viewModelScope.launch {
            val pairing = Pairing.fromCode(code)
            if (pairing == null) {
                onResult(Result.failure(IllegalArgumentException("That code is not valid")))
            } else {
                onResult(sync.join(pairing))
            }
        }

    fun unpair() = viewModelScope.launch { sync.unpair() }

    fun setDueDateFromLmp(lmp: LocalDate) = setDueDate(Pregnancy.dueDateFromLmp(lmp))

    /**
     * End of onboarding: keep the date that was picked, unless joining a partner's record
     * already brought one. Writing ours in that case would win the details merge on
     * recency alone and take their baby name and sex with it.
     */
    fun finishOnboarding(picked: LocalDate) = launch {
        if (repo.state.first().dueDate == null) {
            repo.setDueDate(picked)
            refreshBumpWidget(getApplication())
        }
    }

    fun setBabyDetails(name: String, nickname: String, sex: BabySex) = launch {
        repo.setBabyDetails(name, nickname, sex)
    }

    /** Both end the countdown on both phones, so the widget is redrawn with the write. */
    fun setBornOn(date: LocalDate?) = launch {
        repo.setBornOn(date)
        refreshBumpWidget(getApplication())
    }

    fun setPaused(paused: Boolean) = launch {
        repo.setPaused(paused)
        refreshBumpWidget(getApplication())
    }

    fun addContact(c: Contact) = launch { repo.addContact(c) }

    val names = Bump.nameTable(app)

    private val fetchedNames = FetchedNames(File(app.filesDir, "fetched_names.json"))
    private val _fetched = MutableStateFlow<List<NameIdea>>(emptyList())
    /** Names fetched from the public API earlier; see [fetchMoreNames]. */
    val fetched: StateFlow<List<NameIdea>> = _fetched

    /** null when idle and fine, "" while fetching, otherwise the message to show. */
    private val _fetchStatus = MutableStateFlow<String?>(null)
    val fetchStatus: StateFlow<String?> = _fetchStatus

    init {
        viewModelScope.launch {
            try {
                _fetched.value = withContext(Dispatchers.IO) { fetchedNames.load() }
            } catch (e: Exception) {
                Log.w("Bump", "Reading fetched names failed", e)
                _fetchStatus.value = "Fetched names couldn't be read"
            }
        }
    }

    /**
     * Fetch a batch of names from the public API. Only called from the names screen, on
     * the button or when the deck runs out; never from the background job.
     */
    fun fetchMoreNames(votes: List<NameVote>) {
        if (_fetchStatus.value == "") return
        _fetchStatus.value = ""
        viewModelScope.launch {
            try {
                val known = names.all.mapTo(HashSet()) { it.name.lowercase() } +
                    votes.map { it.name.lowercase() }
                val before = _fetched.value.size
                _fetched.value = fetchedNames.fetchMore(known)
                _fetchStatus.value = if (_fetched.value.size == before) "No new names this time, try again" else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Bump", "Fetching names failed", e)
                _fetchStatus.value = "Couldn't fetch more names. Check your connection."
            }
        }
    }

    /** This phone's voter id; null for the moment before it is read. */
    private val _voter = MutableStateFlow<String?>(null)
    val voter: StateFlow<String?> = _voter

    init {
        viewModelScope.launch { _voter.value = syncSettings.voterId() }
        // The background name check exists only while there is a partner to hear from.
        viewModelScope.launch {
            syncSettings.pairing.collect { if (it != null) scheduleNameChecks(app) else cancelNameChecks(app) }
        }
    }

    /** The names screen is open: everything on it counts as seen, and its notification goes. */
    fun namesSeen() = viewModelScope.launch { markNamesSeen(getApplication()) }

    fun vote(name: String, group: NameGroup, verdict: Verdict) {
        val me = _voter.value ?: return
        launch { repo.vote(NameVote(NameVote.idFor(me, name.trim()), me, name.trim(), group, verdict)) }
    }

    /**
     * Take back this phone's [verdict] on a name, which puts it back in the deck. Runs as
     * one store update after the vote's own (DataStore applies them in order), and does
     * nothing if the vote has since been changed to something else.
     */
    fun unvote(name: String, verdict: Verdict) {
        val me = _voter.value ?: return
        launch { repo.unvote(NameVote.idFor(me, name), verdict) }
    }
    /**
     * The contraction being timed right now, if any. Held here rather than in the screen so
     * closing the timer by reflex mid-contraction — to check the bag list, say — does not
     * throw it away.
     *
     * ponytail: lives as long as the process. If the system kills the app mid-contraction
     * that one is lost; persist it locally (not in the synced record) if that ever bites.
     */
    private val _contractionStartedAt = MutableStateFlow(0L)
    val contractionStartedAt: StateFlow<Long> = _contractionStartedAt

    /** The one this phone saved last — the only one "remove" may touch, never a synced one. */
    private val _lastTimed = MutableStateFlow<Contraction?>(null)
    val lastTimed: StateFlow<Contraction?> = _lastTimed

    fun startContraction() {
        _contractionStartedAt.value = System.currentTimeMillis()
    }

    fun cancelContraction() {
        _contractionStartedAt.value = 0L
    }

    fun endContraction() {
        val started = _contractionStartedAt.value
        if (started == 0L) return
        val c = Contraction(startedAtMillis = started, durationMillis = System.currentTimeMillis() - started)
        _contractionStartedAt.value = 0L
        _lastTimed.value = c
        launch { repo.addContraction(c) }
    }

    fun removeLastTimed() {
        val c = _lastTimed.value ?: return
        _lastTimed.value = null
        delete(c)
    }
    fun addChecklistItem(item: ChecklistItem) = launch { repo.addChecklistItem(item) }
    fun setChecked(item: ChecklistItem, done: Boolean) = launch { repo.setChecked(item.id, done) }

    /**
     * What the last export said, held here rather than in the screen so a rotation or an
     * activity recreated behind the save sheet cannot swallow it — in particular the
     * "without N photos" warning, which is what keeps a short archive from being a silent one.
     */
    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult

    /** Write the export zip to the document the user picked in the system save sheet. */
    fun export(uri: Uri) = saveTo(uri) { state, out ->
        val missing = writeExport(state, Bump.photoFiles(getApplication())::file, out)
        when (missing.size) {
            0 -> "Saved."
            1 -> "Saved, without 1 photo that hasn't reached this phone yet."
            else -> "Saved, without ${missing.size} photos that haven't reached this phone yet."
        }
    }

    /** The printable keepsake: the whole record as a small illustrated book. */
    fun makeKeepsake(uri: Uri) = saveTo(uri) { state, out ->
        val app = getApplication<Application>()
        when (val unplaced = KeepsakePdf(app, weeks, Bump.photoFiles(app)).write(state, out)) {
            0 -> "Keepsake saved."
            1 -> "Keepsake saved, without 1 photo that isn't on this phone yet."
            else -> "Keepsake saved, without $unplaced photos that aren't on this phone yet."
        }
    }

    /**
     * Write to a document the save sheet created, reporting through [exportResult]. The
     * sheet makes the file before anything is written, so a failure removes it: a
     * half-written file left behind under a plausible name is worse than none.
     */
    private fun saveTo(uri: Uri, write: suspend (AppState, OutputStream) -> String) = viewModelScope.launch {
        val app = getApplication<Application>()
        _exportResult.value = "Saving…"
        _exportResult.value = try {
            val state = repo.state.first()
            withContext(Dispatchers.IO) {
                val out = app.contentResolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Provider returned no stream")
                out.use { write(state, it) }
            }
        } catch (e: CancellationException) {
            discard(uri)
            throw e
        } catch (e: Exception) {
            Log.w("Bump", "Saving to $uri failed", e)
            failed(uri, "That couldn't be saved")
        } catch (e: OutOfMemoryError) {
            // Many large photos can still outgrow the heap while the book is drawn.
            failed(uri, "Ran out of memory making that")
        }
    }

    /** Remove the half-written file, and only claim it is gone if it is. */
    private fun failed(uri: Uri, what: String): String =
        if (discard(uri)) {
            "$what, so nothing was kept. Try again, or pick another place."
        } else {
            "$what. A partial file may be left where you saved it; delete it before keeping a copy."
        }

    private fun discard(uri: Uri): Boolean =
        runCatching { DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, uri) }
            .onFailure { Log.w("Bump", "Couldn't remove the failed file", it) }
            .getOrDefault(false)

    fun addAppointment(a: Appointment) = launch { repo.addAppointment(a) }
    fun addWeight(w: WeightEntry) = launch { repo.addWeight(w) }
    fun addNote(n: Note) = launch { repo.addNote(n) }
    fun addKickSession(k: KickSession) = launch { repo.addKickSession(k) }

    fun addPhoto(p: Photo) = launch { repo.addPhoto(p) }
    fun editPhoto(p: Photo, date: LocalDate, caption: String) = launch { repo.editPhoto(p.id, date, caption) }

    /**
     * Shrink and store a picked image, handing back its blob. Kept apart from [addPhoto] so
     * the sheet can show the picture before anything is saved to the record.
     *
     * ponytail: a photo picked and then abandoned leaves its file behind, unreferenced.
     * It is a few hundred KB per abandoned pick; sweep unreferenced blobs if that adds up.
     */
    fun importPhoto(uri: Uri, onResult: (Result<String>) -> Unit) = viewModelScope.launch {
        onResult(
            try {
                Result.success(Bump.photoFiles(getApplication()).import(uri))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Resolver and filesystem messages ("Permission Denial: opening provider…")
                // mean nothing to the person holding the phone; keep them for logcat.
                Log.w("Bump", "Photo import failed", e)
                Result.failure(IllegalStateException("That picture could not be opened"))
            } catch (e: OutOfMemoryError) {
                // A very large panorama can still be too big to decode at a quarter size.
                Result.failure(IllegalStateException("That picture is too large to open"))
            }
        )
    }

    fun delete(item: Logged) = launch { repo.delete(item) }
    fun restore(item: Logged) = launch { repo.restore(item) }

    /**
     * Every write schedules a sync, debounced so that adding three notes in a row is one
     * round trip rather than three. Sync is deliberately not awaited: the write is already
     * saved locally, and making the UI wait on the network would undo offline-first.
     */
    private var pendingSync: Job? = null

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            pendingSync?.cancel()
            pendingSync = viewModelScope.launch {
                delay(SYNC_DEBOUNCE_MILLIS)
                syncAndRedraw()
            }
        }
    }

    /** A sync can bring the partner's birth date or pause, which the widget has to show. */
    private suspend fun syncAndRedraw() {
        sync.sync()
        refreshBumpWidget(getApplication())
    }

    private companion object {
        const val SYNC_DEBOUNCE_MILLIS = 2_000L
    }
}
