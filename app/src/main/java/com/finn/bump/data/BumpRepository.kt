package com.finn.bump.data

import android.content.Context
import androidx.datastore.core.DataStore
import com.finn.bump.data.sync.SyncSettings
import com.finn.bump.data.sync.Syncer
import com.finn.bump.domain.Milestone
import com.finn.bump.domain.MilestoneTable
import com.finn.bump.domain.WeekInfo
import com.finn.bump.domain.WeekTable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.KSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
    @Volatile private var syncer: Syncer? = null
    @Volatile private var photos: PhotoFiles? = null

    fun repository(context: Context): BumpRepository =
        repo ?: synchronized(this) {
            repo ?: BumpRepository(storeOf(context)).also { repo = it }
        }

    fun syncer(context: Context): Syncer =
        syncer ?: synchronized(this) {
            syncer ?: Syncer(
                repository(context),
                SyncSettings(context.applicationContext),
                photoFiles(context),
            ).also { syncer = it }
        }

    fun photoFiles(context: Context): PhotoFiles =
        photos ?: synchronized(this) {
            photos ?: PhotoFiles(context.applicationContext).also { photos = it }
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

    /** What the app sees: deleted items are filtered here so no screen has to remember to. */
    val state: Flow<AppState> = store.data.map { it.withoutTombstones() }

    /** What sync sees. Tombstones must survive the round trip or deletes do not propagate. */
    val rawState: Flow<AppState> = store.data

    suspend fun update(transform: (AppState) -> AppState) {
        store.updateData(transform)
    }

    suspend fun setDueDate(date: Iso8601Date) =
        update { it.copy(dueDate = date, detailsUpdatedAt = System.currentTimeMillis()) }

    suspend fun setBabyDetails(name: String, nickname: String, sex: BabySex) = update {
        it.copy(
            babyName = name.trim(),
            babyNickname = nickname.trim(),
            babySex = sex,
            detailsUpdatedAt = System.currentTimeMillis(),
        )
    }

    suspend fun addAppointment(appointment: Appointment) =
        update { it.copy(appointments = it.appointments + appointment) }

    suspend fun addWeight(entry: WeightEntry) =
        update { it.copy(weights = it.weights + entry) }

    suspend fun addNote(note: Note) =
        update { it.copy(notes = it.notes + note) }

    suspend fun addKickSession(session: KickSession) =
        update { it.copy(kickSessions = it.kickSessions + session) }

    suspend fun addPhoto(photo: Photo) =
        update { it.copy(photos = it.photos + photo.copy(caption = photo.caption.trim())) }

    /** Caption and date are the only editable parts; the picture itself is fixed by its hash. */
    suspend fun editPhoto(id: String, date: Iso8601Date, caption: String) = update { s ->
        val now = System.currentTimeMillis()
        s.copy(photos = s.photos.map {
            if (it.id == id) it.copy(date = date, caption = caption.trim(), updatedAt = now) else it
        })
    }

    suspend fun delete(item: Logged) = setDeleted(item, true)

    /** Undo support: puts a deleted item back exactly as it was. */
    suspend fun restore(item: Logged) = setDeleted(item, false)

    /**
     * A delete is an edit that sets the tombstone, not a removal — see [Logged.deleted].
     * Undo is the same edit in reverse, which is why both go through here.
     *
     * The stamp is refreshed on every flip so the most recent decision wins the merge.
     * Without it, deleting an entry the other phone edited later would lose the delete.
     */
    private suspend fun setDeleted(item: Logged, deleted: Boolean) {
        val now = System.currentTimeMillis()
        update { s ->
            when (item) {
                is Appointment -> s.copy(appointments = s.appointments.map {
                    if (it.id == item.id) it.copy(deleted = deleted, updatedAt = now) else it
                })
                is WeightEntry -> s.copy(weights = s.weights.map {
                    if (it.id == item.id) it.copy(deleted = deleted, updatedAt = now) else it
                })
                is Note -> s.copy(notes = s.notes.map {
                    if (it.id == item.id) it.copy(deleted = deleted, updatedAt = now) else it
                })
                is KickSession -> s.copy(kickSessions = s.kickSessions.map {
                    if (it.id == item.id) it.copy(deleted = deleted, updatedAt = now) else it
                })
                is Photo -> s.copy(photos = s.photos.map {
                    if (it.id == item.id) it.copy(deleted = deleted, updatedAt = now) else it
                })
            }
        }
    }

    /** Replace the whole document, after a sync merge. */
    suspend fun replace(state: AppState) = update { state }
}
