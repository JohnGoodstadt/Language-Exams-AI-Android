package com.goodstadt.john.language.exams.managers

import kotlin.math.roundToInt

data class AuditReport(
    val confidence: Int,
    val readiness: Int
)

object AuditEngine {
    // 1. Define Weights
    private const val WEIGHT_PART_1 = 1.0f
    private const val WEIGHT_PART_2 = 1.2f
    private const val WEIGHT_PART_3 = 1.4f
    private const val WEIGHT_PART_4 = 1.4f

    // 2. Define Confidence steps
    private val confidenceLevels = mapOf(1 to 40, 2 to 65, 3 to 85, 4 to 98)
    private val weights = mapOf(1 to WEIGHT_PART_1, 2 to WEIGHT_PART_2, 3 to WEIGHT_PART_3, 4 to WEIGHT_PART_4)

    /**
     * Calculates Confidence and Readiness based on performance.
     * @param testScores Map of PartIndex (1-4) to Score (0-10)
     */
    fun calculate(testScores: Map<Int, Int>): AuditReport {
        if (testScores.isEmpty()) return AuditReport(0, 0)

        var totalEarnedWeighted = 0f
        var totalPossibleWeighted = 0f
        val completedParts = testScores.keys.filter { it in 1..4 }

        // Sum weighted scores for attempted parts
        completedParts.forEach { partIndex ->
            val score = testScores[partIndex]?.coerceIn(0, 10) ?: 0
            val weight = weights[partIndex] ?: 1.0f

            totalEarnedWeighted += (score * weight)
            totalPossibleWeighted += (10 * weight)
        }

        // Calculate confidence based on volume (how many tests done)
        val confidence = confidenceLevels[completedParts.size] ?: 0

        // Calculate readiness based on accuracy
        var readinessRaw = (totalEarnedWeighted / totalPossibleWeighted) * 100

        // Apply "Engine Penalty": -5% if Part 4 is completed but score is < 6
        val part4Score = testScores[4]
        if (part4Score != null && part4Score < 6) {
            readinessRaw -= 5f
        }

        // Apply global constraints: Max readiness is 98%, min is 0%
        val finalReadiness = readinessRaw.roundToInt().coerceIn(0, 98)

        return AuditReport(
            confidence = confidence,
            readiness = finalReadiness
        )
    }
}