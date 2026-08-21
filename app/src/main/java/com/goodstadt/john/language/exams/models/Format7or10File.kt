package com.goodstadt.john.language.exams.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

//import com.google.gson.annotations.SerializedName
//import kotlinx.serialization.Serializable

@Serializable
data class Format7or10File(
    @SerialName("fileformat") val fileFormat: Int, //case sensitive
    @SerialName("sheetname") var sheetName: String,
    val title: String? = null,//"", //read title for localised versions
    val updatedDate: Long,
    val location: Int,
    val data: List<Format7or10List>
)
@Serializable
data class Format7or10List(
    val title: String,
    val description: String,
    val sortorder: Int,
    val learningTitle: String? = null,
    val learningPoints: List<String> = emptyList(),
    var sections: List<Format7or10Section>
)
@Serializable
data class Format7or10Section(
    val title: String,
    var page: Int,
    val sentence: String,
    val explain: String,
    val summary: String,
    // CEFR band for this question ("A2", "B1", "B2"). Only the baseline audit uses it,
    // to place the learner by band performance; null/absent for all other content.
    val level: String? = null,
    // Grammar area this question tests (e.g. "Present Perfect", "Conditionals"). Used to tally
    // strengths/weaknesses across the audit tests; null/absent for non-audit content.
    val category: String? = null,
    var words: List<Format7or10Word>
)
@Serializable
data class Format7or10Word(
    val word: String,
    val ok: Boolean
)

// --- Firestore read DTOs (the FLATTENED `sections` subcollection docs the admin upload writes) ---
// Each section doc carries its list's metadata (levelTitle/description/sortorder/learningTitle/
// learningPoints) plus the section fields; the reader regroups by `sortorder` into Format7or10List.
// Plain data classes with all defaults so Firestore's toObject() can instantiate them.

data class Format7or10SectionDTO(
    val levelTitle: String = "",
    val description: String = "",
    val sortorder: Int = 0,
    val learningTitle: String = "",
    val learningPoints: List<String> = emptyList(),
    val title: String = "",
    val page: Int = 0,
    val summary: String = "",
    val level: String = "",
    val category: String = "",
    val explain: String = "",
    val sentence: String = "",
    val words: List<Format7or10WordDTO> = emptyList()
)

data class Format7or10WordDTO(
    val ok: Boolean = false,
    val word: String = ""
)
