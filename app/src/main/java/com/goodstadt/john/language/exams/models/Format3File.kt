package com.goodstadt.john.language.exams.models

import kotlinx.serialization.Serializable

@Serializable
data class Format3File(
    val fileformat: Int,
    val location: Int,
    val sheetname: String,

    val title: String,
    val subtitle: String,
    val description: String,

    val level: String,
    val targetLanguage: String,

    val categories: List<Format3Category>
)

@Serializable
data class Format3Category(
    val sortOrder: Int,
    val title: String,
    val description: String,
    val words: List<Format3Word>
)

@Serializable
data class Format3Word(
    val id: Int,
    val sortOrder: Int,
    val sentences: List<Format3Sentence>
)

@Serializable
data class Format3Sentence(
    val sentence: String
)
