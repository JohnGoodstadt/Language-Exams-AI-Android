package com.goodstadt.john.language.exams.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


/**
 * The root data class for your "Format1" sheet type.
 * Equivalent to the Swift `HeaderWordsSentencesListRoot` struct.
 */
@kotlinx.serialization.Serializable
data class Format1File(
    val fileformat: Int,
    // Using @SerialName to map the JSON key "sheetname" to the Kotlin-conventional property "sheetName".
    @SerialName("sheetname")
    val sheetName: String,
    // Assuming 'location' is a simple string in the JSON. If it's a complex object,
    // you would need to create a separate data class for it.
    val location: String,
    val data: List<Format1Level>
)

/**
 * Represents a single section within the "Format1" sheet.
 * Equivalent to the Swift `HeaderWordsSentencƒesList` struct.
 */
@Serializable
data class Format1Level(
    val title: String,
    val description: String,
    @SerialName("sortorder")
    val sortOrder: Int,
    @SerialName("wordsAndSentences")
    val wordsAndSentences: List<Format1Entry>
)

/**
 * Represents a single word/sentence pair within a section.
 * Equivalent to the Swift `HeaderWordAndSentence` struct.
 */
@Serializable
data class Format1Entry(
    val word: String,
    val sentence: String,

    // ✅ THE FIX: Provide default values for properties that might be missing in older JSON.
    // This is the clean, declarative Kotlin equivalent of Swift's custom `init(from:)`.
    val translation: String = "",
    val definition: String = ""
)