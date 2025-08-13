package com.goodstadt.john.language.exams.config

// This object in the 'main' source set will be replaced by the flavor-specific one.
// It can be empty or provide default values.
object LanguageConfig {
    val voiceName: String = "de-DE-Neural2-G" //female
    val languageCode: String = "de-DE"
    val defaultFileName: String = "vocab_data_a1"
    val defaulSkillLevel: String = "A1"
    val meTabMenuItems = listOf("Settings", "Search","Progress", "Conjugations", "Prepositions", "Paragraph", "Conversation")
    val conjugationsFileName: String? = "conjugations_de"
    val prepositionsFileName: String? = "prepositions_de"
    val LLMSystemText: String = "I am learning German and I need to learn new words in a sentence. You are a teacher of German, and want to help me. I will give you a few words in German, and you will construct simple sentences using these words in any order. Do not give any extra words than the text you send back. Don't put any words in angled brackets. Put the German response in square brackets []. give me a paragraph of text including the list of words at the level of <skilllevel>. try to make the paragraph sensible. Fill between these words with verbs, adjectives, prepositions, other nouns etc at the level of <skilllevel>."
}