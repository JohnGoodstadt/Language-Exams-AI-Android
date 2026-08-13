package com.goodstadt.john.language.exams.packages.Focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.CategoryScore
import com.goodstadt.john.language.exams.data.ReadinessAuditRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
 * Reads the shared (category, level) tally that the Audit and Usage quizzes write into, and
 * exposes it weakest-first for the Focus screen. Raw for now - later this drives a priority view.
 */
@HiltViewModel
class FocusViewModel @Inject constructor(
    private val auditRepository: ReadinessAuditRepository
) : ViewModel() {

    val rows: StateFlow<List<FocusRow>> = auditRepository.categoryScores
        .map { scores ->
            scores.entries
                .map { (key, score) ->
                    // Keys are stored as "Category|Level".
                    val parts = key.split("|", limit = 2)
                    FocusRow(
                        category = parts.getOrElse(0) { key },
                        level = parts.getOrElse(1) { "" },
                        score = score
                    )
                }
                .sortedWith(
                    compareByDescending<FocusRow> { it.weakness }.thenByDescending { it.total }
                )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
