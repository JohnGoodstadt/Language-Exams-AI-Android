package com.goodstadt.john.language.exams

import com.goodstadt.john.language.exams.managers.AuditEngine
import com.goodstadt.john.language.exams.managers.AuditReport
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Boundary tests for how a completed baseline quiz is scored.
 *
 * The baseline mixes CEFR bands - mirroring BaselineAudit-en: 3x A2, 4x B1, 3x B2 (10 questions).
 * Three things are derived from the per-question results and checked here:
 *  - [AuditEngine.placeBaselineLevel]     -> the band the learner is *placed* at (>= 60% per band).
 *  - [AuditEngine.baselineUnlockCeiling]  -> how far the level tests *unlock* (100% per band, bottom-up).
 *  - [AuditEngine.calculate]              -> the Confidence / Readiness numbers once part 1 is done.
 *
 * Placement is generous (any band clearing 60% counts, highest wins) while the unlock ceiling is
 * strict (a band must be 100% correct, and only counts if every band below it is too) - so the two
 * deliberately diverge, which several cases below pin down.
 */
class AuditEngineBaselineTest {

    /**
     * Builds a 10-question baseline result (3 A2, 4 B1, 3 B2) with the given number of correct
     * answers in each band. e.g. baseline(3, 2, 0) = all A2 right, 2 of 4 B1 right, all B2 wrong.
     */
    private fun baseline(a2Correct: Int, b1Correct: Int, b2Correct: Int): List<Pair<String?, Boolean>> {
        val results = mutableListOf<Pair<String?, Boolean>>()
        repeat(3) { results.add("A2" to (it < a2Correct)) }
        repeat(4) { results.add("B1" to (it < b1Correct)) }
        repeat(3) { results.add("B2" to (it < b2Correct)) }
        return results
    }

    /** Confidence/Readiness for a *completed* baseline (part 1), scored by total correct answers. */
    private fun baselineStats(results: List<Pair<String?, Boolean>>): AuditReport {
        val score = results.count { it.second }
        return AuditEngine.calculate(testScores = mapOf(1 to score), partProgress = mapOf(1 to 1f))
    }

    // region Placement + unlock ceiling boundaries

    @Test
    fun `no passes - every answer wrong`() {
        val r = baseline(a2Correct = 0, b1Correct = 0, b2Correct = 0)
        assertEquals("A2", AuditEngine.placeBaselineLevel(r))  // floor placement
        assertEquals(1, AuditEngine.baselineUnlockCeiling(r))  // nothing unlocks
    }

    @Test
    fun `all passes - every answer correct`() {
        val r = baseline(a2Correct = 3, b1Correct = 4, b2Correct = 3)
        assertEquals("B2", AuditEngine.placeBaselineLevel(r))
        assertEquals(4, AuditEngine.baselineUnlockCeiling(r))  // A2 + B1 + B2 tests
    }

    @Test
    fun `nine of ten with only the last B2 wrong places at B2 and unlocks the B2 test`() {
        // Reported case: A2 3/3, B1 4/4, B2 2/3. B2 clears the 60% bar (2 of 3), so placement is
        // B2 and the unlock reaches the B2 test too - the "Go to your B2 test" button is correct.
        val r = baseline(a2Correct = 3, b1Correct = 4, b2Correct = 2)
        assertEquals("B2", AuditEngine.placeBaselineLevel(r))
        assertEquals(4, AuditEngine.baselineUnlockCeiling(r))
    }

    @Test
    fun `only A2 mastered - unlocks the A2 test`() {
        val r = baseline(a2Correct = 3, b1Correct = 0, b2Correct = 0)
        assertEquals("A2", AuditEngine.placeBaselineLevel(r))
        assertEquals(2, AuditEngine.baselineUnlockCeiling(r))
    }

    @Test
    fun `A2 and B1 mastered but B2 all wrong - unlocks A2 and B1 tests`() {
        val r = baseline(a2Correct = 3, b1Correct = 4, b2Correct = 0)
        assertEquals("B1", AuditEngine.placeBaselineLevel(r))
        assertEquals(3, AuditEngine.baselineUnlockCeiling(r))
    }

    @Test
    fun `clearing every band at 60 percent unlocks all tests without needing perfect scores`() {
        // 2/3 A2, 3/4 B1, 2/3 B2: every band clears the 60% bar, so placement is B2 AND the
        // unlock ceiling agrees (all tests) - unlock uses the same pass bar as placement.
        val r = baseline(a2Correct = 2, b1Correct = 3, b2Correct = 2)
        assertEquals("B2", AuditEngine.placeBaselineLevel(r))
        assertEquals(4, AuditEngine.baselineUnlockCeiling(r))
    }

    @Test
    fun `unlock still needs bands cleared contiguously from the bottom`() {
        // Aces B1 and B2 but only 1/3 A2 (below the 60% bar) - the unlock stops at the A2 gap.
        // Placement stays generous and reports the highest band cleared.
        val r = baseline(a2Correct = 1, b1Correct = 4, b2Correct = 3)
        assertEquals(1, AuditEngine.baselineUnlockCeiling(r))
        assertEquals("B2", AuditEngine.placeBaselineLevel(r))
    }

    @Test
    fun `B1 band needs 3 of 4 correct to pass`() {
        // 2 of 4 = 50% -> fails, placement falls back to A2.
        assertEquals("A2", AuditEngine.placeBaselineLevel(baseline(a2Correct = 3, b1Correct = 2, b2Correct = 0)))
        // 3 of 4 = 75% -> passes, placement climbs to B1.
        assertEquals("B1", AuditEngine.placeBaselineLevel(baseline(a2Correct = 3, b1Correct = 3, b2Correct = 0)))
    }

    @Test
    fun `A2 band needs 2 of 3 correct to clear`() {
        // 1 of 3 = 33% -> A2 not cleared; placement defaults to the A2 floor, nothing unlocks.
        val oneOfThree = baseline(a2Correct = 1, b1Correct = 0, b2Correct = 0)
        assertEquals("A2", AuditEngine.placeBaselineLevel(oneOfThree))
        assertEquals(1, AuditEngine.baselineUnlockCeiling(oneOfThree))

        // 2 of 3 = 67% -> A2 cleared: placement A2 and the A2 test (ceiling 2) unlocks.
        val twoOfThree = baseline(a2Correct = 2, b1Correct = 0, b2Correct = 0)
        assertEquals("A2", AuditEngine.placeBaselineLevel(twoOfThree))
        assertEquals(2, AuditEngine.baselineUnlockCeiling(twoOfThree))
    }

    @Test
    fun `placement takes the highest passed band even when a lower band failed`() {
        // Non-contiguous: A2 and B1 both fail, B2 aced. Placement is generous (B2); ceiling is
        // strict and stays at baseline because A2 was not mastered.
        val r = baseline(a2Correct = 0, b1Correct = 0, b2Correct = 3)
        assertEquals("B2", AuditEngine.placeBaselineLevel(r))
        assertEquals(1, AuditEngine.baselineUnlockCeiling(r))
    }

    @Test
    fun `band labels are normalised for case and surrounding whitespace`() {
        val r = listOf<Pair<String?, Boolean>>(
            " a2 " to true, "A2" to true, "a2" to true,              // 3/3 A2
            "b1" to true, "B1" to true, " b1" to true, "B1 " to true, // 4/4 B1
            "B2" to true, "b2" to true, "b2 " to true                 // 3/3 B2
        )
        assertEquals("B2", AuditEngine.placeBaselineLevel(r))
        assertEquals(4, AuditEngine.baselineUnlockCeiling(r))
    }

    @Test
    fun `questions with a null or blank band are ignored`() {
        val r = baseline(3, 4, 3) + listOf<Pair<String?, Boolean>>(null to false, "" to false, "  " to false)
        assertEquals("B2", AuditEngine.placeBaselineLevel(r))
        assertEquals(4, AuditEngine.baselineUnlockCeiling(r))
    }

    // endregion

    // region Confidence & Readiness once the baseline (part 1) is complete

    @Test
    fun `completed baseline is always 40 percent confidence regardless of score`() {
        // Confidence is coverage, not correctness: finishing all 10 baseline questions covers
        // part 1 (cap 40%), whether the learner scored 0 or 10.
        assertEquals(40, baselineStats(baseline(0, 0, 0)).confidence)
        assertEquals(40, baselineStats(baseline(2, 3, 2)).confidence)
        assertEquals(40, baselineStats(baseline(3, 4, 3)).confidence)
        // ...and that reads as "Getting Started" under the confidence labels.
        assertEquals("Getting Started", AuditEngine.getConfidenceLabel(40))
    }

    @Test
    fun `readiness after baseline is two percent per correct answer`() {
        // Only part 1 (weight 1.0 of the 5.0 total) is earned, so readiness = totalCorrect / 50 * 100.
        assertEquals(0, baselineStats(baseline(0, 0, 0)).readiness)   // 0 correct
        assertEquals(6, baselineStats(baseline(3, 0, 0)).readiness)   // 3 correct  -> 6%
        assertEquals(14, baselineStats(baseline(3, 4, 0)).readiness)  // 7 correct  -> 14%
        assertEquals(20, baselineStats(baseline(3, 4, 3)).readiness)  // 10 correct -> 20%
    }

    @Test
    fun `a perfect baseline still reads as Foundational because 3 of 4 parts are untaken`() {
        val stats = baselineStats(baseline(3, 4, 3))
        assertEquals(40, stats.confidence)
        assertEquals(20, stats.readiness)
        assertEquals("Foundational Work Needed", AuditEngine.getReadinessVerdict(stats.readiness))
    }

    @Test
    fun `end to end - A2 and B1 mastered ties placement, unlock, confidence and readiness together`() {
        val r = baseline(a2Correct = 3, b1Correct = 4, b2Correct = 0) // 7 correct
        assertEquals("B1", AuditEngine.placeBaselineLevel(r))
        assertEquals(3, AuditEngine.baselineUnlockCeiling(r))         // A2 + B1 tests unlocked
        val stats = baselineStats(r)
        assertEquals(40, stats.confidence)
        assertEquals(14, stats.readiness)
    }

    // endregion

    // region Downward credit - completing a harder test carries the untaken easier tests

    @Test
    fun `baseline plus a strong B2 credits the untaken A2 and B1 tests`() {
        // Reported case: baseline 10/10 (part 1) and B2 test 9/10 (part 4), A2 & B1 tests untaken.
        // A2 (part 2) and B1 (part 3) are credited with the B2 score (9), not counted as zero.
        val stats = AuditEngine.calculate(testScores = mapOf(1 to 10, 4 to 9), partProgress = emptyMap())
        assertEquals(98, stats.confidence)  // caps 40 + 25 + 20 + 13
        assertEquals(92, stats.readiness)   // (10 + 9*1.2 + 9*1.4 + 9*1.4) / 50 * 100
    }

    @Test
    fun `baseline plus B1 credits only the untaken A2 test, not the still-harder B2`() {
        // Highest completed part is B1 (part 3), so only A2 (part 2) is credited; B2 (part 4)
        // stays absent because nothing above B1 has been demonstrated.
        val stats = AuditEngine.calculate(testScores = mapOf(1 to 10, 3 to 8), partProgress = emptyMap())
        assertEquals(85, stats.confidence)  // caps 40 + 25 + 20 (no B2 cap)
        assertEquals(62, stats.readiness)   // (10 + 8*1.2 + 8*1.4 + 0) / 50 * 100 = 61.6 -> 62
    }

    @Test
    fun `a weak B2 does not inflate the credited easier tests`() {
        // B2 failed at 4/10: the credited A2/B1 inherit that 4 (not full marks), and the sub-6
        // B2 penalty still applies - so readiness stays low even though coverage is high.
        val stats = AuditEngine.calculate(testScores = mapOf(1 to 10, 4 to 4), partProgress = emptyMap())
        assertEquals(98, stats.confidence)  // coverage is high - we tested a lot
        assertEquals(47, stats.readiness)   // (10 + 4*1.2 + 4*1.4 + 4*1.4)/50*100 = 52, minus 5 penalty
    }

    @Test
    fun `baseline alone gets no downward credit - nothing sits below it`() {
        val stats = AuditEngine.calculate(testScores = mapOf(1 to 10), partProgress = emptyMap())
        assertEquals(40, stats.confidence)
        assertEquals(20, stats.readiness)
    }

    @Test
    fun `all four parts taken - credit is a no-op, real scores stand`() {
        val stats = AuditEngine.calculate(
            testScores = mapOf(1 to 10, 2 to 8, 3 to 7, 4 to 9),
            partProgress = emptyMap()
        )
        assertEquals(98, stats.confidence)
        assertEquals(84, stats.readiness)   // (10 + 8*1.2 + 7*1.4 + 9*1.4) / 50 * 100 = 84
    }

    // endregion

    // region Onboarding path reference (Confidence / Readiness the device should show)

    private fun stats(vararg parts: Pair<Int, Int>) =
        AuditEngine.calculate(testScores = parts.toMap(), partProgress = emptyMap())

    @Test fun `path - baseline only, perfect`() {
        val s = stats(1 to 10); assertEquals(40, s.confidence); assertEquals(20, s.readiness)
    }

    @Test fun `path - baseline then A2, perfect`() {
        val s = stats(1 to 10, 2 to 10); assertEquals(65, s.confidence); assertEquals(44, s.readiness)
    }

    @Test fun `path - baseline then A2 then B1, perfect`() {
        val s = stats(1 to 10, 2 to 10, 3 to 10); assertEquals(85, s.confidence); assertEquals(72, s.readiness)
    }

    @Test fun `path - baseline then A2 B1 B2 all perfect`() {
        val s = stats(1 to 10, 2 to 10, 3 to 10, 4 to 10); assertEquals(98, s.confidence); assertEquals(98, s.readiness)
    }

    @Test fun `path - baseline straight to B2, perfect (A2 and B1 credited)`() {
        val s = stats(1 to 10, 4 to 10); assertEquals(98, s.confidence); assertEquals(98, s.readiness)
    }

    @Test fun `path - baseline straight to B1, perfect (A2 credited)`() {
        val s = stats(1 to 10, 3 to 10); assertEquals(85, s.confidence); assertEquals(72, s.readiness)
    }

    @Test fun `path - baseline straight to B2, one B2 wrong`() {
        val s = stats(1 to 10, 4 to 9); assertEquals(98, s.confidence); assertEquals(92, s.readiness)
    }

    @Test fun `path - all four done, one B2 wrong (no credit needed)`() {
        val s = stats(1 to 10, 2 to 10, 3 to 10, 4 to 9); assertEquals(98, s.confidence); assertEquals(97, s.readiness)
    }

    @Test fun `path - baseline straight to B1, one B1 wrong`() {
        val s = stats(1 to 10, 3 to 9); assertEquals(85, s.confidence); assertEquals(67, s.readiness)
    }

    @Test fun `path - baseline straight to a weak B2 (penalty applies)`() {
        val s = stats(1 to 10, 4 to 5); assertEquals(98, s.confidence); assertEquals(55, s.readiness)
    }

    // endregion
}
