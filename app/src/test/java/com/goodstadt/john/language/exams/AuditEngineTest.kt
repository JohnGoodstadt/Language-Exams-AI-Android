package com.goodstadt.john.language.exams

import com.goodstadt.john.language.exams.managers.AuditEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class AuditEngineTest {

    @Test
    fun `Part 1 perfect score - result should have 40 percent confidence`() {
        val scores = mapOf(1 to 10)
        val result = AuditEngine.calculate(scores)

        assertEquals(40, result.confidence)
        // (10/10) * 100 = 100, then coerced to 98
        assertEquals(98, result.readiness)
    }

    @Test
    fun `Part 1 zero score - result should have 40 percent confidence and 0 readiness`() {
        val scores = mapOf(1 to 0)
        val result = AuditEngine.calculate(scores)

        assertEquals(40, result.confidence)
        assertEquals(0, result.readiness)
    }

    @Test
    fun `Mixed performance across all 4 parts - calculates weighted average`() {
        // Weighted logic:
        // P1: 10/10 (10pts)
        // P2: 5/10  (6pts)
        // P3: 10/10 (14pts)
        // P4: 10/10 (14pts)
        // Total Earned: 44. Total Possible: 50. Readiness: 88%
        val scores = mapOf(1 to 10, 2 to 5, 3 to 10, 4 to 10)
        val result = AuditEngine.calculate(scores)

        assertEquals(98, result.confidence)
        assertEquals(88, result.readiness)
    }

    @Test
    fun `Part 4 failure applies penalty - readiness drops by 5 percent`() {
        // All parts perfect (50/50 points) except Part 4 is 5/10 (7/14 weighted pts)
        // Earned: 10 + 12 + 14 + 7 = 43.
        // 43 / 50 = 86%
        // Penalty: -5% because P4 < 6
        // Final: 81%
        val scores = mapOf(1 to 10, 2 to 10, 3 to 10, 4 to 5)
        val result = AuditEngine.calculate(scores)

        assertEquals(98, result.confidence)
        assertEquals(81, result.readiness)
    }

    @Test
    fun `Part 4 bare pass - no penalty applied`() {
        // Part 4 is 6/10 (Bare pass)
        // Earned: 10+12+14 + (6 * 1.4 = 8.4) = 44.4
        // 44.4 / 50 = 88.8% -> 89%
        val scores = mapOf(1 to 10, 2 to 10, 3 to 10, 4 to 6)
        val result = AuditEngine.calculate(scores)

        assertEquals(98, result.confidence)
        assertEquals(89, result.readiness)
    }

    @Test
    fun `Readiness never exceeds 98 percent`() {
        val scores = mapOf(1 to 10, 2 to 10, 3 to 10, 4 to 10)
        val result = AuditEngine.calculate(scores)

        assertEquals(98, result.readiness)
    }

    @Test
    fun `Readiness does not get to B1 level`() {
        //user not ready for B1
        val scores = mapOf(1 to 5, 2 to 4, 3 to 3, 4 to 1)
        val result = AuditEngine.calculate(scores)

        assertEquals(98, result.confidence)
        assertEquals(26, result.readiness)
    }
    @Test
    fun `Readiness is in B1 level`() {
        //user not ready for B1
        val scores = mapOf(1 to 10, 2 to 10, 3 to 5, 4 to 0)
        val result = AuditEngine.calculate(scores)

        assertEquals(98, result.confidence)
        assertEquals(53, result.readiness)
    }
    @Test
    fun `Empty map returns zero stats`() {
        val result = AuditEngine.calculate(emptyMap())
        assertEquals(0, result.confidence)
        assertEquals(0, result.readiness)
    }
}