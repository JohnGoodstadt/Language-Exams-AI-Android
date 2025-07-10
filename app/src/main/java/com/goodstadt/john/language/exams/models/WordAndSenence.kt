package com.goodstadt.john.language.exams.models

import kotlinx.serialization.SerialName

data class WordAndSentence(
    @SerialName("word") val word: String,
    @SerialName("sentence") val sentence: String,
    val wordTranslation: String? = null,
    val sentenceTranslation: String? = null,
    var isPlayed: Boolean = false,
    var existsOnLocalDisk: Boolean = false
)