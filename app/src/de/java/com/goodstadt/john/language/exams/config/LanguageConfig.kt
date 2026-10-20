package com.goodstadt.john.language.exams.config

// This object in the 'main' source set will be replaced by the flavor-specific one.
// It can be empty or provide default values.
object LanguageConfig {
    val showLanguageSelectionSetting: Boolean = false
    val hasDialectSelection: Boolean = false // 👈 Only English needs US/UK/AU choice
    val voiceName: String = "de-DE-Neural2-G" //female
    val languageCode: String = "de-DE"
    val defaultFileName: String = "vocab_data_b1" // first launch loads B1 vocab (matches defaulSkillLevel)
    val defaulSkillLevel: String = "B1"

    /*
   NOTE:   1. this drives the screen
           2. MeScreen() goes from this title to route (getMeScreenRouteFromTitle)
    */
    val meTabMenuItems = listOf( //NOTE:  this drives the screen -- not RemoteConfig Yet -- July 2026
        "Progress",
        "Settings",
        "Focus",
        "Saved",
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
            "Haben" -> "GermanConjugationsToHave"
            "Sein" -> "GermanConjugationsToBe"
            "Machen" -> "GermanConjugationsToDo"
            "Bekommen" -> "GermanConjugationsToGet"
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
            // Pronomen III (Format 8): one sheet per personal-pronoun subject.
            "german_pronouns3_ich" -> "GermanPronouns3Ich"
            "german_pronouns3_du" -> "GermanPronouns3Du"
            "german_pronouns3_er" -> "GermanPronouns3Er"
            "german_pronouns3_sie_singular" -> "GermanPronouns3SieSingular"
            "german_pronouns3_es" -> "GermanPronouns3Es"
            "german_pronouns3_wir" -> "GermanPronouns3Wir"
            "german_pronouns3_ihr" -> "GermanPronouns3Ihr"
            "german_pronouns3_sie_plural" -> "GermanPronouns3SiePlural"
            "german_pronouns3_sie_formal" -> "GermanPronouns3SieFormal"

            // Pronomen II (Format 8) bundled resources.
            "german_pronouns2_accusative2" -> "GermanPronouns2Accusative2"
            "german_pronouns2_both_case2" -> "GermanPronouns2BothCases2"
            "german_pronouns2_both_dative2" -> "GermanPronouns2Dative2"
            "german_pronouns2_both_learning_verbs2" -> "GermanPronouns2LearningVerbs2"
            "german_pronouns2_both_nominative2" -> "GermanPronouns2Nominative2"
            "german_pronouns2_both_patterns2" -> "GermanPronouns2Patterns2"

            // Podcasts (Format 9)
            "german_podcast_football_trip_stuttgart_a2" -> "GermanPodcastFootballTripStuttgartA2"

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
            // Pronomen III (Format 8) logical sheet names.
            "GermanPronouns3Ich" -> "german_pronouns3_ich"
            "GermanPronouns3Du" -> "german_pronouns3_du"
            "GermanPronouns3Er" -> "german_pronouns3_er"
            "GermanPronouns3SieSingular" -> "german_pronouns3_sie_singular"
            "GermanPronouns3Es" -> "german_pronouns3_es"
            "GermanPronouns3Wir" -> "german_pronouns3_wir"
            "GermanPronouns3Ihr" -> "german_pronouns3_ihr"
            "GermanPronouns3SiePlural" -> "german_pronouns3_sie_plural"
            "GermanPronouns3SieFormal" -> "german_pronouns3_sie_formal"

            // Pronomen II (Format 8) logical sheet names.
            "GermanPronouns2Accusative2" -> "german_pronouns2_accusative2"
            "GermanPronouns2BothCases2" -> "german_pronouns2_both_case2"
            "GermanPronouns2Dative2" -> "german_pronouns2_both_dative2"
            "GermanPronouns2LearningVerbs2" -> "german_pronouns2_both_learning_verbs2"
            "GermanPronouns2Nominative2" -> "german_pronouns2_both_nominative2"
            "GermanPronouns2Patterns2" -> "german_pronouns2_both_patterns2"

            // Podcasts (Format 9)
            "GermanPodcastFootballTripStuttgartA2" -> "german_podcast_football_trip_stuttgart_a2"

            "GermanA1Vocab" -> "vocab_data_a1"
            "GermanA2Vocab" -> "vocab_data_a2"
            "GermanB1Vocab" -> "vocab_data_b1"
            "GermanB2Vocab" -> "vocab_data_b2"
            "GermanConjugationsToBe" -> "conjugations_to_be"
            "GermanConjugationsToHave" -> "conjugations_to_have"
            "GermanConjugationsToDo" -> "conjugations_to_do"
            "GermanConjugationsToGet" -> "conjugations_to_get"

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
    // Kept in their own Quizzes/QuizSheet/ subfolder for neatness; the readers open "Quizzes/<value>".
    val quizSheetPrepositionsFilename: String = "QuizSheet/QuizSheetPrepositions-de"
    val quizSheetWordPairsFilename: String = "QuizSheet/QuizSheetWordPairs-de.json"

//    val prepositionsFirestoreName: String = "prepositions"
    val LLMSystemText: String =
        "I am learning German in Germany and I need to learn new words in a sentence. You are a teacher of German in Germany, and want to help me. I will give you a few words in German in Germany, and you will construct simple sentences using these words in any order. Don't put any words in angled brackets. Do not give any extra words than the text you send back. Put the German response in square brackets []. give me a paragraph of text that includes the list of words at the level of <skilllevel>. try to make the paragraph sensible. Fill between these words with verbs, adjectives, prepositions, other nouns etc at the level of <skilllevel>."
}
