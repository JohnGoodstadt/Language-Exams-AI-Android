package com.goodstadt.john.language.exams.managers

import com.goodstadt.john.language.exams.data.ReadinessAuditRepository
import com.goodstadt.john.language.exams.data.repository.VocabQuizRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of the comfort-band Confidence/Readiness dials (see [AuditEngine.computeDials]), shared by
 * every screen that shows them (Progress, Focus, Readiness Audit) so they always agree. It gathers the
 * per-band evidence once - baseline placement, Verify level tests, and practice (70% Vocab section
 * first-perfect/attempts + 30% in-context quiz answers from the shared Usage/Grammar/baseline tally) - and
 * re-anchors the result to whatever vocab level the caller passes as the "loaded" level.
 */
@Singleton
class ReadinessDialsProvider @Inject constructor(
    private val auditRepository: ReadinessAuditRepository,
    private val vocabQuizRepository: VocabQuizRepository
) {
    /**
     * Dials re-anchored to [loadedLevelFlow] (the currently loaded vocab level, e.g. "B1"). Re-emits when
     * the loaded level changes, a test/quiz answer is recorded, or a section quiz's mastery changes.
     */
    fun dials(loadedLevelFlow: Flow<String>): Flow<AuditEngine.Dials> =
        combine(
            loadedLevelFlow,
            auditRepository.baselineLevel,
            auditRepository.auditScores,
            auditRepository.categoryScores,
            vocabQuizRepository.dataUpdateEvents.onStart { emit(Unit) }
        ) { loadedLevel, baselineLevel, partScores, categoryScores, _ ->
            val bands = (0..3).map { i -> bandInput(i, partScores, categoryScores) }
            AuditEngine.computeDials(
                bands = bands,
                placementIndex = AuditEngine.bandIndex(baselineLevel ?: "A1"),
                loadedIndex = AuditEngine.bandIndex(loadedLevel)
            )
        }

    /** Gather one band's evidence (band index 0=A1 .. 3=B2). */
    private fun bandInput(
        bandIndex: Int,
        partScores: Map<Int, Int>,
        categoryScores: Map<String, com.goodstadt.john.language.exams.data.CategoryScore>
    ): AuditEngine.BandInput {
        val label = AuditEngine.bandLabel(bandIndex)

        // Verify level test for this band: A2->part2, B1->part3, B2->part4; A1 has no audit band.
        val testCorrect = when (bandIndex) { 1 -> partScores[2]; 2 -> partScores[3]; 3 -> partScores[4]; else -> null }

        // In-context quiz evidence at this band (baseline + Usage + Grammar), from the shared tally
        // keyed "category|level".
        var answered = 0
        var correct = 0
        categoryScores.forEach { (key, s) ->
            if (key.substringAfterLast('|').equals(label, ignoreCase = true)) {
                answered += s.correct + s.incorrect + s.dontKnow
                correct += s.correct
            }
        }

        // Vocab section evidence at this band: first-perfect (>=1 flawless attempt) and attempted.
        var perfect = 0
        var attempted = 0
        vocabQuizRepository.getAllCategoryMasteryStates()
            .filter { it.level.equals(label, ignoreCase = true) }
            .forEach { st ->
                val attempts = vocabQuizRepository.getCategoryAttempts(st.category, st.level)
                if (attempts.isNotEmpty()) attempted++
                if (attempts.any { it.flawless }) perfect++
            }

        return AuditEngine.BandInput(
            levelTestCorrect = testCorrect,
            quizAnswered = answered,
            quizCorrect = correct,
            vocabSectionsFirstPerfect = perfect,
            vocabSectionsAttempted = attempted,
            sectionsAtLevel = AuditEngine.EXPECTED_SECTIONS_PER_LEVEL
        )
    }
}
