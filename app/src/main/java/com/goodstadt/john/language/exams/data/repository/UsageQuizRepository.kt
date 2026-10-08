package com.goodstadt.john.language.exams.data.repository

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.goodstadt.john.language.exams.models.CategoryMasteryLevel
import com.goodstadt.john.language.exams.models.UsageMastery
import com.goodstadt.john.language.exams.models.UsageQuestionStat
import com.goodstadt.john.language.exams.models.UsageQuizAttempt
import com.goodstadt.john.language.exams.models.UsageQuizStat
import com.goodstadt.john.language.exams.models.VocabQuizOutcome
import com.goodstadt.john.language.exams.models.WordMasteryLevel
import com.goodstadt.john.language.exams.data.strength.ReferenceStrength
import com.goodstadt.john.language.exams.data.strength.ReferenceStrengthMath
import com.goodstadt.john.language.exams.screens.UsageQuiz.UsageQuizLevelsFilename
import java.time.Instant
import java.time.ZoneId
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

    // In-Memory Store: Map<QuizID, QuizStats>. Accessed from several threads (record* from caller
    // threads, save/load from the IO scope), and Gson iterates it while serialising - so EVERY read,
    // structural mutation and serialisation must hold [lock] or we get ConcurrentModificationException.
    private var quizStates: MutableMap<String, UsageQuizStat> = mutableMapOf()
    private val lock = Any()

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
            synchronized(lock) {
                val quizStat = quizStates.getOrPut(quizId) { UsageQuizStat(quizId) }
                val questionStat = quizStat.questions.getOrPut(pageNumber) { UsageQuestionStat(pageNumber) }

                val now = System.currentTimeMillis()
                VocabMasteryEngine.updateMastery(questionStat, outcome, now)
                questionStat.lastAnsweredAt = now

                Timber.d(
                    "UsageQuiz: $quizId [$pageNumber] $outcome -> ${questionStat.masteryLevel} " +
                        "(streak=${questionStat.correctStreak})"
                )
            }
            saveToDisk()
            _dataUpdateEvents.emit(Unit)
        }
    }

    /**
     * Call this when the whole quiz finishes to update high-level stats.
     */
    fun finishQuiz(quizId: String, finalScore: Int) {
        scope.launch {
            synchronized(lock) {
                val quizStat = quizStates.getOrPut(quizId) { UsageQuizStat(quizId) }
                quizStat.timesCompleted += 1
                quizStat.bestScore = max(quizStat.bestScore, finalScore)
            }
            saveToDisk()
            _dataUpdateEvents.emit(Unit)
        }
    }

    // MARK: - Whole-quiz attempts + flawless-on-3-days award

    /**
     * Record one dated attempt at a whole quiz set (a single go), update the high-level stats, and report
     * whether this go just earned the "flawless on 3 separate days" award. A go is **flawless** when the
     * set was completed with no errors: correct == tries == total (every question right, one tap each).
     * Mutates the in-memory store synchronously (so the returned award flag is accurate) and persists async.
     */
    fun recordAttempt(quizId: String, correct: Int, tries: Int, total: Int): Boolean {
        val justEarnedAward = synchronized(lock) {
            val quizStat = quizStates.getOrPut(quizId) { UsageQuizStat(quizId) }
            // Guard against a null list from a legacy save that pre-dates the `attempts` field.
            @Suppress("SENSELESS_COMPARISON")
            if (quizStat.attempts == null) quizStat.attempts = mutableListOf()
            val flawless = total > 0 && correct == total && tries == total
            quizStat.attempts.add(
                UsageQuizAttempt(
                    attemptedAt = System.currentTimeMillis(),
                    total = total,
                    correct = correct,
                    tries = tries,
                    flawless = flawless
                )
            )
            quizStat.timesCompleted += 1
            quizStat.bestScore = max(quizStat.bestScore, correct)

            val earned = flawless && flawlessDistinctDays(quizId) >= 3 && !quizStat.threeDayAwardGiven
            if (earned) quizStat.threeDayAwardGiven = true
            earned
        }

        scope.launch {
            saveToDisk()
            _dataUpdateEvents.emit(Unit)
        }
        return justEarnedAward
    }

    /** Every dated attempt at one quiz, oldest first. */
    fun getQuizAttempts(quizId: String): List<UsageQuizAttempt> =
        synchronized(lock) { quizStates[quizId]?.attempts?.toList() ?: emptyList() }

    /**
     * One rolled-up strength per usage quiz that has been COMPLETED at least once - the data behind the
     * Focus tab's "Usage Quiz" section (mirrors the iOS implementation). The mark is the quiz RESULT
     * (correct out of total) blended across completions via [ReferenceStrengthMath], so a poor run counts
     * against you. Buckets Weak/OK/Strong with the same math as the Reference and Vocab-Quiz sections; the
     * label is the quiz's friendly topic title from the per-flavour [UsageQuizLevelsFilename] catalogue.
     */
    fun usageQuizStrengths(): List<ReferenceStrength> = synchronized(lock) {
        val titles = UsageQuizLevelsFilename.entries
            .flatMap { it.quizzes }
            .associate { it.baseName to it.title }

        quizStates.mapNotNull { (quizId, stat) ->
            val attempts = stat.attempts?.sortedBy { it.attemptedAt } ?: emptyList()
            var mark: Float? = null
            var priorAttempts = 0
            var totalCorrect = 0
            var totalAnswered = 0
            for (a in attempts) {
                if (a.total <= 0) continue
                mark = ReferenceStrengthMath.blend(mark, priorAttempts, a.correct, a.total)
                priorAttempts += 1
                totalCorrect += a.correct
                totalAnswered += a.total
            }
            if (priorAttempts == 0) return@mapNotNull null
            val m = mark ?: 0f
            ReferenceStrength(
                areaId = quizId,
                label = titles[quizId] ?: quizId,
                mark = m,
                level = ReferenceStrengthMath.level(m, totalAnswered, priorAttempts),
                attempts = priorAttempts,
                totalCorrect = totalCorrect,
                totalAnswered = totalAnswered,
                lastUpdated = attempts.lastOrNull()?.attemptedAt ?: 0L
            )
        }.sortedWith(compareBy({ it.level.ordinal }, { it.label }))
    }

    /** Number of DISTINCT local calendar days on which this quiz was completed flawlessly (no errors).
     *  Callers already holding [lock] are fine - the monitor is reentrant. */
    fun flawlessDistinctDays(quizId: String): Int = synchronized(lock) {
        val zone = ZoneId.systemDefault()
        quizStates[quizId]?.attempts
            ?.filter { it.flawless }
            ?.map { Instant.ofEpochMilli(it.attemptedAt).atZone(zone).toLocalDate() }
            ?.toSet()?.size ?: 0
    }

    /** True once the user has completed this quiz flawlessly on 3 separate days. */
    fun isThreeDayFlawlessAwardEarned(quizId: String): Boolean = flawlessDistinctDays(quizId) >= 3

    /**
     * Roll the [total] questions' mastery up into ONE whole-quiz status (for reporting / a future dot).
     * "Worst-attention wins, except Mastered needs every question." Unanswered pages count as New.
     */
    fun getQuizMastery(quizId: String, total: Int): CategoryMasteryLevel {
        if (total <= 0) return CategoryMasteryLevel.New
        val levels = synchronized(lock) {
            val qmap = quizStates[quizId]?.questions
            (1..total).map { page -> qmap?.get(page)?.masteryLevel ?: WordMasteryLevel.New }
        }
        return when {
            levels.all { it == WordMasteryLevel.Mastered } -> CategoryMasteryLevel.Mastered
            levels.any { it == WordMasteryLevel.Struggling } -> CategoryMasteryLevel.Struggling
            levels.any { it == WordMasteryLevel.Learning } -> CategoryMasteryLevel.Learning
            levels.any { it == WordMasteryLevel.Review } -> CategoryMasteryLevel.Review
            else -> CategoryMasteryLevel.New
        }
    }

    // MARK: - READ

    fun getStatsForQuiz(quizId: String): UsageQuizStat? =
        synchronized(lock) { quizStates[quizId] }

    fun getStatsForQuestion(quizId: String, pageNumber: Int): UsageQuestionStat? =
        synchronized(lock) { quizStates[quizId]?.questions?.get(pageNumber) }

    // MARK: - DELETE

    fun clearStatsForQuiz(quizId: String) {
        scope.launch {
            val removed = synchronized(lock) { quizStates.remove(quizId) != null }
            if (removed) {
                saveToDisk()
                _dataUpdateEvents.emit(Unit)
            }
        }
    }

    fun clearAll() {
        scope.launch {
            synchronized(lock) { quizStates.clear() }
            val file = File(context.filesDir, fileName)
            if (file.exists()) file.delete()
            _dataUpdateEvents.emit(Unit)
        }
    }

    // MARK: - PERSISTENCE

    private fun saveToDisk() {
        try {
            // Serialise under the lock (Gson iterates the map + its nested maps/lists) then write the
            // file outside it, so disk IO doesn't hold the lock against the record* callers.
            val jsonString = synchronized(lock) { gson.toJson(quizStates) }
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
                val loaded = gson.fromJson<MutableMap<String, UsageQuizStat>>(file.readText(), type)
                    ?: mutableMapOf()
                // Files saved before `attempts` existed deserialize it as null (Gson bypasses the
                // constructor, so the default isn't applied) - normalise so recordAttempt can add to it.
                loaded.values.forEach { stat ->
                    @Suppress("SENSELESS_COMPARISON")
                    if (stat.attempts == null) stat.attempts = mutableListOf()
                }
                synchronized(lock) { quizStates = loaded }
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
    fun getQuestionMastery(quizId: String, pageNumber: Int): UsageMastery =
        synchronized(lock) { quizStates[quizId]?.questions?.get(pageNumber)?.mastery ?: UsageMastery.New }

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
        synchronized(lock) {
            quizStates.forEach { (id, stat) ->
                Timber.d("Quiz: $id (Completed: ${stat.timesCompleted}, Best: ${stat.bestScore})")
                stat.questions.forEach { (page, qStat) ->
                    Timber.d("   Page $page: ${qStat.masteryLevel} (streak=${qStat.correctStreak})")
                }
            }
        }
        Timber.d("=============================")
    }
}