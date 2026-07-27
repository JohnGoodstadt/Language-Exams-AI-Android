package com.goodstadt.john.language.exams.models

import kotlinx.serialization.Serializable

@Serializable
data class DiagnosticQuestion(
    val page: Int,
    val summary: String,
    val explain: String,
    val sentence: String,
    val words: List<DiagnosticOption>
)

@Serializable
data class DiagnosticOption(
    val ok: Boolean,
    val word: String
)

data class DiagnosticUiState(
    val currentQuestionIndex: Int = 0,
    val score: Int = 0,
    val isComplete: Boolean = false,
    val showResult: Boolean = false,
    val questions: List<DiagnosticQuestion> = emptyList()
)

data class DiagnosticStats(
    val testsCompleted: Int = 0, // 0 to 4
    val cumulativeScore: Int = 0, // total correct answers out of 40
    val confidence: Float = 0.0f,
    val readiness: Float = 0.0f
)