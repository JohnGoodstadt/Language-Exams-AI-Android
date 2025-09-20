package com.goodstadt.john.language.exams.navigation

// A sealed class defines a restricted set of types, perfect for screen routes.
sealed class MeScreen(val route: String, val title: String) {
    // This will be the "root" screen of the Me tab, showing the horizontal menu
    object MeRoot : MeScreen("me_root", "Me")

    // Routes for each of the possible menu items
    object Focusing : MeScreen("me_focus", "Focusing")
    object Settings : MeScreen("me_settings", "Settings")
    object Search : MeScreen("me_search", "Search")
    object Progress : MeScreen("me_progress", "Progress")
    object Paragraph : MeScreen("me_paragraph", "Paragraph")
//    object Quiz : MeScreen("me_quiz", "Quiz")

//    object Conjugations : MeScreen("me_conjugations", "Conjugations")
//    object Prepositions : MeScreen("me_prepositions", "Prepositions")

//    object Conversation : MeScreen("me_conversation", "Conversation")
}

// A helper function to map a menu item title string to its corresponding screen route
fun getMeScreenRouteFromTitle(title: String): String? {
    return when (title) {
        MeScreen.Focusing.title -> MeScreen.Focusing.route
        MeScreen.Settings.title -> MeScreen.Settings.route
        MeScreen.Search.title -> MeScreen.Search.route
        MeScreen.Progress.title -> MeScreen.Progress.route
        MeScreen.Paragraph.title -> MeScreen.Paragraph.route
//        MeScreen.Quiz.title -> MeScreen.Quiz.route
//        MeScreen.Conjugations.title -> MeScreen.Conjugations.route
//        MeScreen.Prepositions.title -> MeScreen.Prepositions.route

//        MeScreen.Conversation.title -> MeScreen.Conversation.route
        else -> null
    }
}