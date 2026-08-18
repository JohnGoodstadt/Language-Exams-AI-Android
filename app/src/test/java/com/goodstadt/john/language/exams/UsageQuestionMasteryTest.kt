package com.goodstadt.john.language.exams

import com.goodstadt.john.language.exams.data.repository.VocabMasteryEngine
import com.goodstadt.john.language.exams.models.UsageMastery
import com.goodstadt.john.language.exams.models.UsageQuestionStat
import com.goodstadt.john.language.exams.models.VocabQuizOutcome
import com.goodstadt.john.language.exams.models.WordMasteryLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Proves the per-question mastery used by the Usage Quiz filter now uses the SAME spaced-repetition
 * model as the Vocab Quiz (New / Struggling / Learning / Review / Mastered). A [UsageQuestionStat] is
 * an [com.goodstadt.john.language.exams.models.SrsState], so it is driven by the shared
 * [VocabMasteryEngine] exactly as a word is. Mastery = correct first-try in 3 separate spaced sessions.
 *
 * Encodes what the user expects for one visit to a quiz:
 *   - not attempted                 -> New
 *   - correct on the 1st try        -> Review (on the way to Mastered)
 *   - correct but used the Info hint -> Review
 *   - wrong, then correct           -> Learning
 *   - wrong (never correct)         -> Struggling
 *
 * The stats persist, so the same results must hold after leaving and re-entering the screen.
 */
class UsageQuestionMasteryTest {

    private val now = 1_000_000_000_000L // fixed clock; a fresh question has nextReviewTime 0 (< now)

    private fun apply(q: UsageQuestionStat, outcome: VocabQuizOutcome, at: Long = now) =
        VocabMasteryEngine.updateMastery(q, outcome, at)

    @Test
    fun `not attempted is New`() {
        assertEquals(UsageMastery.New, UsageQuestionStat(pageNumber = 1).masteryLevel)
    }

    @Test
    fun `correct on first try is Review`() {
        val q = UsageQuestionStat(pageNumber = 1)
        apply(q, VocabQuizOutcome.FLAWLESS)
        assertEquals(UsageMastery.Review, q.masteryLevel)
        assertEquals(1, q.correctStreak)
    }

    @Test
    fun `correct but used info is Review`() {
        val q = UsageQuestionStat(pageNumber = 1)
        apply(q, VocabQuizOutcome.ASSISTED)
        assertEquals(UsageMastery.Review, q.masteryLevel)
    }

    @Test
    fun `wrong then correct is Learning`() {
        val q = UsageQuestionStat(pageNumber = 1)
        apply(q, VocabQuizOutcome.FAILED)   // first wrong tap
        apply(q, VocabQuizOutcome.STUMBLED) // then got it right
        assertEquals(UsageMastery.Learning, q.masteryLevel)
    }

    @Test
    fun `wrong and never correct is Struggling`() {
        val q = UsageQuestionStat(pageNumber = 1)
        apply(q, VocabQuizOutcome.FAILED)
        assertEquals(UsageMastery.Struggling, q.masteryLevel)
    }

    @Test
    fun `correct first-try in three spaced sessions reaches Mastered`() {
        val q = UsageQuestionStat(pageNumber = 1)
        var t = now
        repeat(3) {
            VocabMasteryEngine.updateMastery(q, VocabQuizOutcome.FLAWLESS, t)
            t = q.nextReviewTime + 1 // fast-forward to when the question is next due (new session)
        }
        assertEquals(UsageMastery.Mastered, q.masteryLevel)
    }

    /**
     * The user's scenario over 10 questions in one visit: 4 correct first try, 3 incorrect, 1 correct
     * on the second try, 2 not attempted -> the filter buttons bucket them 4 Review / 3 Struggling /
     * 1 Learning / 2 New.
     */
    @Test
    fun `full scenario buckets the ten questions correctly`() {
        val q = (1..10).map { UsageQuestionStat(pageNumber = it) }

        // 4 correct on the first try -> Review
        listOf(0, 1, 2, 3).forEach { apply(q[it], VocabQuizOutcome.FLAWLESS) }
        // 3 incorrect -> Struggling
        listOf(4, 5, 6).forEach { apply(q[it], VocabQuizOutcome.FAILED) }
        // 1 correct on the second try -> Learning (FAILED tap, then STUMBLED)
        apply(q[7], VocabQuizOutcome.FAILED); apply(q[7], VocabQuizOutcome.STUMBLED)
        // q[8], q[9] never attempted -> New

        val counts = q.groupingBy { it.masteryLevel }.eachCount()
        assertEquals("Review (correct first try)", 4, counts[WordMasteryLevel.Review] ?: 0)
        assertEquals("Struggling (incorrect)", 3, counts[WordMasteryLevel.Struggling] ?: 0)
        assertEquals("Learning (correct on 2nd try)", 1, counts[WordMasteryLevel.Learning] ?: 0)
        assertEquals("New (not attempted)", 2, counts[WordMasteryLevel.New] ?: 0)
    }
}
