package com.finn.bump.data

import com.finn.bump.domain.Pregnancy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One logged thing placed on the calendar, ready to be set on a page. A day of timed
 * contractions is one moment — a night of forty is one event, not forty lines — with
 * the first as [item] and the whole day in [contractions].
 */
data class Moment(
    val date: LocalDate,
    val sortMillis: Long,
    val item: Logged,
    val contractions: List<Contraction> = emptyList(),
)

/**
 * A run of moments under one heading: a gestational [week], or `null` for everything
 * after the birth. Only chapters with something in them exist — the keepsake is the
 * record you kept, not a calendar of the weeks you did not write anything in.
 */
data class Chapter(val week: Int?, val moments: List<Moment>)

/**
 * The record as the keepsake tells it: in pregnancy order, week by week, oldest first.
 *
 * A note keeps the week it was written in rather than one recomputed from its date, so a
 * due date corrected later does not move what someone wrote under "week 12". Contacts
 * have no date, so they are listed with the facts instead; the bag list is a to-do, not
 * a memory, and is left to the data export. Pure, so the ordering is tested on
 * the JVM; the PDF renderer only draws what this returns.
 */
fun keepsakeChapters(state: AppState, zone: ZoneId = ZoneId.systemDefault()): List<Chapter> {
    val due = state.dueDate ?: return emptyList()
    val live = state.withoutTombstones()

    fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    fun startOf(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()

    val moments = buildList {
        live.appointments.forEach { add(Moment(day(it.atMillis), it.atMillis, it)) }
        live.weights.forEach { add(Moment(it.date, startOf(it.date), it)) }
        live.notes.forEach { add(Moment(day(it.createdAtMillis), it.createdAtMillis, it)) }
        live.kickSessions.forEach { add(Moment(day(it.startedAtMillis), it.startedAtMillis, it)) }
        live.photos.forEach { add(Moment(it.date, startOf(it.date), it)) }
        live.contractions.groupBy { day(it.startedAtMillis) }.forEach { (date, day) ->
            val sorted = day.sortedBy { it.startedAtMillis }
            add(Moment(date, sorted.first().startedAtMillis, sorted.first(), sorted))
        }
    }

    fun weekOf(m: Moment): Int? = when {
        state.bornOn != null && m.date > state.bornOn -> null
        m.item is Note -> m.item.week
        else -> Pregnancy.week(due, m.date)
    }

    return moments
        .groupBy(::weekOf)
        .map { (week, ms) -> Chapter(week, ms.sortedBy { it.sortMillis }) }
        // Weeks in order, and the time after the birth last.
        .sortedBy { it.week ?: Int.MAX_VALUE }
}
