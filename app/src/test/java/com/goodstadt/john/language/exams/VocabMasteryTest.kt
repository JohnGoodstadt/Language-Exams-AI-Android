package com.goodstadt.john.language.exams

import com.goodstadt.john.language.exams.data.repository.VocabMasteryEngine
import com.goodstadt.john.language.exams.models.VocabLearningState
import com.goodstadt.john.language.exams.models.VocabQuizOutcome
import com.goodstadt.john.language.exams.models.WordMasteryLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Exercises the Vocab Quiz spaced-repetition mastery engine that backs the New / Struggling /
 * Learning / Review / Mastered filter chips. The stats persist, so the same results must hold after
 * leaving and re-entering the quiz.
 *
 * NB: these prove the *engine* (outcome -> mastery) is correct. The live bug the user is seeing is
 * upstream in VocabSectionQuizViewModel: `_currentQuestionAttempts` is incremented twice per wrong
 * tap (once in vocabQuizAttemptStats, again in markAnswerSelected), so a "wrong then correct" answer
 * is scored as 3 tries -> FAILED instead of 2 -> STUMBLED. i.e. the wrong OUTCOME is fed to this
 * (correct) engine. See `secondTryOutcomeShouldBeLearning_notStruggling` below.
 */
class VocabMasteryTest {

    private val now = 1_000_000_000_000L // fixed clock; a fresh word has nextReviewTime 0 (< now)

    private fun fresh(word: String = "acquire") = VocabLearningState(word = word)

    private fun apply(state: VocabLearningState, outcome: VocabQuizOutcome) =
        VocabMasteryEngine.updateMastery(state, outcome, now)

    @Test
    fun `not attempted is New`() {
        assertEquals(WordMasteryLevel.New, fresh().masteryLevel)
    }

    @Test
    fun `right first time goes to Review`() {
        val s = fresh()
        apply(s, VocabQuizOutcome.FLAWLESS)
        assertEquals(WordMasteryLevel.Review, s.masteryLevel)
        assertEquals(1, s.correctStreak)
    }

    @Test
    fun `three spaced-out perfect reviews reach Mastered`() {
        val s = fresh()
        var t = System.currentTimeMillis()
        repeat(3) {
            VocabMasteryEngine.updateMastery(s, VocabQuizOutcome.FLAWLESS, t)
            t = s.nextReviewTime + 1 // fast-forward to when the word is next due
        }
        assertEquals(WordMasteryLevel.Mastered, s.masteryLevel)
    }

    @Test
    fun `answering a word again the same session does not advance the streak (anti-cramming)`() {
        val s = fresh()
        val t = System.currentTimeMillis()
        VocabMasteryEngine.updateMastery(s, VocabQuizOutcome.FLAWLESS, t) // Review, streak 1, due tomorrow
        VocabMasteryEngine.updateMastery(s, VocabQuizOutcome.FLAWLESS, t) // too early -> no change
        assertEquals(WordMasteryLevel.Review, s.masteryLevel)
        assertEquals(1, s.correctStreak)
    }

    @Test
    fun `right but used info is Review`() {
        val s = fresh()
        apply(s, VocabQuizOutcome.ASSISTED)
        assertEquals(WordMasteryLevel.Review, s.masteryLevel)
    }

    @Test
    fun `wrong then correct (STUMBLED) is Learning`() {
        val s = fresh()
        apply(s, VocabQuizOutcome.STUMBLED)
        assertEquals(WordMasteryLevel.Learning, s.masteryLevel)
    }

    @Test
    fun `never correct (FAILED) is Struggling`() {
        val s = fresh()
        apply(s, VocabQuizOutcome.FAILED)
        assertEquals(WordMasteryLevel.Struggling, s.masteryLevel)
    }

    @Test
    fun `a struggling word that is aced recovers to Review`() {
        val s = fresh()
        apply(s, VocabQuizOutcome.FAILED)      // -> Struggling
        apply(s, VocabQuizOutcome.FLAWLESS)    // first-try correct restarts the success ladder
        assertEquals(WordMasteryLevel.Review, s.masteryLevel)
        assertEquals(1, s.correctStreak)
    }

    /**
     * Documents the user's expectation: getting a word right on the SECOND attempt must end up as
     * Learning (via a STUMBLED outcome), NOT Struggling. The engine does this correctly; the live
     * defect is that the ViewModel mis-scores the attempt count and sends FAILED instead of STUMBLED.
     */
    @Test
    fun `secondTryOutcomeShouldBeLearning_notStruggling`() {
        val correctOnSecondTry = fresh()
        apply(correctOnSecondTry, VocabQuizOutcome.STUMBLED)
        assertEquals(WordMasteryLevel.Learning, correctOnSecondTry.masteryLevel)

        // For contrast, the outcome the buggy ViewModel actually records for a 2nd-try answer:
        val whatTheBuggyVmRecords = fresh()
        apply(whatTheBuggyVmRecords, VocabQuizOutcome.FAILED)
        assertEquals(WordMasteryLevel.Struggling, whatTheBuggyVmRecords.masteryLevel)
    }
}
