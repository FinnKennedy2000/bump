package com.finn.bump.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The Bump palette, taken from the "Bump Redesign" design doc.
 *
 * Paper and ink rather than Material's greys: a warm off-white ground, hairline rules
 * instead of shadows, clay as the only accent that acts, and four muted hues that carry
 * meaning (scan / appointment / test / admin) and nothing else.
 *
 * Where a token from the doc failed WCAG AA 4.5:1 as *text* it was darkened or lightened
 * along its own hue and the doc value kept for decoration only — see [LabelLight],
 * [LabelDark] and [OchreText]. Contrast is not negotiable; see CLAUDE.md.
 */
object Palette {
    // Clay — the one colour that means "you can act on this".
    val Clay = Color(0xFFB04E30)
    val ClayPressed = Color(0xFF99422A)
    val ClayDark = Color(0xFFE4805C)
    /** The doc's #F6EAE5 leaves clay-on-tint at 4.48:1; one step lighter clears AA. */
    val ClayTintLight = Color(0xFFF8EFEB)
    val ClayTintDark = Color(0xFF2A1C17)

    // Ground and ink.
    val PaperLight = Color(0xFFFBF8F4)
    val RaisedLight = Color(0xFFFFFFFF)
    val InkLight = Color(0xFF17130F)

    val PaperDark = Color(0xFF131110)
    val RaisedDark = Color(0xFF1C1917)
    val InkDark = Color(0xFFF5F0EA)

    // Secondary text. Both clear AA on their own paper and on the raised surface.
    val MutedLight = Color(0xFF6F6459)
    val MutedDark = Color(0xFFA2968B)

    /** Micro-labels. The doc's #8A7D73 measures 3.8:1; this is the same hue at 5.2:1. */
    val LabelLight = Color(0xFF736759)

    /** The doc's #776D64 measures 3.5:1 on the raised dark surface; this is 4.9:1. */
    val LabelDark = Color(0xFF948A7F)

    // Lines. Never text, so the doc's values stand.
    val BorderLight = Color(0xFFD8CDC0)
    val RuleLight = Color(0xFFE8E0D6)
    val TrackLight = Color(0xFFEAE2D8)
    val FaintLight = Color(0xFFC3B8AA)

    /**
     * Interactive boundaries — button and segment frames — sit a step above the rules
     * they share a screen with. At the rule's #2E2926 a bordered control on the dark
     * ground measures 1.3:1 and the frame simply is not there.
     */
    val BorderDark = Color(0xFF4A433E)
    val RuleDark = Color(0xFF2E2926)
    val TrackDark = Color(0xFF2E2926)
    val FaintDark = Color(0xFF4A433E)

    /**
     * The fill under a selected segment or chip.
     *
     * Light is ink, straight from the doc. Dark is *not* the inverse: flipping ink puts a
     * near-white slab on a dark screen, which reads as a stray pill rather than a
     * selection. It is the ground lifted a step instead, which is what "raised" means on
     * a dark theme.
     */
    val SelectedLight = Color(0xFF17130F)
    val SelectedDark = Color(0xFF3A3430)

    val HoverLight = Color(0x0A17130F)
    val HoverDark = Color(0x14F5F0EA)
    val Scrim = Color(0x8F17130F)

    // The four meanings. Light values are the doc's; dark values are lifted for the ground.
    val Rose = Color(0xFFA8496A)          // scan
    val RoseDark = Color(0xFFD2809A)
    val Slate = Color(0xFF4F6275)         // appointment
    val SlateDark = Color(0xFF7C97AE)
    val Moss = Color(0xFF4E6A43)          // test, and "gained weight"
    val MossDark = Color(0xFF8FA96B)
    val Ochre = Color(0xFFC0871F)         // admin
    val OchreDark = Color(0xFFDFA847)

    /** Ochre as a dot is fine at 3:1; ochre as words is not. This is the same hue at 5.3:1. */
    val OchreText = Color(0xFF8A5F12)

    val Error = Color(0xFFB3261E)
    val ErrorDark = Color(0xFFF2B8B5)
}
