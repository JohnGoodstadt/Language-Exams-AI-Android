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
// Settings row icons: the sidebar study palette, in display order. Both appearances share
// the same hue sequence; dark mode uses brighter variants. Keep these lists non-empty.
private val LightSettingsIconPalette = listOf(
    Color(0xFFFC4902), // orange-red
    Color(0xFFC630D9), // magenta
    Color(0xFF5E54F1), // violet
    Color(0xFF2785E0), // blue
    Color(0xFFFB2B53), // pink-red
    Color(0xFF03C0E4), // cyan
    Color(0xFF2FC2AF), // mint
    Color(0xFFEE903E), // warm orange
    Color(0xFF48C06A), // green
)

private val DarkSettingsIconPalette = listOf(
    Color(0xFFFF6B35), // orange-red
    Color(0xFFDB68E8), // magenta
    Color(0xFF9287FF), // violet
    Color(0xFF64B5FF), // blue
    Color(0xFFFF6488), // pink-red
    Color(0xFF53D8F5), // cyan
    Color(0xFF66D9C7), // mint
    Color(0xFFFFB366), // warm orange
    Color(0xFF76D893), // green
)

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
    /** "Success"/correct green used for text & highlighted correct words. Bright green on dark,
     *  a darker green on light (pure green is too light on white). */
    val textAccentAlternative: Color,
    /** Icon buttons (e.g. CategoryTab actions). Orange on dark (unchanged), dark blue on light. */
    val iconAccent: Color,
    /** Section headers / titles. Orange on dark (unchanged), black on light. */
    val sectionHeader: Color,
    /** The Material "status/warning/learning" orange that was hard-coded as Color(0xFFFF9800)
     *  across the UI. Alter here to retint all those usages. */
    val statusOrange: Color,
    /** Leading Settings icons, cycling in visible row order. */
    val settingsIconPalette: List<Color>,
) {
    fun settingsIconColor(index: Int): Color = settingsIconPalette[index % settingsIconPalette.size]
}

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
    textAccentAlternative = Color.Green,     // bright green reads on dark
    iconAccent = orangeLight,                // unchanged on dark (orange)
    sectionHeader = orangeLight,             // unchanged on dark (orange)
    statusOrange = statusOrange,             // 0xFFFF9800
    settingsIconPalette = DarkSettingsIconPalette,
)

val LightExtendedColors = ExtendedColors(
    screenAccent = blueDark,                 // 0xFF0D47A1 dark blue accent on light (was orange 0xFFD35400)
    buttonBlue = buttonColor,                // blue reads on both
    teal = Color(0xFF00796B),                // true teal, legible on white
    cardBackground = Color(0xFFF2F2F7),
    screenBackground = Color(0xFFFFFFFF),
    rowSelected = Color(0xFFD0D0D5),
    rowUnselected = Color(0xFFFFFFFF),
    mutedGrey = Color(0xFF8E8E93),
    quizQuestionText = Color(0xFF000000),    // black answer text on white
    textAccentAlternative = Color(0xFF2E7D32), // darker green, legible on white
    iconAccent = blueDark,                   // 0xFF0D47A1 dark blue on light
    sectionHeader = Color(0xFF000000),       // black headers on light
    statusOrange = Color(0xFFFF9800),        // alter this to retint light-mode status orange
    settingsIconPalette = LightSettingsIconPalette,
)

// --- Experimental / reserve palettes -------------------------------------------------------------
// Start as exact copies of the real palettes. Override any field(s) here to try new values, e.g.
//   val LightExtendedColorsAlternative = LightExtendedColors.copy(
//       screenAccent = Color(0xFFE8740C),
//       teal         = Color(0xFF00695C),
//   )
// Rebuild, then enable the DEBUG "Use alternative palette" switch in Settings to see them live.
// Swapped 2026-10-27: the dark-blue accent (previously the alternative) is now the main light
// palette; the alternative keeps the previous orange accent for comparison.
// 2026-10-27: also trials the Apple Health light-mode lessons (LIGHT_MODE_APPLE_HEALTH_ANALYSIS.md):
// grey page with white cards (Theme.kt greys the Material background/surface to match), and
// system-blue accents instead of navy. Orange accent kept for comparison with the main palette.
val LightExtendedColorsAlternative = LightExtendedColors.copy(
    screenAccent = blueDark,//Color(0xFFD35400),
    screenBackground = Color(0xFFF2F2F7),    // Apple grouped background (page)
    cardBackground = Color(0xFFFFFFFF),      // white cards on the grey page
    iconAccent = buttonColor,                // 0xFF1777FB system-blue icon buttons (navy read as black)
)
val DarkExtendedColorsAlternative = DarkExtendedColors.copy(
)

val LocalExtendedColors = staticCompositionLocalOf { DarkExtendedColors }

/**
 * Whether the active theme is dark (the resolved System/Light/Dark choice). Provided by
 * [LanguageExamsAITheme]. For the few components whose light-mode STYLE differs, not just their
 * colours (e.g. the iOS-style sub-tab menu and numbered bottom-tab circles).
 */
val LocalIsDarkTheme = staticCompositionLocalOf { true }

/**
 * Whether highlighted words in sentences are underlined. Default off (colour only); the Settings
 * "Underline highlighted words" switch turns it on (helps accessibility / colour-blind users).
 * Provided at the app root from the persisted setting.
 */
val LocalHighlightUnderline = staticCompositionLocalOf { false }
