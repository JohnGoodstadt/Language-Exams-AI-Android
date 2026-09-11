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

    // ---------------------------------------------------------------------------------------------
    // Per-level dials (comfort-band model)
    //
    // A single underlying position U in 0..400 (0-99 = A1, 100-199 = A2, 200-299 = B1, 300-399 = B2)
    // tracks how far up the CEFR ladder the learner is. The two visible dials RE-ANCHOR that position
    // to whatever vocab level is currently LOADED, so:
    //   - loading a level BELOW your comfort band shows ~98% (you're clearly above it),
    //   - loading your comfort band shows your real progress through it (0..98%),
    //   - loading a level ABOVE shows a low number.
    //
    // Each band has a completion 0..1 for each axis, taken as the MAX of these floors (numbers never drop):
    //   - the Verify level test (A2/B1/B2): score rounded 3->0 .. 7->1,
    //   - practice = 70% Vocab section + 30% in-context quiz (Usage/Grammar/baseline answers):
    //       Readiness uses the section FIRST-PERFECT fraction + quiz accuracy,
    //       Confidence uses the section ATTEMPTED fraction + quiz coverage.
    // "Downward crediting": every band below the comfort band is treated as complete, so an unfinished
    // lower level never holds you back once you've demonstrated a higher one. The comfort band is the
    // highest band whose Readiness completion >= PROMOTION_THRESHOLD, floored at the baseline placement.
    // Everything is tunable via the constants below.
    // ---------------------------------------------------------------------------------------------

    /** Ceiling for the displayed dials (0..98). */
    const val LEVEL_MAX = 98
    /** Underlying scale: each CEFR band spans this many points (A1 0-99 .. B2 300-399; full = 400). */
    const val BAND_SPAN = 100
    /** Default section (Vocab) categories per level - the practice denominator (tunable; ~14-18 in content). */
    const val EXPECTED_SECTIONS_PER_LEVEL = 16
    /** In-context quiz answers at a level that count as full quiz coverage (~one quiz's worth). */
    private const val EXPECTED_QUIZ_ANSWERS = 10
    /** Minimum quiz answers before we trust the quiz accuracy (avoids 1/1 = 100%). */
    private const val MIN_ANSWERS_FOR_ACCURACY = 3
    /** Practice split: Vocab sections vs in-context quizzes (Usage/Grammar). */
    private const val VOCAB_WEIGHT = 0.7f
    private const val QUIZ_WEIGHT = 0.3f
    /** Readiness completion a band needs to become the comfort band (and promote the bands below it). */
    const val PROMOTION_THRESHOLD = 0.5f
    /** Taking a Verify level test is worth this much Confidence coverage for its band. */
    private const val TEST_CONFIDENCE = 0.7f

    /** Per-band evidence gathered by the caller (list ordered A1, A2, B1, B2). */
    data class BandInput(
        val levelTestCorrect: Int?,          // Verify test score 0..10 for this band, or null (not taken / A1)
        val quizAnswered: Int,               // in-context quiz answers at this band (Usage/Grammar/baseline)
        val quizCorrect: Int,
        val vocabSectionsFirstPerfect: Int,  // sections at this band with >=1 flawless completion
        val vocabSectionsAttempted: Int,     // sections at this band attempted at all
        val sectionsAtLevel: Int             // denominator (real count if known, else EXPECTED_SECTIONS_PER_LEVEL)
    )

    /** The two display dials (0..98) for the loaded level, plus the comfort band and underlying position. */
    data class Dials(
        val confidence: Int,
        val readiness: Int,
        val comfortBandIndex: Int,           // 0..3 (A1..B2)
        val underlyingReadiness: Int         // 0..400
    )

    /** Raw (pre-crediting) Readiness/Confidence completions (0..1) for one band. */
    private fun bandCompletions(b: BandInput): Pair<Float, Float> {
        val sections = if (b.sectionsAtLevel > 0) b.sectionsAtLevel else EXPECTED_SECTIONS_PER_LEVEL
        val perfectFrac = (b.vocabSectionsFirstPerfect.toFloat() / sections).coerceIn(0f, 1f)
        val attemptFrac = (b.vocabSectionsAttempted.toFloat() / sections).coerceIn(0f, 1f)

        val quizAccuracy =
            if (b.quizAnswered >= MIN_ANSWERS_FOR_ACCURACY && b.quizAnswered > 0)
                (b.quizCorrect.toFloat() / b.quizAnswered).coerceIn(0f, 1f)
            else 0f
        val quizCoverage = (b.quizAnswered.toFloat() / EXPECTED_QUIZ_ANSWERS).coerceIn(0f, 1f)

        // Level test: 3/10 -> 0, 7/10 -> 1 (your onboarding rounding), linear between.
        val testRead = b.levelTestCorrect?.let { ((it - 3).toFloat() / 4f).coerceIn(0f, 1f) } ?: 0f
        val testConf = if (b.levelTestCorrect != null) TEST_CONFIDENCE else 0f

        val readiness =
            maxOf(testRead, VOCAB_WEIGHT * perfectFrac + QUIZ_WEIGHT * quizAccuracy).coerceIn(0f, 1f)
        val confidence =
            maxOf(testConf, VOCAB_WEIGHT * attemptFrac + QUIZ_WEIGHT * quizCoverage).coerceIn(0f, 1f)
        return readiness to confidence
    }

    /**
     * Re-anchored Confidence & Readiness dials for the currently loaded level [loadedIndex] (0..3),
     * from per-band evidence and the baseline [placementIndex]. See the block comment above.
     */
    fun computeDials(bands: List<BandInput>, placementIndex: Int, loadedIndex: Int): Dials {
        val cRead = FloatArray(4)
        val cConf = FloatArray(4)
        bands.forEachIndexed { i, input ->
            if (i in 0..3) {
                val (r, c) = bandCompletions(input)
                cRead[i] = r
                cConf[i] = c
            }
        }

        // Comfort band = highest band demonstrated (Readiness >= threshold), never below placement.
        var comfort = placementIndex.coerceIn(0, 3)
        for (i in 3 downTo 0) {
            if (cRead[i] >= PROMOTION_THRESHOLD) { comfort = maxOf(comfort, i); break }
        }

        // Underlying position: bands below comfort count as complete (downward crediting).
        var uRead = 0f
        for (i in 0..3) uRead += if (i < comfort) 1f else cRead[i]
        val underlyingReadiness = (uRead * BAND_SPAN).roundToInt().coerceIn(0, 4 * BAND_SPAN)

        // Display re-anchored to the loaded level: below comfort -> maxed; at/above -> that band's completion.
        val loaded = loadedIndex.coerceIn(0, 3)
        fun dial(c: FloatArray): Int =
            if (loaded < comfort) LEVEL_MAX
            else (c[loaded] * LEVEL_MAX).roundToInt().coerceIn(0, LEVEL_MAX)

        return Dials(
            confidence = dial(cConf),
            readiness = dial(cRead),
            comfortBandIndex = comfort,
            underlyingReadiness = underlyingReadiness
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

    // Baseline test = 2x A1, 4x A2, 4x B1 (no B2). Placement uses per-band correct counts with
    // lucky-guess rounding (a lone correct -> 0) and an unlucky allowance (3/4 still "cleared").
    // A1 is binary: both right, or it doesn't count.

    /**
     * Band index (0=A1, 1=A2, 2=B1, 3=B2) the baseline places the learner into, from per-band
     * correct counts. See the decision rules inline.
     */
    fun placeBaseline(a1Correct: Int, a2Correct: Int, b1Correct: Int): Int {
        val a2 = if (a2Correct == 1) 0 else a2Correct    // a lone correct is a lucky guess -> discount
        val b1 = if (b1Correct == 1) 0 else b1Correct
        val a1Pass = a1Correct >= 2                       // A1 is binary (2 of 2)
        val a2Clear = a2 >= 3                             // one wrong is unlucky -> still cleared
        val b1Clear = b1 >= 3
        val strong = !a1Pass && a2Clear && b1Clear        // fluffed A1 but clearly not a beginner

        if (!a1Pass && !strong) return 0                  // A1 (beginner safety net)

        if (a2Clear) {
            return when {
                b1Correct == 4 -> 3                       // aced B1 (no B2 questions) -> recommend B2
                b1 >= 2 -> 2                              // some real B1 -> B1
                else -> 1                                 // no real B1 -> A2
            }
        }
        // a1Pass with A2 not cleared (a `strong` learner always has a2Clear, so isn't reached here).
        // Contiguity: a partial/failed A2 caps placement at A2/A1 regardless of B1 ("weight to A2").
        return if (a2 == 2) 1 else 0                      // partial A2 -> A2, else -> A1
    }

    /**
     * Highest audit part index the baseline unlocks for [placementIndex]: 1 = baseline only,
     * 2 = +A2 test, 3 = +B1 test, 4 = +B2 test. Contiguous from the placement band.
     */
    fun baselineUnlockCeiling(placementIndex: Int): Int = (placementIndex + 1).coerceIn(1, 4)

    /** CEFR label for a band index (0=A1 .. 3=B2). */
    fun bandLabel(index: Int): String = when (index.coerceIn(0, 3)) {
        0 -> "A1"; 1 -> "A2"; 2 -> "B1"; else -> "B2"
    }

    /** Band index for a CEFR label ("A1".."B2"); defaults to A1 (0) for anything unexpected. */
    fun bandIndex(level: String): Int = when (level.trim().uppercase()) {
        "A2" -> 1; "B1" -> 2; "B2" -> 3; else -> 0
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
