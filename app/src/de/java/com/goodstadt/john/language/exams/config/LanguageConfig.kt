package com.goodstadt.john.language.exams.config

// This object in the 'main' source set will be replaced by the flavor-specific one.
// It can be empty or provide default values.
object LanguageConfig {
    val showLanguageSelectionSetting: Boolean = false
    val hasDialectSelection: Boolean = false // 👈 Only nglish needs US/UK/AU choice
    val voiceName: String = "de-DE-Neural2-G" //female
    val languageCode: String = "de-DE"
    val defaultFileName: String = "vocab_data_a1"
    val defaulSkillLevel: String = "B1"

    /*
   NOTE:   1. this drives the screen
           2. MeScreen() goes from this title to route (getMeScreenRouteFromTitle)
    */
    val meTabMenuItems = listOf( //NOTE:  this drives the screen -- not RemoteConfig Yet -- July 2026
        "Progress",
        "Settings",
        "Vocab",
        "Word of the Day",
        "Paragraph",

        )

    val conjugationOptions = listOf("Haben", "Sein", "Machen", "Bekommen")
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
            "To Have" -> "GermanConjugationsToHave"
            "To Be" -> "GermanConjugationsToBe"
            "To Do" -> "GermanConjugationsToDo"
            "To Get" -> "GermanConjugationsToGet"
            else -> "GermanConjugationsToHave" // Default option
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
            "vocab_data_a1" -> "GermanA1Vocab"
            "vocab_data_a2" -> "GermanA2Vocab"
            "vocab_data_b1" -> "GermanB1Vocab"
            "vocab_data_b2" -> "GermanB2Vocab"
            "conjugations_to_be" -> "GermanConjugationsToBe"
            "conjugations_to_have" -> "GermanConjugationsToHave"
            "conjugations_to_do" -> "GermanConjugationsToDo"
            "conjugations_to_get" -> "GermanConjugationsToGet"
            "german_prepositions" -> "GermanPrepositions"
            "german_a1_adjectives" -> "GermanA1Adjectives"
            "german_a2_adjectives" -> "GermanA1Adjectives"
            "german_b1_adjectives" -> "GermanB1Adjectives"
            "german_b2_adjectives" -> "GermanB2Adjectives"

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
            "GermanA1Vocab" -> "vocab_data_a1"
            "GermanA2Vocab" -> "vocab_data_a2"
            "GermanB1Vocab" -> "vocab_data_b1"
            "GermanB2Vocab" -> "vocab_data_b2"
            "GermanConjugationsToBe" -> "conjugations_to_be"
            "GermanConjugationsToHave" -> "conjugations_to_have"
            "GermanConjugationsToDo" -> "conjugations_to_do"
            "GermanConjugationsToGet" -> "conjugations_to_get"
//            "GermanConjugationsToGet" -> "prepositions_de"
            "GermanA1Adjectives" -> "german_a1_adjectives"
            "GermanA2Adjectives" -> "german_a2_adjectives"
            "GermanB1Adjectives" -> "german_b1_adjectives"
            "GermanB2Adjectives" -> "german_b2_adjectives"
            "GermanKennenWissen" -> "german_kennen_wissen"
            "GermanBringenHolen" -> "german_bringen_holen"
            "GermanHoerenZuhoeren"-> "german_hoeren_zuhoeren"
            "GermanFragenBitten" -> "german_fragen_bitten"
            "GermanSoundsTheSame" -> "german_sounds_the_same"
            "GermanPrepositions" -> "german_prepositions"

            else -> sheet_name // Fallback for other files
        }
    }
    val prepositionsBundleFileName: String = "prepositions_de"
//    val prepositionsFirestoreName: String = "prepositions"
    val LLMSystemText: String =
        "I am learning American English and I need to learn new words in a sentence. You are a teacher of American in America, and want to help me. I will give you a few words in American in America, and you will construct simple sentences using these words in any order. Don't put any words in angled brackets. Do not give any extra words than the text you send back. Put the English response in square brackets []. give me a paragraph of text that includes the list of words at the level of <skilllevel>. try to make the paragraph sensible. Fill between these words with verbs, adjectives, prepositions, other nouns etc at the level of <skilllevel>."
}
