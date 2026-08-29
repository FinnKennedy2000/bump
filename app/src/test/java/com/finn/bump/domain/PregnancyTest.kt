package com.finn.bump.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The domain functions are the only non-trivial logic in the app, so this is the only
 * test class. Everything here is pure, so `today` is passed in and no clock is faked.
 */
class PregnancyTest {

    private val due = LocalDate.of(2026, 12, 25)

    /** Convenience: the date [daysBefore] days before the due date. */
    private fun before(daysBefore: Long) = due.minusDays(daysBefore)

    @Test
    fun `due date is 280 days after LMP and back again`() {
        val lmp = LocalDate.of(2026, 3, 20)
        assertEquals(lmp.plusDays(280), Pregnancy.dueDateFromLmp(lmp))
        assertEquals(lmp, Pregnancy.lmpFromDueDate(Pregnancy.dueDateFromLmp(lmp)))
    }

    @Test
    fun `on the due date it is week 40 with nothing left to go`() {
        assertEquals(40, Pregnancy.week(due, due))
        assertEquals(0, Pregnancy.dayOfWeek(due, due))
        assertEquals(0, Pregnancy.daysToGo(due, due))
        assertEquals(1f, Pregnancy.progress(due, due), 0.0001f)
        assertFalse(Pregnancy.isOverdue(due, due))
    }

    @Test
    fun `the day the pregnancy starts is week 0 day 0`() {
        val lmp = before(280)
        assertEquals(0, Pregnancy.week(due, lmp))
        assertEquals(0, Pregnancy.dayOfWeek(due, lmp))
        assertEquals(280, Pregnancy.daysToGo(due, lmp))
        assertEquals(0f, Pregnancy.progress(due, lmp), 0.0001f)
    }

    @Test
    fun `a due date further out than a full term still reports week 0, never negative`() {
        val tooEarly = before(400)
        assertEquals(0, Pregnancy.week(due, tooEarly))
        assertEquals(0, Pregnancy.gestationalDay(due, tooEarly))
        assertEquals(0f, Pregnancy.progress(due, tooEarly), 0.0001f)
    }

    @Test
    fun `overdue keeps counting weeks and goes negative on days to go`() {
        val threeDaysOver = due.plusDays(3)
        assertEquals(40, Pregnancy.week(due, threeDaysOver))
        assertEquals(3, Pregnancy.dayOfWeek(due, threeDaysOver))
        assertEquals(-3, Pregnancy.daysToGo(due, threeDaysOver))
        assertTrue(Pregnancy.isOverdue(due, threeDaysOver))

        val eightDaysOver = due.plusDays(8)
        assertEquals(41, Pregnancy.week(due, eightDaysOver))
        assertEquals(-8, Pregnancy.daysToGo(due, eightDaysOver))
    }

    @Test
    fun `progress is clamped at both ends`() {
        assertEquals(0f, Pregnancy.progress(due, before(999)), 0.0001f)
        assertEquals(1f, Pregnancy.progress(due, due.plusDays(30)), 0.0001f)
    }

    @Test
    fun `a week rolls over exactly once, on day 7`() {
        // Gestational day 139 is week 19 day 6; 140 is week 20 day 0.
        val day139 = before(280 - 139)
        val day140 = before(280 - 140)

        assertEquals(19, Pregnancy.week(due, day139))
        assertEquals(6, Pregnancy.dayOfWeek(due, day139))

        assertEquals(20, Pregnancy.week(due, day140))
        assertEquals(0, Pregnancy.dayOfWeek(due, day140))
    }

    @Test
    fun `trimester boundaries fall at weeks 13 and 28`() {
        assertEquals(1, Pregnancy.trimester(0))
        assertEquals(1, Pregnancy.trimester(12))
        assertEquals(2, Pregnancy.trimester(13))
        assertEquals(2, Pregnancy.trimester(27))
        assertEquals(3, Pregnancy.trimester(28))
        assertEquals(3, Pregnancy.trimester(42))
    }

    @Test
    fun `gestational day is the complement of days to go`() {
        for (daysOut in 0..280 step 17) {
            val today = before(daysOut.toLong())
            assertEquals(280 - daysOut, Pregnancy.gestationalDay(due, today))
        }
    }
}
