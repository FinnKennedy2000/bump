package com.finn.bump.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.finn.bump.domain.MilestoneKind

/**
 * The parts of the palette Material 3 has no slot for: the hairlines the layout is built
 * from, the micro-label tier, and the four hues that mean something.
 */
@Immutable
data class BumpExtras(
    val label: Color,
    val selected: Color,
    val onSelected: Color,
    val rule: Color,
    val track: Color,
    val faint: Color,
    val hover: Color,
    val scan: Color,
    val appointment: Color,
    val test: Color,
    val admin: Color,
    val adminText: Color,
    val gain: Color,
)

val LocalBumpExtras = staticCompositionLocalOf<BumpExtras> {
    error("BumpExtras accessed outside BumpTheme")
}

/** The dot colour for a milestone kind. Classes share the scan hue; both are visits. */
@Composable
fun MilestoneKind.color(): Color {
    val extras = LocalBumpExtras.current
    return when (this) {
        MilestoneKind.SCAN -> extras.scan
        MilestoneKind.APPOINTMENT -> extras.appointment
        MilestoneKind.TEST -> extras.test
        MilestoneKind.CLASS -> extras.scan
        MilestoneKind.ADMIN -> extras.admin
    }
}

private val LightScheme = lightColorScheme(
    primary = Palette.Clay,
    onPrimary = Color.White,
    primaryContainer = Palette.ClayTintLight,
    onPrimaryContainer = Palette.Clay,
    secondary = Palette.Slate,
    onSecondary = Color.White,
    tertiary = Palette.Moss,
    onTertiary = Color.White,
    background = Palette.PaperLight,
    onBackground = Palette.InkLight,
    surface = Palette.RaisedLight,
    onSurface = Palette.InkLight,
    surfaceVariant = Palette.PaperLight,
    onSurfaceVariant = Palette.MutedLight,
    outline = Palette.BorderLight,
    outlineVariant = Palette.RuleLight,
    error = Palette.Error,
    onError = Color.White,
    scrim = Palette.Scrim,
)

private val DarkScheme = darkColorScheme(
    primary = Palette.ClayDark,
    onPrimary = Palette.PaperDark,
    primaryContainer = Palette.ClayTintDark,
    onPrimaryContainer = Palette.ClayDark,
    secondary = Palette.SlateDark,
    onSecondary = Palette.PaperDark,
    tertiary = Palette.MossDark,
    onTertiary = Palette.PaperDark,
    background = Palette.PaperDark,
    onBackground = Palette.InkDark,
    surface = Palette.RaisedDark,
    onSurface = Palette.InkDark,
    surfaceVariant = Palette.PaperDark,
    onSurfaceVariant = Palette.MutedDark,
    outline = Palette.BorderDark,
    outlineVariant = Palette.RuleDark,
    error = Palette.ErrorDark,
    onError = Palette.PaperDark,
    scrim = Palette.Scrim,
)

private val LightExtras = BumpExtras(
    label = Palette.LabelLight,
    selected = Palette.SelectedLight,
    onSelected = Palette.PaperLight,
    rule = Palette.RuleLight,
    track = Palette.TrackLight,
    faint = Palette.FaintLight,
    hover = Palette.HoverLight,
    scan = Palette.Rose,
    appointment = Palette.Slate,
    test = Palette.Moss,
    admin = Palette.Ochre,
    adminText = Palette.OchreText,
    gain = Palette.Moss,
)

private val DarkExtras = BumpExtras(
    label = Palette.LabelDark,
    selected = Palette.SelectedDark,
    onSelected = Palette.InkDark,
    rule = Palette.RuleDark,
    track = Palette.TrackDark,
    faint = Palette.FaintDark,
    hover = Palette.HoverDark,
    scan = Palette.RoseDark,
    appointment = Palette.SlateDark,
    test = Palette.MossDark,
    admin = Palette.OchreDark,
    adminText = Palette.OchreDark,
    gain = Palette.MossDark,
)

@Composable
fun BumpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalBumpExtras provides if (darkTheme) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = BumpTypography,
            shapes = BumpShapes,
            content = content,
        )
    }
}
