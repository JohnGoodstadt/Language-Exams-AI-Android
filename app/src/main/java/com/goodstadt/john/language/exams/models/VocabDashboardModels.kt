package com.goodstadt.john.language.exams.models

data class CategoryMasteryStats(
    val categoryTitle: String,
    val totalWords: Int,
    val newCount: Int,       // MasteryLevel.New
    val learningCount: Int,  // Learning + Review + Struggling
    val masteredCount: Int,  // MasteryLevel.Mastered
    val day1Count: Int = 0,  // not Mastered yet: right first try on 1 of the 3 days needed
    val day2Count: Int = 0,  // ... on 2 of the 3 days
    val dayProgress: CategoryDayProgress? = null, // per-CATEGORY "N of 3 days" (null until the quiz is played)
) {
    // Helpers for UI progress bars (0.0 - 1.0)
    val masteredFraction: Float get() = if (totalWords > 0) masteredCount.toFloat() / totalWords else 0f
    val learningFraction: Float get() = if (totalWords > 0) learningCount.toFloat() / totalWords else 0f
}

/**
 * Per-CATEGORY progress to Mastered: a day counts when the WHOLE quiz is done with every word right first
 * time ("flawless"), at most one per calendar day; 3 days = Mastered. A go with mistakes never loses the
 * days already earned. Same rules and wording as iOS CategoryDayProgress.
 */
data class CategoryDayProgress(
    val days: Int,              // 0..3 distinct days with a flawless go
    val hasAttempts: Boolean,   // the quiz has been played at least once
    val lastFlawless: Boolean,  // the most recent go had no mistakes
    val lastCorrect: Int,       // most recent go: words right first time
    val lastTotal: Int,         // most recent go: words in the quiz
    val earnedToday: Boolean,   // a flawless go already counted today
) {
    val isMastered: Boolean get() = days >= 3

    /** e.g. "1 of 3 days", "1 of 3 days · last go 6 of 8", "Last go: 6 of 8 correct". */
    val progressLine: String get() = when {
        isMastered -> "3 of 3 days"
        !lastFlawless && days > 0 -> "$days of 3 days · last go $lastCorrect of $lastTotal"
        !lastFlawless -> "Last go: $lastCorrect of $lastTotal correct"
        else -> "$days of 3 days"
    }

    /** Encouraging "what's next". */
    val nextStep: String get() = when {
        isMastered -> "Mastered"
        earnedToday -> "Do again tomorrow"
        !lastFlawless -> "Try again now"
        days == 2 -> "Do today for mastery"
        else -> "Do today"
    }
}

data class DashboardUiState(
    val isLoading: Boolean = true,
    val isColdStart: Boolean = true, // No quizzes ever taken
    val wordsDueCount: Int = 0,
    val wordsDueList: List<String> = listOf(),
    val totalMastered: Int = 0,
    val categoryStats: List<CategoryMasteryStats> = emptyList()
)