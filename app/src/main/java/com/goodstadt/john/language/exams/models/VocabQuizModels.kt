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