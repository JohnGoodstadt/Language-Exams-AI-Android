package com.goodstadt.john.language.exams.navigation

// A sealed class defines a restricted set of types, perfect for screen routes.
sealed class MeScreen(val route: String, val title: String) {
    // This will be the "root" screen of the Me tab, showing the horizontal menu
    object MeRoot : MeScreen("me_root", "Me")

    // Routes for each of the possible menu items
    object Focus : MeScreen("me_focus", "Focus")
    object Saved : MeScreen("me_saved", "Saved")
    object Settings : MeScreen("me_settings", "Settings")
    object Search : MeScreen("me_search", "Vocab")
    object Progress : MeScreen("me_progress", "Progress")
    object Paragraph : MeScreen("me_paragraph", "Paragraph")
    object DailyWord : MeScreen("me_daily_word", "Word of the Day")
    object WordQuiz : MeScreen("me_word_quiz", "Vocab Quiz")
}

// A helper function to map a menu item title string to its corresponding screen route
fun getMeScreenRouteFromTitle(title: String): String? {
    return when (title) {
        MeScreen.Focus.title -> MeScreen.Focus.route
        MeScreen.Saved.title -> MeScreen.Saved.route
        MeScreen.Settings.title -> MeScreen.Settings.route
        MeScreen.Search.title -> MeScreen.Search.route
        MeScreen.Progress.title -> MeScreen.Progress.route
        MeScreen.Paragraph.title -> MeScreen.Paragraph.route
        MeScreen.DailyWord.title -> MeScreen.DailyWord.route
        MeScreen.WordQuiz.title -> MeScreen.WordQuiz.route
        else -> null
    }
}