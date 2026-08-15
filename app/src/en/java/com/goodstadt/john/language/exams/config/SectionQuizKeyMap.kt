package com.goodstadt.john.language.exams.screens.CategoryTab

/**
 * Maps an English vocab category title to the language-independent WordQuiz base key used to build
 * the quiz filename, e.g. "Food and Drink" -> "FoodandDrink" -> WordQuizFoodandDrink1-en.json.
 *
 * For English the key is a pure strip of the title (see [deriveSectionQuizKey]), so there are no
 * explicit entries - keyFor() just derives. Other flavours ship an [overrides] map for titles that
 * can't be derived (real translations, e.g. de "Essen & Trinken" -> "FoodandDrink").
 *
 * A category with no quiz simply produces a key whose file doesn't exist; loadSectionQuiz then finds
 * no files and shows nothing - so no explicit whitelist is needed here.
 */
object SectionQuizKeyMap {

    // English derives cleanly, so no overrides are needed.
    private val overrides: Map<String, Map<String, String>> = emptyMap()

    /** @return the WordQuiz base key for [title] at [level]: an override if present, else derived. */
    fun keyFor(level: String, title: String): String =
        overrides[level]?.get(title.trim()) ?: deriveSectionQuizKey(title)
}
