package com.goodstadt.john.language.exams.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a document from the 'tabs' sub-collection in Firestore for a Format1 sheet.
 */

@kotlinx.serialization.Serializable
data class HeaderWordsSentencesListRootDTO(
    val fileformat: Int,
    @SerialName("sheetname")
    val sheetName: String,
    val updatedDate: String,
    val uploadDate: String

)

@kotlinx.serialization.Serializable
data class TabHeaderForFirestore(
    val title: String = "",
    val description: String = "",
    val tabID: Int = 0,
    val sortorder: Int = 0
)

/**
 * Represents a document from the 'wordsAndSentences' sub-collection in Firestore.
 */
@Serializable
data class WordAndSentenceForFirestore(
    val parentID: Int = 0,
    val word: String = "",
    val sentence: String = "",
    // Default values provide backward compatibility if these fields are missing
    val translation: String = "",
    val definition: String = ""
)