package com.goodstadt.john.language.exams.config

// This object in the 'main' source set will be replaced by the flavor-specific one.
// It can be empty or provide default values.
object LanguageConfig {
    val showLanguageSelectionSetting: Boolean = true
    val hasDialectSelection: Boolean = true // 👈 English needs US/UK/AU choice
    val voiceName: String = "en-GB-Neural2-C"
    val languageCode: String = "en-GB"
    val defaultFileName: String = "vocab_data_b1"
    val defaulSkillLevel: String = "B1"
    /*
    NOTE:   1. this drives the screen
            2. MeScreen() goes from this title to route (getMeScreenRouteFromTitle)
     */
    val meTabMenuItems = listOf( //NOTE:  this drives the screen -- not RemoteConfig Yet -- July 2026
        "Progress",
        "Settings",
        "Focus",
        "Vocab",
        "Word of the Day",
        "Paragraph",

    )
    val refTabMenuItems = listOf(
        "Quiz",
        "Conjugations",
        "Prepositions"
    )
    //val conjugationsFileName: String? = "conjugations_en"
    val conjugationOptions = listOf("To Have", "To Be", "To Do", "To Get")
    fun getConjugationBundleFileName(title: String): String {
        val jsonFileName = when (title) {
            "To Have" -> "conjugations_to_have"
            "To Be" -> "conjugations_to_be"
            "To Do" -> "conjugations_to_do"
            "To Get" -> "conjugations_to_get"
            else -> "conjugationsToHave" // Default option
        }
        return jsonFileName
    }
    fun getConjugationFirestoreSheetName(title: String): String {
        val jsonFileName = when (title) {
            "To Have" -> "EnglishConjugationsToHave"
            "To Be" -> "EnglishConjugationsToBe"
            "To Do" -> "EnglishConjugationsToDo"
            "To Get" -> "EnglishConjugationsToGet"
            else -> "EnglishConjugationsToHave" // Default option
        }
        return jsonFileName
    }
    /**
     * ✅ ADDED: This is the "Anti-Corruption Layer".
     * It ensures that any legacy resource names are immediately converted to the
     * canonical logical name used throughout the new system.
     */
    fun normalizeToLogicalName(resourceName: String): String {
        return when (resourceName) {
            "vocab_data_a1" -> "EnglishA1Vocab"
            "vocab_data_a2" -> "EnglishA2Vocab"
            "vocab_data_b1" -> "EnglishB1Vocab"
            "vocab_data_b2" -> "EnglishB2Vocab"
            "conjugations_to_be" -> "EnglishConjugationsToBe"
            "conjugations_to_have" -> "EnglishConjugationsToHave"
            "conjugations_to_do" -> "EnglishConjugationsToDo"
            "conjugations_to_get" -> "EnglishConjugationsToGet"
            "prepositions_en" -> "EnglishPrepositions"


            // Add any other legacy mappings here

            // If the name is already in the correct format, just return it.
            else -> resourceName
        }
    }
    /**
     * Maps the logical Firestore name to the Android-specific resource name.
     */
    fun mapLogicalToResourceName(sheet_name: String): String {
        return when (sheet_name) {
            "EnglishA1Vocab" -> "vocab_data_a1"
            "EnglishA2Vocab" -> "vocab_data_a2"
            "EnglishB1Vocab" -> "vocab_data_b1"
            "EnglishB2Vocab" -> "vocab_data_b2"
            "EnglishConjugationsToBe" -> "conjugations_to_be"
            "EnglishConjugationsToHave" -> "conjugations_to_have"
            "EnglishConjugationsToDo" -> "conjugations_to_do"
            "EnglishConjugationsToGet" -> "conjugations_to_get"
            "EnglishPrepositions" -> "prepositions_en"
            // Add other mappings here as needed
            else -> sheet_name // Fallback for other files
        }
    }
    val prepositionsBundleFileName: String = "prepositions_en"
    val prepositionsFirestoreName: String = "prepositions"
    val quizSheetPrepositionsFilename: String = "QuizSheetPrepositions-en"
    val quizSheetWordPairsFilename: String = "QuizSheetWordPairs-en.json"

    val LLMSystemText: String =
        "I am learning American English and I need to learn new words in a sentence. You are a teacher of American in America, and want to help me. I will give you a few words in American in America, and you will construct simple sentences using these words in any order. Don't put any words in angled brackets. Do not give any extra words than the text you send back. Put the English response in square brackets []. give me a paragraph of text that includes the list of words at the level of <skilllevel>. try to make the paragraph sensible. Fill between these words with verbs, adjectives, prepositions, other nouns etc at the level of <skilllevel>."
}
