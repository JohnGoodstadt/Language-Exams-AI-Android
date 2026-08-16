package com.goodstadt.john.language.exams.packages.Focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.CategoryQuizRepository
import com.goodstadt.john.language.exams.data.CategoryScore
import com.goodstadt.john.language.exams.data.GrammarRow
import com.goodstadt.john.language.exams.data.ReadinessAuditRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.managers.AuditEngine
import com.goodstadt.john.language.exams.packages.ReadinessAudit.AuditStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** One line for the Focus screen: a grammar category at a CEFR level, with its tally. */
data class FocusRow(
    val category: String,
    val level: String,
    val score: CategoryScore
) {
    // True if it's a weakness at all: any wrong OR any admitted "don't know" (correct count ignored).
    val weakness: Int get() = score.incorrect + score.dontKnow
    val total: Int get() = score.correct + score.incorrect + score.dontKnow

    // How well the learner scores in this category, 0f..1f (e.g. 3 of 10 -> 0.3). Lower = weaker.
    // A don't-know counts against you just like a wrong answer. No answers yet -> treated as 1f
    // (nothing to worry about), though such rows are filtered out before ranking anyway.
    val accuracy: Float get() = if (total == 0) 1f else score.correct.toFloat() / total
}

/** What the Focus screen should show. */
sealed interface FocusUiState {
    /** Not assessed yet (baseline not done) -> nudge them to take the audit. */
    data object NotEnoughData : FocusUiState
    /** Assessed, but nothing weak at/below their level. */
    data object AllCaughtUp : FocusUiState
    /** The prioritised weak areas (already capped and sorted). */
    data class Priorities(val rows: List<FocusRow>) : FocusUiState
}

/** Which (category, level) the Focus screen is practising right now - drives the quiz bottom sheet. */
data class PracticeTarget(val category: String, val level: String)

/**
 * Focus page data. Reads the shared (category, level) tally the Audit/Usage quizzes write into and
 * produces:
 *  - [focusRows]: the priority list = categories at/below the learner's current level that have at
 *    least one incorrect or don't-know. Empty => "not enough data yet" (show the take-the-audit prompt).
 *  - [allRows]: everything, unfiltered, for the debug section at the bottom.
 *  - [auditStats]: drives the prompt's "Verify Part N" button label.
 */
@HiltViewModel
class FocusViewModel @Inject constructor(
    private val auditRepository: ReadinessAuditRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val categoryQuizRepository: CategoryQuizRepository
) : ViewModel() {

    /** The (category, level) whose quiz sheet is open, or null when none is. */
    private val _practiceTarget = MutableStateFlow<PracticeTarget?>(null)
    val practiceTarget: StateFlow<PracticeTarget?> = _practiceTarget.asStateFlow()

    /** The canonical grammar grid (category × level) for the "all categories" browse list. */
    val grammarCategories: List<GrammarRow> = categoryQuizRepository.grammarCatalog()

    /** Practice a weak category from the priority list. */
    fun practiceCategory(row: FocusRow) {
        _practiceTarget.value = PracticeTarget(row.category, row.level)
    }

    /** Practice any category from the "all grammar categories" browse list. */
    fun practiceGrammar(row: GrammarRow) {
        _practiceTarget.value = PracticeTarget(row.category, row.level)
    }

    fun dismissPractice() {
        _practiceTarget.value = null
    }

    val currentLevel: StateFlow<String> = userPreferencesRepository.selectedSkillLevelFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LanguageConfig.defaulSkillLevel)

    val allRows: StateFlow<List<FocusRow>> = auditRepository.categoryScores
        .map { toRows(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val focusState: StateFlow<FocusUiState> =
        combine(
            auditRepository.categoryScores,
            currentLevel,
            auditRepository.auditScores // to know if the baseline (part 1) has been completed
        ) { scores, level, partScores ->
            val ceiling = levelRank(level)
            val prioritised = toRows(scores)
                .filter { it.weakness > 0 && levelRank(it.level) in 1..ceiling }
                // Weakest first: lowest score (e.g. 3/10 above 4/10) at the top. Ties broken by more
                // admitted don't-knows, then more evidence (bigger sample), then name for stability.
                .sortedWith(
                    compareBy<FocusRow> { it.accuracy }
                        .thenByDescending { it.score.dontKnow }
                        .thenByDescending { it.total }
                        .thenBy { it.category }
                )
                .take(MAX_FOCUS_ROWS)

            when {
                prioritised.isNotEmpty() -> FocusUiState.Priorities(prioritised)
                partScores.containsKey(1) -> FocusUiState.AllCaughtUp   // baseline done, nothing weak
                else -> FocusUiState.NotEnoughData                      // not assessed yet
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FocusUiState.NotEnoughData)

    val auditStats: StateFlow<AuditStats> =
        combine(auditRepository.auditDataFlow, auditRepository.confidenceBonus) { data, bonus ->
            val report = AuditEngine.calculate(
                testScores = data.scores,
                partProgress = data.activeProgress,
                confidenceBonus = bonus
            )
            AuditStats(report.confidence, report.readiness)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuditStats(0, 0))

    val auditVersion: StateFlow<Int> = auditRepository.auditVersion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    private fun toRows(scores: Map<String, CategoryScore>): List<FocusRow> =
        scores.entries
            .map { (key, score) ->
                val parts = key.split("|", limit = 2) // keys stored as "Category|Level"
                FocusRow(parts.getOrElse(0) { key }, parts.getOrElse(1) { "" }, score)
            }
            .sortedWith(compareByDescending<FocusRow> { it.weakness }.thenByDescending { it.total })

    companion object {
        /** Never show more than this many priority areas - keep it focused. */
        const val MAX_FOCUS_ROWS = 5

        /** CEFR ordering A1 < A2 < B1 < B2. Unknown levels rank high so they're excluded from focus. */
        fun levelRank(level: String): Int = when (level.trim().uppercase()) {
            "A1" -> 1
            "A2" -> 2
            "B1" -> 3
            "B2" -> 4
            else -> 99
        }
    }
}
