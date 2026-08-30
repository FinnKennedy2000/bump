package com.finn.bump.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.finn.bump.data.Appointment
import com.finn.bump.data.AppState
import com.finn.bump.data.BabySex
import com.finn.bump.data.Bump
import com.finn.bump.data.KickSession
import com.finn.bump.data.Logged
import com.finn.bump.data.Note
import com.finn.bump.data.WeightEntry
import com.finn.bump.data.sync.Pairing
import com.finn.bump.data.sync.SyncStatus
import com.finn.bump.data.sync.SyncSettings
import com.finn.bump.domain.Pregnancy
import com.finn.bump.widget.refreshBumpWidget
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class BumpViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Bump.repository(app)
    private val sync = Bump.syncer(app)
    val weeks = Bump.weekTable(app)
    val milestones = Bump.milestoneTable(app)

    val syncStatus: StateFlow<SyncStatus> = sync.status

    val pairing: StateFlow<Pairing?> = SyncSettings(app).pairing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    /** Called on resume, and by pull-to-refresh in the pairing sheet. */
    fun syncNow() = viewModelScope.launch { sync.sync() }

    fun createSpace(baseUrl: String, onResult: (Result<Pairing>) -> Unit) =
        viewModelScope.launch { onResult(sync.createSpace(baseUrl)) }

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

    fun setBabyDetails(name: String, nickname: String, sex: BabySex) = launch {
        repo.setBabyDetails(name, nickname, sex)
    }

    fun addAppointment(a: Appointment) = launch { repo.addAppointment(a) }
    fun addWeight(w: WeightEntry) = launch { repo.addWeight(w) }
    fun addNote(n: Note) = launch { repo.addNote(n) }
    fun addKickSession(k: KickSession) = launch { repo.addKickSession(k) }

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
                sync.sync()
            }
        }
    }

    private companion object {
        const val SYNC_DEBOUNCE_MILLIS = 2_000L
    }
}
