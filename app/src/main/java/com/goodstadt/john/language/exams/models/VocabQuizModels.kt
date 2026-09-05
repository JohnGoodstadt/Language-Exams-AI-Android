package com.goodstadt.john.language.exams.models

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.util.UUID

// 1. The Input: What happened in the UI?
// (Used by ViewModel to report results)
@Keep
enum class VocabQuizOutcome {
    FLAWLESS,   // 1 try, no info button (Mastered)
    ASSISTED,   // 1 try, but used Info button (Needs review)
    STUMBLED,   // 2+ tries, eventually got it (Short review)
    FAILED      // Many tries / Gave up (Immediate review)
}

// 2. The Internal Status
// (Used by Repository to schedule reviews)
@Keep
enum class WordMasteryLevel {
    New,        // Never seen
    Struggling, // Got it wrong repeatedly (High priority)
    Learning,   // Got it right, but took > 1 try
    Review,     // Got it right first time consecutively
    Mastered    // Correct first time 3+ times in a row (Don't show again)
}

// 2b. The Whole-Quiz (Category) Status
// A single rolled-up status for an entire quiz/category, derived from its words' [WordMasteryLevel]s, so a
// future dashboard can show one dot per quiz. "Worst-attention wins", except Mastered needs ALL words:
//   New        - nothing attempted yet
//   Struggling - at least one word is Struggling (a problem to flag)   -> e.g. red dot
//   Learning   - some word still Learning, none Struggling             -> e.g. orange dot
//   Review     - some word in Review, none Struggling/Learning         -> e.g. blue dot
//   Mastered   - EVERY word is Mastered (3 correct-first-time sessions) -> e.g. green dot
@Keep
enum class CategoryMasteryLevel {
    New,
    Struggling,
    Learning,
    Review,
    Mastered
}

// 2c. Persisted whole-quiz status + the counts behind it, keyed by (level, category). Written whenever a
// word in the category is answered, so a dashboard can enumerate quizzes and their status without having to
// reload every quiz's word list.
@Keep
data class CategoryMasteryState(
    @SerializedName("cat") val category: String = "",
    @SerializedName("lvl") val level: String = "",
    @SerializedName("status") var status: CategoryMasteryLevel = CategoryMasteryLevel.New,
    @SerializedName("total") var total: Int = 0,
    @SerializedName("new") var newCount: Int = 0,
    @SerializedName("struggling") var struggling: Int = 0,
    @SerializedName("learning") var learning: Int = 0,
    @SerializedName("review") var review: Int = 0,
    @SerializedName("mastered") var mastered: Int = 0,
    @SerializedName("updated") var updatedAt: Long = 0
)

// 2d. One dated ATTEMPT at a whole quiz (a single go from opening the quiz to closing it). Multiple goes at
// the same quiz are kept as separate records (separated by [attemptedAt]), so progress over repeated goes
// can be reported. "completed" means every question was answered at least once (answered >= total).
@Keep
data class CategoryQuizAttempt(
    @SerializedName("cat") val category: String = "",
    @SerializedName("lvl") val level: String = "",
    @SerializedName("at") val attemptedAt: Long = 0,     // epoch millis - the date/time of this go
    @SerializedName("total") val total: Int = 0,         // questions in the quiz
    @SerializedName("answered") val answered: Int = 0,   // distinct questions answered (right or wrong)
    @SerializedName("correct") val correct: Int = 0,     // distinct questions answered correctly
    @SerializedName("tries") val tries: Int = 0,         // total answer taps this go
    @SerializedName("completed") val completed: Boolean = false // answered every question (answered >= total)
)

// 3. The Transaction (History Log)
// (Kept inside the State object for debugging/analytics)
@Keep
data class WordQuizAttempt(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val word: String,
    val triesNeeded: Int, // 1 = Perfect, 2 = Retry, etc.
    val wasCorrectEventually: Boolean
)

// 4. The Aggregate State (The "Brain" for that word)
// This is the object saved to JSON/Firestore
@Keep
data class VocabLearningState(
    @SerializedName("w") val word: String,

    @SerializedName("cat") var sourceCategory: String = "",

    @SerializedName("lvl_src") var sourceLevel: String = "", //ee.g. B1,A2...

    @SerializedName("lvl") override var masteryLevel: WordMasteryLevel = WordMasteryLevel.New,

    @SerializedName("streak") override var correctStreak: Int = 0, // Consecutive first-try successes

    @SerializedName("next_due") override var nextReviewTime: Long = 0, // When to show this again

    @SerializedName("history") val history: MutableList<WordQuizAttempt> = mutableListOf(),

    @SerializedName("last_out") var lastOutcome: VocabQuizOutcome? = null
) : SrsState