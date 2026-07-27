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

const val QUIZ_DETAIL_ROUTE = "quiz_detail/{categoryId}"

sealed class RefScreen(val route: String) {
    // These are for your FIXED screens
    object Quiz : RefScreen("quiz")
   // object ReadinessAudit : RefScreen("readinessaudit")

    // 2. NEW: This represents the sub-screen (The List of Sets)
    object QuizDetail : RefScreen("quiz_detail/{categoryId}") {
        fun createRoute(categoryId: String) = "quiz_detail/$categoryId"
    }


    object Conjugations : RefScreen("conjugations")
    object VocabQuizDashboard : RefScreen("vocab_dashboard")
    object Prepositions : RefScreen("prepositions")

    // These are for your DYNAMIC screen types
    object DynamicSheet : RefScreen("dynamic_sheet/{documentId}") {
        fun createRoute(documentId: String) = "dynamic_sheet/$documentId"
    }
    object GroupedSheet : RefScreen("grouped_sheet/{tabId}") {
        fun createRoute(tabId: String) = "grouped_sheet/$tabId"
    }
    object Format1 : RefScreen("format1_screen/{documentId}") {
        fun createRoute(documentId: String) = "format1_screen/$documentId"
    }
    object Format2 : RefScreen("format2_screen/{documentId}") {
        fun createRoute(documentId: String) = "format2_screen/$documentId"
    }
    object Format3 : RefScreen("format3_screen/{documentId}") {
        fun createRoute(documentId: String) = "format3_screen/$documentId"
    }
    object GroupedFormat2 : RefScreen("grouped_format2/{tabId}") {
        fun createRoute(tabId: String) = "grouped_format2/$tabId"
    }
    object GroupedFormat3 : RefScreen("grouped_format3/{tabId}") {
        fun createRoute(tabId: String) = "grouped_format3/$tabId"
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