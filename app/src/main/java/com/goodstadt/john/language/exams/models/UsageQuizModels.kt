package com.goodstadt.john.language.exams.models

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * The Usage Quiz now shares the Vocab Quiz's spaced-repetition mastery model, so its filter chips
 * use the same 5 levels (New / Struggling / Learning / Review / Mastered).
 */
typealias UsageMastery = WordMasteryLevel

@Keep
data class UsageQuestionStat(
    val pageNumber: Int,   // The static ID (1-10) — the unique key together with the quizId
    @SerializedName("lvl") override var masteryLevel: WordMasteryLevel = WordMasteryLevel.New,
    @SerializedName("streak") override var correctStreak: Int = 0, // first-try successes across sessions
    @SerializedName("next_due") override var nextReviewTime: Long = 0, // when this question is due again
    @SerializedName("last_at") var lastAnsweredAt: Long = 0 // timestamp of the most recent answer
) : SrsState {
    /** Convenience alias so existing call-sites reading `.mastery` keep working. */
    val mastery: UsageMastery get() = masteryLevel
}

// One dated ATTEMPT at a whole Usage quiz set (a single go through its 10 questions), so repeated goes are
// kept separately (by [attemptedAt]) and we can report completions over time. "flawless" means the whole
// set was completed with NO errors (every question right, one tap each: correct == tries == total).
@Keep
data class UsageQuizAttempt(
    @SerializedName("at") val attemptedAt: Long = 0,   // epoch millis of this go
    @SerializedName("total") val total: Int = 0,       // questions in the set (always 10)
    @SerializedName("correct") val correct: Int = 0,   // correct answers this go
    @SerializedName("tries") val tries: Int = 0,       // total answer taps this go
    @SerializedName("flawless") val flawless: Boolean = false // completed with no errors
)

@Keep
data class UsageQuizStat(
    val quizId: String, // e.g. "UsageQuiz1A1"
    // Map of PageNumber (Int) -> Stats
    val questions: MutableMap<Int, UsageQuestionStat> = mutableMapOf(),

    // Aggregate High-Level Stats
    var timesCompleted: Int = 0,
    var bestScore: Int = 0,

    // Dated history of each go (oldest first), so completions - and flawless completions on separate days -
    // can be reported. `var` + normalised on load because Gson leaves it null for files saved before this
    // field existed (it bypasses the constructor, so the default isn't applied).
    @SerializedName("attempts") var attempts: MutableList<UsageQuizAttempt> = mutableListOf(),

    // Set once the "flawless on 3 separate days" award has been given, so it isn't awarded again.
    @SerializedName("award3day") var threeDayAwardGiven: Boolean = false
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