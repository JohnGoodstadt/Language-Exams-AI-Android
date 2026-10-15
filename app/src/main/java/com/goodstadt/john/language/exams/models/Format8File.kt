package com.goodstadt.john.language.exams.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Shared Format 8 schema: sheet -> sections -> entries -> translated examples. */
@Serializable
data class Format8File(
    val fileformat: Int,
    val location: Int,
    val sheetname: String,
    val title: String,
    val description: String,
    val updatedDate: Int,
    val intro: String,
    val sections: List<Format8Section>
)

@Serializable
data class Format8Section(
    val title: String,
    val sortorder: Int,
    val explanation: String,
    val rule: String,
    val entries: List<Format8Entry>
)

@Serializable
data class Format8Entry(
    val header: String,
    val kind: Format8EntryKind,
    val explanation: String,
    val pattern: String,
    val examples: List<Format8Example>
)

@Serializable
enum class Format8EntryKind {
    @SerialName("conjugation") CONJUGATION,
    @SerialName("comparison") COMPARISON,
    @SerialName("pronoun") PRONOUN,
    @SerialName("pattern") PATTERN,
    @SerialName("learning") LEARNING,
    @SerialName("review") REVIEW
}

@Serializable
data class Format8Example(
    val german: String,
    val english: String,
    val focus: String,
    val note: String,
    /** Exact, case-sensitive German pronoun to highlight. */
    val key: String
)
