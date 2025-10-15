package com.goodstadt.john.language.exams.screens.reference

sealed class RefScreen(val route: String) {
    object Quiz : RefScreen("quiz")
    object Conjugations : RefScreen("conjugations")
    object Prepositions : RefScreen("prepositions")

    // ADDED: A new route for dynamic sheets that accepts a documentId
    object DynamicSheet : RefScreen("dynamic_sheet/{documentId}") {
        fun createRoute(documentId: String) = "dynamic_sheet/$documentId"
    }
}