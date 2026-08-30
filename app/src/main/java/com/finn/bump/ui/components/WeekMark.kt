package com.finn.bump.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.finn.bump.R

/**
 * The drawn mark for a gestational week — one flat vector per week, 3 to 40, from the
 * design doc. The same mark appears on Today, the timeline, the week sheet, the size
 * chart and the widget, which is the point of having one.
 *
 * Weeks outside 3..40 clamp to the ends: before week 3 there is nothing to draw, and the
 * table itself clamps at 40.
 *
 * Nineteen of the marks lean on near-black or deep fills that fall under 3:1 on the dark
 * card, so those have a `drawable-night/` variant with each offending colour lifted along
 * its own hue. The rest are light enough to serve both themes from one file.
 */
@Composable
fun WeekMark(week: Int, size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(weekMark(week)),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

fun weekMark(week: Int): Int = when (week.coerceIn(3, 40)) {
    3 -> R.drawable.week_03
    4 -> R.drawable.week_04
    5 -> R.drawable.week_05
    6 -> R.drawable.week_06
    7 -> R.drawable.week_07
    8 -> R.drawable.week_08
    9 -> R.drawable.week_09
    10 -> R.drawable.week_10
    11 -> R.drawable.week_11
    12 -> R.drawable.week_12
    13 -> R.drawable.week_13
    14 -> R.drawable.week_14
    15 -> R.drawable.week_15
    16 -> R.drawable.week_16
    17 -> R.drawable.week_17
    18 -> R.drawable.week_18
    19 -> R.drawable.week_19
    20 -> R.drawable.week_20
    21 -> R.drawable.week_21
    22 -> R.drawable.week_22
    23 -> R.drawable.week_23
    24 -> R.drawable.week_24
    25 -> R.drawable.week_25
    26 -> R.drawable.week_26
    27 -> R.drawable.week_27
    28 -> R.drawable.week_28
    29 -> R.drawable.week_29
    30 -> R.drawable.week_30
    31 -> R.drawable.week_31
    32 -> R.drawable.week_32
    33 -> R.drawable.week_33
    34 -> R.drawable.week_34
    35 -> R.drawable.week_35
    36 -> R.drawable.week_36
    37 -> R.drawable.week_37
    38 -> R.drawable.week_38
    39 -> R.drawable.week_39
    40 -> R.drawable.week_40
    else -> R.drawable.week_40
}
