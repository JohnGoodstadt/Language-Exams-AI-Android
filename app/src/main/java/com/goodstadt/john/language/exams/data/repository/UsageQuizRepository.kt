package com.goodstadt.john.language.exams.data.repository

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.goodstadt.john.language.exams.models.UsageMastery
import com.goodstadt.john.language.exams.models.UsageQuestionStat
import com.goodstadt.john.language.exams.models.UsageQuizStat
import com.goodstadt.john.language.exams.models.VocabQuizOutcome
import com.goodstadt.john.language.exams.models.WordMasteryLevel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class UsageQuizRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val fileName = "usage_quiz_stats.json"
    private val scope = CoroutineScope(Dispatchers.IO)

    // In-Memory Store: Map<QuizID, QuizStats>
    private var quizStates: MutableMap<String, UsageQuizStat> = mutableMapOf()

    // Reactive flow to notify UI of changes
    private val _dataUpdateEvents = MutableSharedFlow<Unit>(replay = 1)
    val dataUpdateEvents = _dataUpdateEvents.asSharedFlow()

    init {
        loadFromDisk()
    }

    // MARK: - UPDATE (Record Result)

    /**
     * Records one answer for a single question and runs the shared spaced-repetition engine.
     * The [outcome] captures how the answer went (see [VocabQuizOutcome]); the engine turns that
     * into a mastery level, streak and next-review time. Mastery = correct first-try in 3 separate
     * spaced sessions, matching the Vocab Quiz.
     * @param quizId:     e.g. "UsageQuiz1A1"
     * @param pageNumber: the question's stable page id (1..10) - the unique key with quizId
     * @param outcome:    FLAWLESS / ASSISTED / STUMBLED / FAILED for this question
     */
    fun recordQuestionResult(quizId: String, pageNumber: Int, outcome: VocabQuizOutcome) {
        scope.launch {
            val quizStat = quizStates.getOrPut(quizId) { UsageQuizStat(quizId) }
            val questionStat = quizStat.questions.getOrPut(pageNumber) { UsageQuestionStat(pageNumber) }

            val now = System.currentTimeMillis()
            VocabMasteryEngine.updateMastery(questionStat, outcome, now)
            questionStat.lastAnsweredAt = now

            Timber.d(
                "UsageQuiz: $quizId [$pageNumber] $outcome -> ${questionStat.masteryLevel} " +
                    "(streak=${questionStat.correctStreak})"
            )

            saveToDisk()
            _dataUpdateEvents.emit(Unit)
        }
    }

    /**
     * Call this when the whole quiz finishes to update high-level stats.
     */
    fun finishQuiz(quizId: String, finalScore: Int) {
        scope.launch {
            val quizStat = quizStates.getOrPut(quizId) { UsageQuizStat(quizId) }

            quizStat.timesCompleted += 1
            quizStat.bestScore = max(quizStat.bestScore, finalScore)

            saveToDisk()
            _dataUpdateEvents.emit(Unit)
        }
    }

    // MARK: - READ

    fun getStatsForQuiz(quizId: String): UsageQuizStat? {
        return quizStates[quizId]
    }

    fun getStatsForQuestion(quizId: String, pageNumber: Int): UsageQuestionStat? {
        return quizStates[quizId]?.questions?.get(pageNumber)
    }

    // MARK: - DELETE

    fun clearStatsForQuiz(quizId: String) {
        scope.launch {
            if (quizStates.remove(quizId) != null) {
                saveToDisk()
                _dataUpdateEvents.emit(Unit)
            }
        }
    }

    fun clearAll() {
        scope.launch {
            quizStates.clear()
            val file = File(context.filesDir, fileName)
            if (file.exists()) file.delete()
            _dataUpdateEvents.emit(Unit)
        }
    }

    // MARK: - PERSISTENCE

    private fun saveToDisk() {
        try {
            val jsonString = gson.toJson(quizStates)
            File(context.filesDir, fileName).writeText(jsonString)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save usage quiz stats")
        }
    }

    private fun loadFromDisk() {
        try {
            val file = File(context.filesDir, fileName)
            if (file.exists()) {
                val type = object : TypeToken<MutableMap<String, UsageQuizStat>>() {}.type
                quizStates = gson.fromJson(file.readText(), type)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load usage quiz stats")
        }
    }

    // In UsageQuizRepository.kt
    fun getQuizStatus(quizId: String): QuizFluency {
        // 1. Get the score/stars for this specific key
        val stats = getStatsForQuiz(quizId)

        return QuizFluency.FLUENT


//        val score = getScoreForQuiz(quizId) // Replace with your internal storage call
//        val attempts = getAttemptsForQuiz(quizId)
//
//        // 2. Determine the label logic
//        return when {
//            attempts == 0 -> QuizFluency.NEVER_DONE
//            score >= 9 -> QuizFluency.FLUENT
//            score >= 7 -> QuizFluency.GOOD
//            else -> QuizFluency.NEEDS_ATTENTION
//        }
    }
    fun getFluencyStatus(quizId: String): QuizFluency {
        // Replace these with your actual data fetching logic from your storage
        val stats = getStatsForQuiz(quizId)

        val correct = stats?.bestScore ?: 0//getCorrectCount(quizId)
        val total = stats?.timesCompleted ?: 0// getTotalAttempts(quizId)

        if (total == 0) return QuizFluency.NEVER_DONE //never looked at

        if (correct > 0) return QuizFluency.FLUENT //at least 1 perfect run through

        if (total > 0) return QuizFluency.LEARNING //tried at least once - but not perfect

        val percentage = (correct.toFloat() / total.toFloat()) * 100

        return when {
            percentage >= 90 -> QuizFluency.FLUENT
            percentage < 60 -> QuizFluency.CAREFUL
            else -> QuizFluency.LEARNING
        }
    }
    enum class QuizFluency(val label: String, val color: Color) {
        NEVER_DONE("", Color.Gray), //don't show "Not started"
        FLUENT("Fluent", Color(0xFF4CAF50)),
        CAREFUL("Be careful", Color(0xFFFF5252)),
        LEARNING("In progress...", Color(0xFFFF9500))
    }
    // MARK: - MASTERY HELPERS

    /**
     * Returns the mastery level for a specific question.
     */
    fun getQuestionMastery(quizId: String, pageNumber: Int): UsageMastery {
        return quizStates[quizId]?.questions?.get(pageNumber)?.mastery ?: UsageMastery.New
    }

    /**
     * Returns display label + color for a given UsageMastery level (shared 5-level model).
     */
    fun getMasteryDisplay(mastery: UsageMastery): Pair<String, Color> {
        return when (mastery) {
            WordMasteryLevel.New -> "New" to Color.Gray
            WordMasteryLevel.Struggling -> "Struggling" to Color.Red
            WordMasteryLevel.Learning -> "Learning" to Color(0xFFFF9800) // Orange
            WordMasteryLevel.Review -> "Review" to Color(0xFF2196F3) // Blue
            WordMasteryLevel.Mastered -> "Mastered" to Color(0xFF4CAF50) // Green
        }
    }

    // MARK: - DEBUG

    fun debugPrint() {
        Timber.d("===== USAGE QUIZ REPORT =====")
        quizStates.forEach { (id, stat) ->
            Timber.d("Quiz: $id (Completed: ${stat.timesCompleted}, Best: ${stat.bestScore})")
            stat.questions.forEach { (page, qStat) ->
                Timber.d("   Page $page: ${qStat.masteryLevel} (streak=${qStat.correctStreak})")
            }
        }
        Timber.d("=============================")
    }
}