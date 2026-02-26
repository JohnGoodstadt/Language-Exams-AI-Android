package com.goodstadt.john.language.exams.config

// This object in the 'main' source set will be replaced by the flavor-specific one.
// It can be empty or provide default values.
object LanguageConfig {
    val voiceName: String = "en-GB-Neural2-C"
    val languageCode: String = "en-GB"
    val defaultFileName: String = "vocab_data_b1"
    val defaulSkillLevel: String = "B1"
    /*
    NOTE:   1. this drives the screen
            2. MeScreen() goes from this title to route (getMeScreenRouteFromTitle)
     */
    val meTabMenuItems = listOf( //NOTE:  this drives the screen -- not RemoteConfig Yet
//        "Focusing",
        "Settings",
        "Vocab",
        "Word of the Day",
        "Progress",
        "Paragraph",
//        "Vocab Quiz"

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
    val prepositionsBundleFileName: String = "prepositions_en"
    val prepositionsFirestoreName: String = "prepositions"
    val LLMSystemText: String =
        "I am learning American English and I need to learn new words in a sentence. You are a teacher of American in America, and want to help me. I will give you a few words in American in America, and you will construct simple sentences using these words in any order. Don't put any words in angled brackets. Do not give any extra words than the text you send back. Put the English response in square brackets []. give me a paragraph of text that includes the list of words at the level of <skilllevel>. try to make the paragraph sensible. Fill between these words with verbs, adjectives, prepositions, other nouns etc at the level of <skilllevel>."
}
