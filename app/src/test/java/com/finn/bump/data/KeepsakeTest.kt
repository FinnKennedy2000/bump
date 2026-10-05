package com.finn.bump.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class KeepsakeTest {

    private val due = LocalDate.of(2027, 2, 14)          // week 20 starts 2026-09-27
    private val utc = ZoneOffset.UTC
    private fun millis(d: LocalDate, hour: Int = 9) = d.atTime(hour, 0).toInstant(utc).toEpochMilli()

    @Test
    fun `moments group by week in pregnancy order, oldest first, with deleted ones gone`() {
        val state = AppState(
            dueDate = due,
            weights = listOf(WeightEntry(id = "w", date = LocalDate.of(2026, 10, 5), kg = 64.0)),
            appointments = listOf(Appointment(id = "a", title = "Scan", atMillis = millis(LocalDate.of(2026, 9, 28), 14))),
            notes = listOf(
                Note(id = "n", createdAtMillis = millis(LocalDate.of(2026, 9, 28), 8), week = 20, text = "First flutter"),
                Note(id = "gone", createdAtMillis = millis(LocalDate.of(2026, 9, 29)), week = 20, text = "x", deleted = true),
            ),
            contacts = listOf(Contact(label = "Triage", number = "1")),
        )

        val chapters = keepsakeChapters(state, utc)

        assertEquals(listOf(20, 21), chapters.map { it.week })
        assertEquals(listOf("n", "a"), chapters[0].moments.map { it.item.id })
        assertEquals(listOf("w"), chapters[1].moments.map { it.item.id })
    }

    @Test
    fun `a note keeps the week it was written under`() {
        val state = AppState(
            dueDate = due,
            notes = listOf(Note(id = "n", createdAtMillis = millis(LocalDate.of(2026, 10, 5)), week = 12, text = "")),
        )
        assertEquals(12, keepsakeChapters(state, utc).single().week)
    }

    @Test
    fun `everything after the birth comes last, under no week`() {
        val born = LocalDate.of(2027, 2, 10)
        val state = AppState(
            dueDate = due,
            bornOn = born,
            photos = listOf(
                Photo(id = "after", date = born.plusDays(2), blob = "b"),
                Photo(id = "on", date = born, blob = "b"),
            ),
        )
        val chapters = keepsakeChapters(state, utc)
        assertEquals(listOf(39, null), chapters.map { it.week })
        assertTrue(chapters.last().moments.single().item.id == "after")
    }

    @Test
    fun `no due date, no book`() {
        assertTrue(keepsakeChapters(AppState(), utc).isEmpty())
    }
}
