package com.goodstadt.john.language.exams.data.repository

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.goodstadt.john.language.exams.ui.theme.statusOrange
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
import com.goodstadt.john.language.exams.BuildConfig
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
    fun recordAttempt(quizId: String, correct: Int, tries: Int, total: Int, firstTryCorrect: Int = correct): Boolean {
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
                    flawless = flawless,
                    firstTryCorrect = firstTryCorrect
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

    /**
     * Short history summary for a quiz's picker row, e.g. "Just now · 1x · 8/10" or
     * "20m · 5x · 2p · 10/10 9/10 10/10": time since the last go, times completed, the number of different
     * days with a flawless go ("2p", shown once there is one), and the first-try scores of the last
     * [RECENT_RUNS_SHOWN] goes, newest first. Once mastered (flawless on 3 different days - mistakes in
     * between don't reset it) the scores are replaced by "3 on 3 days · Mastered" and the row adds a crown.
     * Mirrors iOS UsageQuizRepository.recentSummary. Null when the quiz has never been completed.
     */
    fun recentSummary(quizId: String, now: Long = System.currentTimeMillis()): String? = synchronized(lock) {
        val stat = quizStates[quizId] ?: return@synchronized null
        @Suppress("SENSELESS_COMPARISON")
        val attempts = if (stat.attempts == null) emptyList() else stat.attempts.toList()
        if (stat.timesCompleted <= 0 && attempts.isEmpty()) return@synchronized null

        val parts = mutableListOf<String>()
        attempts.maxOfOrNull { it.attemptedAt }?.takeIf { it > 0 }?.let { parts += shortAge(now - it) }
        parts += "${maxOf(stat.timesCompleted, attempts.size)}x"
        val perfectDays = flawlessDistinctDays(quizId)
        if (perfectDays >= MASTERY_DAYS) {
            parts += "$MASTERY_DAYS on $MASTERY_DAYS days"
            parts += "Mastered"
        } else {
            // Progress dots "●●○" = flawless on 2 of the 3 different days needed (was "2p"). Mirrors iOS.
            if (perfectDays > 0) parts += "●".repeat(perfectDays) + "○".repeat(MASTERY_DAYS - perfectDays)
            val recent = attempts.sortedByDescending { it.attemptedAt }
                .filter { it.total > 0 }
                .take(RECENT_RUNS_SHOWN)
            if (recent.isNotEmpty()) {
                parts += recent.joinToString(" ") { "${it.firstTryCorrect ?: it.correct}/${it.total}" }
            }
        }
        parts.joinToString(" · ")
    }

    // ---------------- Gamification helpers (mirror iOS UsageQuizRepository) ----------------

    /** "↻ ready": 1 or 2 flawless days, not mastered, and none yet TODAY (dayIndex: 30 s "days" in DEBUG). */
    fun isReadyAgain(quizId: String, now: Long = System.currentTimeMillis()): Boolean {
        val days = getQuizAttempts(quizId).filter { it.flawless }
            .map { VocabMasteryEngine.dayIndex(it.attemptedAt, BuildConfig.DEBUG) }.toSet()
        return days.isNotEmpty() && days.size < MASTERY_DAYS &&
            VocabMasteryEngine.dayIndex(now, BuildConfig.DEBUG) !in days
    }

    /** Best FIRST-TRY score so far, and whether the quiz has been finished before (for "New best!"). */
    fun previousBest(quizId: String): Pair<Int, Boolean> {
        val attempts = getQuizAttempts(quizId)
        return (attempts.maxOfOrNull { it.firstTryCorrect ?: it.correct } ?: 0) to attempts.isNotEmpty()
    }

    /** Daily goal: how many DIFFERENT usage quizzes were finished today (real calendar day, not DEBUG days). */
    fun quizzesCompletedToday(now: Long = System.currentTimeMillis()): Int = synchronized(lock) {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = now
        val y = cal.get(java.util.Calendar.YEAR); val d = cal.get(java.util.Calendar.DAY_OF_YEAR)
        quizStates.count { (_, stat) ->
            @Suppress("SENSELESS_COMPARISON")
            val attempts = if (stat.attempts == null) emptyList() else stat.attempts
            attempts.any {
                cal.timeInMillis = it.attemptedAt
                cal.get(java.util.Calendar.YEAR) == y && cal.get(java.util.Calendar.DAY_OF_YEAR) == d
            }
        }
    }

    /** Level crown: every quiz in the level (by baseName) is mastered. */
    fun isLevelMastered(baseNames: List<String>): Boolean =
        baseNames.isNotEmpty() && baseNames.all { isMastered(it) }

    /** True once the quiz has been completed flawlessly on [MASTERY_DAYS] different days (row crown). */
    fun isMastered(quizId: String): Boolean = flawlessDistinctDays(quizId) >= MASTERY_DAYS

    /**
     * Teaching tip shown as a toast when a quiz is opened (null = nothing to say). Mirrors iOS entryTip:
     * - never completed and its intro not yet shown -> how mastery works (marks the intro shown);
     * - flawless on 2 different days, one of them TODAY, not yet mastered -> today's perfect go already
     *   counts, so the crown needs another perfect go tomorrow.
     */
    fun entryTip(quizId: String, now: Long = System.currentTimeMillis()): Pair<String, String>? {
        val prefs = context.getSharedPreferences("usage_quiz_tips", Context.MODE_PRIVATE)
        val shown = prefs.getStringSet("introShownIds", emptySet()) ?: emptySet()
        val attempts = getQuizAttempts(quizId)
        if (attempts.isEmpty() && (quizStates[quizId]?.timesCompleted ?: 0) == 0 && quizId !in shown) {
            prefs.edit().putStringSet("introShownIds", shown + quizId).apply()
            return "Aim for the crown 👑" to
                "Get every question right first time on $MASTERY_DAYS different days to master this quiz."
        }
        val perfectDays = attempts.filter { it.flawless }
            .map { VocabMasteryEngine.dayIndex(it.attemptedAt, BuildConfig.DEBUG) }.toSet()
        val today = VocabMasteryEngine.dayIndex(now, BuildConfig.DEBUG)
        if (perfectDays.size == MASTERY_DAYS - 1 && today in perfectDays) {
            return "${perfectDays.size} of $MASTERY_DAYS perfect days ✓" to
                "Today's perfect run is already counted. Come back tomorrow for your last one and the crown 👑."
        }
        return null
    }

    /**
     * DEBUG: everything stored for one quiz as readable log lines - to check the picker-row summary
     * ("5x · 2p · …") and the 3-different-days mastery rule. Every dated attempt is listed with its day
     * number ([VocabMasteryEngine.dayIndex]: a 30-second window in DEBUG, a calendar day in release).
     * Mirrors iOS UsageQuizRepository.debugDescription.
     */
    fun debugDescription(quizId: String): List<String> = synchronized(lock) {
        val stat = quizStates[quizId] ?: return@synchronized listOf("UsageQuiz '$quizId': no stored data (never answered)")
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        fun time(t: Long) = if (t > 0) fmt.format(java.util.Date(t)) else "-"
        fun day(i: Long) = if (BuildConfig.DEBUG) "$i (30s window from ${time(i * 30_000L)})"
            else "$i (year ${i / 1000}, day-of-year ${i % 1000})"
        @Suppress("SENSELESS_COMPARISON")
        val attempts = if (stat.attempts == null) emptyList() else stat.attempts.sortedBy { it.attemptedAt }
        val perfectDays = attempts.filter { it.flawless }
            .map { VocabMasteryEngine.dayIndex(it.attemptedAt, BuildConfig.DEBUG) }.distinct().sortedDescending()

        val lines = mutableListOf<String>()
        lines += "===== UsageQuiz '$quizId' ====="
        lines += "summary row       : ${recentSummary(quizId) ?: "(none)"}"
        lines += "timesCompleted    : ${stat.timesCompleted}   bestScore: ${stat.bestScore}"
        lines += "attempts (old→new): ${attempts.size}"
        attempts.forEachIndexed { i, a ->
            lines += "   #${i + 1} ${time(a.attemptedAt)}  firstTry ${a.firstTryCorrect ?: a.correct}/${a.total}" +
                "  correct ${a.correct}  tries ${a.tries}  ${if (a.flawless) "FLAWLESS" else "with errors"}" +
                "  day ${VocabMasteryEngine.dayIndex(a.attemptedAt, BuildConfig.DEBUG)}"
        }
        lines += "perfect days      : ${perfectDays.size} of $MASTERY_DAYS needed (newest first, one per day)"
        perfectDays.forEach { lines += "   • day ${day(it)}" }
        lines += "isMastered        : ${perfectDays.size >= MASTERY_DAYS}   3-day award given: ${stat.threeDayAwardGiven}"
        if (BuildConfig.DEBUG) lines += "NOTE: DEBUG build - a 'day' is a 30-second window; flawless goes inside the same window count once."
        lines += "questions (${stat.questions.size}):"
        stat.questions.keys.sorted().forEach { p ->
            stat.questions[p]?.let { q ->
                lines += "   p$p: ${q.masteryLevel}, streak ${q.correctStreak}, last ${time(q.lastAnsweredAt)}, next due ${time(q.nextReviewTime)}"
            }
        }
        lines
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
                // Score by FIRST-TRY correct (honest) - `correct` is ~always the full total in this
                // keep-trying quiz, so it would read 100%/Strong and never surface as a weakness.
                val ftc = a.firstTryCorrect ?: a.correct
                mark = ReferenceStrengthMath.blend(mark, priorAttempts, ftc, a.total)
                priorAttempts += 1
                totalCorrect += ftc
                totalAnswered += a.total
            }
            if (priorAttempts == 0) return@mapNotNull null
            // 100% once flawless on 3 different days - the same rule as the 3-day award; mistakes in between
            // don't reset it. Otherwise the running EMA.
            val m = if (flawlessDistinctDays(quizId) >= ReferenceStrengthMath.PERFECT_STREAK_FOR_MASTERY) 1f else (mark ?: 0f)
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
     *  Uses [VocabMasteryEngine.dayIndex] like the vocab stars, so DEBUG builds count 30-second "days".
     *  Callers already holding [lock] are fine - the monitor is reentrant. */
    fun flawlessDistinctDays(quizId: String): Int = synchronized(lock) {
        quizStates[quizId]?.attempts
            ?.filter { it.flawless }
            ?.map { VocabMasteryEngine.dayIndex(it.attemptedAt, BuildConfig.DEBUG) }
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
            WordMasteryLevel.Learning -> "Learning" to statusOrange // Orange
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

    companion object {
        /** Recent goes shown on a quiz row = the perfect goes in a row needed for mastery. */
        const val RECENT_RUNS_SHOWN = 3
        /** Flawless goes on this many different days = mastered (row crown, Focus 100%, 3-day award). */
        const val MASTERY_DAYS = 3
        /** Daily goal: finish this many DIFFERENT usage quizzes today. */
        const val DAILY_GOAL = 2

        /** "Just now" (< 10 min), then minutes "50m", hours "3h", days "2d", weeks "3w", years "1y". */
        fun shortAge(elapsedMillis: Long): String {
            val s = maxOf(0L, elapsedMillis) / 1000
            val minute = 60L; val hour = 3_600L; val day = 86_400L; val week = 604_800L; val year = 31_536_000L
            return when {
                s < 10 * minute -> "Just now"
                s < hour -> "${s / minute}m"
                s < day -> "${s / hour}h"
                s < week -> "${s / day}d"
                s < year -> "${s / week}w"
                else -> "${s / year}y"
            }
        }
    }
}