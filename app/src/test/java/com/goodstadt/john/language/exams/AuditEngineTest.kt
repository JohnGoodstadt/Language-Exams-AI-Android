package com.goodstadt.john.language.exams

import com.goodstadt.john.language.exams.managers.AuditEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class AuditEngineTest {

    // region Confidence - coverage across the 4 parts, ignores correctness

    @Test
    fun `nothing answered - confidence and readiness are both zero`() {
        val result = AuditEngine.calculate(testScores = emptyMap(), partProgress = emptyMap())

        assertEquals(0, result.confidence)
        assertEquals(0, result.readiness)
    }

    @Test
    fun `confidence grows smoothly as questions are answered within part 1`() {
        // Part 1 (Baseline) has 10 questions. Confidence should climb roughly linearly with
        // every single answer, not jump in large steps.
        val afterQ1 = AuditEngine.calculate(emptyMap(), mapOf(1 to 0.1f))
        assertEquals("after 1 of 10 answered", 3, afterQ1.confidence) // 0.1/4*100 = 2.5 -> 3

        val afterQ5 = AuditEngine.calculate(emptyMap(), mapOf(1 to 0.5f))
        assertEquals("after 5 of 10 answered", 13, afterQ5.confidence) // 0.5/4*100 = 12.5 -> 13

        val afterQ10 = AuditEngine.calculate(emptyMap(), mapOf(1 to 1.0f))
        assertEquals("after all 10 of part 1 answered", 25, afterQ10.confidence) // 1/4*100 = 25
    }

    @Test
    fun `confidence ignores correctness - only coverage counts`() {
        // A part fully answered but entirely wrong still counts as fully covered for Confidence.
        val allWrong = AuditEngine.calculate(testScores = mapOf(1 to 0), partProgress = mapOf(1 to 1f))
        assertEquals(25, allWrong.confidence)
    }

    @Test
    fun `two parts fully complete - confidence is 50 percent`() {
        val result = AuditEngine.calculate(emptyMap(), mapOf(1 to 1f, 2 to 1f))
        assertEquals(50, result.confidence)
    }

    @Test
    fun `all 4 parts fully complete - confidence is 100 percent`() {
        val result = AuditEngine.calculate(emptyMap(), mapOf(1 to 1f, 2 to 1f, 3 to 1f, 4 to 1f))
        assertEquals(100, result.confidence)
    }

    @Test
    fun `mixed progress across parts averages correctly`() {
        // Part 1 fully done, Part 2 half done -> (1.0 + 0.5) / 4 * 100 = 37.5 -> 38
        val result = AuditEngine.calculate(emptyMap(), mapOf(1 to 1f, 2 to 0.5f))
        assertEquals(38, result.confidence)
    }

    @Test
    fun `progress fractions are defensively coerced into 0f to 1f`() {
        val overOne = AuditEngine.calculate(emptyMap(), mapOf(1 to 1.5f))
        assertEquals(25, overOne.confidence) // treated as 1.0, not 1.5

        val negative = AuditEngine.calculate(emptyMap(), mapOf(1 to -0.5f))
        assertEquals(0, negative.confidence) // treated as 0
    }

    // endregion

    // region Readiness - weighted accuracy prorated across ALL 4 parts

    @Test
    fun `regression - acing part 1 alone must not read as near-perfect readiness`() {
        // This is the exact shape of bug report: user answers a handful of Part 1 questions
        // correctly and Readiness used to jump to 98% ("Elite Performance") despite 3 of the
        // 4 parts never having been attempted. Only Part 1 (weight 1.0) of the 5.0 total
        // weight (1.0+1.2+1.4+1.4) has been earned, so readiness must stay low.
        val result = AuditEngine.calculate(testScores = mapOf(1 to 10), partProgress = mapOf(1 to 1f))

        assertEquals(20, result.readiness) // 10*1.0 / 50 * 100 = 20%, not 98%
    }

    @Test
    fun `readiness grows gradually as part 1 questions are answered`() {
        // Score is now correct-so-far divided by the TOTAL questions in the part (10), not by
        // how many have been attempted - so 1 correct out of 1 attempted is NOT a perfect 10.
        val afterQ1Correct = AuditEngine.calculate(mapOf(1 to 1), mapOf(1 to 0.1f)) // 1/10 -> score 1
        assertEquals(2, afterQ1Correct.readiness) // 1*1.0 / 50 * 100 = 2%

        val afterHalfAt80percent = AuditEngine.calculate(mapOf(1 to 4), mapOf(1 to 0.5f)) // 4/10 correct so far
        assertEquals(8, afterHalfAt80percent.readiness) // 4*1.0 / 50 * 100 = 8%

        val afterAllAt80percent = AuditEngine.calculate(mapOf(1 to 8), mapOf(1 to 1f)) // 8/10 final
        assertEquals(16, afterAllAt80percent.readiness) // 8*1.0 / 50 * 100 = 16%
    }

    @Test
    fun `all 4 parts perfect - readiness caps at 98`() {
        val result = AuditEngine.calculate(mapOf(1 to 10, 2 to 10, 3 to 10, 4 to 10), mapOf(1 to 1f, 2 to 1f, 3 to 1f, 4 to 1f))
        assertEquals(98, result.readiness)
    }

    @Test
    fun `all 4 parts zero - readiness is zero`() {
        val result = AuditEngine.calculate(mapOf(1 to 0, 2 to 0, 3 to 0, 4 to 0), mapOf(1 to 1f, 2 to 1f, 3 to 1f, 4 to 1f))
        assertEquals(0, result.readiness)
    }

    @Test
    fun `mixed performance across all 4 completed parts - weighted average`() {
        // P1: 10/10 (10pts), P2: 5/10 (6pts), P3: 10/10 (14pts), P4: 10/10 (14pts)
        // Earned 44 / Possible 50 = 88%
        val scores = mapOf(1 to 10, 2 to 5, 3 to 10, 4 to 10)
        val progress = mapOf(1 to 1f, 2 to 1f, 3 to 1f, 4 to 1f)
        val result = AuditEngine.calculate(scores, progress)

        assertEquals(100, result.confidence)
        assertEquals(88, result.readiness)
    }

    @Test
    fun `part 4 failure applies penalty - readiness drops by 5 percent`() {
        // All parts perfect except Part 4 at 5/10 (< 6 triggers penalty)
        // Earned: 10 + 12 + 14 + 7 = 43. 43/50 = 86%. Penalty -5% -> 81%
        val scores = mapOf(1 to 10, 2 to 10, 3 to 10, 4 to 5)
        val result = AuditEngine.calculate(scores, mapOf(1 to 1f, 2 to 1f, 3 to 1f, 4 to 1f))

        assertEquals(81, result.readiness)
    }

    @Test
    fun `part 4 bare pass - no penalty applied`() {
        // Part 4 at 6/10 is a bare pass, no penalty.
        // Earned: 10 + 12 + 14 + 8.4 = 44.4. 44.4/50 = 88.8% -> 89%
        val scores = mapOf(1 to 10, 2 to 10, 3 to 10, 4 to 6)
        val result = AuditEngine.calculate(scores, mapOf(1 to 1f, 2 to 1f, 3 to 1f, 4 to 1f))

        assertEquals(89, result.readiness)
    }

    @Test
    fun `low scores across all 4 parts stay below B1 level`() {
        val scores = mapOf(1 to 5, 2 to 4, 3 to 3, 4 to 1)
        val result = AuditEngine.calculate(scores, mapOf(1 to 1f, 2 to 1f, 3 to 1f, 4 to 1f))

        assertEquals(26, result.readiness)
    }

    @Test
    fun `decent scores across all 4 parts reach B1 level`() {
        val scores = mapOf(1 to 10, 2 to 10, 3 to 5, 4 to 0)
        val result = AuditEngine.calculate(scores, mapOf(1 to 1f, 2 to 1f, 3 to 1f, 4 to 1f))

        assertEquals(53, result.readiness)
    }

    // endregion

    // region Confidence label thresholds

    @Test
    fun `confidence label matches each threshold`() {
        assertEquals("Not Started", AuditEngine.getConfidenceLabel(0))
        assertEquals("Getting Started", AuditEngine.getConfidenceLabel(1))
        assertEquals("Getting Started", AuditEngine.getConfidenceLabel(40))
        assertEquals("In Progress", AuditEngine.getConfidenceLabel(41))
        assertEquals("In Progress", AuditEngine.getConfidenceLabel(84))
        assertEquals("Nearly Complete", AuditEngine.getConfidenceLabel(85))
        assertEquals("Nearly Complete", AuditEngine.getConfidenceLabel(100))
    }

    // endregion

    // region Readiness verdict thresholds ("Elite Performance" and friends)

    @Test
    fun `readiness verdict matches each threshold`() {
        assertEquals("Let's Get Started", AuditEngine.getReadinessVerdict(0))
        assertEquals("Foundational Work Needed", AuditEngine.getReadinessVerdict(1))
        assertEquals("Foundational Work Needed", AuditEngine.getReadinessVerdict(35))
        assertEquals("Borderline B1 Candidate", AuditEngine.getReadinessVerdict(36))
        assertEquals("Borderline B1 Candidate", AuditEngine.getReadinessVerdict(55))
        assertEquals("B1/B2 Ready", AuditEngine.getReadinessVerdict(56))
        assertEquals("B1/B2 Ready", AuditEngine.getReadinessVerdict(85))
        assertEquals("Elite Performance", AuditEngine.getReadinessVerdict(86))
        assertEquals("Elite Performance", AuditEngine.getReadinessVerdict(98))
    }

    @Test
    fun `regression - a handful of correct part 1 answers no longer verdicts as Elite Performance`() {
        // The exact scenario from the bug report: only Part 1 attempted, scored well on it.
        val result = AuditEngine.calculate(testScores = mapOf(1 to 10), partProgress = mapOf(1 to 1f))
        val verdict = AuditEngine.getReadinessVerdict(result.readiness)

        assertEquals(20, result.readiness)
        assertEquals("Foundational Work Needed", verdict)
    }

    @Test
    fun `Elite Performance only appears once all 4 parts are genuinely strong`() {
        val scores = mapOf(1 to 9, 2 to 9, 3 to 9, 4 to 9)
        val result = AuditEngine.calculate(scores, mapOf(1 to 1f, 2 to 1f, 3 to 1f, 4 to 1f))

        assertEquals("Elite Performance", AuditEngine.getReadinessVerdict(result.readiness))
    }

    // endregion
}
