package com.goodstadt.john.language.exams.models

import kotlinx.serialization.Serializable

@Serializable
data class Format3File(
    val fileformat: Int = 0,
    val location: Int = 0,
    val sheetname: String = "",

    val title: String = "",
    val subtitle: String= "",
    val description: String= "",

    val level: String= "",
    val targetLanguage: String= "",

    val categories: List<Format3Category> = emptyList()
)

@Serializable
data class Format3Category(
    val sortOrder: Int = 0,
    val title: String= "",
    val description: String= "",
    val words: List<Format3Word> = emptyList()
)

@Serializable
data class Format3Word(
    val id: Int = 0,
    val sortOrder: Int = 0,
    val sentences: List<Format3Sentence> = emptyList()
)

@Serializable
data class Format3Sentence(
    val sentence: String = ""
)
