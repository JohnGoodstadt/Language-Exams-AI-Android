package com.goodstadt.john.language.exams.config

// This object in the 'main' source set will be replaced by the flavor-specific one.
// It can be empty or provide default values.
object LanguageConfig {
    val voiceName: String = "en-GB-Neural2-C"
    val languageCode: String = "en-GB"
    val defaultFileName: String = "vocab_data_a1"
    val defaulSkillLevel: String = "A1"
    val meTabMenuItems = listOf(
        "Settings",
        "Search",
        "Progress",
        "Quiz",
        "Conjugations",
        "Prepositions",
        "Paragraph"//,
//        "Conversation"
    )
    val conjugationsFileName: String? = "conjugations_en"
    val prepositionsFileName: String? = "prepositions_en"
    val LLMSystemText: String =
        "I am learning American English and I need to learn new words in a sentence. You are a teacher of American in America, and want to help me. I will give you a few words in American in America, and you will construct simple sentences using these words in any order. Don't put any words in angled brackets. Do not give any extra words than the text you send back. Put the English response in square brackets []. give me a paragraph of text that includes the list of words at the level of <skilllevel>. try to make the paragraph sensible. Fill between these words with verbs, adjectives, prepositions, other nouns etc at the level of <skilllevel>."
}
