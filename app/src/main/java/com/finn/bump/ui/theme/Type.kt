package com.finn.bump.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.finn.bump.R

/**
 * DM Sans, bundled as the single variable font Google ships (there is no static build
 * in google/fonts any more). Weights are selected through the `wght` axis, which
 * requires API 26 — the app's minSdk.
 */
@OptIn(ExperimentalTextApi::class)
private fun dmSans(weight: FontWeight) = Font(
    resId = R.font.dm_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val DMSans = FontFamily(
    dmSans(FontWeight.Normal),
    dmSans(FontWeight.Medium),
    dmSans(FontWeight.Bold),
)

private fun moon(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = DMSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
)

/**
 * Nine of Moon's 54 text styles, mapped onto the M3 slots the app uses.
 * [R] = Regular, [S] = Bold in Moon's naming.
 */
val MoonTypography = Typography(
    displaySmall = moon(40, 48, FontWeight.Bold, -0.5),      // UI 4xl [S]
    headlineMedium = moon(32, 40, FontWeight.Bold, -0.5),    // UI 3xl [S]
    headlineSmall = moon(24, 32, FontWeight.Bold),           // UI 2xl [S]
    titleLarge = moon(20, 32, FontWeight.Bold),              // UI xl  [S]
    titleMedium = moon(16, 24, FontWeight.Bold),             // UI md  [S]
    bodyLarge = moon(16, 24),                                // Paragraph md [R]
    bodyMedium = moon(14, 20),                               // Paragraph sm [R]
    labelLarge = moon(14, 24, FontWeight.Bold),              // UI sm  [S]
    labelSmall = moon(10, 16, FontWeight.Bold),              // UI 2xs [S]
)
