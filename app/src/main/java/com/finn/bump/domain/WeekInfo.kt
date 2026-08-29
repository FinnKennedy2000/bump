package com.finn.bump.domain

import kotlinx.serialization.Serializable

/**
 * One week's reference content, loaded from `res/raw/weeks.json`.
 *
 * [lengthCm] is crown-rump length up to week 20 and crown-heel length from week 21 —
 * the standard convention, and the reason the figure jumps at that boundary. The UI
 * says which one it is showing rather than hiding the discontinuity.
 */
@Serializable
data class WeekInfo(
    val week: Int,
    val size: String,
    val lengthCm: Double?,
    val weightG: Int?,
    val blurb: String,
) {
    val measuresCrownToHeel: Boolean get() = week >= 21
}

/** Look-up over the bundled week table, clamped at both ends. */
class WeekTable(private val weeks: List<WeekInfo>) {

    init {
        require(weeks.isNotEmpty()) { "week table is empty" }
    }

    /** Nearest entry for [week]; clamps below 0 and above the last defined week. */
    operator fun get(week: Int): WeekInfo {
        val clamped = week.coerceIn(weeks.first().week, weeks.last().week)
        return weeks.first { it.week == clamped }
    }

    val lastWeek: Int get() = weeks.last().week
}
