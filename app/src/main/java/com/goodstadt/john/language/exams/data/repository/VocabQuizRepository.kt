package com.goodstadt.john.language.exams.data.repository

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.models.CategoryMasteryLevel
import com.goodstadt.john.language.exams.models.CategoryMasteryState
import com.goodstadt.john.language.exams.models.CategoryQuizAttempt
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

    // Whole-quiz (category) rolled-up status, keyed by "$level|$category". Persisted so a future dashboard
    // can list quizzes and their status/counts. Updated whenever a word in the category is answered.
    private val categoryFileName = "vocab_category_progress.json"
    private var categoryStates: MutableMap<String, CategoryMasteryState> = mutableMapOf()

    // The full word list of each registered quiz (in-memory), so the whole-quiz status can tell "all
    // Mastered" vs "some words never attempted". Registered when a quiz loads (see registerCategoryWords).
    private val categoryWordLists: MutableMap<String, MutableList<String>> = mutableMapOf()

    // Dated history of every whole-quiz go, keyed by "$level|$category" -> list of attempts (oldest first).
    // Persisted so repeated goes at the same quiz can be reported over time.
    private val categoryAttemptsFileName = "vocab_category_attempts.json"
    private var categoryAttempts: MutableMap<String, MutableList<CategoryQuizAttempt>> = mutableMapOf()

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

            // 3b. Roll the word's new status up into the whole-quiz (category) status.
            recomputeCategoryStatus(categoryTitle, level)

            // 4. Notify listeners (e.g. dashboard) so they refresh
            _dataUpdateEvents.tryEmit(Unit)

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
        // The spaced-repetition transition lives in the pure, unit-tested VocabMasteryEngine.
        VocabMasteryEngine.updateMastery(state, outcome, System.currentTimeMillis())
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

                // Whole-quiz (category) statuses.
                val catFile = File(context.filesDir, categoryFileName)
                if (catFile.exists()) {
                    val catType = object : TypeToken<MutableMap<String, CategoryMasteryState>>() {}.type
                    categoryStates = gson.fromJson(catFile.readText(), catType) ?: mutableMapOf()
                }

                // Dated whole-quiz attempt history.
                val attemptsFile = File(context.filesDir, categoryAttemptsFileName)
                if (attemptsFile.exists()) {
                    val attemptsType =
                        object : TypeToken<MutableMap<String, MutableList<CategoryQuizAttempt>>>() {}.type
                    categoryAttempts = gson.fromJson(attemptsFile.readText(), attemptsType) ?: mutableMapOf()
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

    // MARK: - Whole-quiz (Category) status

    private fun categoryKey(category: String, level: String) = "$level|$category"

    /**
     * Roll a set of per-word [WordMasteryLevel]s up into ONE whole-quiz status. "Worst-attention wins",
     * except Mastered which requires EVERY word. Unattempted words should be included as New by the caller.
     */
    private fun aggregateCategory(levels: List<WordMasteryLevel>): CategoryMasteryLevel = when {
        levels.isEmpty() -> CategoryMasteryLevel.New
        levels.all { it == WordMasteryLevel.Mastered } -> CategoryMasteryLevel.Mastered
        levels.any { it == WordMasteryLevel.Struggling } -> CategoryMasteryLevel.Struggling
        levels.any { it == WordMasteryLevel.Learning } -> CategoryMasteryLevel.Learning
        levels.any { it == WordMasteryLevel.Review } -> CategoryMasteryLevel.Review
        else -> CategoryMasteryLevel.New
    }

    /** A word's current mastery level (New if unattempted), via the case-tolerant [getWordStats] lookup. */
    private fun masteryLevelFor(word: String): WordMasteryLevel = getWordStats(word).masteryLevel

    /**
     * Pure aggregation for callers that already hold the full word list (e.g. a live screen). Reads each
     * word's current status (defaults to New if unattempted) and rolls them up.
     */
    fun getCategoryMastery(allWords: List<String>): CategoryMasteryLevel =
        aggregateCategory(allWords.map { masteryLevelFor(it) })

    /**
     * Register the FULL word list of a quiz so its whole-quiz status (and totals) can be computed
     * accurately - including "all Mastered" and "how many still New". Call this when a section quiz loads.
     * Computing here also establishes the category's status/counts before the user answers anything.
     */
    fun registerCategoryWords(category: String, level: String, words: List<String>) {
        if (category.isBlank() || words.isEmpty()) return
        categoryWordLists[categoryKey(category, level)] = words.toMutableList()
        recomputeCategoryStatus(category, level)
    }

    /** Recompute + persist the rolled-up status (and counts) for one category from its registered words. */
    private fun recomputeCategoryStatus(category: String, level: String) {
        val words = categoryWordLists[categoryKey(category, level)] ?: return
        val levels = words.map { masteryLevelFor(it) }
        categoryStates[categoryKey(category, level)] = CategoryMasteryState(
            category = category,
            level = level,
            status = aggregateCategory(levels),
            total = words.size,
            newCount = levels.count { it == WordMasteryLevel.New },
            struggling = levels.count { it == WordMasteryLevel.Struggling },
            learning = levels.count { it == WordMasteryLevel.Learning },
            review = levels.count { it == WordMasteryLevel.Review },
            mastered = levels.count { it == WordMasteryLevel.Mastered },
            updatedAt = System.currentTimeMillis()
        )
        saveCategoriesToDisk()
    }

    /** Persisted whole-quiz status for one quiz, or null if it has never been registered/played. */
    fun getCategoryMasteryState(category: String, level: String): CategoryMasteryState? =
        categoryStates[categoryKey(category, level)]

    /** All persisted whole-quiz statuses - for a future dashboard listing every quiz with a status dot. */
    fun getAllCategoryMasteryStates(): List<CategoryMasteryState> = categoryStates.values.toList()

    // MARK: - Whole-quiz attempts (dated history of each go)

    /** Append one dated whole-quiz attempt (a single go) and persist it. Kept separate per go. */
    fun recordCategoryAttempt(attempt: CategoryQuizAttempt) {
        if (attempt.category.isBlank()) return
        categoryAttempts.getOrPut(categoryKey(attempt.category, attempt.level)) { mutableListOf() }
            .add(attempt)
        saveCategoryAttemptsToDisk()
        _dataUpdateEvents.tryEmit(Unit)
    }

    /** Every dated attempt at one quiz, oldest first. */
    fun getCategoryAttempts(category: String, level: String): List<CategoryQuizAttempt> =
        categoryAttempts[categoryKey(category, level)]?.toList() ?: emptyList()

    /** All attempts across all quizzes (keyed by "$level|$category") - for a future dashboard. */
    fun getAllCategoryAttempts(): Map<String, List<CategoryQuizAttempt>> =
        categoryAttempts.mapValues { it.value.toList() }

    // Star streak: one gold star per flawless (no-error) whole-quiz completion, each counted completion at
    // least a "day" apart from the previous one, up to 3. RELEASE: a real day (24h). DEBUG: 30s stands in
    // for a day so the stars can be earned and tested within a couple of minutes.
    private val streakGapMs: Long = if (BuildConfig.DEBUG) 30_000L else 24L * 60L * 60L * 1000L

    /**
     * The CURRENT flawless streak for a section quiz, as a star count (0..3): consecutive flawless
     * completions from the most recent attempt backwards, each counted only when ~a day apart
     * ([streakGapMs]). Returns 0 the moment the latest attempt has an error, so the row shows the status
     * dots instead of stars (they're mutually exclusive). "flawless" = every question right, one tap each.
     */
    fun flawlessStreakStars(category: String, level: String): Int {
        val attempts = getCategoryAttempts(category, level).sortedBy { it.attemptedAt }
        if (attempts.isEmpty() || !attempts.last().flawless) return 0 // never taken, or last go had errors

        var stars = 0
        var nextCountedAt = Long.MAX_VALUE
        for (a in attempts.asReversed()) {
            if (!a.flawless) break // a faulty go breaks the streak
            if (stars == 0 || nextCountedAt - a.attemptedAt >= streakGapMs) {
                stars++
                nextCountedAt = a.attemptedAt
                if (stars >= 3) break
            }
        }
        return stars
    }

    private fun saveCategoryAttemptsToDisk() {
        try {
            File(context.filesDir, categoryAttemptsFileName).writeText(gson.toJson(categoryAttempts))
        } catch (e: Exception) {
            Timber.e(e, "Failed to save vocab category attempts")
        }
    }

    private fun saveCategoriesToDisk() {
        try {
            File(context.filesDir, categoryFileName).writeText(gson.toJson(categoryStates))
        } catch (e: Exception) {
            Timber.e(e, "Failed to save vocab category progress")
        }
    }
    // MARK: - Debug / Reset
// In VocabQuizRepository.kt

    fun getWordStats(word: String): VocabLearningState {
        // recordResult stores under the RAW word, so look that up first; fall back to a lowercase key for any
        // legacy lowercase-stored entries. (Looking up only the lowercase key missed every capitalised word -
        // e.g. German nouns - so they always read back as New and the mastery filter chips saw nothing.)
        // Otherwise return a 'New' state so the UI has something to show.
        return wordStates[word] ?: wordStates[word.lowercase()] ?: VocabLearningState(
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