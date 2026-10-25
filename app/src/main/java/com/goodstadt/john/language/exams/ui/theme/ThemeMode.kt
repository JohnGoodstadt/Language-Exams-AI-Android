// <project-root>/app/src/main/java/com/goodstadt/john/language/exams/ui/theme/ThemeMode.kt
package com.goodstadt.john.language.exams.ui.theme

/**
 * User-facing appearance choice (Settings). Persisted by its [name] in DataStore
 * (UserPreferencesRepository.themeModeFlow) and read at the app root in MainActivity, where
 * [SYSTEM] defers to `isSystemInDarkTheme()`. See Android/DARK_MODE_PLAN.md (Phase 3).
 */
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    companion object {
        /** Safe parse of a stored name; unknown/legacy values fall back to [SYSTEM]. */
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}
