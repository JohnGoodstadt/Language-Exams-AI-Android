package com.goodstadt.john.language.exams.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The top-level object in the JSON file.
 * @Serializable allows this class to be converted from/to JSON.
 * @SerialName maps the JSON key string to the Kotlin property name if they are different.
 */
@Serializable
data class VocabFile(
    val fileformat: Int,
    val location: Int,
    @SerialName("sheetname")
    val sheetName: String,
    val updatedDate: Int,
    @SerialName("uploaddate")
    val uploadDate: Double,
    val id: String,
    val native: String,
    val name: String,
    val romanized: Boolean,
    @SerialName("nativename")
    val nativeName: String,
    @SerialName("googlevoiceprefix")
    val googleVoicePrefix: String,
    @SerialName("voicename")
    val voiceName: String,
    val tabtitles: List<String>,
    val categories: List<Category>
)

@Serializable
data class Category(
    val title: String,
//    @SerialName("tabnumber") //can crash on decode
    val tabNumber: Int,
    val sortOrder: Int,
    val words: List<VocabWord>
) {
    // Note: A Kotlin data class's default equals() and hashCode() compare all properties.
    // This is different from your Swift version which only compared the title.
    // This is usually the desired behavior.
}

@Serializable
data class VocabWord(
    val id: Int,
    @SerialName("sortOrder")
    val sortOrder: Int,
    val translation: String,
    val romanisation: String,
    @SerialName("partOfSpeech")
    val partOfSpeech: String,
    val word: String,
    val definition: String = "",
    val group: String,
    val sentences: List<Sentence>
)

@Serializable
data class Sentence(
    val sentence: String,
    val translation: String
)