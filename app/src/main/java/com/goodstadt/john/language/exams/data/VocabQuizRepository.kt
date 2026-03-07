package com.goodstadt.john.language.exams.data

import android.content.Context
import com.goodstadt.john.language.exams.models.VocabLearningState
import com.goodstadt.john.language.exams.models.VocabQuizOutcome
import com.goodstadt.john.language.exams.models.WordMasteryLevel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Calendar
/*

!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
!! NOTE: IS THIS A DUPLICATE !!!
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

 */
@Singleton
class VocabQuizRepositoryDuplicate @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val fileName = "vocab_quiz_learning_state.json"
    private val scope = CoroutineScope(Dispatchers.IO)

    // In-memory cache: Map<Word, State>
    private var learningStates: MutableMap<String, VocabLearningState> = mutableMapOf()

//    init {
//        loadFromDisk()
//    }

    // MARK: - Public API

    /**
     * Process the result of a specific word quiz.
     * @param word The unique word (key).
     * @param outcome The result based on tries and info button usage.
     */
    /*
    fun recordResult(word: String, outcome: VocabQuizOutcome) {
        scope.launch {
            val state = learningStates.getOrPut(word) { VocabLearningState(word) }
            state.lastOutcome = outcome

            // CALCULATE NEW SCHEDULE
            val now = System.currentTimeMillis()

            // Helper constants
            val oneHour = 3600 * 1000L
            val oneDay = 24 * oneHour

            when (outcome) {
                // Scenario 1: Correct 1st time, no help.
                // Action: Mark as Mastered (Exit the loop).
                VocabQuizOutcome.FLAWLESS -> {
                    state.masteryLevel = WordMasteryLevel.Mastered
                    state.streak += 1
                    // Set to far future (or handle "Random Review" logic later)
                    state.nextReviewTimestamp = Long.MAX_VALUE
                }

                // Scenario 2: Correct 1st time, but used Info (Cheated).
                // Action: Treat as new/learning. Standard Forgetting Curve.
                VocabQuizOutcome.ASSISTED -> {
                    state.masteryLevel = MasteryLevel.Review
                    // If they have a streak, keep it but reduce interval growth?
                    // For simplicity: Reset to 1 day if they needed help.
                    state.nextReviewTimestamp = getNextMorning(daysToAdd = 1)
                }

                // Scenario 3: 1 Wrong answer, then Correct.
                // Action: Short review (Sooner than tomorrow).
                VocabQuizOutcome.STUMBLED -> {
                    state.masteryLevel = MasteryLevel.Learning
                    state.streak = 0 // Reset streak
                    // Show again in 4-6 hours (same day review)
                    state.nextReviewTimestamp = now + (6 * oneHour)
                }

                // Scenario 4: Multiple wrongs or gave up.
                // Action: Deep Failure. Immediate review.
                VocabQuizOutcome.FAILED -> {
                    state.masteryLevel = MasteryLevel.Learning
                    state.streak = 0
                    // Show again in 10 minutes (or next session)
                    state.nextReviewTimestamp = now + (10 * 60 * 1000L)
                }
            }

            Timber.i("Vocab Update: '$word' -> ${state.masteryLevel} (Due: ${java.util.Date(state.nextReviewTimestamp)})")
            saveToDisk()
        }
    }

     */

    /**
     * Get words that need to be tested right now.
     * Use this to populate the Quiz Screen.
     */
//    fun getDueWords(limit: Int = 10): List<String> {
//        val now = System.currentTimeMillis()
//
//        return learningStates.values
//            .filter { it.masteryLevel != MasteryLevel.Mastered } // Exclude mastered
//            .filter { it.nextReviewTimestamp <= now }            // Only if due
//            .sortedBy { it.nextReviewTimestamp }                 // Most overdue first
//            .take(limit)
//            .map { it.word }
//    }

    // MARK: - Helpers

    // Calculates "6:00 AM" on X days from now
//    private fun getNextMorning(daysToAdd: Int): Long {
//        val cal = Calendar.getInstance()
//        cal.add(Calendar.DAY_OF_YEAR, daysToAdd)
//        cal.set(Calendar.HOUR_OF_DAY, 6)
//        cal.set(Calendar.MINUTE, 0)
//        cal.set(Calendar.SECOND, 0)
//        return cal.timeInMillis
//    }
//
//    // MARK: - Persistence
//
//    private fun saveToDisk() {
//        try {
//            val jsonString = gson.toJson(learningStates)
//            File(context.filesDir, fileName).writeText(jsonString)
//        } catch (e: Exception) {
//            Timber.e(e, "Failed to save vocab states")
//        }
//    }

//    private fun loadFromDisk() {
//        scope.launch {
//            try {
//                val file = File(context.filesDir, fileName)
//                if (file.exists()) {
//                    val type = object : TypeToken<MutableMap<String, VocabLearningState>>() {}.type
//                    learningStates = gson.fromJson(file.readText(), type)
//                }
//            } catch (e: Exception) {
//                Timber.e(e, "Failed to load vocab states")
//            }
//        }
//    }



}