package com.goodstadt.john.language.exams.models

data class CategoryMasteryStats(
    val categoryTitle: String,
    val totalWords: Int,
    val newCount: Int,       // MasteryLevel.New
    val learningCount: Int,  // Learning + Review + Struggling
    val masteredCount: Int   // MasteryLevel.Mastered
) {
    // Helpers for UI progress bars (0.0 - 1.0)
    val masteredFraction: Float get() = if (totalWords > 0) masteredCount.toFloat() / totalWords else 0f
    val learningFraction: Float get() = if (totalWords > 0) learningCount.toFloat() / totalWords else 0f
}

data class DashboardUiState(
    val isLoading: Boolean = true,
    val isColdStart: Boolean = true, // No quizzes ever taken
    val wordsDueCount: Int = 0,
    val totalMastered: Int = 0,
    val categoryStats: List<CategoryMasteryStats> = emptyList()
)