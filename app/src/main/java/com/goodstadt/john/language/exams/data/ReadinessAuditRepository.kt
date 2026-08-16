package com.goodstadt.john.language.exams.data


import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

private val Context.auditDataStore by preferencesDataStore(name = "readiness_audit_prefs")

/** How a single question was answered, for the category tally. */
enum class AnswerOutcome { CORRECT, INCORRECT, DONT_KNOW }

/** Running tally of answers for one grammar category at one CEFR level. */
@Serializable
data class CategoryScore(
    val correct: Int = 0,
    val incorrect: Int = 0,
    val dontKnow: Int = 0
)

/**
 * Persisted state for a single quiz attempt.
 * @param answers Question index -> the option the user locked in for that question (one shot, never overwritten).
 * @param completedAt Epoch millis when every question was answered; null while still in progress.
 */
data class ReadinessQuizAttemptState(
    val answers: Map<Int, String> = emptyMap(),
    val completedAt: Long? = null,
    val totalQuestions: Int? = null
)

@Singleton
class ReadinessAuditRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    // Keys for the 4 parts
    private val KEY_PART_1 = intPreferencesKey("audit_score_1")
    private val KEY_PART_2 = intPreferencesKey("audit_score_2")
    private val KEY_PART_3 = intPreferencesKey("audit_score_3")
    private val KEY_PART_4 = intPreferencesKey("audit_score_4")

    private fun partKey(partIndex: Int) = when (partIndex) {
        1 -> KEY_PART_1; 2 -> KEY_PART_2; 3 -> KEY_PART_3; 4 -> KEY_PART_4; else -> null
    }

    // Which audit version the learner is on (1 = first set, 2 = "New Audit" fresh set, ...).
    // Advanced by the New Audit button; gates whether that button is still enabled.
    private val KEY_AUDIT_VERSION = intPreferencesKey("audit_version")

    val auditVersion: Flow<Int> = context.auditDataStore.data.map { prefs ->
        prefs[KEY_AUDIT_VERSION] ?: 1
    }

    suspend fun saveAuditVersion(version: Int) {
        context.auditDataStore.edit { prefs -> prefs[KEY_AUDIT_VERSION] = version }
    }

    // Number of parts the learner has completed in a version >= 2. Each one is "extra data" that
    // nudges Confidence up a touch. Exposed pre-multiplied as the Confidence bonus (points).
    private val KEY_REDONE_PARTS = intPreferencesKey("audit_redone_parts")

    val confidenceBonus: Flow<Int> = context.auditDataStore.data.map { prefs ->
        (prefs[KEY_REDONE_PARTS] ?: 0) * CONFIDENCE_BONUS_PER_REDONE_PART
    }

    suspend fun incrementRedoneParts() {
        context.auditDataStore.edit { prefs ->
            prefs[KEY_REDONE_PARTS] = (prefs[KEY_REDONE_PARTS] ?: 0) + 1
        }
    }

    // Per-(category, level) correct/incorrect tally, accumulated across every audit answer and
    // every version. Stored as one JSON blob keyed "Category|Level". Feeds the future Priority page.
    private val KEY_CATEGORY_SCORES = stringPreferencesKey("audit_category_scores")

    private fun categoryScoreKey(category: String, level: String) = "$category|$level"

    val categoryScores: Flow<Map<String, CategoryScore>> = context.auditDataStore.data.map { prefs ->
        decodeCategoryScores(prefs[KEY_CATEGORY_SCORES])
    }

    /**
     * Adds one answer to the tally for [category] at [level] and returns the updated score.
     * Accumulates forever (a v2 retake adds fresh data), so ratios shift as the learner improves.
     */
    suspend fun recordCategoryResult(category: String, level: String, outcome: AnswerOutcome): CategoryScore {
        val key = categoryScoreKey(category, level)
        var updated = CategoryScore()
        context.auditDataStore.edit { prefs ->
            val map = decodeCategoryScores(prefs[KEY_CATEGORY_SCORES]).toMutableMap()
            val current = map[key] ?: CategoryScore()
            updated = when (outcome) {
                AnswerOutcome.CORRECT -> current.copy(correct = current.correct + 1)
                AnswerOutcome.INCORRECT -> current.copy(incorrect = current.incorrect + 1)
                AnswerOutcome.DONT_KNOW -> current.copy(dontKnow = current.dontKnow + 1)
            }
            map[key] = updated
            prefs[KEY_CATEGORY_SCORES] = json.encodeToString(map)
        }
        return updated
    }

    /**
     * Mastery clear: a fully-correct quiz run means the learner has (re)mastered [category] at
     * [level], so zero its incorrect / don't-know counts. The [correct] tally is kept, so weakness
     * ([incorrect] + [dontKnow]) drops to 0 and the category leaves the Focus list until the learner
     * slips again. No-op if the category has no entry yet.
     */
    suspend fun clearCategoryWeakness(category: String, level: String) {
        val key = categoryScoreKey(category, level)
        context.auditDataStore.edit { prefs ->
            val map = decodeCategoryScores(prefs[KEY_CATEGORY_SCORES]).toMutableMap()
            val current = map[key] ?: return@edit
            if (current.incorrect == 0 && current.dontKnow == 0) return@edit
            map[key] = current.copy(incorrect = 0, dontKnow = 0)
            prefs[KEY_CATEGORY_SCORES] = json.encodeToString(map)
        }
    }

    private fun decodeCategoryScores(raw: String?): Map<String, CategoryScore> =
        if (raw.isNullOrBlank()) emptyMap()
        else try { json.decodeFromString<Map<String, CategoryScore>>(raw) } catch (e: Exception) { emptyMap() }

    /**
     * DEBUG: dumps the whole accumulated category tally (across Audit + Usage + future quizzes),
     * weakest first. Called at the end of any 10-question quiz. Not shown to the user.
     */
    suspend fun logCategorySummary(source: String) {
        val scores = categoryScores.first()
        Timber.d("AUDIT-CAT-SUMMARY [$source] ---- ${scores.size} (category|level) entries, weakest first ----")
        scores.entries
            // Weakness = things they got wrong OR admitted not knowing.
            .sortedByDescending { it.value.incorrect + it.value.dontKnow }
            .forEach { (key, s) ->
                val total = s.correct + s.incorrect + s.dontKnow
                Timber.d("AUDIT-CAT-SUMMARY   $key -> correct=${s.correct} incorrect=${s.incorrect} dontknow=${s.dontKnow} (of $total)")
            }
    }

    companion object {
        // Highest audit version whose question files exist. New Audit is disabled once reached.
        const val MAX_AUDIT_VERSION = 2
        // Confidence points added per part completed in a fresh (v>=2) audit.
        const val CONFIDENCE_BONUS_PER_REDONE_PART = 2
    }

    val auditScores: Flow<Map<Int, Int>> = context.auditDataStore.data.map { prefs ->
        val scores = mutableMapOf<Int, Int>()
        prefs[KEY_PART_1]?.let { scores[1] = it }
        prefs[KEY_PART_2]?.let { scores[2] = it }
        prefs[KEY_PART_3]?.let { scores[3] = it }
        prefs[KEY_PART_4]?.let { scores[4] = it }
        scores
    }

    private val KEY_CURRENT_PART_INDEX = intPreferencesKey("current_part_index")
    private val KEY_CURRENT_PART_PROGRESS = floatPreferencesKey("current_part_progress")

    // CEFR band the learner was placed into by their banded baseline answers ("A2"/"B1"/"B2").
    // Set when a baseline quiz completes; drives the summary verdict in the base view. Null until
    // a baseline quiz is completed under the banded-scoring build.
    private val KEY_BASELINE_LEVEL = stringPreferencesKey("audit_baseline_level")

    val baselineLevel: Flow<String?> = context.auditDataStore.data.map { prefs ->
        prefs[KEY_BASELINE_LEVEL]
    }

    suspend fun saveBaselineLevel(level: String) {
        context.auditDataStore.edit { prefs ->
            prefs[KEY_BASELINE_LEVEL] = level
        }
    }

    // How far the baseline result unlocks the level tests, as a part index:
    // 1 = baseline only (A2 not mastered), 2 = +A2 test, 3 = +B1 test, 4 = +B2 test.
    // Defaults to 1 (no level tests unlocked) until a baseline quiz is completed.
    private val KEY_BASELINE_UNLOCK_CEILING = intPreferencesKey("audit_baseline_unlock_ceiling")

    val baselineUnlockCeiling: Flow<Int> = context.auditDataStore.data.map { prefs ->
        prefs[KEY_BASELINE_UNLOCK_CEILING] ?: 1
    }

    suspend fun saveBaselineUnlockCeiling(ceiling: Int) {
        context.auditDataStore.edit { prefs ->
            prefs[KEY_BASELINE_UNLOCK_CEILING] = ceiling
        }
    }

    // 🟢 THE SOURCE OF TRUTH: One flow that returns both scores and live progress
    data class AuditData(val scores: Map<Int, Int>, val activeProgress: Map<Int, Float>)

    val currentProgress: Flow<Map<Int, Float>> = context.auditDataStore.data.map { prefs ->
        val part = prefs[KEY_CURRENT_PART_INDEX] ?: 0
        val progress = prefs[KEY_CURRENT_PART_PROGRESS] ?: 0f
        if (part > 0) mapOf(part to progress) else emptyMap()
    }

    val auditDataFlow: Flow<AuditData> = context.auditDataStore.data.map { prefs ->
        val scores = mutableMapOf<Int, Int>()
        prefs[KEY_PART_1]?.let { scores[1] = it }
        prefs[KEY_PART_2]?.let { scores[2] = it }
        prefs[KEY_PART_3]?.let { scores[3] = it }
        prefs[KEY_PART_4]?.let { scores[4] = it }

        val activePart = prefs[KEY_CURRENT_PART_INDEX] ?: 0
        val progressValue = prefs[KEY_CURRENT_PART_PROGRESS] ?: 0f

        val activeMap = if (activePart > 0 && !scores.containsKey(activePart)) {
            mapOf(activePart to progressValue)
        } else {
            emptyMap()
        }

        AuditData(scores, activeMap)
    }

    // And add a reset function if you don't have one
    suspend fun resetAll() {
        context.auditDataStore.edit { it.clear() }
    }

    /**
     * Saves the score for a specific part (1-4).
     */
    /**
     * Records a part's score, keeping only the BEST ever achieved (ratchet). A weaker retake in a
     * later audit version can never lower a part - Readiness only ever holds or improves.
     */
    suspend fun saveScore(partIndex: Int, score: Int) {
        val key = partKey(partIndex) ?: return
        context.auditDataStore.edit { prefs ->
            prefs[key] = maxOf(prefs[key] ?: 0, score)
        }
    }

    /**
     * Returns all stored audit scores as a Map for the AuditEngine.
     */
    suspend fun getAuditScores(): Map<Int, Int> {
        val prefs = context.auditDataStore.data.first()
        val scores = mutableMapOf<Int, Int>()

        prefs[KEY_PART_1]?.let { scores[1] = it }
        prefs[KEY_PART_2]?.let { scores[2] = it }
        prefs[KEY_PART_3]?.let { scores[3] = it }
        prefs[KEY_PART_4]?.let { scores[4] = it }

        return scores
    }

    // MARK: - Per-quiz attempt locking (order enforcement / once-per-day / one-shot answers)

    private fun answersKey(quizKey: String) = stringPreferencesKey("audit_answers_$quizKey")
    private fun completedAtKey(quizKey: String) = longPreferencesKey("audit_completed_at_$quizKey")
    private fun totalQuestionsKey(quizKey: String) = intPreferencesKey("audit_total_$quizKey")

    /**
     * Returns the persisted attempt (locked-in answers + completion time + question count) for
     * a given quiz.
     * @param quizKey Unique key per level+quiz, e.g. "BASELINE_1".
     */
    suspend fun getQuizAttemptState(quizKey: String): ReadinessQuizAttemptState {
        val prefs = context.auditDataStore.data.first()
        val answers = prefs[answersKey(quizKey)]?.let { decodeAnswers(it) } ?: emptyMap()
        val completedAt = prefs[completedAtKey(quizKey)]
        val totalQuestions = prefs[totalQuestionsKey(quizKey)]
        return ReadinessQuizAttemptState(answers = answers, completedAt = completedAt, totalQuestions = totalQuestions)
    }

    /**
     * Records how many questions this quiz has, so Confidence can be computed as a fraction of
     * questions answered even for parts other than the one currently loaded. Safe to call
     * repeatedly - always just overwrites with the latest known count.
     */
    suspend fun saveTotalQuestions(quizKey: String, total: Int) {
        context.auditDataStore.edit { prefs ->
            prefs[totalQuestionsKey(quizKey)] = total
        }
    }

    /**
     * Locks in the user's single answer for a question. Once saved for an index it will be
     * overwritten only by the caller re-saving the same value; callers must not call this twice
     * for the same index with a different answer.
     */
    suspend fun saveAnswer(quizKey: String, questionIndex: Int, selectedOption: String) {
        context.auditDataStore.edit { prefs ->
            val existing = prefs[answersKey(quizKey)]?.let { decodeAnswers(it) } ?: emptyMap()
            val updated = existing + (questionIndex to selectedOption)
            prefs[answersKey(quizKey)] = json.encodeToString(updated)
        }
    }
    suspend fun saveLiveProgress(partIndex: Int, progress: Float) {
        context.auditDataStore.edit { prefs ->
            prefs[KEY_CURRENT_PART_INDEX] = partIndex
            prefs[KEY_CURRENT_PART_PROGRESS] = progress
        }
    }

    /**
     * Marks the quiz as fully completed, locking it until the next calendar day.
     */
    suspend fun markQuizCompleted(quizKey: String, timestamp: Long = System.currentTimeMillis()) {
        context.auditDataStore.edit { prefs ->
            prefs[completedAtKey(quizKey)] = timestamp
            prefs.remove(KEY_CURRENT_PART_INDEX)
            prefs.remove(KEY_CURRENT_PART_PROGRESS)
        }
    }

    /**
     * Wipes a quiz's locked answers/completion so a fresh attempt can start.
     * Used once a completed attempt is detected to be from a previous day.
     */
    suspend fun clearQuizAttempt(quizKey: String) {
        context.auditDataStore.edit { prefs ->
            prefs.remove(answersKey(quizKey))
            prefs.remove(completedAtKey(quizKey))
        }
    }

    /**
     * True if both timestamps fall on the same calendar day (device-local time).
     */
    fun isSameDay(t1: Long, t2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = t1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = t2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun decodeAnswers(jsonString: String): Map<Int, String> {
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyMap()
        }
    }

}
