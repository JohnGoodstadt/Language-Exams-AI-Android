package com.goodstadt.john.language.exams.models

import java.util.Date

data class WordHistory(
    val word: String,
    val sentence: String,
    val wordCount: Int,
    val sentenceCount: Int,
    val timestamp: Date
)