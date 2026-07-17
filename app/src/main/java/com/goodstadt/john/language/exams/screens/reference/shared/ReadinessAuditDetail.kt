package com.goodstadt.john.language.exams.screens.reference.shared

/**
 * A simple data class to hold the metadata for a single quiz.
 *
 * @param id A unique identifier for the quiz within its level (e.g., 1, 2, 3...).
 * @param baseName The name of the JSON asset file for this quiz.
 * @param title The human-readable display name for this quiz (e.g., "Quiz 1 - Simple Tenses").
 */
data class ReadinessAuditDetail(
    val id: Int,
    val baseName: String, //e.g. "Quiz1Elementary-en"
    val title: String
)