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
 *  - [AuditEngine.placeBaselineLevel]     -> the learner's working level: the first band (bottom-up)
 *                                            they have NOT cleared (>= 60% per band); the top band if all clear.
 *  - [AuditEngine.baselineUnlockCeiling]  -> how far the level tests *unlock*: the highest band cleared
 *                                            contiguously from the bottom (same 60% bar).
 *  - [AuditEngine.calculate]              -> the Confidence / Readiness numbers once part 1 is done.
 *
 * Both use the same 60% pass bar and both refuse to skip a failed lower band, so a lucky higher-band
 * answer never leapfrogs a failed lower one. Placement names the first band still to master (the
 * working level); the ceiling names the last band fully mastered (which tests to open) - so for a
 * partly-done band the two differ by one, which several cases below pin down.
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
    fun `only A2 mastered - placed at B1, unlocks the A2 test`() {
        // A2 cleared but B1 not (0/4): the working level is B1, and only the A2 test unlocks.
        val r = baseline(a2Correct = 3, b1Correct = 0, b2Correct = 0)
        assertEquals("B1", AuditEngine.placeBaselineLevel(r))
        assertEquals(2, AuditEngine.baselineUnlockCeiling(r))
    }

    @Test
    fun `A2 and B1 mastered but B2 all wrong - placed at B2, unlocks A2 and B1 tests`() {
        // A2 & B1 cleared, B2 failed: the working level is B2, and the A2 + B1 tests unlock.
        val r = baseline(a2Correct = 3, b1Correct = 4, b2Correct = 0)
        assertEquals("B2", AuditEngine.placeBaselineLevel(r))
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
        // Aces B1 and B2 but only 1/3 A2 (below the 60% bar) - the unlock stops at the A2 gap, and
        // placement likewise refuses to leapfrog: the working level is A2 (first band not cleared).
        val r = baseline(a2Correct = 1, b1Correct = 4, b2Correct = 3)
        assertEquals(1, AuditEngine.baselineUnlockCeiling(r))
        assertEquals("A2", AuditEngine.placeBaselineLevel(r))
    }

    @Test
    fun `B1 band needs 3 of 4 correct to clear - placement moves up once it does`() {
        // A2 already cleared. B1 at 2 of 4 = 50% -> not cleared, so B1 is the working level.
        assertEquals("B1", AuditEngine.placeBaselineLevel(baseline(a2Correct = 3, b1Correct = 2, b2Correct = 0)))
        // B1 at 3 of 4 = 75% -> cleared, so the working level moves up to B2 (B2 not yet cleared).
        assertEquals("B2", AuditEngine.placeBaselineLevel(baseline(a2Correct = 3, b1Correct = 3, b2Correct = 0)))
    }

    @Test
    fun `A2 band needs 2 of 3 correct to clear`() {
        // 1 of 3 = 33% -> A2 not cleared; the working level is A2 and nothing unlocks.
        val oneOfThree = baseline(a2Correct = 1, b1Correct = 0, b2Correct = 0)
        assertEquals("A2", AuditEngine.placeBaselineLevel(oneOfThree))
        assertEquals(1, AuditEngine.baselineUnlockCeiling(oneOfThree))

        // 2 of 3 = 67% -> A2 cleared: the working level moves to B1, and the A2 test (ceiling 2) unlocks.
        val twoOfThree = baseline(a2Correct = 2, b1Correct = 0, b2Correct = 0)
        assertEquals("B1", AuditEngine.placeBaselineLevel(twoOfThree))
        assertEquals(2, AuditEngine.baselineUnlockCeiling(twoOfThree))
    }

    @Test
    fun `placement does not leapfrog a failed lower band`() {
        // A2 and B1 both fail, B2 aced. Placement is the first uncleared band = A2 - a lucky B2
        // answer cannot promote past a failed A2; the unlock ceiling likewise stays at baseline.
        val r = baseline(a2Correct = 0, b1Correct = 0, b2Correct = 3)
        assertEquals("A2", AuditEngine.placeBaselineLevel(r))
        assertEquals(1, AuditEngine.baselineUnlockCeiling(r))
    }

    @Test
    fun `placement is the first uncleared band - the learner's working level`() {
        // A2 aced (3/3), but B1 only 2/4 (50%, below the 60% bar) and B2 1/3. The working level is
        // B1 (first band not cleared); the lucky B2 answer cannot promote past it. The unlock reaches
        // the A2 test (ceiling 2, since A2 cleared) then stops at the B1 gap.
        val r = baseline(a2Correct = 3, b1Correct = 2, b2Correct = 1)
        assertEquals("B1", AuditEngine.placeBaselineLevel(r))
        assertEquals(2, AuditEngine.baselineUnlockCeiling(r))
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
    fun `end to end - A2 and B1 mastered, working on B2, A2 and B1 tests unlocked`() {
        val r = baseline(a2Correct = 3, b1Correct = 4, b2Correct = 0) // 7 correct
        assertEquals("B2", AuditEngine.placeBaselineLevel(r))         // working level: A2+B1 done, on B2
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

    // region Live Readiness - climbs while a test is in progress

    @Test
    fun `readiness climbs with each correct answer during the B2 test`() {
        // Baseline done (10), part-way through B2: correct-so-far drives Readiness up live.
        val after2of3 = AuditEngine.calculate(
            testScores = mapOf(1 to 10), partProgress = mapOf(4 to 0.3f), liveScores = mapOf(4 to 2)
        )
        val after5of5 = AuditEngine.calculate(
            testScores = mapOf(1 to 10), partProgress = mapOf(4 to 0.5f), liveScores = mapOf(4 to 5)
        )
        assertEquals(36, after2of3.readiness)   // credited (10 + 2*1.2 + 2*1.4 + 2*1.4)/50*100
        assertEquals(60, after5of5.readiness)   // credited (10 + 5*1.2 + 5*1.4 + 5*1.4)/50*100
    }

    @Test
    fun `live readiness converges to the completed value with no jump`() {
        val live = AuditEngine.calculate(
            testScores = mapOf(1 to 10), partProgress = mapOf(4 to 1.0f), liveScores = mapOf(4 to 9)
        )
        val completed = AuditEngine.calculate(testScores = mapOf(1 to 10, 4 to 9), partProgress = emptyMap())
        assertEquals(92, live.readiness)
        assertEquals(completed.readiness, live.readiness)
    }

    @Test
    fun `live score lifts readiness but not confidence's full cap`() {
        // No progress fraction yet, but 9 correct-so-far: readiness reflects it, confidence does not.
        val s = AuditEngine.calculate(
            testScores = mapOf(1 to 10), partProgress = emptyMap(), liveScores = mapOf(4 to 9)
        )
        assertEquals(40, s.confidence)  // only the completed baseline's cap - B2 not yet finished
        assertEquals(92, s.readiness)   // but readiness already reflects the strong live B2
    }

    // endregion

    // region Confidence bonus for redone (v>=2) audits

    @Test
    fun `redone-audit bonus nudges confidence up but not readiness`() {
        // Baseline done; a full v2 redo (4 parts) earns +8 confidence (2 per part).
        val base = AuditEngine.calculate(testScores = mapOf(1 to 10), partProgress = emptyMap())
        val withBonus = AuditEngine.calculate(
            testScores = mapOf(1 to 10), partProgress = emptyMap(), confidenceBonus = 8
        )
        assertEquals(40, base.confidence)
        assertEquals(48, withBonus.confidence)     // 40 + 8
        assertEquals(base.readiness, withBonus.readiness) // readiness untouched by the bonus
    }

    @Test
    fun `confidence bonus never pushes confidence past the 98 cap`() {
        val s = AuditEngine.calculate(
            testScores = mapOf(1 to 10, 2 to 10, 3 to 10, 4 to 10),
            partProgress = emptyMap(),
            confidenceBonus = 8
        )
        assertEquals(98, s.confidence)
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
