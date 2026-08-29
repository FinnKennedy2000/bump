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
import com.finn.bump.domain.Pregnancy
import com.finn.bump.widget.refreshBumpWidget
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class BumpViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Bump.repository(app)
    val weeks = Bump.weekTable(app)
    val milestones = Bump.milestoneTable(app)

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

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
