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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

private val Context.auditDataStore by preferencesDataStore(name = "readiness_audit_prefs")

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
    suspend fun saveScore(partIndex: Int, score: Int) {
        context.auditDataStore.edit { prefs ->
            when (partIndex) {
                1 -> prefs[KEY_PART_1] = score
                2 -> prefs[KEY_PART_2] = score
                3 -> prefs[KEY_PART_3] = score
                4 -> prefs[KEY_PART_4] = score
            }
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
     * @param quizKey Unique key per level+quiz, e.g. "ELEMENTARY_1".
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
