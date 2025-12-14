package com.goodstadt.john.language.exams.data


import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

import java.util.UUID
import java.util.Date

data class QuizAttempt(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val skillLevel: String,   // "A1", "B1" etc
    val quizNumber: Int,      // 1, 2, 3...
    val correctAnswers: Int,  // e.g. 8
    val totalQuestions: Int,  // e.g. 10
    val duration: Long = 0    // Seconds
) {
    val scorePercentage: Double
        get() = if (totalQuestions > 0) correctAnswers.toDouble() / totalQuestions else 0.0

    val isPerfect: Boolean
        get() = correctAnswers == totalQuestions
}

// Helper for the Side Quest Sheet
data class GlobalQuizStats(
    val totalAttempts: Int,
    val perfectScores: Int,
    val uniqueQuizzesPlayed: Int,
    val strongestLevel: String
)

@Singleton
class QuizHistoryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val analytics = FirebaseAnalytics.getInstance(context)
    private val fileName = "quiz_history_v1.json"

    // Structure: [SkillLevel : [QuizNumber : [List of Attempts]]]
    private var history: MutableMap<String, MutableMap<Int, MutableList<QuizAttempt>>> = mutableMapOf()

    init {
        loadHistory()
    }

    // MARK: - Public API

    fun saveAttempt(
        skillLevel: String,
        quizNumber: Int,
        correct: Int,
        total: Int,
        duration: Long = 0
    ) {
        val attempt = QuizAttempt(
            skillLevel = skillLevel,
            quizNumber = quizNumber,
            correctAnswers = correct,
            totalQuestions = total,
            duration = duration
        )

        // Initialize maps if empty
        val levelMap = history.getOrPut(skillLevel) { mutableMapOf() }
        val quizList = levelMap.getOrPut(quizNumber) { mutableListOf() }

        // Add Attempt
        quizList.add(attempt)

        // Persist
        saveHistory()

        // Analytics
        logQuizResultToCloud(quizNumber, correct, skillLevel, total, isFirstAttempt = quizList.size == 1)

        Timber.d("Quiz Saved: $skillLevel #$quizNumber ($correct/$total)")
    }

    fun getLastAttempt(skillLevel: String, quizNumber: Int): QuizAttempt? {
        return history[skillLevel]?.get(quizNumber)?.lastOrNull()
    }

    fun getBestAttempt(skillLevel: String, quizNumber: Int): QuizAttempt? {
        val attempts = history[skillLevel]?.get(quizNumber) ?: return null

        // Sort by Score Descending, then Date Descending
        return attempts.sortedWith(
            compareByDescending<QuizAttempt> { it.correctAnswers }
                .thenByDescending { it.timestamp }
        ).firstOrNull()
    }

    // MARK: - Progress Helpers

    fun getProgress(forLevel: String, totalQuizzesAvailable: Int): Pair<Int, Int> {
        val attemptedCount = history[forLevel]?.keys?.size ?: 0
        return Pair(attemptedCount, totalQuizzesAvailable)
    }

    fun getWeakestQuizzes(forLevel: String): List<Int> {
        val levelData = history[forLevel] ?: return emptyList()
        val weakIds = mutableListOf<Int>()

        for ((id, attempts) in levelData) {
            val totalCorrect = attempts.sumOf { it.correctAnswers }
            val totalPossible = attempts.sumOf { it.totalQuestions }

            if (totalPossible > 0) {
                val average = totalCorrect.toDouble() / totalPossible
                if (average < 0.7) { // Below 70% average
                    weakIds.add(id)
                }
            }
        }
        return weakIds.sorted()
    }

    /**
     * Used by Smart Coach to suggest a level to review.
     * Returns the level name with the lowest average score.
     */
    fun getWeakestSkillLevel(): String? {
        val levelAverages = mutableMapOf<String, Double>()

        history.forEach { (level, quizzes) ->
            var totalScore = 0.0
            var count = 0

            quizzes.values.forEach { attempts ->
                attempts.forEach { attempt ->
                    totalScore += attempt.scorePercentage
                    count++
                }
            }

            if (count > 0) {
                levelAverages[level] = totalScore / count
            }
        }

        // Return key with min value
        return levelAverages.minByOrNull { it.value }?.key
    }

    /**
     * Used by Side Quest Stats Sheet (QuizMasteryCard).
     */
    fun getGlobalStats(): GlobalQuizStats {
        var totalAttempts = 0
        var perfectScores = 0
        var uniqueQuizzes = 0
        val levelScores = mutableMapOf<String, Int>()

        history.forEach { (level, quizzes) ->
            if (quizzes.isNotEmpty()) {
                // Count unique quizzes (keys in the map)
                uniqueQuizzes += quizzes.size
            }

            quizzes.values.flatten().forEach { attempt ->
                totalAttempts++

                // Weighting for "Strongest Level"
                val currentScore = levelScores.getOrDefault(level, 0)

                if (attempt.isPerfect) {
                    perfectScores++
                    levelScores[level] = currentScore + 5 // Bonus for perfect
                } else {
                    levelScores[level] = currentScore + 1
                }
            }
        }

        val strongest = levelScores.maxByOrNull { it.value }?.key ?: "None"

        return GlobalQuizStats(
            totalAttempts = totalAttempts,
            perfectScores = perfectScores,
            uniqueQuizzesPlayed = uniqueQuizzes,
            strongestLevel = strongest
        )
    }

    // MARK: - Helper for Breakdown List

    fun getStatsForQuiz(level: String, quizID: Int): Pair<Int, Int> {
        val attempts = history[level]?.get(quizID) ?: return Pair(0, 0)
        val perfects = attempts.count { it.isPerfect }
        return Pair(perfects, attempts.size)
    }

    // MARK: - Analytics

    private fun logQuizResultToCloud(quizID: Int, score: Int, level: String, totalQuestions: Int, isFirstAttempt: Boolean) {
        val params = Bundle().apply {
            putInt("quiz_id", quizID)
            putString("skill_level", level)
            putInt("score", score)
            putDouble("score_percent", score.toDouble() / totalQuestions)
            putString("is_first_attempt", if (isFirstAttempt) "true" else "false")
        }
        analytics.logEvent("quiz_attempt_result", params)
    }

    // MARK: - Persistence

    private fun saveHistory() {
        try {
            val jsonString = gson.toJson(history)
            val file = File(context.filesDir, fileName)
            file.writeText(jsonString)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save quiz history")
        }
    }

    private fun loadHistory() {
        try {
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                val jsonString = file.readText()
                val type = object : TypeToken<MutableMap<String, MutableMap<Int, MutableList<QuizAttempt>>>>() {}.type
                history = gson.fromJson(jsonString, type)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load quiz history")
        }
    }

    // MARK: - Debug

    fun clearHistory() {
        history.clear()
        val file = File(context.filesDir, fileName)
        if (file.exists()) {
            file.delete()
        }
        Timber.w("🚨 Quiz History Cleared")
    }
}