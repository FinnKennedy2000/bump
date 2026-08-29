package com.finn.bump.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate

/** What sort of thing a milestone is, which is all the UI needs to colour and group it. */
@Serializable
enum class MilestoneKind { SCAN, APPOINTMENT, TEST, CLASS, ADMIN }

/**
 * One entry in the typical NHS antenatal pathway, loaded from `res/raw/milestones.json`.
 *
 * Stored as a gestational week window rather than a date, because a milestone is a fact
 * about pregnancies in general, not about this one. Resolving it against a due date is
 * [MilestoneTable.forPregnancy].
 *
 * These are typical timings, not appointments anyone has booked. Nothing derives from
 * them and nothing is persisted — a milestone only becomes real data when the user
 * turns it into an [com.finn.bump.data.Appointment].
 */
@Serializable
data class Milestone(
    val fromWeek: Int,
    val toWeek: Int,
    val title: String,
    val detail: String,
    val kind: MilestoneKind,
) {
    init {
        require(fromWeek in 0..45 && toWeek in fromWeek..45) {
            "bad milestone window $fromWeek..$toWeek for '$title'"
        }
    }

    /** "Week 20" for a single week, "Weeks 11–14" for a range. */
    val weekLabel: String get() =
        if (fromWeek == toWeek) "Week $fromWeek" else "Weeks $fromWeek–$toWeek"
}

/** A [Milestone] resolved against one due date, so it has real calendar dates. */
data class MilestoneWindow(
    val milestone: Milestone,
    val start: LocalDate,
    val end: LocalDate,
) {
    operator fun contains(date: LocalDate): Boolean = date >= start && date <= end
}

/** Look-up over the bundled milestone list. */
class MilestoneTable(private val items: List<Milestone>) {

    init {
        require(items.isNotEmpty()) { "milestone table is empty" }
    }

    val all: List<Milestone> get() = items

    /**
     * Every milestone as a date window for [dueDate].
     *
     * Week `n` spans gestational days `7n` to `7n + 6`, and gestational day `d` is
     * `dueDate - (280 - d)`, so the window runs from the first day of [Milestone.fromWeek]
     * to the last day of [Milestone.toWeek].
     */
    fun forPregnancy(dueDate: LocalDate): List<MilestoneWindow> =
        items.map { m ->
            MilestoneWindow(
                milestone = m,
                start = dayOf(dueDate, m.fromWeek * 7),
                end = dayOf(dueDate, m.toWeek * 7 + 6),
            )
        }.sortedWith(compareBy({ it.start }, { it.milestone.title }))

    private fun dayOf(dueDate: LocalDate, gestationalDay: Int): LocalDate =
        dueDate.minusDays((Pregnancy.TERM_DAYS - gestationalDay).toLong())
}

/** The next window that hasn't finished yet, or null once they're all behind you. */
fun List<MilestoneWindow>.nextFrom(today: LocalDate): MilestoneWindow? =
    firstOrNull { !it.end.isBefore(today) }

fun List<MilestoneWindow>.on(date: LocalDate): List<MilestoneWindow> =
    filter { date in it }
