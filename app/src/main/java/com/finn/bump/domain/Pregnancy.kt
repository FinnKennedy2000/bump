package com.finn.bump.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Pure gestational-age arithmetic. No clock, no Android, no state — `today` is always
 * passed in, which is what makes every case below testable without a fake clock.
 *
 * Convention is the standard obstetric one: gestational age is counted from the last
 * menstrual period (LMP) and a term pregnancy is 280 days, so
 * `dueDate == lmp + 280 days` and `gestationalDay == 280 - daysToGo`.
 */
object Pregnancy {

    const val TERM_DAYS = 280
    const val WEEKS_AT_TERM = 40

    fun dueDateFromLmp(lmp: LocalDate): LocalDate = lmp.plusDays(TERM_DAYS.toLong())

    fun lmpFromDueDate(dueDate: LocalDate): LocalDate = dueDate.minusDays(TERM_DAYS.toLong())

    /** Days remaining until the due date. Negative once overdue. */
    fun daysToGo(dueDate: LocalDate, today: LocalDate): Int =
        ChronoUnit.DAYS.between(today, dueDate).toInt()

    /**
     * Days elapsed since the LMP, floored at 0 so a due date more than 280 days away
     * reports week 0 rather than a negative week.
     */
    fun gestationalDay(dueDate: LocalDate, today: LocalDate): Int =
        (TERM_DAYS - daysToGo(dueDate, today)).coerceAtLeast(0)

    /** Completed weeks of gestation. 40 on the due date; keeps counting when overdue. */
    fun week(dueDate: LocalDate, today: LocalDate): Int =
        gestationalDay(dueDate, today) / 7

    /** Day 0..6 within the current gestational week. */
    fun dayOfWeek(dueDate: LocalDate, today: LocalDate): Int =
        gestationalDay(dueDate, today) % 7

    /** 1, 2 or 3. Weeks 0-12 / 13-27 / 28+. */
    fun trimester(week: Int): Int = when {
        week < 13 -> 1
        week < 28 -> 2
        else -> 3
    }

    fun trimester(dueDate: LocalDate, today: LocalDate): Int =
        trimester(week(dueDate, today))

    /** Progress through the 280 days, clamped to 0f..1f. */
    fun progress(dueDate: LocalDate, today: LocalDate): Float =
        (gestationalDay(dueDate, today).toFloat() / TERM_DAYS).coerceIn(0f, 1f)

    fun isOverdue(dueDate: LocalDate, today: LocalDate): Boolean =
        daysToGo(dueDate, today) < 0
}
