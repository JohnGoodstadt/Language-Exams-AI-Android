package com.goodstadt.john.language.exams.data.repository

import com.goodstadt.john.language.exams.models.SrsState
import com.goodstadt.john.language.exams.models.VocabLearningState
import com.goodstadt.john.language.exams.models.VocabQuizOutcome
import com.goodstadt.john.language.exams.models.WordMasteryLevel

/**
 * The spaced-repetition "brain" shared by the Vocab Quiz (keyed by word) and the Usage Quiz (keyed
 * by page). Applies one answer [outcome] to an item's [SrsState], updating its mastery level,
 * streak and next-review time.
 *
 * Pure and time-injectable ([now]) so it can be unit-tested without Android / coroutines / disk.
 *
 * Forgetting-curve intervals (a "session" = leaving the quiz and coming back later; because an item
 * is hidden until [SrsState.nextReviewTime] passes, a streak can only advance in a later session):
 *   FAILED   (never right)      -> Struggling, due in ~10 min
 *   STUMBLED (right, 2nd+ try)  -> Learning,   due in ~20 min
 *   ASSISTED (right, used info) -> Review,     due in ~20 min (streak frozen)
 *   FLAWLESS (right, 1st try)   -> Review,     due in 20 min (streak 1) then 6 h (streak 2),
 *                                  then Mastered at streak 3 (a final check 1 day out)
 * Mastery therefore means: correct, first-try, in 3 separate spaced sessions.
 */
object VocabMasteryEngine {

    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
    private const val DAY = 24 * HOUR

    private const val FAILED_INTERVAL = 10 * MINUTE
    private const val STUMBLED_INTERVAL = 20 * MINUTE
    private const val ASSISTED_INTERVAL = 20 * MINUTE
    private const val MASTERED_INTERVAL = DAY // final confirmation review once mastered

    /** Interval after the Nth consecutive first-try success, indexed by (streak - 1). */
    private val SUCCESS_LADDER = longArrayOf(20 * MINUTE, 6 * HOUR)

    const val MASTERED_STREAK = 3

    /**
     * VOCAB QUIZ rule (same as iOS VocabQuizRepository.updateMasteryLogic): Mastered = right FIRST TRY on
     * 3 DIFFERENT DAYS. One first-try success counts per word per calendar day ([VocabLearningState.lastSuccessAt]),
     * so the days are always separate - and a retry right after a wrong answer counts straight away, provided
     * that word hasn't already earned today. A wrong answer never loses the days already earned.
     *   FLAWLESS -> if not already earned today: streak+1 (at 3 Mastered, never due again), else Review due 6am tomorrow
     *   ASSISTED -> Review, due 6am tomorrow (no day earned)
     *   STUMBLED -> Learning, due in 6 h     (streak kept)
     *   FAILED   -> Struggling, due in 10 min (streak kept)
     * [debug]: a "day" is 30 s so the ladder can be tested quickly, as on iOS. Usage Quiz keeps [updateMastery].
     */
    fun updateVocabMasteryDaily(state: VocabLearningState, outcome: VocabQuizOutcome, now: Long, debug: Boolean) {
        when (outcome) {
            VocabQuizOutcome.FLAWLESS -> {
                val earnedToday = state.lastSuccessAt > 0 && dayIndex(state.lastSuccessAt, debug) == dayIndex(now, debug)
                if (!earnedToday) {
                    state.correctStreak++
                    state.lastSuccessAt = now
                }
                if (state.correctStreak >= MASTERED_STREAK) {
                    state.masteryLevel = WordMasteryLevel.Mastered
                    state.nextReviewTime = Long.MAX_VALUE
                } else {
                    state.masteryLevel = WordMasteryLevel.Review
                    state.nextReviewTime = nextDay(now, debug)
                }
            }
            VocabQuizOutcome.ASSISTED -> {
                if (state.masteryLevel != WordMasteryLevel.Mastered) {
                    state.masteryLevel = WordMasteryLevel.Review
                    state.nextReviewTime = nextDay(now, debug)
                }
            }
            VocabQuizOutcome.STUMBLED -> {
                state.masteryLevel = WordMasteryLevel.Learning
                state.nextReviewTime = now + if (debug) MINUTE else 6 * HOUR
            }
            VocabQuizOutcome.FAILED -> {
                state.masteryLevel = WordMasteryLevel.Struggling
                state.nextReviewTime = now + if (debug) 10_000L else 10 * MINUTE
            }
        }
    }

    /** Calendar day number in local time (debug: 30 s "days"). Shared with the per-category day count. */
    fun dayIndex(t: Long, debug: Boolean): Long {
        if (debug) return t / 30_000L
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = t }
        return cal.get(java.util.Calendar.YEAR) * 1000L + cal.get(java.util.Calendar.DAY_OF_YEAR)
    }

    /** 6am local time tomorrow (debug: +30 s), when a word can next earn a day. */
    private fun nextDay(now: Long, debug: Boolean): Long {
        if (debug) return now + 30_000L
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 6)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun updateMastery(state: SrsState, outcome: VocabQuizOutcome, now: Long) {
        // Anti-cramming: if the item wasn't due yet and they got it right, don't advance the
        // schedule (repeating a card in the same session must not count as a new session).
        if (state.nextReviewTime > now && state.masteryLevel != WordMasteryLevel.Struggling) {
            if (outcome == VocabQuizOutcome.FLAWLESS || outcome == VocabQuizOutcome.ASSISTED) {
                return
            }
        }

        when (outcome) {
            VocabQuizOutcome.FLAWLESS -> {
                state.correctStreak++
                if (state.correctStreak >= MASTERED_STREAK) {
                    state.masteryLevel = WordMasteryLevel.Mastered
                    state.nextReviewTime = now + MASTERED_INTERVAL
                } else {
                    state.masteryLevel = WordMasteryLevel.Review
                    val rung = (state.correctStreak - 1).coerceIn(0, SUCCESS_LADDER.lastIndex)
                    state.nextReviewTime = now + SUCCESS_LADDER[rung]
                }
            }

            VocabQuizOutcome.ASSISTED -> {
                // Got it right, but needed help: keep the streak, review again soon.
                state.masteryLevel = WordMasteryLevel.Review
                state.nextReviewTime = now + ASSISTED_INTERVAL
            }

            VocabQuizOutcome.STUMBLED -> {
                state.correctStreak = 0
                state.masteryLevel = WordMasteryLevel.Learning
                state.nextReviewTime = now + STUMBLED_INTERVAL
            }

            VocabQuizOutcome.FAILED -> {
                state.correctStreak = 0
                state.masteryLevel = WordMasteryLevel.Struggling
                state.nextReviewTime = now + FAILED_INTERVAL
            }
        }
    }
}
