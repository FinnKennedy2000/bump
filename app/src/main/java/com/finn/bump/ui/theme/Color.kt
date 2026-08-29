package com.finn.bump.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Moon Design System v1 colour tokens, extracted from the Figma "Colors" variable
 * collection (Light / Dark modes). Names are Moon's own.
 *
 * Only the tokens the app actually uses are ported. Moon defines 61; adding another is
 * one line, so there is nothing to gain from porting the rest up front.
 */
object Moon {
    // Main
    val Piccolo = Color(0xFF4E46B4)          // brand indigo, both modes
    val Hit = Color(0xFF40A69F)              // brand teal, both modes
    val Goten = Color(0xFFFFFFFF)            // text on brand, both modes

    val BeerusLight = Color(0xFFE2E2E2)
    val BeerusDark = Color(0xFF444444)
    val GokuLight = Color(0xFFF5F5F5)
    val GokuDark = Color(0xFF0B0B0B)
    val GohanLight = Color(0xFFFFFFFF)
    val GohanDark = Color(0xFF1F1F1F)
    val BulmaLight = Color(0xFF000000)
    val BulmaDark = Color(0xFFFFFFFF)

    val TrunksLight = Color(0xFF595D62)

    /**
     * Deliberate deviation from Moon.
     *
     * Moon ships `trunks` as #595D62 in BOTH modes. On its own dark background
     * (#0B0B0B) that measures ~3.4:1 — below the WCAG AA 4.5:1 floor for body text.
     * This is the lightened value (~9:1). Secondary text has to be readable.
     */
    val TrunksDark = Color(0xFFA3A8AE)

    val JirenLight = Color(0x1F4E46B4)       // brand @ 12%
    val JirenDark = Color(0x524E46B4)        // brand @ 32%
    val HelesLight = Color(0x0A000000)       // hover overlay @ 4%
    val HelesDark = Color(0x14FFFFFF)        // hover overlay @ 8%
    val Zeno = Color(0x8F000000)             // scrim @ 56%

    // Supportive
    val Krillin = Color(0xFFFFB319)          // warning
    val KrillinTint = Color(0x1FFFB319)
    val Chichi = Color(0xFFFF4E64)           // pink
    val ChichiTint = Color(0x1FFF4E64)
    val Roshi = Color(0xFF2E7D32)            // success
    val RoshiTint = Color(0x1F2E7D32)
    val Dodoria = Color(0xFFD33030)          // error
    val DodoriaTint = Color(0x1FD33030)
    val Cell = Color(0xFF95F1D5)             // mint
    val Whis = Color(0xFF3448F0)             // info
    val WhisTint = Color(0x1F3448F0)
    val Frieza = Color(0xFF5C33CF)           // violet
    val Raditz = Color(0xFFB3804A)           // tan
    val Nappa = Color(0xFF725550)            // brown
}
