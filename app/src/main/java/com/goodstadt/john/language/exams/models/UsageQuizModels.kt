package com.goodstadt.john.language.exams.models

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
enum class UsageMastery {
    New,
    Struggling, // Many tries, low accuracy
    Learning,   // Getting there, but not perfect every time
    Fluent      // Consistent first-time success
}

@Keep
data class UsageQuestionStat(
    val pageNumber: Int,   // The static ID (1-10)
    var correctCount: Int = 0,  // Total times answered correctly
    var triesCount: Int = 0,    // Total attempts made (clicks)
    var streak: Int = 0         // Consecutive first-try successes
) {
    // Computed property to help UI decide color (Red/Orange/Green)
    val mastery: UsageMastery
        get() {
            if (triesCount == 0) return UsageMastery.New
            if (streak >= 3) return UsageMastery.Fluent
            // If accuracy is > 80%
            if (correctCount.toDouble() / triesCount.toDouble() > 0.8) return UsageMastery.Learning
            return UsageMastery.Struggling
        }
}

@Keep
data class UsageQuizStat(
    val quizId: String, // e.g. "UsageQuiz1A1"
    // Map of PageNumber (Int) -> Stats
    val questions: MutableMap<Int, UsageQuestionStat> = mutableMapOf(),

    // Aggregate High-Level Stats
    var timesCompleted: Int = 0,
    var bestScore: Int = 0
)

data class UsageLevelSummary(
    val levelName: String,
    val totalQuizzes: Int,
    val completedQuizzes: Int,
    val totalStars: Int, // 10/10 = 3 stars, 8/10 = 2 stars, etc.
    val items: List<UsageQuizOverviewItem>
)

data class UsageQuizOverviewItem(
    val id: Int,
    val title: String,
    val bestScore: Int,      // 0-10
    val timesCompleted: Int,
    val isLocked: Boolean,   // Optional: Lock quiz 2 until quiz 1 is done? (We'll keep all open for now)

    // The granular question data (Index 1..10 -> Mastery)
    val questionMastery: Map<Int, UsageMastery>
) {
    val isStarted: Boolean get() = timesCompleted > 0
    val isPerfect: Boolean get() = bestScore == 10
}