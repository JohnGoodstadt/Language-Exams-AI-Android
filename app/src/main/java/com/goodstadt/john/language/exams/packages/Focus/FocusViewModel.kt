package com.goodstadt.john.language.exams.packages.Focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.CategoryScore
import com.goodstadt.john.language.exams.data.ReadinessAuditRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.managers.AuditEngine
import com.goodstadt.john.language.exams.packages.ReadinessAudit.AuditStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One line for the Focus screen: a grammar category at a CEFR level, with its tally. */
data class FocusRow(
    val category: String,
    val level: String,
    val score: CategoryScore
) {
    // "Needs work" weight = things gotten wrong or admitted not knowing.
    val weakness: Int get() = score.incorrect + score.dontKnow
    val total: Int get() = score.correct + score.incorrect + score.dontKnow
}

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
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val currentLevel: StateFlow<String> = userPreferencesRepository.selectedSkillLevelFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LanguageConfig.defaulSkillLevel)

    val allRows: StateFlow<List<FocusRow>> = auditRepository.categoryScores
        .map { toRows(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val focusRows: StateFlow<List<FocusRow>> =
        combine(auditRepository.categoryScores, currentLevel) { scores, level ->
            val ceiling = levelRank(level)
            toRows(scores).filter { row ->
                row.weakness > 0 && levelRank(row.level).let { it in 1..ceiling }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
