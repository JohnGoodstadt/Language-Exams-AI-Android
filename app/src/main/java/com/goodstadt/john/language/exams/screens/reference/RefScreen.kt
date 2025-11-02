package com.goodstadt.john.language.exams.screens.reference


//TODO: do I need thjs - is it a duplicate
sealed class RefScreen(val route: String) {
    object Quiz : RefScreen("quiz")
    object Conjugations : RefScreen("conjugations")
    object Prepositions : RefScreen("prepositions")

    // ADDED: A new route for dynamic sheets that accepts a documentId
    object DynamicSheet : RefScreen("dynamic_sheet/{documentId}") {
        fun createRoute(documentId: String) = "dynamic_sheet/$documentId"
    }
    object Format1 : RefScreen("format1_screen/{documentId}") {
        fun createRoute(documentId: String) = "format1_screen/$documentId"
    }
}