package com.finn.bump.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.finn.bump.R

/**
 * Two families, split by job.
 *
 * [Gambetta] is a serif and every *measured figure* is set in it — the week number, the
 * size name, lengths, weights, dates. [Satoshi] is the interface: labels, buttons, body.
 * That split is the whole typographic idea of the redesign, so the display slots below
 * are serif and everything else is sans, rather than the usual one-family ramp.
 *
 * Both are bundled as static TTFs (Fontshare, ITF Free Font License — see
 * `assets/ITF-FreeFontLicense.txt`). No downloadable-fonts provider, because the app has
 * no INTERNET permission and never will.
 */
val Gambetta = FontFamily(Font(R.font.gambetta_500, FontWeight.Medium))

val Satoshi = FontFamily(
    Font(R.font.satoshi_400, FontWeight.Normal),
    Font(R.font.satoshi_500, FontWeight.Medium),
    Font(R.font.satoshi_700, FontWeight.Bold),
)

/** A measured figure: serif, medium, tight. */
fun figure(size: Int, lineHeight: Int = size + 4, tracking: Double = -0.2) = TextStyle(
    fontFamily = Gambetta,
    fontWeight = FontWeight.Medium,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

private fun ui(
    size: Int,
    lineHeight: Int = size + 8,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = Satoshi,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

/**
 * Serif in the display and headline slots, sans everywhere else.
 *
 * [Typography.labelSmall] is the uppercase micro-label the whole layout leans on
 * ("ABOUT THE SIZE OF", "WEEK 40"), so it carries the tracking rather than every call
 * site setting it.
 */
val BumpTypography = Typography(
    displayLarge = figure(92, 82, -2.5),      // the week number on Today
    displayMedium = figure(46, 52, -0.8),     // onboarding
    displaySmall = figure(40, 44, -0.4),      // the kick timer
    headlineLarge = figure(44, 50, -0.8),     // the size name in the week sheet
    headlineMedium = figure(36, 40, -0.4),    // the size name on Today
    headlineSmall = figure(30, 34, -0.3),     // screen titles
    titleLarge = ui(17, 24, FontWeight.Bold),
    titleMedium = ui(15, 22, FontWeight.Bold),
    titleSmall = figure(22, 26, -0.2),        // dates, month names, gutter figures
    bodyLarge = ui(16, 26),
    bodyMedium = ui(14, 22),
    bodySmall = ui(13, 20),
    labelLarge = ui(13, 20, FontWeight.Bold),
    labelMedium = ui(12, 18, FontWeight.Bold),
    labelSmall = ui(11, 16, FontWeight.Bold, 1.0),
)
