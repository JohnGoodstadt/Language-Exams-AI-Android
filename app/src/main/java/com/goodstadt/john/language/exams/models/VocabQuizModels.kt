package com.goodstadt.john.language.exams.models

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

// 1. The Input: What happened in the UI?
enum class VocabQuizOutcome {
    FLAWLESS,   // 1 try, no info button (Mastered)
    ASSISTED,   // 1 try, but used Info button (Needs review)
    STUMBLED,   // 2+ tries, eventually got it (Short review)
    FAILED      // Many tries / Gave up (Immediate review)
}

// 2. The Internal Status
enum class MasteryLevel {
    New,        // Never seen
    Learning,   // In the short-term cycle
    Review,     // In the long-term cycle
    Mastered    // Done (won't show again unless forced)
}

// 3. The Persistent State (Saved to JSON/Firestore)
@Keep
data class VocabLearningState(
    @SerializedName("w") val word: String, // ID
    @SerializedName("lvl") var masteryLevel: MasteryLevel = MasteryLevel.New,
    @SerializedName("due") var nextReviewTimestamp: Long = 0, // Unix Interval
    @SerializedName("strk") var streak: Int = 0, // Consecutive successful reviews
    @SerializedName("last_out") var lastOutcome: VocabQuizOutcome? = null
)