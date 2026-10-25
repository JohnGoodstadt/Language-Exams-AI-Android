// <project-root>/app/src/main/java/com/goodstadt/john/language/exams/ui/theme/ExtendedColors.kt
package com.goodstadt.john.language.exams.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Brand / surface colours that are NOT standard Material 3 roles, exposed with light + dark
 * variants so screens stop reaching for the hardcoded values in [Color.kt].
 *
 * Phase 1 of the dark/light-mode work (see Android/DARK_MODE_PLAN.md): this is INERT for now —
 * the app is still forced to Dark in MainActivity, so only [DarkExtendedColors] is ever used and
 * nothing changes visually. Phase 2 sweeps usages onto `LocalExtendedColors.current.*`; the light
 * values below are placeholders tuned for legibility on a white surface and get a QA pass in Phase 3.
 */
@Immutable
data class ExtendedColors(
    val brandOrange: Color,
    val accent: Color,
    val buttonBlue: Color,
    val teal: Color,
    val speakerTeal: Color,
    val cardBackground: Color,
    val rowSelected: Color,
    val rowUnselected: Color,
    val mutedGrey: Color,
)

val DarkExtendedColors = ExtendedColors(
    brandOrange = Orange,                    // 0xFFFFA500
    accent = accentColor,                    // 0xFFFFAE02
    buttonBlue = buttonColor,                // 0xFF1777FB
    teal = Teal,                             // 0xFF66B5D5
    speakerTeal = SpeakerTeal,               // 0xFF00897B
    cardBackground = CardBG,                 // 0xFF1C1C1E
    rowSelected = selectedBackground,        // 0xFF5B5B60
    rowUnselected = nonSelectedBackground,   // 0xFF1C1C1F
    mutedGrey = greyLight2,                  // 0xFF505055
)

val LightExtendedColors = ExtendedColors(
    brandOrange = orangeDark,                // 0xFFC17D03 — darker, legible on white
    accent = Color(0xFFC88A00),
    buttonBlue = buttonColor,                // blue reads on both
    teal = Color(0xFF2A8FB5),                // darker teal for white
    speakerTeal = SpeakerTeal,               // 0xFF00897B reads on both
    cardBackground = Color(0xFFF2F2F7),
    rowSelected = Color(0xFFD0D0D5),
    rowUnselected = Color(0xFFFFFFFF),
    mutedGrey = Color(0xFF8E8E93),
)

val LocalExtendedColors = staticCompositionLocalOf { DarkExtendedColors }
