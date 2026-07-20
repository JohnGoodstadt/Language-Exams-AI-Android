package com.goodstadt.john.language.exams.managers

import kotlin.math.roundToInt

data class AuditReport(
    val confidence: Int,
    val readiness: Int
)

object AuditEngine {
    // 1. Define Weights (used for Readiness only - how much each part counts toward accuracy)
    private const val WEIGHT_PART_1 = 1.0f
    private const val WEIGHT_PART_2 = 1.2f
    private const val WEIGHT_PART_3 = 1.4f
    private const val WEIGHT_PART_4 = 1.4f
    private const val TOTAL_PARTS = 4

    private val weights = mapOf(1 to WEIGHT_PART_1, 2 to WEIGHT_PART_2, 3 to WEIGHT_PART_3, 4 to WEIGHT_PART_4)

    /**
     * Calculates Confidence and Readiness based on performance.
     * @param testScores Map of PartIndex (1-4) to Score (0-10), used for Readiness (accuracy).
     *   A part with no entry counts as a score of 0 - it still occupies its full weight in the
     *   denominator, so Readiness can't run ahead of how much of the exam has actually been
     *   demonstrated (e.g. acing 1 of 10 questions in Part 1 should not read as "exam ready").
     * @param partProgress Map of PartIndex (1-4) to fraction (0f-1f) of that part's questions
     *   answered so far, used for Confidence (coverage). A part with no entry counts as 0.
     */
    fun calculate(testScores: Map<Int, Int>, partProgress: Map<Int, Float>): AuditReport {
        // Confidence: a plain average of how much of each part has been answered - grows
        // smoothly with every question, rather than jumping in big steps per part touched.
        val totalProgress = (1..TOTAL_PARTS).sumOf { part ->
            (partProgress[part] ?: 0f).coerceIn(0f, 1f).toDouble()
        }
        val confidence = ((totalProgress / TOTAL_PARTS) * 100).roundToInt().coerceIn(0, 100)

        // Readiness: weighted accuracy prorated across ALL 4 parts, not just the ones touched
        // so far. An untouched (or barely-started) part contributes little/no earned score but
        // still counts its full weight below the line.
        var totalEarnedWeighted = 0f
        var totalPossibleWeighted = 0f
        for (partIndex in 1..TOTAL_PARTS) {
            val weight = weights[partIndex] ?: 1.0f
            val score = testScores[partIndex]?.coerceIn(0, 10) ?: 0
            totalEarnedWeighted += score * weight
            totalPossibleWeighted += 10 * weight
        }

        var readinessRaw = if (totalPossibleWeighted > 0) (totalEarnedWeighted / totalPossibleWeighted) * 100 else 0f

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

    /** Descriptive sub-label shown under the Confidence percentage. */
    fun getConfidenceLabel(confidence: Int): String {
        return when (confidence) {
            0 -> "Not Started"
            in 1..40 -> "Getting Started"
            in 41..84 -> "In Progress"
            else -> "Nearly Complete"
        }
    }

    /** Verdict tier shown under Exam Readiness. */
    fun getReadinessVerdict(readiness: Int): String {
        return when (readiness) {
            0 -> "Let's Get Started"
            in 1..35 -> "Foundational Work Needed"
            in 36..55 -> "Borderline B1 Candidate"
            in 56..85 -> "B1/B2 Ready"
            else -> "Elite Performance"
        }
    }
}
