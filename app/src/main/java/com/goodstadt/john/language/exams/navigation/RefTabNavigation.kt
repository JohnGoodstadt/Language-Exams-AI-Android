package com.goodstadt.john.language.exams.navigation

// A sealed class defines a restricted set of types, perfect for screen routes.
//sealed class RefScreen(val route: String, val title: String) {
//    // This will be the "root" screen of the Me tab, showing the horizontal menu
//    object RefRoot : RefScreen("me_root", "Me")
//    object Quiz : RefScreen("me_quiz", "Quiz")
//    object Conjugations : RefScreen("me_conjugations", "Conjugations")
//    object Prepositions : RefScreen("me_prepositions", "Prepositions")
//    object DynamicSheet : RefScreen("dynamic_sheet/{documentId}", "Dynamic Content") {
//        // This helper function builds the full route with the specific ID.
//        // This is the function your screen is trying to call.
//        fun createRoute(documentId: String) = "dynamic_sheet/$documentId"
//    }
//    object GroupedSheet : RefScreen("grouped_sheet/{tabId}", "Grouped Content") {
//        // This helper function builds the full route with the specific ID
//        // of the parent tab (e.g., "adjectives_group").
//        fun createRoute(tabId: String) = "grouped_sheet/$tabId"
//    }
//}
//
//// A helper function to map a menu item title string to its corresponding screen route
//fun getRefScreenRouteFromTitle(title: String): String? {
//    return when (title) {
//        RefScreen.Quiz.title -> RefScreen.Quiz.route
//        RefScreen.Conjugations.title -> RefScreen.Conjugations.route
//        RefScreen.Prepositions.title -> RefScreen.Prepositions.route
//
//        else -> null
//    }
//}

sealed class RefScreen(val route: String) {
    // These are for your FIXED screens
    object Quiz : RefScreen("quiz")
    object Conjugations : RefScreen("conjugations")
    object Prepositions : RefScreen("prepositions")

    // These are for your DYNAMIC screen types
    object DynamicSheet : RefScreen("dynamic_sheet/{documentId}") {
        fun createRoute(documentId: String) = "dynamic_sheet/$documentId"
    }
    object GroupedSheet : RefScreen("grouped_sheet/{tabId}") {
        fun createRoute(tabId: String) = "grouped_sheet/$tabId"
    }
}

// Helper function (can be simplified or removed later, but useful for startDestination)
fun getRefScreenRouteFromTitle(title: String): String? {
    return when (title) {
        "Quiz" -> RefScreen.Quiz.route
        "Conjugations" -> RefScreen.Conjugations.route
        "Prepositions" -> RefScreen.Prepositions.route
        else -> null
    }
}