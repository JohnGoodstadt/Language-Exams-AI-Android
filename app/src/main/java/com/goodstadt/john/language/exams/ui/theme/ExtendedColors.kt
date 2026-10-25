// <project-root>/app/src/main/java/com/goodstadt/john/language/exams/ui/theme/ExtendedColors.kt
package com.goodstadt.john.language.exams.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Brand / surface colours that are NOT standard Material 3 roles, exposed with light + dark
 * variants so screens stop reaching for the hardcoded values in [Color.kt].
 *
 * See Android/DARK_MODE_PLAN.md. [LanguageExamsAITheme] provides [DarkExtendedColors] or
 * [LightExtendedColors] according to the active theme (driven by the Settings appearance choice).
 * Read via `LocalExtendedColors.current.*`. The light values are tuned for legibility on a white
 * surface; some still want an on-device QA pass.
 */
@Immutable
data class ExtendedColors(
    val brandOrange: Color,
    val accent: Color,
    val buttonBlue: Color,
    val teal: Color,
    val speakerTeal: Color,
    val cardBackground: Color,
    /** Screen/page backdrop behind cards (the near-black on dark, white on light). */
    val screenBackground: Color,
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
    screenBackground = Color(0xFF121212),    // near-black page backdrop
    rowSelected = selectedBackground,        // 0xFF5B5B60
    rowUnselected = nonSelectedBackground,   // 0xFF1C1C1F
    mutedGrey = greyLight2,                  // 0xFF505055
)

val LightExtendedColors = ExtendedColors(
    brandOrange = Color(0xFFE8740C),         // vivid orange that reads on white
    accent = Color(0xFFE8740C),
    buttonBlue = buttonColor,                // blue reads on both
    teal = Color(0xFF00796B),                // true teal, legible on white (harmonises with speakerTeal)
    speakerTeal = SpeakerTeal,               // 0xFF00897B reads on both
    cardBackground = Color(0xFFF2F2F7),
    screenBackground = Color(0xFFFFFFFF),
    rowSelected = Color(0xFFD0D0D5),
    rowUnselected = Color(0xFFFFFFFF),
    mutedGrey = Color(0xFF8E8E93),
)

val LocalExtendedColors = staticCompositionLocalOf { DarkExtendedColors }
