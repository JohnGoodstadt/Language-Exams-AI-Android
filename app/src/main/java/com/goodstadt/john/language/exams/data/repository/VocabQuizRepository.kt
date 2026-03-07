package com.goodstadt.john.language.exams.data.repository

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.models.VocabLearningState
import com.goodstadt.john.language.exams.models.VocabQuizOutcome
import com.goodstadt.john.language.exams.models.WordMasteryLevel
import com.goodstadt.john.language.exams.models.WordQuizAttempt
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Locale

data class DueItem(val word: String, val category: String)

@Singleton
class VocabQuizRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val fileName = "vocab_quiz_progress.json"
    private val scope = CoroutineScope(Dispatchers.IO)

    // In-Memory Store: Map<Word, State>
    private var wordStates: MutableMap<String, VocabLearningState> = mutableMapOf()

    // Observable State for UI
    private val _quizDataLoaded = MutableStateFlow(false)
    val quizDataLoaded = _quizDataLoaded.asStateFlow()

    // 1. ✅ ADD THIS: A trigger flow
    // replay=1 ensures that if a view subscribes late, it gets the latest signal immediately
    private val _dataUpdateEvents = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(replay = 1)
    val dataUpdateEvents = _dataUpdateEvents.asSharedFlow()

    init {
        loadFromDisk()
    }

    fun updateEvents(){
        _dataUpdateEvents.tryEmit(Unit)
    }
    // MARK: - Public API

    /**
     * Call this after the user finishes a word in the quiz.
     * @param word The word (e.g. "acquire")
     * @param tries How many attempts it took (1 = Perfect)
     */
    fun recordResult(word: String, outcome: VocabQuizOutcome, categoryTitle: String,level:String ) {

        scope.launch {
            val state = wordStates.getOrPut(word) {
                VocabLearningState(word = word, sourceCategory = categoryTitle)
            }
            state.sourceCategory = categoryTitle
            state.lastOutcome = outcome
            state.sourceLevel = level
            // 1. Create History Entry
            // We store the outcome enum directly now, or map it to tries if you prefer
            // Assuming you updated WordQuizAttempt to store 'outcome' or we map it here:
            val attempt = WordQuizAttempt(
                word = word,
                triesNeeded = getTriesFromOutcome(outcome), // Helper to map back to Int for history
                wasCorrectEventually = outcome != VocabQuizOutcome.FAILED
            )
            state.history.add(attempt)

            // 2. Update Algorithm
            updateMasteryLogic(state, outcome)

            // 3. Save
            saveToDisk()

            debugPrintAllWordStates()
        }
    }
    // Updated Getter
    fun getDueItems(limit: Int = 20, levelFilter: String): List<DueItem> {
        val now = System.currentTimeMillis()

        return wordStates.values
            .filter { it.sourceLevel == levelFilter }
            .filter { it.masteryLevel != WordMasteryLevel.Mastered }
            .filter { it.nextReviewTime <= now }
            .sortedBy { it.nextReviewTime }
            .take(limit)
            // Map to our helper struct
            .map { DueItem(it.word, it.sourceCategory) }
    }
    /**
     * Returns a list of words that need to be quizzed right now.
     * Filters out "Mastered" words and words scheduled for the future.
     */
    fun getDueWords(limit: Int = 10, levelFilter: String): List<String> {
        val now = System.currentTimeMillis()

        return wordStates.values
            .filter { it.sourceLevel == levelFilter }
            .filter { it.masteryLevel != WordMasteryLevel.Mastered } // Ignore Mastered
            .filter { it.nextReviewTime <= now } // Only show if due
            .sortedBy { it.nextReviewTime } // Show most overdue first
            .take(limit)
            .map { it.word }
    }

    // MARK: - The "Brain" Logic


    // Requires: import java.time.*
    private fun updateMasteryLogic(state: VocabLearningState, outcome: VocabQuizOutcome) {
        val now = System.currentTimeMillis()

        // 🛑 ANTI-CRAMMING CHECK
        // If it wasn't due yet, and they got it right, don't boost them further.
        if (state.nextReviewTime > now && state.masteryLevel != WordMasteryLevel.Struggling) {
            // Only return if it was a "Success" type outcome.
            // If they failed or stumbled while cramming, we STILL want to downgrade them.
            if (outcome == VocabQuizOutcome.FLAWLESS || outcome == VocabQuizOutcome.ASSISTED) {
                Timber.d("Quiz: User reviewed '${state.word}' too early. Schedule unchanged.")
                return
            }
        }

        when (outcome) {
            // --- PERFECT ---
            VocabQuizOutcome.FLAWLESS -> {
                state.correctStreak++

                if (state.masteryLevel == WordMasteryLevel.Struggling) {
                    // Graduated from struggling
                    state.masteryLevel = WordMasteryLevel.Learning
                    state.nextReviewTime = getFutureMorningTime(1) // Tomorrow
                } else if (state.correctStreak >= 3) {
                    // Mastered
                    state.masteryLevel = WordMasteryLevel.Mastered
                    state.nextReviewTime = Long.MAX_VALUE
                } else {
                    // Standard Review
                    state.masteryLevel = WordMasteryLevel.Review
                    state.nextReviewTime = getFutureMorningTime(state.correctStreak)
                }
            }

            // --- CHEATED / HINTED ---
            VocabQuizOutcome.ASSISTED -> {
                // They got it right, but needed help.
                // Treat as "Learning" (Tomorrow), but don't increase streak.
                state.masteryLevel = WordMasteryLevel.Review
                state.nextReviewTime = getFutureMorningTime(1)
                // Optional: Reset streak or keep it? usually reset or freeze.
                // state.correctStreak = 0
            }

            // --- ALMOST ---
            VocabQuizOutcome.STUMBLED -> {
                state.correctStreak = 0
                state.masteryLevel = WordMasteryLevel.Learning
                // Review later today (e.g. 6 hours)
                state.nextReviewTime = now + (6 * 60 * 60 * 1000L)
            }

            // --- FAILED ---
            VocabQuizOutcome.FAILED -> {
                state.correctStreak = 0
                state.masteryLevel = WordMasteryLevel.Struggling
                // Review immediately (10 mins)
                state.nextReviewTime = now + (10 * 60 * 1000L)
            }
        }

        Timber.d("Quiz: Updated '${state.word}' to ${state.masteryLevel}. Outcome: $outcome")
    }

    // Helper to map Enum -> Int (For your history log)
    private fun getTriesFromOutcome(outcome: VocabQuizOutcome): Int {
        return when(outcome) {
            VocabQuizOutcome.FLAWLESS, VocabQuizOutcome.ASSISTED -> 1
            VocabQuizOutcome.STUMBLED -> 2
            VocabQuizOutcome.FAILED -> 3
        }
    }
    private fun getFutureMorningTime(daysToAdd: Int): Long {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        // Add days to current date
        val targetDate = today.plusDays(daysToAdd.toLong())

        // Set time to 06:00 AM
        val targetDateTime = targetDate.atTime(6, 0) // 06:00

        return targetDateTime.atZone(zone).toInstant().toEpochMilli()
    }

    // MARK: - Persistence

    private fun saveToDisk() {
        try {
            val jsonString = gson.toJson(wordStates)
            File(context.filesDir, fileName).writeText(jsonString)
            // 🔥 SIGNAL THE APP THAT DATA CHANGED
           // _dataUpdateEvents.tryEmit(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to save vocab quiz progress")
        }
    }

    private fun loadFromDisk() {
        scope.launch {
            try {
                val file = File(context.filesDir, fileName)
                if (file.exists()) {
                    val jsonString = file.readText()
                    val type = object : TypeToken<MutableMap<String, VocabLearningState>>() {}.type
                    wordStates = gson.fromJson(jsonString, type)
                }
                _quizDataLoaded.value = true
            } catch (e: Exception) {
                Timber.e(e, "Failed to load vocab quiz progress")
            }
        }
    }

    // MARK: - Debugging

    fun debugPrintAllWordStates() {

        if (!BuildConfig.DEBUG){
            return
        }
        val now = System.currentTimeMillis()
        val tag = "VocabRepo"

        Timber.tag(tag).d("\n🧠 ===== VOCAB QUIZ REPOSITORY STATE =====")

        if (wordStates.isEmpty()) {
            Timber.tag(tag).d("   (No words tracked yet)")
            Timber.tag(tag).d("==========================================\n")
            return
        }

        // 1. Group by Source Level (e.g. "B1", "A2")
        // Use "Unknown" if the data is old and doesn't have a level yet
        val groupedByLevel = wordStates.values.groupBy { it.sourceLevel.ifEmpty { "Unknown" } }

        // 2. Iterate through levels (Sorted alphabetically A1 -> B2)
        groupedByLevel.toSortedMap().forEach { (level, states) ->

            Timber.tag(tag).d("\n📂 === LEVEL: $level ===")

            // Level Summary
            val mastered = states.count { it.masteryLevel == WordMasteryLevel.Mastered }
            val struggling = states.count { it.masteryLevel == WordMasteryLevel.Struggling }
            val due = states.count { it.nextReviewTime <= now && it.masteryLevel != WordMasteryLevel.Mastered }

            Timber.tag(tag).d("   Total: ${states.size} | Mastered: $mastered | Struggling: $struggling | Due Now: $due")

            Timber.tag(tag).d("   ------------------------------------------------------------------------------------------------")
            // Adjusted padding for headers
            Timber.tag(tag).d("   ST | WORD             | STRK | OUTCOME      | DUE IN       | CATEGORY")
            Timber.tag(tag).d("   ------------------------------------------------------------------------------------------------")

            // Sort by Due Date (Overdue first)
            val sortedList = states.sortedBy { it.nextReviewTime }

            // Print Rows
            // Limit to 50 items per level to keep Logcat readable
            sortedList.take(50).forEach { state ->
                val icon = getStatusIcon(state.masteryLevel)
                val word = state.word.take(16).padEnd(16)
                val streak = state.correctStreak.toString().padEnd(4)
                val outcome = (state.lastOutcome?.name ?: "-").take(12).padEnd(12)

                // Fixed width for Time column so Category lines up
                val due = getTimeString(state.nextReviewTime, now).take(12).padEnd(12)
                val cat = state.sourceCategory

                Timber.tag(tag).d("   $icon | $word | $streak | $outcome | $due | $cat")
            }

            if (states.size > 50) {
                Timber.tag(tag).d("   ... and ${states.size - 50} more.")
            }
        }

        Timber.tag(tag).d("\n================================================================================\n")
    }
    // MARK: - Debug Helpers

    private fun getStatusIcon(level: WordMasteryLevel): String {
        return when (level) {
            WordMasteryLevel.New -> "🆕"
            WordMasteryLevel.Struggling -> "🔴"
            WordMasteryLevel.Learning -> "🟠"
            WordMasteryLevel.Review -> "🔵"
            WordMasteryLevel.Mastered -> "🟢"
        }
    }

    private fun getTimeString(dueTime: Long, now: Long): String {
        if (dueTime == Long.MAX_VALUE) return "NEVER"
        if (dueTime == 0L) return "NOW"

        val diff = dueTime - now

        // Handle Overdue/Ready
        if (diff <= 0) return "✅ READY"

        // Format nice relative string
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d ${hours % 24}h"
            hours > 0 -> "${hours}h ${minutes % 60}m"
            else -> "${minutes}m ${seconds % 60}s"
        }
    }

    fun getAllStates(): Map<String, VocabLearningState> {
        // Return a copy (.toMap) to prevent external modification
        return wordStates.toMap()
    }
    // MARK: - Debug / Reset
// In VocabQuizRepository.kt

    fun getWordStats(word: String): VocabLearningState {
        // Return the existing state if we have it,
        // otherwise return a 'New' state so the UI has something to show
        return wordStates[word.lowercase()] ?: VocabLearningState(
            word = word,
            masteryLevel = WordMasteryLevel.New
        )
    }
    fun getFluencyDisplay(level: WordMasteryLevel): Pair<String, Color> {
        return when (level) {
            WordMasteryLevel.New -> "" to Color.Gray //"New Word"
            WordMasteryLevel.Struggling -> "Be careful" to Color.Red
            WordMasteryLevel.Learning -> "Learning" to Color.LightGray// Orange
            WordMasteryLevel.Review -> "Not Quite Fluent (3 correct streaks for fluency)" to Color.Cyan
            WordMasteryLevel.Mastered -> "Mastered" to Color(0xFF4CAF50) // Green
        }
    }
    /**
     * 🚨 DEBUG: Wipes all vocabulary mastery progress.
     * Simulates a fresh install for the Vocab Quiz feature.
     */
    fun debugClearAllProgress() {
        scope.launch {
            // 1. Clear Memory
            wordStates.clear()

            // 2. Clear Disk
            try {
                val file = File(context.filesDir, fileName)
                if (file.exists()) {
                    val deleted = file.delete()
                    if (deleted) {
                        Timber.w("🚨 Vocab Quiz File deleted successfully.")
                    }
                }
                _dataUpdateEvents.tryEmit(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete vocab quiz history")
            }

            Timber.w("🚨 VOCAB QUIZ MEMORY CLEARED (Simulating new user)")
        }
    }

//    fun getFormattedNextReviewTime(word: String): String {
//        return  getFormattedNextReviewTime(word)
//    }
}