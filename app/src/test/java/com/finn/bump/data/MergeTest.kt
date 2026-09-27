package com.finn.bump.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The merge is the only piece of sync that can silently lose a real entry, so it is the
 * piece that gets tested. Everything here is a scenario two phones actually produce.
 */
class MergeTest {

    private fun note(id: String, text: String, updatedAt: Long, deleted: Boolean = false) =
        Note(id = id, createdAtMillis = 0, week = 12, text = text,
             updatedAt = updatedAt, deleted = deleted)

    private fun state(vararg notes: Note, detailsAt: Long = 0L, name: String = "") =
        AppState(babyName = name, detailsUpdatedAt = detailsAt, notes = notes.toList())

    @Test
    fun `entries added on different phones are both kept`() {
        val mine = state(note("a", "mine", 100))
        val theirs = state(note("b", "theirs", 100))

        val merged = merge(mine, theirs)

        assertEquals(setOf("a", "b"), merged.notes.map { it.id }.toSet())
    }

    @Test
    fun `the later edit of the same entry wins`() {
        val mine = state(note("a", "old", 100))
        val theirs = state(note("a", "new", 200))

        assertEquals("new", merge(mine, theirs).notes.single().text)
        assertEquals("order must not matter", "new", merge(theirs, mine).notes.single().text)
    }

    @Test
    fun `a delete is not resurrected by the other phone's stale copy`() {
        val mine = state(note("a", "gone", 200, deleted = true))
        val theirs = state(note("a", "still here", 100))

        val merged = merge(mine, theirs)

        assertTrue("tombstone must survive the merge", merged.notes.single().deleted)
        assertTrue("and the UI must not show it", merged.withoutTombstones().notes.isEmpty())
    }

    @Test
    fun `an edit made after a delete brings the entry back`() {
        val mine = state(note("a", "deleted", 100, deleted = true))
        val theirs = state(note("a", "restored", 200))

        val merged = merge(mine, theirs)

        assertEquals("restored", merged.notes.single().text)
        assertTrue(merged.withoutTombstones().notes.isNotEmpty())
    }

    @Test
    fun `equal stamps keep the local copy so the result does not depend on fetch order`() {
        val mine = state(note("a", "mine", 100))
        val theirs = state(note("a", "theirs", 100))

        assertEquals("mine", merge(mine, theirs).notes.single().text)
    }

    @Test
    fun `baby details move as one group, most recently edited wins`() {
        val mine = state(detailsAt = 100, name = "Ours")
        val theirs = AppState(
            babyName = "Theirs",
            babyNickname = "Peanut",
            babySex = BabySex.GIRL,
            dueDate = LocalDate.of(2026, 3, 1),
            detailsUpdatedAt = 200,
        )

        val merged = merge(mine, theirs)

        assertEquals("Theirs", merged.babyName)
        assertEquals("Peanut", merged.babyNickname)
        assertEquals(BabySex.GIRL, merged.babySex)
        assertEquals(LocalDate.of(2026, 3, 1), merged.dueDate)
    }

    @Test
    fun `older details do not overwrite newer ones`() {
        val mine = state(detailsAt = 300, name = "Ours")
        val theirs = AppState(babyName = "Theirs", detailsUpdatedAt = 100)

        assertEquals("Ours", merge(mine, theirs).babyName)
    }

    @Test
    fun `a fresh install adopts the shared record wholesale`() {
        val blank = AppState()
        val theirs = state(note("a", "theirs", 100), detailsAt = 50, name = "Bean")
            .copy(dueDate = LocalDate.of(2026, 3, 1))

        val merged = merge(blank, theirs)

        assertEquals(theirs, merged)
    }

    @Test
    fun `merging is idempotent so a repeated sync changes nothing`() {
        val mine = state(note("a", "mine", 100), note("b", "gone", 150, deleted = true))
        val theirs = state(note("b", "theirs", 100), note("c", "new", 300))

        val once = merge(mine, theirs)

        assertEquals(once, merge(once, theirs))
        assertEquals(once, merge(once, once))
    }

    @Test
    fun `every list is merged, not just notes`() {
        val mine = AppState(
            appointments = listOf(Appointment(id = "ap", title = "mine", atMillis = 1, updatedAt = 100)),
            weights = listOf(WeightEntry(id = "w", date = LocalDate.of(2026, 1, 1), kg = 60.0, updatedAt = 100)),
            kickSessions = listOf(KickSession(id = "k", startedAtMillis = 1, durationMillis = 2, kicks = 3, updatedAt = 100)),
            photos = listOf(Photo(id = "p", date = LocalDate.of(2026, 1, 1), caption = "mine", blob = "b1", updatedAt = 100)),
        )
        val theirs = AppState(
            appointments = listOf(Appointment(id = "ap", title = "theirs", atMillis = 1, updatedAt = 200)),
            weights = listOf(WeightEntry(id = "w2", date = LocalDate.of(2026, 1, 2), kg = 61.0, updatedAt = 100)),
            kickSessions = listOf(KickSession(id = "k", startedAtMillis = 1, durationMillis = 2, kicks = 9, updatedAt = 200)),
            photos = listOf(Photo(id = "p", date = LocalDate.of(2026, 1, 1), caption = "theirs", blob = "b1", updatedAt = 200)),
        )

        val merged = merge(mine, theirs)
        assertEquals("theirs", merged.photos.single().caption)

        assertEquals("theirs", merged.appointments.single().title)
        assertEquals(2, merged.weights.size)
        assertEquals(9, merged.kickSessions.single().kicks)
    }

    @Test
    fun `an empty remote never wipes local entries`() {
        val mine = state(note("a", "mine", 100), detailsAt = 10, name = "Bean")

        val merged = merge(mine, AppState())

        assertEquals(1, merged.notes.size)
        assertEquals("Bean", merged.babyName)
    }

    @Test
    fun `a blank remote does not clear a due date set locally`() {
        val mine = AppState(dueDate = LocalDate.of(2026, 3, 1), detailsUpdatedAt = 100)

        assertEquals(LocalDate.of(2026, 3, 1), merge(mine, AppState()).dueDate)
        assertNull(merge(AppState(), AppState()).dueDate)
    }

    private fun photo(id: String, blob: String, deleted: Boolean = false) =
        Photo(id = id, date = LocalDate.of(2026, 1, 1), blob = blob, updatedAt = 1, deleted = deleted)

    @Test
    fun `photo bytes move to whichever side lacks them`() {
        val state = AppState(photos = listOf(photo("1", "mine"), photo("2", "theirs"), photo("3", "both")))

        val t = photoTransfers(state, onPhone = setOf("mine", "both"), onServer = setOf("theirs", "both"))

        assertEquals(setOf("mine"), t.upload)
        assertEquals(setOf("theirs"), t.download)
    }

    @Test
    fun `deleted photos are not transferred either way`() {
        val state = AppState(photos = listOf(photo("1", "gone-mine", deleted = true), photo("2", "gone-theirs", deleted = true)))

        val t = photoTransfers(state, onPhone = setOf("gone-mine"), onServer = setOf("gone-theirs"))

        assertEquals(PhotoTransfers(emptySet(), emptySet()), t)
    }

    @Test
    fun `a photo whose bytes are nowhere yet waits rather than failing`() {
        val t = photoTransfers(AppState(photos = listOf(photo("1", "pending"))), emptySet(), emptySet())

        assertEquals(PhotoTransfers(emptySet(), emptySet()), t)
    }

    @Test
    fun `sample size keeps the long edge at or above the target`() {
        assertEquals(1, sampleSize(4000, 2048))
        assertEquals(2, sampleSize(4096, 2048))
        assertEquals(8, sampleSize(2048, 200))
        assertEquals(1, sampleSize(100, 2048))
    }
}
