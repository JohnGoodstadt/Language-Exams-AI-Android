package com.goodstadt.john.language.exams.screens.CategoryTab

/**
 * The language-independent WordQuiz base key for a category title: drop any "(…)" content, then
 * keep only letters/digits (case preserved). Used to build the quiz filename WordQuiz<key><n>-<lang>.
 *
 * English titles derive to their key exactly ("Food and Drink" -> "FoodandDrink",
 * "Personal Development (B2)" -> "PersonalDevelopment"), so the English SectionQuizKeyMap needs no
 * entries. Other languages keep an override map for their real translations
 * (e.g. de "Essen & Trinken" -> "FoodandDrink") since those can't be derived.
 */
fun deriveSectionQuizKey(title: String): String =
    title.replace(Regex("\\(.*?\\)"), "").filter { it.isLetterOrDigit() }
