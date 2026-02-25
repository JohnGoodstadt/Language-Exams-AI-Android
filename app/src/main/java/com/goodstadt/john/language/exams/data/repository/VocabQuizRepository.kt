package com.goodstadt.john.language.exams.data.repository

import android.content.Context
import com.goodstadt.john.language.exams.models.VocabQuizOutcome
import com.goodstadt.john.language.exams.models.WordLearningState
import com.goodstadt.john.language.exams.models.WordMasteryLevel
import com.goodstadt.john.language.exams.models.WordQuizAttempt
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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

@Singleton
class VocabQuizRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val fileName = "vocab_quiz_progress.json"
    private val scope = CoroutineScope(Dispatchers.IO)

    // In-Memory Store: Map<Word, State>
    private var wordStates: MutableMap<String, WordLearningState> = mutableMapOf()

    // Observable State for UI
    private val _quizDataLoaded = MutableStateFlow(false)
    val quizDataLoaded = _quizDataLoaded.asStateFlow()

    init {
        loadFromDisk()
    }

    // MARK: - Public API

    /**
     * Call this after the user finishes a word in the quiz.
     * @param word The word (e.g. "acquire")
     * @param tries How many attempts it took (1 = Perfect)
     */
    fun recordResult(word: String, outcome: VocabQuizOutcome) {

        scope.launch {
            val state = wordStates.getOrPut(word) { WordLearningState(word) }
            state.lastOutcome = outcome

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

    /**
     * Returns a list of words that need to be quizzed right now.
     * Filters out "Mastered" words and words scheduled for the future.
     */
    fun getDueWords(limit: Int = 20): List<String> {
        val now = System.currentTimeMillis()

        return wordStates.values
            .filter { it.masteryLevel != WordMasteryLevel.Mastered } // Ignore Mastered
            .filter { it.nextReviewTime <= now } // Only show if due
            .sortedBy { it.nextReviewTime } // Show most overdue first
            .take(limit)
            .map { it.word }
    }

    // MARK: - The "Brain" Logic

    private fun updateMasteryLogicOriginal(state: WordLearningState, tries: Int) {
        val oneDay = 24 * 60 * 60 * 1000L

        if (tries == 1) {
            // --- SUCCESS (First Try) ---
            state.correctStreak++

            when {
                // If they were struggling, move them to Learning
                state.masteryLevel == WordMasteryLevel.Struggling -> {
                    state.masteryLevel = WordMasteryLevel.Learning
                    state.nextReviewTime = System.currentTimeMillis() + (oneDay * 1) // Review tomorrow
                }
                // If they hit a streak of 3, mark Mastered
                state.correctStreak >= 3 -> {
                    state.masteryLevel = WordMasteryLevel.Mastered
                    state.nextReviewTime = Long.MAX_VALUE // Never again
                }
                // Standard progression
                else -> {
                    state.masteryLevel = WordMasteryLevel.Review
                    // Spaced Repetition: Delay grows with streak (1 day, 3 days, 7 days...)
                    state.nextReviewTime = System.currentTimeMillis() + (oneDay * state.correctStreak)
                }
            }
        } else {
            // --- FAILURE (Multiple Tries) ---
            state.correctStreak = 0 // Reset streak

            if (tries >= 3) {
                // Severe struggle
                state.masteryLevel = WordMasteryLevel.Struggling
                state.nextReviewTime = System.currentTimeMillis() + (10 * 60 * 1000L) // Review in 10 minutes (Next session)
            } else {
                // Mild struggle (2 tries)
                state.masteryLevel = WordMasteryLevel.Learning
                state.nextReviewTime = System.currentTimeMillis() + (oneDay / 2) // Review in 12 hours
            }
        }

        Timber.d("Quiz: Updated '${state.word}' to ${state.masteryLevel}. Next review in ${(state.nextReviewTime - System.currentTimeMillis()) / 1000}s")
    }
    private fun updateMasteryLogicNextOriginal(state: WordLearningState, tries: Int) {
        val now = System.currentTimeMillis()

        if (tries == 1) {
            // --- SUCCESS (First Try) ---

            // 🛑 ANTI-CRAMMING CHECK
            if (state.nextReviewTime > now && state.masteryLevel != WordMasteryLevel.Struggling) {
                Timber.d("Quiz: User reviewed '${state.word}' too early (Cramming). Keeping existing schedule.")
                return
            }

            state.correctStreak++

            when {
                // Escaping "Struggling" -> Review Tomorrow Morning (6 AM)
                state.masteryLevel == WordMasteryLevel.Struggling -> {
                    state.masteryLevel = WordMasteryLevel.Learning
                    state.nextReviewTime = getFutureMorningTime(1)
                }
                // Graduation -> Done
                state.correctStreak >= 3 -> {
                    state.masteryLevel = WordMasteryLevel.Mastered
                    state.nextReviewTime = Long.MAX_VALUE
                }
                // Normal Progression -> Review in X Mornings
                else -> {
                    state.masteryLevel = WordMasteryLevel.Review
                    // Streak 1 = Tomorrow 6am
                    // Streak 2 = 2 days from now 6am
                    state.nextReviewTime = getFutureMorningTime(state.correctStreak)
                }
            }
        } else {
            // --- FAILURE (Multiple Tries) ---

            state.correctStreak = 0 // Reset streak

            if (tries >= 3) {
                // Hard Fail: Review in 10 mins (Immediate repair)
                state.masteryLevel = WordMasteryLevel.Struggling
                state.nextReviewTime = now + (10 * 60 * 1000L)
            } else {
                // Soft Fail: Review Tomorrow Morning
                // (Even a soft fail usually benefits from a sleep cycle)
                state.masteryLevel = WordMasteryLevel.Learning
                state.nextReviewTime = getFutureMorningTime(1)
            }
        }

        Timber.d("Quiz: Updated '${state.word}' to ${state.masteryLevel}. New time: ${java.util.Date(state.nextReviewTime)}")
    }
    // Requires: import java.time.*
    private fun updateMasteryLogic(state: WordLearningState, outcome: VocabQuizOutcome) {
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

//    private fun getTimeString(dueTime: Long, now: Long): String {
//        // 1. Handle Special States
//        if (dueTime == Long.MAX_VALUE) return "NEVER (Mastered)"
//        if (dueTime <= now) return "✅ READY NOW"
//
//        // 2. Convert timestamps to Calendar Dates (Local Timezone)
//        val zone = ZoneId.systemDefault()
//        val dueDate = Instant.ofEpochMilli(dueTime).atZone(zone).toLocalDate()
//        val todayDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
//
//        // 3. Calculate difference in whole days
//        val daysBetween = ChronoUnit.DAYS.between(todayDate, dueDate)
//
//        // 4. Return Human Readable String
//        return when (daysBetween) {
//            0L -> "Today (Later)" // e.g. if pushed to 10 mins from now
//            1L -> "Tomorrow"      // Replaces "11h 40m"
//            2L -> "Day after Tmrw"
//            in 3L..6L -> dueDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) // e.g. "Monday"
//            else -> "$daysBetween Days" // e.g. "7 Days"
//        }
//    }
    // MARK: - Persistence

    private fun saveToDisk() {
        try {
            val jsonString = gson.toJson(wordStates)
            File(context.filesDir, fileName).writeText(jsonString)
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
                    val type = object : TypeToken<MutableMap<String, WordLearningState>>() {}.type
                    wordStates = gson.fromJson(jsonString, type)
                }
                _quizDataLoaded.value = true
            } catch (e: Exception) {
                Timber.e(e, "Failed to load vocab quiz progress")
            }
        }
    }
    // MARK: - Debugging

    fun debugPrintStatus() {
        val now = System.currentTimeMillis()

        // Sort: Overdue/Due first, then future, Mastered last
        val sortedList = wordStates.values.sortedBy { it.nextReviewTime }

        val dueCount = sortedList.count { it.nextReviewTime <= now && it.masteryLevel != WordMasteryLevel.Mastered }
        val masteredCount = sortedList.count { it.masteryLevel == WordMasteryLevel.Mastered }

        Timber.tag("VocabBrain").d("\n🧠 ===== VOCAB BRAIN REPORT =====")
        Timber.tag("VocabBrain").d("   Total Tracked: ${wordStates.size}")
        Timber.tag("VocabBrain").d("   🔥 Due Now:      $dueCount")
        Timber.tag("VocabBrain").d("   🎓 Mastered:     $masteredCount")
        Timber.tag("VocabBrain").d("----------------------------------------------------------------")
        Timber.tag("VocabBrain").d("   STATUS | WORD             | STREAK | DUE IN")
        Timber.tag("VocabBrain").d("----------------------------------------------------------------")

        if (sortedList.isEmpty()) {
            Timber.tag("VocabBrain").d("   (No data yet)")
        }

        // Print top 20 items (to avoid flooding logs if list is huge)
        sortedList.take(30).forEach { state ->
            val timeString = getTimeString(state.nextReviewTime, now)
            val icon = getStatusIcon(state.masteryLevel)

            // Padding for clean columns
            val wordPad = state.word.take(16).padEnd(16)

            Timber.tag("VocabBrain").d("   $icon     | $wordPad |   ${state.correctStreak}    | $timeString")
        }

        if (sortedList.size > 30) {
            Timber.tag("VocabBrain").d("   ... and ${sortedList.size - 30} more.")
        }
        Timber.tag("VocabBrain").d("================================================================\n")
    }

    // Helpers for the debug print
    private fun getStatusIconObsolete(level: WordMasteryLevel): String {
        return when (level) {
            WordMasteryLevel.New -> "🆕"
            WordMasteryLevel.Struggling -> "🔴" // Warning
            WordMasteryLevel.Learning -> "🟠" // In progress
            WordMasteryLevel.Review -> "🔵" // Good
            WordMasteryLevel.Mastered -> "🟢" // Done
        }
    }

    private fun getTimeStringObsolete(dueTime: Long, now: Long): String {
        if (dueTime == Long.MAX_VALUE) return "NEVER (Mastered)"

        val diff = dueTime - now
        if (diff <= 0) return "✅ READY NOW"

        // Format milliseconds into readable time
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
    // MARK: - Debugging

    fun debugPrintAllWordStates() {
        val now = System.currentTimeMillis()
        val tag = "VocabRepo"

        Timber.tag(tag).d("\n🧠 ===== VOCAB QUIZ REPOSITORY STATE =====")

        if (wordStates.isEmpty()) {
            Timber.tag(tag).d("   (No words tracked yet)")
            Timber.tag(tag).d("==========================================\n")
            return
        }

        // 1. Summary Counts
        val counts = wordStates.values.groupingBy { it.masteryLevel }.eachCount()
        Timber.tag(tag).d("📊 SUMMARY:")
        Timber.tag(tag).d("   🆕 New:        ${counts[WordMasteryLevel.New] ?: 0}")
        Timber.tag(tag).d("   🟠 Learning:   ${counts[WordMasteryLevel.Learning] ?: 0}")
        Timber.tag(tag).d("   🔵 Review:     ${counts[WordMasteryLevel.Review] ?: 0}")
        Timber.tag(tag).d("   🔴 Struggling: ${counts[WordMasteryLevel.Struggling] ?: 0}")
        Timber.tag(tag).d("   🟢 Mastered:   ${counts[WordMasteryLevel.Mastered] ?: 0}")

        Timber.tag(tag).d("--------------------------------------------------------------------------------")
        Timber.tag(tag).d("   LVL | WORD             | STRK | LAST OUTCOME | DUE IN")
        Timber.tag(tag).d("--------------------------------------------------------------------------------")

        // 2. Sort by Next Review Time (Overdue first)
        val sortedList = wordStates.values.sortedBy { it.nextReviewTime }

        // 3. Print Rows
        sortedList.forEach { state ->
            val icon = getStatusIcon(state.masteryLevel)
            val word = state.word.take(16).padEnd(16) // Padding for alignment
            val streak = state.correctStreak.toString().padEnd(4)
            val outcome = (state.lastOutcome?.name ?: "-").take(12).padEnd(12)
            val due = getTimeString(state.nextReviewTime, now)

            Timber.tag(tag).d("   $icon | $word | $streak | $outcome | $due")
        }

        Timber.tag(tag).d("================================================================================\n")
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
}