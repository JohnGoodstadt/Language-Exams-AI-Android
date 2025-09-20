package com.goodstadt.john.language.exams.navigation

// A sealed class defines a restricted set of types, perfect for screen routes.
sealed class RefScreen(val route: String, val title: String) {
    // This will be the "root" screen of the Me tab, showing the horizontal menu
    object RefRoot : RefScreen("me_root", "Me")

    // Routes for each of the possible menu items
//    object Settings : RefScreen("me_settings", "Settings")
//    object Search : RefScreen("me_search", "Search")
//    object Progress : RefScreen("me_progress", "Progress")
//    object Paragraph : RefScreen("me_paragraph", "Paragraph")
    object Quiz : RefScreen("me_quiz", "Quiz")

    object Conjugations : RefScreen("me_conjugations", "Conjugations")
    object Prepositions : RefScreen("me_prepositions", "Prepositions")

}

// A helper function to map a menu item title string to its corresponding screen route
fun getRefScreenRouteFromTitle(title: String): String? {
    return when (title) {
//        RefScreen.Settings.title -> RefScreen.Settings.route
//        RefScreen.Search.title -> RefScreen.Search.route
//        RefScreen.Progress.title -> RefScreen.Progress.route
//        RefScreen.Paragraph.title -> RefScreen.Paragraph.route
        RefScreen.Quiz.title -> RefScreen.Quiz.route
        RefScreen.Conjugations.title -> RefScreen.Conjugations.route
        RefScreen.Prepositions.title -> RefScreen.Prepositions.route

        else -> null
    }
}