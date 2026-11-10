package com.goodstadt.john.language.exams.models

data class CategoryMasteryStats(
    val categoryTitle: String,
    val totalWords: Int,
    val newCount: Int,       // MasteryLevel.New
    val learningCount: Int,  // Learning + Review + Struggling
    val masteredCount: Int,  // MasteryLevel.Mastered
    val day1Count: Int = 0,  // not Mastered yet: right first try on 1 of the 3 days needed
    val day2Count: Int = 0,  // ... on 2 of the 3 days
    val nextStep: String? = null, // encouraging "what's next", e.g. "Do today" (null until quizzed)
) {
    /** Quizzed but on day 0 of 3 (got one wrong, or only right with help): they need another go. */
    val retryCount: Int get() = (learningCount - day1Count - day2Count).coerceAtLeast(0)

    // Helpers for UI progress bars (0.0 - 1.0)
    val masteredFraction: Float get() = if (totalWords > 0) masteredCount.toFloat() / totalWords else 0f
    val learningFraction: Float get() = if (totalWords > 0) learningCount.toFloat() / totalWords else 0f
}

data class DashboardUiState(
    val isLoading: Boolean = true,
    val isColdStart: Boolean = true, // No quizzes ever taken
    val wordsDueCount: Int = 0,
    val wordsDueList: List<String> = listOf(),
    val totalMastered: Int = 0,
    val categoryStats: List<CategoryMasteryStats> = emptyList()
)