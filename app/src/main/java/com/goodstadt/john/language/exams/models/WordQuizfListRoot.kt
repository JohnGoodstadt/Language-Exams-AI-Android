package com.goodstadt.john.language.exams.models

import com.goodstadt.john.language.exams.packages.dailydictionary.DictionaryEntry
import com.goodstadt.john.language.exams.utils.DictionaryEntryOrNullSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WordQuizRoot(
    @SerialName("fileformat") val fileFormat: Int, //case sensitive
    @SerialName("sheetname") var sheetName: String,
    val title: String? = null,//"", //read title for localised versions
    val updatedDate: Long,
    val location: Int,
    val data: List<WordQuizList>
)
@Serializable
data class WordQuizList(
    val title: String,
    val description: String,
    val sortorder: Int,
    var sections: List<WordQuizSections>
)
@Serializable
data class WordQuizSections(
    val title: String,
    var page: Int,
    val question: String,
    //    val explain: String,
    @Serializable(with = DictionaryEntryOrNullSerializer::class)
    val explain: DictionaryEntry? = null,
    val summary: String,
    var answers: List<WordQuizSWordsState>
)
@Serializable
data class WordQuizSWordsState(
    val answer: String,
    val ok: Boolean
)

// --- Firestore read DTOs for fileFormat 13 (section quizzes) ---
// The flattened `sections` subcollection docs the admin upload writes: each carries its list's
// metadata (listTitle/description/sortorder) plus the question, answers[{answer, ok}] and the nested
// `explain` dictionary (kept as a raw map; reconstructed into DictionaryEntry on read).
// Plain data classes with all defaults so Firestore's toObject() can instantiate them.
data class Format13SectionDTO(
    val listTitle: String = "",
    val description: String = "",
    val sortorder: Int = 0,
    val title: String = "",
    val page: Int = 0,
    val question: String = "",
    val summary: String = "",
    val answers: List<Format13AnswerDTO> = emptyList(),
    val explain: Map<String, Any?>? = null
)

data class Format13AnswerDTO(
    val answer: String = "",
    val ok: Boolean = false
)
