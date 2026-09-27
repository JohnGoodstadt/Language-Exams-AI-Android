package com.goodstadt.john.language.exams.data.strength

/**
 * Pure, dependency-free scoring math behind [ReferenceStrengthRepository] (the running per-area mark and
 * its Weak/OK/Strong bucket). Kept separate from the disk-backed store so it can be unit-tested directly
 * and stays byte-for-byte in step with the iOS `ReferenceStrengthMath`. Change constants in BOTH.
 */
object ReferenceStrengthMath {
    const val ALPHA = 0.5f        // EMA weight for the newest run (higher = more responsive)
    const val MIN_ANSWERED = 3    // below this many lifetime answers an area stays UNTESTED
    const val WEAK_CEILING = 0.5f // mark < 0.50 -> WEAK
    const val STRONG_FLOOR = 0.8f // mark >= 0.80 -> STRONG (in between -> OK)

    /**
     * Blend one finished run ([correct]/[answered]) into the running mark. The first run (no previous mark,
     * or zero prior attempts) seeds the mark outright; later runs move it by [ALPHA]. Returns the previous
     * mark unchanged when [answered] <= 0.
     */
    fun blend(previousMark: Float?, previousAttempts: Int, correct: Int, answered: Int): Float {
        if (answered <= 0) return previousMark ?: 0f
        val pct = (correct.toFloat() / answered).coerceIn(0f, 1f)
        return if (previousMark == null || previousAttempts == 0) pct
        else ALPHA * pct + (1 - ALPHA) * previousMark
    }

    /** Bucket a running mark, given how much evidence backs it. */
    fun level(mark: Float, totalAnswered: Int, attempts: Int): ReferenceStrengthLevel = when {
        attempts == 0 || totalAnswered < MIN_ANSWERED -> ReferenceStrengthLevel.UNTESTED
        mark < WEAK_CEILING -> ReferenceStrengthLevel.WEAK
        mark < STRONG_FLOOR -> ReferenceStrengthLevel.OK
        else -> ReferenceStrengthLevel.STRONG
    }
}
