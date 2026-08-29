package com.finn.bump

import com.finn.bump.domain.Milestone
import com.finn.bump.domain.MilestoneKind
import com.finn.bump.domain.MilestoneTable
import com.finn.bump.domain.Pregnancy
import com.finn.bump.domain.nextFrom
import com.finn.bump.domain.on
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class MilestonesTest {

    private val due: LocalDate = LocalDate.of(2026, 12, 25)

    private val table = MilestoneTable(
        listOf(
            Milestone(11, 14, "Dating scan", "d", MilestoneKind.SCAN),
            Milestone(20, 20, "Twenty", "d", MilestoneKind.APPOINTMENT),
        )
    )

    @Test
    fun `window covers the whole week range inclusive`() {
        val w = table.forPregnancy(due).first { it.milestone.fromWeek == 11 }
        // 11+0 through 14+6 is 28 days.
        assertEquals(11, Pregnancy.week(due, w.start))
        assertEquals(0, Pregnancy.dayOfWeek(due, w.start))
        assertEquals(14, Pregnancy.week(due, w.end))
        assertEquals(6, Pregnancy.dayOfWeek(due, w.end))
        assertEquals(28, java.time.temporal.ChronoUnit.DAYS.between(w.start, w.end) + 1)
    }

    @Test
    fun `single week window is seven days`() {
        val w = table.forPregnancy(due).first { it.milestone.fromWeek == 20 }
        assertEquals(7, java.time.temporal.ChronoUnit.DAYS.between(w.start, w.end) + 1)
        assertTrue(w.start in w)
        assertTrue(w.end in w)
        assertTrue(w.start.minusDays(1) !in w)
        assertTrue(w.end.plusDays(1) !in w)
    }

    @Test
    fun `windows are ordered by start date`() {
        val starts = table.forPregnancy(due).map { it.start }
        assertEquals(starts.sorted(), starts)
    }

    @Test
    fun `on returns only milestones covering that day`() {
        val windows = table.forPregnancy(due)
        val scanStart = windows.first { it.milestone.fromWeek == 11 }.start
        assertEquals(listOf("Dating scan"), windows.on(scanStart).map { it.milestone.title })
        assertEquals(emptyList<String>(), windows.on(scanStart.minusDays(1)).map { it.milestone.title })
    }

    @Test
    fun `nextFrom returns a window still running today`() {
        val windows = table.forPregnancy(due)
        val scan = windows.first { it.milestone.fromWeek == 11 }
        // Mid-window still counts as next — you haven't missed it.
        assertEquals(scan, windows.nextFrom(scan.start.plusDays(3)))
        assertEquals("Twenty", windows.nextFrom(scan.end.plusDays(1))?.milestone?.title)
    }

    @Test
    fun `nextFrom is null once everything is behind you`() {
        val windows = table.forPregnancy(due)
        assertNull(windows.nextFrom(windows.last().end.plusDays(1)))
    }

    @Test
    fun `bundled milestones parse and are sane`() {
        val raw = File("src/main/res/raw/milestones.json").readText()
        val items = Json { ignoreUnknownKeys = true }
            .decodeFromString(ListSerializer(Milestone.serializer()), raw)

        assertTrue("expected a real schedule", items.size >= 10)
        items.forEach {
            assertTrue("blank title", it.title.isNotBlank())
            assertTrue("thin detail for '${it.title}'", it.detail.length > 40)
            assertTrue("bad window for '${it.title}'", it.fromWeek <= it.toWeek)
        }
        // Every kind is used, otherwise the calendar legend advertises a colour
        // the user will never see.
        assertEquals(MilestoneKind.entries.toSet(), items.map { it.kind }.toSet())
        // And the whole thing resolves against a real due date without throwing.
        assertNotNull(MilestoneTable(items).forPregnancy(due).nextFrom(due.minusDays(200)))
    }
}
