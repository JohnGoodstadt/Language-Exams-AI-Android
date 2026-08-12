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
    private val confidenceCap = mapOf(1 to 40f, 2 to 25f, 3 to 20f, 4 to 13f)

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
        var totalConfidence = 0f

        // 1. Add confidence for parts that are fully finished (using the caps)
        testScores.keys.forEach { part ->
            totalConfidence += confidenceCap[part] ?: 0f
        }

        // 2. Add confidence for the part currently being played
        partProgress.forEach { (part, progress) ->
            if (!testScores.containsKey(part)) {
                val cap = confidenceCap[part] ?: 0f
                totalConfidence += (cap * progress)
            }
        }

        // ✅ FIX: Use 'totalConfidence' directly.
        // Do NOT recalculate 'confidence' using totalProgress/TOTAL_PARTS here.
        val finalConfidence = totalConfidence.roundToInt().coerceIn(0, 98)

        // 3. Calculate Readiness (Weighted Accuracy)
        var totalEarnedWeighted = 0f
        var totalPossibleWeighted = 0f
        for (partIndex in 1..TOTAL_PARTS) {
            val weight = weights[partIndex] ?: 1.0f
            val score = testScores[partIndex]?.coerceIn(0, 10) ?: 0
            totalEarnedWeighted += score * weight
            totalPossibleWeighted += 10 * weight
        }

        var readinessRaw = if (totalPossibleWeighted > 0) (totalEarnedWeighted / totalPossibleWeighted) * 100 else 0f

        if (testScores[4] != null && testScores[4]!! < 6) {
            readinessRaw -= 5f
        }

        return AuditReport(
            confidence = finalConfidence,
            readiness = readinessRaw.roundToInt().coerceIn(0, 98)
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

    // Ordering of the CEFR bands the baseline audit places into, lowest to highest.
    private val BASELINE_BANDS = listOf("A2", "B1", "B2")
    // A band is "passed" once at least this fraction of its questions are correct.
    // With 3/4/3 questions per band this means A2 & B2 need 2 of 3, B1 needs 3 of 4.
    private const val BAND_PASS_RATIO = 0.6f

    /**
     * Places a learner from their banded baseline answers.
     *
     * The baseline quiz mixes CEFR bands (e.g. 3x A2, 4x B1, 3x B2). Rather than a flat
     * correct-count, we look at each band in isolation: a band is "passed" when the learner
     * gets [BAND_PASS_RATIO] of its questions right. The placement is the highest band they
     * pass (so failing every B2 but passing B1 lands them at B1; passing 2 of 3 B2s keeps
     * them at B2). Questions whose level is null/blank are ignored.
     *
     * @param results one (level, isCorrect) pair per answered baseline question.
     * @return "A2" / "B1" / "B2" - defaults to the lowest band when nothing is passed.
     */
    fun placeBaselineLevel(results: List<Pair<String?, Boolean>>): String {
        var placement = BASELINE_BANDS.first()
        for (band in BASELINE_BANDS) {
            val inBand = results.filter { it.first?.trim()?.uppercase() == band }
            if (inBand.isEmpty()) continue
            val correct = inBand.count { it.second }
            val passed = correct.toFloat() / inBand.size >= BAND_PASS_RATIO
            if (passed) placement = band
        }
        return placement
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
