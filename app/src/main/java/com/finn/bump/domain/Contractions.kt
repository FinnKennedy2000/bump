package com.finn.bump.domain

/**
 * Plain arithmetic over timed contractions: how long each lasted and how far apart they
 * started. Deliberately no thresholds and no advice — when to go in is your maternity
 * unit's call, made on the phone, not the app's.
 *
 * Works on bare (start, duration) pairs so the domain stays free of the data layer.
 */
object Contractions {

    /** One contraction with the gap from the start of the one before it, if any. */
    data class Timed(val startedAtMillis: Long, val durationMillis: Long, val gapMillis: Long?)

    /** Average length and gap over a window, and how many fell in it. */
    data class Summary(val count: Int, val averageDurationMillis: Long, val averageGapMillis: Long?)

    /** Oldest first, each with the start-to-start gap from its predecessor. */
    fun timed(contractions: List<Pair<Long, Long>>): List<Timed> {
        val sorted = contractions.sortedBy { it.first }
        return sorted.mapIndexed { i, (start, duration) ->
            Timed(start, duration, if (i == 0) null else start - sorted[i - 1].first)
        }
    }

    /**
     * The contractions that started in the [windowMillis] before [now]. Gaps are only
     * averaged between contractions inside the window, so a quiet spell before it does
     * not drag the figure.
     */
    fun summary(contractions: List<Pair<Long, Long>>, now: Long, windowMillis: Long = HOUR): Summary? {
        val recent = timed(contractions.filter { it.first in (now - windowMillis)..now })
        if (recent.isEmpty()) return null
        val gaps = recent.mapNotNull { it.gapMillis }
        return Summary(
            count = recent.size,
            averageDurationMillis = recent.map { it.durationMillis }.average().toLong(),
            averageGapMillis = gaps.takeIf { it.isNotEmpty() }?.average()?.toLong(),
        )
    }

    const val HOUR = 60 * 60 * 1000L
}
