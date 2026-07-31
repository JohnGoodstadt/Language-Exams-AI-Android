package com.goodstadt.john.language.exams.screens.CategoryTab

/**
 * Maps an English vocab category title (exactly as it appears in vocab_data_*.json /
 * Firebase) to the language-independent WordQuiz base key used to build the quiz
 * filename, e.g. "Food and Drink" -> "FoodandDrink" -> WordQuizFoodandDrink1-en.json …
 *
 * Same structure as every other flavour's SectionQuizKeyMap: an explicit lookup keyed by
 * SKILL LEVEL first, then title. English titles happen to match their keys closely, but we
 * list them explicitly (rather than deriving) so the resolution rule is identical across
 * languages and a title change surfaces as a missing entry instead of a silently-wrong key.
 *
 * keyFor() returns null when a category has no quiz at that level; the ViewModel then
 * shows no quiz (and the Quiz button can be hidden on null).
 */
object SectionQuizKeyMap {

    private val byLevel: Map<String, Map<String, String>> = mapOf(

        "A1" to mapOf(
            "Hello" to "Hello",
            "Dates" to "Dates",
            "Numbers" to "Numbers",
            "Family" to "Family",
            "Everyday" to "Everyday",
            "Colors" to "Colors",
            "Occupations" to "Occupations",
            "Places" to "Places",
            "Verbs" to "Verbs",
            "Adjectives" to "Adjectives",
            "up,in,on,at,by" to "upinonatby",
        ),

        "A2" to mapOf(
            "Personal" to "Personal",
            "Home" to "Home",
            "Shopping" to "Shopping",
            "Food and Drink" to "FoodandDrink",
            "Health" to "Health",
            "Education" to "Education",
            "Jobs" to "Jobs",
            "Travel" to "Travel",
            "Leisure" to "Leisure",
            "Weather" to "Weather",
            "Nature" to "Nature",
            "Social" to "Social",
            "Technology" to "Technology",
            "Past Tense" to "PastTense",
            "in, and, big, quickly" to "inandbigquickly",
            "Patterns" to "Patterns",
        ),

        "B1" to mapOf(
            "Personal" to "Personal",
            "Education" to "Education",
            "Travel" to "Travel",
            "Health" to "Health",
            "Leisure" to "Leisure",
            "Food" to "Food",
            "Shopping" to "Shopping",
            "Technology" to "Technology",
            "Nature" to "Nature",
            "Social" to "Social",
            "Relationships" to "Relationships",
            "Emotions" to "Emotions",
            "Verbs" to "Verbs",
            "Adjectives" to "Adjectives",
            "Adverbs" to "Adverbs",
            "Complex Sentences" to "ComplexSentences",
        ),

        "B2" to mapOf(
            "Personal Development (B2)" to "PersonalDevelopment",
            "Education" to "Education",
            "Work" to "Work",
            "Travel" to "Travel",
            "Health" to "Health",
            "Entertainment" to "Entertainment",
            "Technology" to "Technology",
            "Nature" to "Nature",
            "Social Issues" to "SocialIssues",
            "Relationships" to "Relationships",
            "Emotions" to "Emotions",
            "Verbs" to "Verbs",
            "Adjectives" to "Adjectives",
            "Adverbs" to "Adverbs",
        ),
    )

    /** @return the WordQuiz base key for [title] at [level], or null if none is mapped. */
    fun keyFor(level: String, title: String): String? = byLevel[level]?.get(title.trim())
}
