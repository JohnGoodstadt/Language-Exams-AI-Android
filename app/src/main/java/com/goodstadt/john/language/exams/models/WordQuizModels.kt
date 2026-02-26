package com.goodstadt.john.language.exams.models

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.util.UUID

// 1. The Status of a specific word
//@Keep
//enum class WordMasteryLevel {
//    New,        // Never seen
//    Struggling, // Got it wrong repeatedly (High priority)
//    Learning,   // Got it right, but took > 1 try, or only got it right once
//    Review,     // Got it right first time consecutively
//    Mastered    // Correct first time 3+ times in a row (Don't show again)
//}
//
//// 2. The Transaction (What happened just now?)
//@Keep
//data class WordQuizAttempt(
//    val id: String = UUID.randomUUID().toString(),
//    val timestamp: Long = System.currentTimeMillis(),
//    val word: String,
//    val triesNeeded: Int, // 1 = Perfect, 2 = Retry, etc.
//    val wasCorrectEventually: Boolean
//)
//
//// 3. The Aggregate State (The "Brain" for that word)
//@Keep
//data class WordLearningState(
//    @SerializedName("w") val word: String,
//    @SerializedName("lvl") var masteryLevel: WordMasteryLevel = WordMasteryLevel.New,
//    @SerializedName("streak") var correctStreak: Int = 0, // Consecutive first-try successes
//    @SerializedName("next_due") var nextReviewTime: Long = 0, // When to show this again
//    @SerializedName("history") val history: MutableList<WordQuizAttempt> = mutableListOf(),
//    @SerializedName("last_out") var lastOutcome: VocabQuizOutcome? = null
//)