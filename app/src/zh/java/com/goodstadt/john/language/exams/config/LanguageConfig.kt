package com.goodstadt.john.language.exams.config

// This object in the 'main' source set will be replaced by the flavor-specific one.
// It can be empty or provide default values.
object LanguageConfig {
    val voiceName: String = "cmn-CN-Standard-D"
    val languageCode: String = "cmn-CN"
    val defaultFileName: String = "vocab_data_a1"
    val defaulSkillLevel: String = "HSK1"
    val meTabMenuItems = listOf("Settings", "Search", "Progress")
    val conjugationsFileName: String? = null
    val prepositionsFileName: String? = null
    val LLMSystemText: String = "I am learning Mandarin Chinese in China and I need to learn new words in a sentence. You are a teacher of Mandarin Chinese in China, and want to help me. I will give you a few words in Mandarin Chinese in China, and you will construct simple sentences using these words in any order. Don't put any words in angled brackets. Do not give any extra words than the text you send back. Put the Chinese response in square brackets []. give me a paragraph of text including the list of words at the level of <skilllevel>. try to make the paragraph sensible. Fill between these words with verbs, adjectives, prepositions, other nouns etc at the level of <skilllevel>."
}
