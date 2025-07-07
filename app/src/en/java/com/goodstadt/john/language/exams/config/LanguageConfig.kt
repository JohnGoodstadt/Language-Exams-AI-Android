package com.goodstadt.john.language.exams.config

// This object in the 'main' source set will be replaced by the flavor-specific one.
// It can be empty or provide default values.
object LanguageConfig {
    val voiceName: String = "en-GB-Neural2-C"
    val languageCode: String = "en-GB"
    val defaultFileName: String = "vocab_data_a1"
    val meTabMenuItems = listOf("Settings", "Search","Progress", "Quiz",  "Conjugations", "Prepositions", "Paragraph", "Conversation")
    val conjugationsFileName: String? = "conjugations_en"
    val prepositionsFileName: String? = "prepositions_en"
}