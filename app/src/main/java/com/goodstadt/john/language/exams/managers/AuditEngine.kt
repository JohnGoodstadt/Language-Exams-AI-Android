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
    /**
     * @param testScores completed part scores (0-10), keyed by part index 1-4.
     * @param partProgress fraction (0f-1f) of the in-progress part answered so far - drives the
     *   live Confidence rise.
     * @param liveScores correct-so-far for the in-progress part (0-10), keyed by part index. Lets
     *   Readiness climb live as questions are answered, the way Confidence already does. It counts
     *   toward Readiness (accuracy) but NOT toward Confidence's full cap - the in-progress part
     *   still earns Confidence gradually through [partProgress].
     */
    fun calculate(
        testScores: Map<Int, Int>,
        partProgress: Map<Int, Float>,
        liveScores: Map<Int, Int> = emptyMap(),
        confidenceBonus: Int = 0
    ): AuditReport {
        // Downward credit: the parts are ordered easiest-to-hardest (1 Baseline .. 4 B2), so
        // demonstrating a harder part implies competence on the easier ones. Any lower part the
        // learner has no score for is credited with the score of the HIGHEST demonstrated part -
        // self-limiting (a weak hardest score credits the easier parts weakly too). A part's own
        // real score always wins over the credited one.
        //
        // Confidence (coverage) credits only COMPLETED parts; the in-progress part earns its
        // Confidence gradually via partProgress below. Readiness (accuracy) also folds in the
        // in-progress part's live score so it moves with every answer and converges smoothly to
        // the final value on completion.
        val confidenceScores = creditLowerParts(testScores)
        val readinessScores = creditLowerParts(testScores + liveScores)

        var totalConfidence = 0f

        // 1. Add confidence for parts that are finished OR credited as finished (using the caps)
        confidenceScores.keys.forEach { part ->
            totalConfidence += confidenceCap[part] ?: 0f
        }

        // 2. Add confidence for the part currently being played (above the credited range)
        partProgress.forEach { (part, progress) ->
            if (!confidenceScores.containsKey(part)) {
                val cap = confidenceCap[part] ?: 0f
                totalConfidence += (cap * progress)
            }
        }

        // Extra data from redone (v>=2) audits nudges Confidence up a little, still capped at 98.
        val finalConfidence = (totalConfidence.roundToInt() + confidenceBonus).coerceIn(0, 98)

        // 3. Calculate Readiness (Weighted Accuracy) over the effective (credited + live) scores.
        var totalEarnedWeighted = 0f
        var totalPossibleWeighted = 0f
        for (partIndex in 1..TOTAL_PARTS) {
            val weight = weights[partIndex] ?: 1.0f
            val score = readinessScores[partIndex]?.coerceIn(0, 10) ?: 0
            totalEarnedWeighted += score * weight
            totalPossibleWeighted += 10 * weight
        }

        var readinessRaw = if (totalPossibleWeighted > 0) (totalEarnedWeighted / totalPossibleWeighted) * 100 else 0f

        // Penalty is for an actual COMPLETED weak B2 attempt only - never a credited or in-progress one.
        if (testScores[4] != null && testScores[4]!! < 6) {
            readinessRaw -= 5f
        }

        return AuditReport(
            confidence = finalConfidence,
            readiness = readinessRaw.roundToInt().coerceIn(0, 98)
        )
    }

    /**
     * Fills in parts below the highest completed part with that part's score, so completing a
     * harder test carries the easier (untaken) parts with it. Parts with a real score keep it;
     * parts above the highest completed part are left absent. Returns the original map unchanged
     * when nothing has been completed.
     */
    private fun creditLowerParts(testScores: Map<Int, Int>): Map<Int, Int> {
        val maxScoredPart = testScores.keys.maxOrNull() ?: return testScores
        val assumedScore = testScores[maxScoredPart] ?: 0
        return (1..maxScoredPart).associateWith { part -> testScores[part] ?: assumedScore }
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
    // A band is "cleared" once at least this fraction of its questions are correct.
    // With 3/4/3 questions per band this means A2 & B2 need 2 of 3, B1 needs 3 of 4.
    private const val BAND_PASS_RATIO = 0.6f

    /** True when the learner cleared [band]'s pass ratio. Empty/absent bands are not cleared. */
    private fun bandCleared(results: List<Pair<String?, Boolean>>, band: String): Boolean {
        val inBand = results.filter { it.first?.trim()?.uppercase() == band }
        if (inBand.isEmpty()) return false
        return inBand.count { it.second }.toFloat() / inBand.size >= BAND_PASS_RATIO
    }

    /**
     * Places a learner from their banded baseline answers.
     *
     * The baseline quiz mixes CEFR bands (e.g. 3x A2, 4x B1, 3x B2). Rather than a flat
     * correct-count, we look at each band in isolation: a band is "cleared" when the learner
     * gets [BAND_PASS_RATIO] of its questions right (see [bandCleared]).
     *
     * Placement is the learner's "working level": the FIRST band (bottom-up) they have NOT
     * cleared. A passed higher band can never leapfrog a failed lower one - clearing A2 but
     * failing B1 places them at B1 (their current level), even if they happened to pass B2. If
     * every band is cleared they are placed at the top band. Questions whose level is null/blank
     * are ignored. This keeps placement in step with [baselineUnlockCeiling], which also refuses
     * to skip a failed lower band.
     *
     * @param results one (level, isCorrect) pair per answered baseline question.
     * @return "A2" / "B1" / "B2" - the first uncleared band, or the top band when all are cleared.
     */
    fun placeBaselineLevel(results: List<Pair<String?, Boolean>>): String {
        for (band in BASELINE_BANDS) {
            if (!bandCleared(results, band)) return band
        }
        return BASELINE_BANDS.last()
    }

    // Maps each baseline band onto the audit "part index" of the level test it gates open.
    // Clearing the A2 baseline band opens the A2 test (part 2), etc.
    private val BAND_UNLOCKS_PART = mapOf("A2" to 2, "B1" to 3, "B2" to 4)

    /**
     * Decides how far the baseline result unlocks the level tests, using the same per-band
     * pass bar as placement ([BAND_PASS_RATIO]). Bands must be cleared in order from the
     * bottom: clearing A2 unlocks the A2 test; clearing A2+B1 unlocks the A2 and B1 tests; a
     * clean sweep unlocks everything. The first band the learner does NOT clear stops the
     * unlock there - so this agrees with [placeBaselineLevel] whenever the cleared bands are
     * contiguous from A2 (the normal case), and only lags it if a lower band is skipped.
     *
     * @param results one (level, isCorrect) pair per answered baseline question.
     * @return the highest unlocked part index: 1 = baseline only (A2 not cleared),
     *   2 = +A2 test, 3 = +B1 test, 4 = +B2 test.
     */
    fun baselineUnlockCeiling(results: List<Pair<String?, Boolean>>): Int {
        var ceiling = 1 // baseline only
        for (band in BASELINE_BANDS) {
            if (bandCleared(results, band)) {
                ceiling = BAND_UNLOCKS_PART[band] ?: ceiling
            } else {
                break
            }
        }
        return ceiling
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
