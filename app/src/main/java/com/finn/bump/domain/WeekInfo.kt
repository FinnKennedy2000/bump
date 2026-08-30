package com.finn.bump.domain

import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * One week's reference content, loaded from `res/raw/weeks.json`.
 *
 * The picture for a week is not here: it is a drawn vector in `res/drawable/week_NN.xml`,
 * looked up by week number. See `ui/components/WeekMark.kt`.
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

    /** "head to bottom" up to week 20, "head to heel" after. */
    val measureLabel: String get() = if (measuresCrownToHeel) "head to heel" else "head to bottom"

    /** "34.6 cm · 660 g" — the pair alone, where the context already says what they are. */
    val compactFigures: String get() = listOfNotNull(
        lengthCm?.let(::formatLength),
        weightG?.let(::formatWeight),
    ).joinToString(" · ")

    /** Length and weight as one line, or an empty string in the weeks before either exists. */
    val figures: String get() = listOfNotNull(
        lengthCm?.let { "${formatLength(it)} $measureLabel" },
        weightG?.let(::formatWeight),
    ).joinToString("  ·  ")
}

/**
 * Millimetres below a centimetre.
 *
 * The early weeks are fractions of a centimetre — week 3 is 0.01 cm — and "%.1f cm"
 * renders every one of them as "0.0 cm", which reads as no baby at all.
 */
fun formatLength(cm: Double): String =
    if (cm < 1.0) "%.1f mm".format(Locale.UK, cm * 10) else "%.1f cm".format(Locale.UK, cm)

fun formatWeight(grams: Int): String =
    if (grams < 1000) "$grams g" else "%.2f kg".format(Locale.UK, grams / 1000.0)

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

    /** Every week that has something to show — the first few have no measurement yet. */
    val measured: List<WeekInfo> get() = weeks.filter { it.lengthCm != null }

    /** The longest length in the table, which sets the scale everything else is drawn against. */
    val maxLengthCm: Double get() = weeks.mapNotNull { it.lengthCm }.max()
}
