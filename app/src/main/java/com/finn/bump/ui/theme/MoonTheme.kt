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

/**
 * Moon tokens that have no Material 3 role. M3 has no success/warning/info, and no
 * slot for Moon's decorative supportive hues, so they ride alongside the scheme.
 */
@Immutable
data class MoonExtras(
    val success: Color,
    val successTint: Color,
    val warning: Color,
    val warningTint: Color,
    val info: Color,
    val infoTint: Color,
    val accentPink: Color,
    val accentPinkTint: Color,
    val accentMint: Color,
    val hover: Color,
)

val LocalMoonExtras = staticCompositionLocalOf<MoonExtras> {
    error("MoonExtras accessed outside MoonTheme")
}

private val LightScheme = lightColorScheme(
    primary = Moon.Piccolo,
    onPrimary = Moon.Goten,
    primaryContainer = Moon.JirenLight,
    onPrimaryContainer = Moon.Piccolo,
    secondary = Moon.Hit,
    onSecondary = Moon.Goten,
    tertiary = Moon.Frieza,
    onTertiary = Moon.Goten,
    background = Moon.GokuLight,
    onBackground = Moon.BulmaLight,
    surface = Moon.GohanLight,
    onSurface = Moon.BulmaLight,
    surfaceVariant = Moon.GokuLight,
    onSurfaceVariant = Moon.TrunksLight,
    outline = Moon.TrunksLight,
    outlineVariant = Moon.BeerusLight,
    error = Moon.Dodoria,
    onError = Moon.Goten,
    errorContainer = Moon.DodoriaTint,
    onErrorContainer = Moon.Dodoria,
    scrim = Moon.Zeno,
)

private val DarkScheme = darkColorScheme(
    primary = Moon.Piccolo,
    onPrimary = Moon.Goten,
    primaryContainer = Moon.JirenDark,
    onPrimaryContainer = Moon.Goten,
    secondary = Moon.Hit,
    onSecondary = Moon.Goten,
    tertiary = Moon.Frieza,
    onTertiary = Moon.Goten,
    background = Moon.GokuDark,
    onBackground = Moon.BulmaDark,
    surface = Moon.GohanDark,
    onSurface = Moon.BulmaDark,
    surfaceVariant = Moon.GohanDark,
    onSurfaceVariant = Moon.TrunksDark,
    outline = Moon.TrunksDark,
    outlineVariant = Moon.BeerusDark,
    error = Moon.Dodoria,
    onError = Moon.Goten,
    errorContainer = Moon.DodoriaTint,
    onErrorContainer = Moon.Dodoria,
    scrim = Moon.Zeno,
)

private val LightExtras = MoonExtras(
    success = Moon.Roshi,
    successTint = Moon.RoshiTint,
    warning = Moon.Krillin,
    warningTint = Moon.KrillinTint,
    info = Moon.Whis,
    infoTint = Moon.WhisTint,
    accentPink = Moon.Chichi,
    accentPinkTint = Moon.ChichiTint,
    accentMint = Moon.Cell,
    hover = Moon.HelesLight,
)

private val DarkExtras = LightExtras.copy(hover = Moon.HelesDark)

@Composable
fun MoonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMoonExtras provides if (darkTheme) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = MoonTypography,
            shapes = MoonShapes,
            content = content,
        )
    }
}
