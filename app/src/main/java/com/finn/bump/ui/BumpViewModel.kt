package com.finn.bump.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.finn.bump.data.Appointment
import com.finn.bump.data.AppState
import com.finn.bump.data.BabySex
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
