package com.goodstadt.john.language.exams

import com.goodstadt.john.language.exams.data.strength.ReferenceStrengthLevel
import com.goodstadt.john.language.exams.data.strength.ReferenceStrengthMath
import com.goodstadt.john.language.exams.managers.AuditEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cross-platform scoring values — the Kotlin twin of the iOS `CrossPlatformScoringTests.swift`.
 * Every expected number here MUST equal the value asserted in the Swift file: both platforms run the
 * ported [AuditEngine] and [ReferenceStrengthMath], so a mismatch means the two apps would score the
 * same quiz differently. Use this file (and its Swift twin) as the reference table.
 *
 * Baseline bands are A1(x2) / A2(x4) / B1(x4) — there are NO B2 questions in the baseline; B2 is only
 * reached via the "aced B1" shortcut. (The Verify B2 test is a separate part-4 quiz, below.)
 */
class CrossPlatformScoringTest {

    // =====================================================================================
    // 1. BASELINE PLACEMENT — AuditEngine.placeBaseline(a1, a2, b1) -> band 0=A1,1=A2,2=B1,3=B2
    //    a1Correct 0..2, a2Correct 0..4, b1Correct 0..4.
    // =====================================================================================

    private fun place(a1: Int, a2: Int, b1: Int) = AuditEngine.placeBaseline(a1, a2, b1)

    @Test fun `baseline none correct places A1`() {
        assertEquals(0, place(0, 0, 0))
        assertEquals("A1", AuditEngine.bandLabel(0))
        assertEquals(1, AuditEngine.baselineUnlockCeiling(0)) // baseline only
    }

    @Test fun `baseline all correct places B2`() {
        assertEquals(3, place(2, 4, 4))
        assertEquals("B2", AuditEngine.bandLabel(3))
        assertEquals(4, AuditEngine.baselineUnlockCeiling(3)) // unlocks up to Verify B2
    }

    @Test fun `baseline only A1 correct still places A1`() {
        // Passing A1 but nothing on A2/B1 is not enough to move up.
        assertEquals(0, place(2, 0, 0))
    }

    @Test fun `baseline A2 cleared no B1 places A2`() {
        assertEquals(1, place(2, 3, 0))
        assertEquals("A2", AuditEngine.bandLabel(1))
        assertEquals(2, AuditEngine.baselineUnlockCeiling(1)) // unlocks up to Verify A2
    }

    @Test fun `baseline A2 all plus some B1 places B1`() {
        assertEquals(2, place(2, 4, 2))
        assertEquals("B1", AuditEngine.bandLabel(2))
        assertEquals(3, AuditEngine.baselineUnlockCeiling(2)) // unlocks up to Verify B1
    }

    @Test fun `baseline strong learner who fluffed A1 still places B2`() {
        // !a1Pass but A2 and B1 both cleared -> "strong" -> not the beginner safety net.
        assertEquals(3, place(0, 4, 4))
    }

    @Test fun `baseline aced B1 shortcut places B2 even with A2 at three`() {
        assertEquals(3, place(2, 3, 4)) // b1Correct == 4 -> B2
    }

    @Test fun `baseline partial A2 caps at A2 regardless of B1`() {
        // a2 == 2 (not cleared) -> contiguity caps at A2 even though B1 is aced.
        assertEquals(1, place(2, 2, 4))
    }

    @Test fun `baseline lone-correct answers are discounted as lucky guesses`() {
        // a2Correct==1 -> 0, b1Correct==1 -> 0, so this is effectively (2,0,0) -> A1.
        assertEquals(0, place(2, 1, 1))
    }

    @Test fun `baseline fluffed A1 with B1 not cleared falls to the A1 safety net`() {
        assertEquals(0, place(0, 4, 2)) // not "strong" (b1 not cleared), !a1Pass -> A1
    }

    @Test fun `baseline A2 all no B1 places A2`() {
        assertEquals(1, place(2, 4, 0))
    }

    @Test fun `baseline A2 all lucky-single B1 still places A2`() {
        assertEquals(1, place(2, 4, 1)) // b1Correct==1 discounted to 0
    }

    // =====================================================================================
    // 2. VERIFY TESTS — AuditEngine.calculate(testScores) after completing ONE verify part.
    //    Part 2 = Verify A2, Part 3 = Verify B1, Part 4 = Verify B2 (each scored 0..10).
    //    Completing a part credits the easier parts with the same score (downward crediting),
    //    so Confidence jumps to the cumulative cap and Readiness is the weighted accuracy.
    // =====================================================================================

    private fun readiness(part: Int, score: Int) =
        AuditEngine.calculate(mapOf(part to score), emptyMap()).readiness

    private fun confidence(part: Int, score: Int) =
        AuditEngine.calculate(mapOf(part to score), emptyMap()).confidence

    @Test fun `verify A2 readiness - none, 4, 6, all`() {
        assertEquals(0, readiness(2, 0))
        assertEquals(18, readiness(2, 4))   // 4.4 * 4 = 17.6 -> 18
        assertEquals(26, readiness(2, 6))   // 4.4 * 6 = 26.4 -> 26
        assertEquals(44, readiness(2, 10))  // 4.4 * 10 = 44
        assertEquals(65, confidence(2, 5))  // parts 1+2 credited: 40 + 25
    }

    @Test fun `verify B1 readiness - none, 4, 6, all`() {
        assertEquals(0, readiness(3, 0))
        assertEquals(29, readiness(3, 4))   // 7.2 * 4 = 28.8 -> 29
        assertEquals(43, readiness(3, 6))   // 7.2 * 6 = 43.2 -> 43
        assertEquals(72, readiness(3, 10))  // 7.2 * 10 = 72
        assertEquals(85, confidence(3, 5))  // parts 1+2+3: 40 + 25 + 20
    }

    @Test fun `verify B2 readiness - none, 4, 6, all (with weak-B2 penalty)`() {
        assertEquals(0, readiness(4, 0))    // 0 - 5 penalty -> clamped to 0
        assertEquals(35, readiness(4, 4))   // 40 - 5 penalty (score < 6) = 35
        assertEquals(60, readiness(4, 6))   // 60, no penalty (score == 6)
        assertEquals(98, readiness(4, 10))  // 100 -> clamped to 98
        assertEquals(98, confidence(4, 5))  // all four parts credited: 40+25+20+13
    }

    // =====================================================================================
    // 3. FULL AUDIT (all four parts completed together) — a few reference points.
    // =====================================================================================

    private val allDone = mapOf(1 to 1f, 2 to 1f, 3 to 1f, 4 to 1f)

    @Test fun `full audit all zero`() {
        val r = AuditEngine.calculate(mapOf(1 to 0, 2 to 0, 3 to 0, 4 to 0), allDone)
        assertEquals(0, r.readiness)
        assertEquals(98, r.confidence)
    }

    @Test fun `full audit all perfect caps at 98`() {
        val r = AuditEngine.calculate(mapOf(1 to 10, 2 to 10, 3 to 10, 4 to 10), allDone)
        assertEquals(98, r.readiness)
        assertEquals(98, r.confidence)
    }

    @Test fun `full audit weak B2 applies 5 percent penalty`() {
        // 10 + 12 + 14 + 7 = 43 earned / 50 = 86%, minus 5% penalty = 81%.
        val r = AuditEngine.calculate(mapOf(1 to 10, 2 to 10, 3 to 10, 4 to 5), allDone)
        assertEquals(81, r.readiness)
    }

    // =====================================================================================
    // 4. REFERENCE STRENGTH (drives the Focus tab) — ReferenceStrengthMath.
    //    A quiz calls recordQuizResult(correct, answered); the mark is an EMA (alpha 0.5), the
    //    level buckets: mark < 0.50 WEAK, < 0.80 OK, >= 0.80 STRONG; < 3 lifetime answers UNTESTED.
    //    Focus shows one row per top area, EXCLUDING untested. Areas here are named for readability;
    //    only the (correct, answered) runs affect the maths.
    // =====================================================================================

    /** Replays a sequence of quiz runs into a final (mark, level) exactly as the repository would. */
    private fun simulate(vararg runs: Pair<Int, Int>): Pair<Float, ReferenceStrengthLevel> {
        var mark = 0f
        var attempts = 0
        var answered = 0
        for ((c, a) in runs) {
            mark = ReferenceStrengthMath.blend(
                previousMark = if (attempts == 0) null else mark,
                previousAttempts = attempts,
                correct = c,
                answered = a
            )
            attempts += 1
            answered += a
        }
        return mark to ReferenceStrengthMath.level(mark, answered, attempts)
    }

    private val eps = 1e-4f

    @Test fun `reference single run buckets`() {
        // Adjectives: 10/10 -> 1.00 STRONG
        simulate(10 to 10).let { assertEquals(1.0f, it.first, eps); assertEquals(ReferenceStrengthLevel.STRONG, it.second) }
        // Conjugations: 8/10 -> 0.80 STRONG (boundary: >= 0.80)
        simulate(8 to 10).let { assertEquals(0.8f, it.first, eps); assertEquals(ReferenceStrengthLevel.STRONG, it.second) }
        // Prepositions: 7/10 -> 0.70 OK
        simulate(7 to 10).let { assertEquals(0.7f, it.first, eps); assertEquals(ReferenceStrengthLevel.OK, it.second) }
        // Pronouns: 5/10 -> 0.50 OK (boundary: WEAK is strictly below 0.50)
        simulate(5 to 10).let { assertEquals(0.5f, it.first, eps); assertEquals(ReferenceStrengthLevel.OK, it.second) }
        // Sounds the Same: 4/10 -> 0.40 WEAK
        simulate(4 to 10).let { assertEquals(0.4f, it.first, eps); assertEquals(ReferenceStrengthLevel.WEAK, it.second) }
        // Word Pairs: 0/10 -> 0.00 WEAK
        simulate(0 to 10).let { assertEquals(0.0f, it.first, eps); assertEquals(ReferenceStrengthLevel.WEAK, it.second) }
    }

    @Test fun `reference too few answers stay untested and are hidden on Focus`() {
        // 2/2 = perfect but only 2 lifetime answers (< 3) -> UNTESTED, so Focus does not list it.
        simulate(2 to 2).let { assertEquals(1.0f, it.first, eps); assertEquals(ReferenceStrengthLevel.UNTESTED, it.second) }
        simulate(1 to 1).let { assertEquals(ReferenceStrengthLevel.UNTESTED, it.second) }
        // 1/3 crosses the 3-answer floor -> WEAK (0.333).
        simulate(1 to 3).let { assertEquals(0.3333f, it.first, eps); assertEquals(ReferenceStrengthLevel.WEAK, it.second) }
        // 3/3 -> STRONG.
        simulate(3 to 3).let { assertEquals(1.0f, it.first, eps); assertEquals(ReferenceStrengthLevel.STRONG, it.second) }
    }

    @Test fun `reference EMA pulls a weak area up on a good second run`() {
        // Pronouns: 4/10 then 10/10 -> 0.5*1.0 + 0.5*0.4 = 0.70 OK.
        simulate(4 to 10, 10 to 10).let { assertEquals(0.7f, it.first, eps); assertEquals(ReferenceStrengthLevel.OK, it.second) }
    }

    @Test fun `reference EMA eases a strong area down on a bad second run`() {
        // 10/10 then 0/10 -> 0.5*0.0 + 0.5*1.0 = 0.50 OK.
        simulate(10 to 10, 0 to 10).let { assertEquals(0.5f, it.first, eps); assertEquals(ReferenceStrengthLevel.OK, it.second) }
    }

    @Test fun `reference EMA more scenarios`() {
        // 2/10 twice -> stays 0.20 WEAK.
        simulate(2 to 10, 2 to 10).let { assertEquals(0.2f, it.first, eps); assertEquals(ReferenceStrengthLevel.WEAK, it.second) }
        // 6/10 then 9/10 -> 0.5*0.9 + 0.5*0.6 = 0.75 OK.
        simulate(6 to 10, 9 to 10).let { assertEquals(0.75f, it.first, eps); assertEquals(ReferenceStrengthLevel.OK, it.second) }
        // 8/10 twice -> stays 0.80 STRONG.
        simulate(8 to 10, 8 to 10).let { assertEquals(0.8f, it.first, eps); assertEquals(ReferenceStrengthLevel.STRONG, it.second) }
        // 4/10, 4/10, 10/10 -> 0.40 then 0.40 then 0.70 OK.
        simulate(4 to 10, 4 to 10, 10 to 10).let { assertEquals(0.7f, it.first, eps); assertEquals(ReferenceStrengthLevel.OK, it.second) }
    }
}
