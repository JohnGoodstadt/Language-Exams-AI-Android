package com.goodstadt.john.language.exams.packages.Focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.CategoryQuizRepository
import com.goodstadt.john.language.exams.data.CategoryScore
import com.goodstadt.john.language.exams.data.AnswerOutcome
import com.goodstadt.john.language.exams.data.ReadinessAuditRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.managers.AuditEngine
import com.goodstadt.john.language.exams.packages.ReadinessAudit.AuditStats
import com.goodstadt.john.language.exams.packages.UsageQuiz.QuizQuestion
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

    // Priority = don't-knows (explicit "I don't know", weighted heavier) + incorrects (possible slip).
    // Correct answers never lower this. Higher = nearer the top of the list.
    val priorityScore: Int get() = score.dontKnow * DONT_KNOW_WEIGHT + score.incorrect * INCORRECT_WEIGHT

    companion object {
        const val DONT_KNOW_WEIGHT = 3
        const val INCORRECT_WEIGHT = 1
    }
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

/**
 * A live Focus practice quiz: the pooled questions for one weak (category, level), plus where the
 * learner is up to. Answers are routed to the shared category tally (not word-mastery), so practising
 * a weak area updates the very list it came from.
 */
data class FocusQuizSession(
    val category: String,
    val level: String,
    val questions: List<QuizQuestion>,
    val index: Int = 0,
    /** First-answer correctness per question index - drives the progress dots and Correct count. */
    val answers: Map<Int, Boolean> = emptyMap(),
    /** Total taps (a question can be retried) - shown as "Tries". */
    val tries: Int = 0,
    /** Indices already written to the tally, so a retry never double-counts. */
    val recorded: Set<Int> = emptySet()
) {
    val current: QuizQuestion? get() = questions.getOrNull(index)
    val correct: Int get() = answers.count { it.value }
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
    private val userPreferencesRepository: UserPreferencesRepository,
    private val categoryQuizRepository: CategoryQuizRepository
) : ViewModel() {

    /** The active practice quiz (bottom sheet), or null when none is open. */
    private val _focusQuiz = MutableStateFlow<FocusQuizSession?>(null)
    val focusQuiz: StateFlow<FocusQuizSession?> = _focusQuiz.asStateFlow()

    /**
     * Practice a weak category: pull the pooled questions for this (category, level) and open the
     * quiz sheet. Questions carry their own fileFormat (7 = fill-blank, 10 = choose-the-answer); the
     * sheet renders both. A category with no pooled questions just no-ops.
     */
    fun practiceCategory(row: FocusRow) {
        viewModelScope.launch {
            val questions = categoryQuizRepository.quizForCategory(row.category, row.level)
            val formats = questions.map { it.fileFormat }.toSet()
            Timber.d(
                "FOCUS-PRACTICE category=\"${row.category}\" level=\"${row.level}\" -> " +
                    "${questions.size} questions ready (formats=$formats)."
            )
            if (questions.isNotEmpty()) {
                _focusQuiz.value = FocusQuizSession(row.category, row.level, questions)
            }
        }
    }

    /**
     * Record an answer to the current Focus question. Every tap counts as a try; the outcome is
     * written to the category tally only once per question (first attempt), mirroring how the audit
     * scores a question. This is the "saving of scores" change: Focus practice feeds the shared
     * (category, level) tally rather than word-mastery.
     */
    fun answerFocusQuiz(isCorrect: Boolean) {
        val session = _focusQuiz.value ?: return
        val idx = session.index
        val firstAttempt = !session.recorded.contains(idx)

        _focusQuiz.value = session.copy(
            answers = if (firstAttempt) session.answers + (idx to isCorrect) else session.answers,
            tries = session.tries + 1,
            recorded = session.recorded + idx
        )

        if (firstAttempt) {
            viewModelScope.launch {
                auditRepository.recordCategoryResult(
                    session.category,
                    session.level,
                    if (isCorrect) AnswerOutcome.CORRECT else AnswerOutcome.INCORRECT
                )
            }
        }
    }

    fun focusQuizNext() {
        val s = _focusQuiz.value ?: return
        if (s.index < s.questions.lastIndex) _focusQuiz.value = s.copy(index = s.index + 1)
    }

    fun focusQuizPrev() {
        val s = _focusQuiz.value ?: return
        if (s.index > 0) _focusQuiz.value = s.copy(index = s.index - 1)
    }

    fun dismissFocusQuiz() {
        _focusQuiz.value = null
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
                .sortedWith(compareByDescending<FocusRow> { it.priorityScore }.thenBy { it.category })
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
