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
 *
 * To EXPERIMENT with alternative values: edit [LightExtendedColorsAlternative] /
 * [DarkExtendedColorsAlternative] below, rebuild, then flip the DEBUG "Use alternative palette"
 * switch in Settings to compare them live against the real palette.
 */
@Immutable
data class ExtendedColors(
    /** The app's orange accent (titles, icons, chips). Was brandOrange + accent, now merged. */
    val screenAccent: Color,
    val buttonBlue: Color,
    /** Teal accent for highlights, headwords and speaker names. */
    val teal: Color,
    val cardBackground: Color,
    /** Screen/page backdrop behind cards (the near-black on dark, white on light). */
    val screenBackground: Color,
    val rowSelected: Color,
    val rowUnselected: Color,
    val mutedGrey: Color,
    /** Quiz answer-option text (e.g. the three choices on the Usage/Vocab quiz). */
    val quizQuestionText: Color,
)

val DarkExtendedColors = ExtendedColors(
    screenAccent = Orange,                   // 0xFFFFA500
    buttonBlue = buttonColor,                // 0xFF1777FB
    teal = Teal,                             // 0xFF66B5D5
    cardBackground = CardBG,                 // 0xFF1C1C1E
    screenBackground = Color(0xFF121212),    // near-black page backdrop
    rowSelected = selectedBackground,        // 0xFF5B5B60
    rowUnselected = nonSelectedBackground,   // 0xFF1C1C1F
    mutedGrey = greyLight2,                  // 0xFF505055
    quizQuestionText = orangeLight,          // 0xFFFFA500 (orange on dark, as before)
)

val LightExtendedColors = ExtendedColors(
    screenAccent = Color(0xFFD35400),        // orange accent, legible on white
    buttonBlue = buttonColor,                // blue reads on both
    teal = Color(0xFF00796B),                // true teal, legible on white
    cardBackground = Color(0xFFF2F2F7),
    screenBackground = Color(0xFFFFFFFF),
    rowSelected = Color(0xFFD0D0D5),
    rowUnselected = Color(0xFFFFFFFF),
    mutedGrey = Color(0xFF8E8E93),
    quizQuestionText = Color(0xFF000000),    // black answer text on white
)

// --- Experimental / reserve palettes -------------------------------------------------------------
// Start as exact copies of the real palettes. Override any field(s) here to try new values, e.g.
//   val LightExtendedColorsAlternative = LightExtendedColors.copy(
//       screenAccent = Color(0xFFE8740C),
//       teal         = Color(0xFF00695C),
//   )
// Rebuild, then enable the DEBUG "Use alternative palette" switch in Settings to see them live.
val LightExtendedColorsAlternative = LightExtendedColors.copy(
    screenAccent = blueDark
)
val DarkExtendedColorsAlternative = DarkExtendedColors.copy(
)

val LocalExtendedColors = staticCompositionLocalOf { DarkExtendedColors }
