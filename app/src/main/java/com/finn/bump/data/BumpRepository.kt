package com.finn.bump.data

import android.content.Context
import androidx.datastore.core.DataStore
import com.finn.bump.domain.Milestone
import com.finn.bump.domain.MilestoneTable
import com.finn.bump.domain.WeekInfo
import com.finn.bump.domain.WeekTable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.KSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/**
 * Process-wide singletons, held here rather than in a DI graph.
 *
 * There are a handful of them and none is swapped in tests (the tested code is
 * pure and takes no dependencies), so a DI framework would add a graph, a compiler
 * plugin and build time to solve a problem the app does not have.
 *
 * DataStore throws if two instances are created for the same file, so this must stay
 * the only construction site.
 */
object Bump {

    @Volatile private var store: DataStore<AppState>? = null
    @Volatile private var repo: BumpRepository? = null
    @Volatile private var weeks: WeekTable? = null
    @Volatile private var milestones: MilestoneTable? = null

    fun repository(context: Context): BumpRepository =
        repo ?: synchronized(this) {
            repo ?: BumpRepository(storeOf(context)).also { repo = it }
        }

    private fun storeOf(context: Context): DataStore<AppState> =
        store ?: synchronized(this) {
            store ?: buildAppStateStore(context.applicationContext).also { store = it }
        }

    fun weekTable(context: Context): WeekTable =
        weeks ?: synchronized(this) {
            weeks ?: loadWeekTable(context.applicationContext).also { weeks = it }
        }

    fun milestoneTable(context: Context): MilestoneTable =
        milestones ?: synchronized(this) {
            milestones ?: MilestoneTable(
                readList(context.applicationContext, com.finn.bump.R.raw.milestones, Milestone.serializer())
            ).also { milestones = it }
        }

    private fun loadWeekTable(context: Context): WeekTable =
        WeekTable(readList(context, com.finn.bump.R.raw.weeks, WeekInfo.serializer()))

    private val json = Json { ignoreUnknownKeys = true }

    private fun <T> readList(context: Context, resId: Int, serializer: KSerializer<T>): List<T> {
        val raw = context.resources.openRawResource(resId).bufferedReader().use { it.readText() }
        return json.decodeFromString(ListSerializer(serializer), raw)
    }
}

class BumpRepository(private val store: DataStore<AppState>) {

    val state: Flow<AppState> = store.data

    suspend fun update(transform: (AppState) -> AppState) {
        store.updateData(transform)
    }

    suspend fun setDueDate(date: Iso8601Date) = update { it.copy(dueDate = date) }

    suspend fun setBabyDetails(name: String, nickname: String, sex: BabySex) = update {
        it.copy(babyName = name.trim(), babyNickname = nickname.trim(), babySex = sex)
    }

    suspend fun addAppointment(appointment: Appointment) =
        update { it.copy(appointments = it.appointments + appointment) }

    suspend fun addWeight(entry: WeightEntry) =
        update { it.copy(weights = it.weights + entry) }

    suspend fun addNote(note: Note) =
        update { it.copy(notes = it.notes + note) }

    suspend fun addKickSession(session: KickSession) =
        update { it.copy(kickSessions = it.kickSessions + session) }

    suspend fun delete(item: Logged) = update { s ->
        when (item) {
            is Appointment -> s.copy(appointments = s.appointments.filterNot { it.id == item.id })
            is WeightEntry -> s.copy(weights = s.weights.filterNot { it.id == item.id })
            is Note -> s.copy(notes = s.notes.filterNot { it.id == item.id })
            is KickSession -> s.copy(kickSessions = s.kickSessions.filterNot { it.id == item.id })
        }
    }

    /** Undo support: puts a deleted item back exactly as it was. */
    suspend fun restore(item: Logged) = when (item) {
        is Appointment -> addAppointment(item)
        is WeightEntry -> addWeight(item)
        is Note -> addNote(item)
        is KickSession -> addKickSession(item)
    }
}
