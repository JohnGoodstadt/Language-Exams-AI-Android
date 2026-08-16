package com.goodstadt.john.language.exams.packages.GrammarQuiz

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.data.AnswerOutcome
import com.goodstadt.john.language.exams.data.GrammarCatalog
import com.goodstadt.john.language.exams.data.QuizHistoryManager
import com.goodstadt.john.language.exams.data.ReadinessAuditRepository
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statQuizNotOKCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statQuizOkCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statQuizTotalCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statUsageQuizNotOKCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statUsageQuizOkCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statUsageQuizTotalCount
import com.goodstadt.john.language.exams.data.repository.UsageQuizRepository
import com.goodstadt.john.language.exams.managers.BannerManager
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.managers.XpActionType
import com.goodstadt.john.language.exams.models.AudioPlaybackStatus
import com.goodstadt.john.language.exams.models.TestMyselfListRoot
import com.goodstadt.john.language.exams.models.UsageMastery
import com.goodstadt.john.language.exams.packages.UsageQuiz.QuizQuestion
import com.goodstadt.john.language.exams.packages.UsageQuiz.QuizState
import com.goodstadt.john.language.exams.packages.UsageQuiz.QuizStatistics
import com.goodstadt.john.language.exams.packages.UsageQuiz.UsageQuizUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

/**
 * A single-file grammar practice quiz. Copied from [com.goodstadt.john.language.exams.packages.UsageQuiz]
 * to keep the full Usage Quiz experience - TTS speaker playback, the correct/incorrect mastery filter,
 * per-question mastery badge, stats and paging dots - but simplified: there is **no level picker and no
 * quiz dropdown**. One (category, level) is loaded per launch of the screen from
 * `Quizzes/Grammar/<level>/Grammar<key>-<lang>.json`.
 *
 * Marking is by (category, level): every answer feeds the shared audit tally via
 * [ReadinessAuditRepository.recordCategoryResult], so results surface back on the Focus screen.
 */
@HiltViewModel
class GrammarQuizViewModel @Inject constructor(
    private val application: Application,
    private val ttsStatsRepository: TTSStatsRepository,
    private val billingRepository: BillingRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val quizHistoryManager: QuizHistoryManager,
    private val xpManager: XPManager,
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val usageQuizRepository: UsageQuizRepository,
    private val bannerManager: BannerManager,
    private val auditRepository: ReadinessAuditRepository
) : ViewModel() {

    private val appContext: Context = application.applicationContext

    // The (category, level) currently loaded - used for stats/labels and to know which grammar file
    // this session belongs to. The per-question tally still reads each QuizQuestion's own fields.
    private var currentCategory: String = ""
    private var currentLevel: String = ""

    // Question indices already counted toward the shared category tally this attempt - stops a
    // re-tap on the same question from double-counting.
    private val categoryRecordedIndices = mutableSetOf<Int>()

    private val _uiState = MutableStateFlow(UsageQuizUiState())
    val uiState = _uiState.asStateFlow()

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    private val _questions = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val questions: StateFlow<List<QuizQuestion>> get() = _questions

    // Mastery Filter
    private var _allQuestions: List<QuizQuestion> = emptyList()
    private val _activeFilters = MutableStateFlow<Set<UsageMastery>>(emptySet())
    val activeFilters: StateFlow<Set<UsageMastery>> = _activeFilters.asStateFlow()
    val totalQuestionCount: Int get() = _allQuestions.size

    private val _uiEvent = MutableSharedFlow<com.goodstadt.john.language.exams.storage.UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private val _showRateLimitSheet = MutableStateFlow(false)
    val showRateLimitSheet = _showRateLimitSheet.asStateFlow()

    private val _showRateDailyLimitSheet = MutableStateFlow(false)
    val showRateDailyLimitSheet = _showRateDailyLimitSheet.asStateFlow()

    private val _showRateHourlyLimitSheet = MutableStateFlow(false)
    val showRateHourlyLimitSheet = _showRateHourlyLimitSheet.asStateFlow()

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    val quizStatistics = mutableStateOf(
        QuizStatistics(skillLevel = "", quizNumber = 1, title = "")
    )

    val currentQuestionIndex = mutableStateOf(0)
    val userAnswers = mutableStateOf(mutableMapOf<Int, Boolean>())

    // Constants (same values as the Usage Quiz)
    val quizFillInTheBlanks = 7
    val quizQandA = 10
    val quizMultipleChoice = 11
    val quizDefinitions = 12
    val currentFileFormat = mutableStateOf(quizFillInTheBlanks)

    private var infoUsedForCurrentQuestion = false

    private val _fluency = mutableStateOf(UsageQuizRepository.QuizFluency.NEVER_DONE)
    val fluency: State<UsageQuizRepository.QuizFluency> = _fluency

    init {
        viewModelScope.launch {
            billingRepository.isPurchased.collect { purchasedStatus ->
                _isPremiumUser.value = purchasedStatus
                if (DEBUG) billingRepository.logCurrentStatus()
            }
        }
    }

    /**
     * Load the one grammar file for [category] at [level]. Resolves the language-independent filename
     * key from [GrammarCatalog] and finds the file by prefix so the flavour's language suffix
     * (-en / -de) doesn't need hard-coding.
     */
    fun loadGrammarQuiz(category: String, level: String) {
        viewModelScope.launch {
            val fileKey = GrammarCatalog.fileKeyFor(category)
            if (fileKey == null) {
                Timber.w("GrammarQuiz: no catalogue mapping for category=\"$category\"")
                _questions.value = emptyList()
                return@launch
            }

            val dir = "Quizzes/Grammar/$level"
            val fileName = appContext.assets.list(dir).orEmpty()
                .firstOrNull { it.startsWith("Grammar$fileKey-") && it.endsWith(".json") }
            if (fileName == null) {
                Timber.w("GrammarQuiz: no file for key=$fileKey under $dir")
                _questions.value = emptyList()
                return@launch
            }

            val testData = try {
                appContext.assets.open("$dir/$fileName").bufferedReader().use {
                    jsonParser.decodeFromString<TestMyselfListRoot>(it.readText())
                }
            } catch (e: Exception) {
                Timber.e(e, "GrammarQuiz: failed to parse $dir/$fileName")
                _questions.value = emptyList()
                return@launch
            }

            val baseName = fileName.removeSuffix(".json") // e.g. "GrammarPresentSimple-en"
            currentCategory = category
            currentLevel = level

            _fluency.value = usageQuizRepository.getFluencyStatus(baseName)

            quizStatistics.value = quizStatistics.value.copy(
                title = category, filename = baseName, skillLevel = level, page = 1
            )
            _uiState.update { it.copy(testMyselfListRoot = testData) }

            _allQuestions = generateQuestionsFromData(testData)
            applyFilters()
            resetQuiz()
        }
    }

    fun hideDailyRateLimitSheet() { _showRateDailyLimitSheet.value = false }
    fun hideHourlyRateLimitSheet() { _showRateHourlyLimitSheet.value = false }
    fun hideRateOKLimitSheet() { _showRateLimitSheet.value = false }

    fun handleTap(sentence: String) {
        audioPlaybackRepository.stopPlayback()
        viewModelScope.launch {
            val result = audioPlaybackRepository.playTrackAndGetStatus(
                sentence = sentence,
                level = "Quiz",
                isPremiumUser = false
            )
            when (result) {
                is AudioPlaybackStatus.PlayedFromTTSAPI, is AudioPlaybackStatus.PlayedFromLocalCache, is AudioPlaybackStatus.PlayedFromCloudStorage -> {}
                is AudioPlaybackStatus.RateLimited -> {
                    if (result.failReason == SimpleRateLimiter.FailReason.DAILY) {
                        _showRateDailyLimitSheet.value = true
                    } else {
                        _showRateHourlyLimitSheet.value = true
                    }
                }
                AudioPlaybackStatus.Failure -> Timber.i("GrammarQuizViewModel.handleTap().Failure")
            }
            if (ttsStatsRepository.isMarchOrApril2026()) {
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.GlobalStats)
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
            }
        }
    }

    private fun generateQuestionsFromData(testData: TestMyselfListRoot): List<QuizQuestion> {
        currentFileFormat.value = when (testData.fileFormat) {
            quizQandA -> quizQandA
            quizDefinitions -> quizDefinitions
            quizMultipleChoice -> quizMultipleChoice
            else -> quizFillInTheBlanks
        }

        return testData.data.flatMap { section ->
            section.sections.map { quizSection ->
                val shuffledWords = quizSection.words.shuffled()
                val words = shuffledWords.map { it.word }
                val correctOption = quizSection.words.firstOrNull { it.ok }?.word ?: ""
                QuizQuestion(
                    quizSection.sentence, words, correctOption, quizSection.summary,
                    quizSection.explain, quizSection.title, quizSection.page,
                    quizSection.level, quizSection.category, testData.fileFormat
                )
            }
        }
    }

    private fun resetQuiz() {
        saveQuizState()
        quizStatistics.value = quizStatistics.value.copy(
            state = QuizState.NOT_STARTED,
            skillLevel = currentLevel,
            title = currentCategory,
            answered = 0,
            correct = 0,
            tries = 0
        )
        currentQuestionIndex.value = 0
        userAnswers.value.clear()
        categoryRecordedIndices.clear()
        _activeFilters.value = emptySet()
    }

    // MARK: - Mastery Filter

    fun toggleFilter(level: UsageMastery) {
        val current = _activeFilters.value.toMutableSet()
        if (current.contains(level)) current.remove(level) else current.add(level)
        _activeFilters.value = current
        applyFilters()
    }

    fun selectAllFilters() {
        _activeFilters.value = emptySet()
        applyFilters()
    }

    private fun applyFilters() {
        val filters = _activeFilters.value
        val currentQuizFileName = quizStatistics.value.filename
        if (filters.isEmpty()) {
            _questions.value = _allQuestions
        } else {
            _questions.value = _allQuestions.filter { q ->
                val mastery = usageQuizRepository.getQuestionMastery(currentQuizFileName, q.page)
                filters.contains(mastery)
            }
        }
        currentQuestionIndex.value = 0
    }

    /** Returns the mastery display (label, color) for the current question. */
    fun getQuestionMasteryDisplay(page: Int): Pair<String, Color> {
        val currentQuizFileName = quizStatistics.value.filename
        val mastery = usageQuizRepository.getQuestionMastery(currentQuizFileName, page)
        return usageQuizRepository.getMasteryDisplay(mastery)
    }

    private fun saveQuizState() {
        if (quizStatistics.value.state != QuizState.NOT_STARTED) {
            if (userAnswers.value.count() == _questions.value.count()) {
                quizStatistics.value = quizStatistics.value.copy(state = QuizState.COMPLETED)
            }
        }
    }

    private fun onQuizFinished() {
        val qs = quizStatistics
        val now = System.currentTimeMillis()
        val lastAttempt = quizHistoryManager.getLastAttempt(qs.value.skillLevel, qs.value.quizNumber)
        val questionCount = _questions.value.size
        // The distinct (category, level) pairs this run covered - for the mastery clear below.
        val practisedPairs = _questions.value.mapNotNull { q ->
            val c = q.category; val l = q.level
            if (!c.isNullOrBlank() && !l.isNullOrBlank()) c to l else null
        }.toSet()

        viewModelScope.launch {
            // Mastery clear: aced on first try (every question answered, exactly one tap each, all
            // correct -> correct == tries == questionCount). Zeroes the weak counts for the
            // categories practised, so the Focus page can move on to the next weak area. A single
            // wrong tap anywhere (tries > correct) means it was not aced, so nothing clears.
            if (questionCount > 0 && qs.value.correct == questionCount && qs.value.tries == questionCount) {
                practisedPairs.forEach { (c, l) -> auditRepository.clearCategoryWeakness(c, l) }
            }

            quizHistoryManager.saveAttempt(qs.value.skillLevel, qs.value.quizNumber, qs.value.title, qs.value.correct, qs.value.tries)

            if (qs.value.correct < qs.value.tries) {
                xpManager.registerAction(XpActionType.CompleteQuiz)
                bannerManager.showBanner(
                    title = "Almost Perfect",
                    subtitle = "Not quite. Try again for a perfect score. Don't look at the Info first",
                    seconds = 8
                )
            } else {
                if (lastAttempt != null) {
                    val diff = now - lastAttempt.timestamp
                    val oneDayMillis = 1000 * 60 * 60 * 24
                    if (diff > oneDayMillis) {
                        xpManager.registerAction(XpActionType.MemoryBoost)
                        bannerManager.showBanner(title = "Master!", subtitle = "You finally finished it perfectly. You are fluent")
                    } else {
                        xpManager.registerAction(XpActionType.ReplaySentence)
                        bannerManager.showBanner(
                            title = "Too Early for Mastery",
                            subtitle = "Too early for a perfect score. Wait till tomorrow to try again",
                            seconds = 8
                        )
                    }
                } else {
                    xpManager.registerAction(XpActionType.PerfectQuiz)
                    bannerManager.showBanner(title = "Section Master!", subtitle = "You finished it perfectly. You are fluent")
                }
            }

            usageQuizRepository.finishQuiz(quizId = quizStatistics.value.filename, finalScore = qs.value.correct)
        }
    }

    fun updateAnswer(isCorrect: Boolean) {
        val index = currentQuestionIndex.value
        userAnswers.value[index] = isCorrect

        // Unified strengths/weaknesses tally: same shared store as the audit, keyed by
        // (category, level). Counted once per question.
        if (categoryRecordedIndices.add(index)) {
            _questions.value.getOrNull(index)?.let { q ->
                val cat = q.category
                val lvl = q.level
                if (!cat.isNullOrBlank() && !lvl.isNullOrBlank()) {
                    val lastQuestion = (index + 1) >= _questions.value.size
                    val outcome = if (isCorrect) AnswerOutcome.CORRECT else AnswerOutcome.INCORRECT
                    viewModelScope.launch {
                        val score = auditRepository.recordCategoryResult(cat, lvl, outcome)
                        Timber.d(
                            "GRAMMAR-CAT category=\"$cat\" level=\"$lvl\" " +
                                "correct=${score.correct} incorrect=${score.incorrect} dontknow=${score.dontKnow}  " +
                                "(this answer: $outcome)"
                        )
                        if (lastQuestion) auditRepository.logCategorySummary("GRAMMAR ${quizStatistics.value.filename}")
                    }
                }
            }
        }

        val currentTries = quizStatistics.value.tries + 1
        quizStatistics.value = quizStatistics.value.copy(
            answered = userAnswers.value.size,
            correct = userAnswers.value.count { it.value },
            tries = currentTries
        )

        if (quizStatistics.value.state == QuizState.NOT_STARTED) {
            quizStatistics.value = quizStatistics.value.copy(state = QuizState.IN_PROGRESS)
        }

        val currentQuestion = currentQuestionIndex.value + 1
        if (currentQuestion >= _questions.value.count()) {
            quizStatistics.value = quizStatistics.value.copy(state = QuizState.COMPLETED, title = quizStatistics.value.title)
            onQuizFinished()
        }

        val page = quizStatistics.value.page
        usageQuizRepository.recordQuestionResult(
            quizId = quizStatistics.value.filename,
            pageNumber = page,
            attemptsTaken = currentTries,
            infoUsedForCurrentQuestion
        )
    }

    fun doIHaveCurrentQuestionInfo(): Boolean =
        _questions.value.isNotEmpty() && _questions.value[currentQuestionIndex.value].summary.isNotEmpty()

    fun buyPremiumButtonPressed(activity: Activity) {
        viewModelScope.launch { billingRepository.launchPurchase(activity) }
    }

    fun highlightWordInSentence(
        sentence: String,
        wordToHighlight: String,
        highlightColor: Color
    ): AnnotatedString = buildAnnotatedString {
        val startIndex = sentence.indexOf(wordToHighlight, ignoreCase = true)
        if (startIndex == -1) {
            append(sentence)
            return@buildAnnotatedString
        }
        val endIndex = startIndex + wordToHighlight.length
        append(sentence.substring(0, startIndex))
        withStyle(style = SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
            append(sentence.substring(startIndex, endIndex))
        }
        append(sentence.substring(endIndex))
    }

    fun incQuizStat(success: Boolean = true) {
        val baseName = quizStatistics.value.filename
        val statName = if (success) "${statQuizOkCount}_$baseName" else "${statQuizNotOKCount}_$baseName"

        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER, statName)
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statName)

        if (success) {
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER, statUsageQuizOkCount)
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statUsageQuizOkCount)
        } else {
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER, statUsageQuizNotOKCount)
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statUsageQuizNotOKCount)
        }

        if (success) {
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER, statUsageQuizTotalCount)
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statUsageQuizTotalCount)
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER, statQuizTotalCount)
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statQuizTotalCount)
        }

        viewModelScope.launch {
            if (ttsStatsRepository.isMarchOrApril2026()) {
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.GlobalStats)
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
            }
        }
    }

    fun onInfoButtonTapped() { infoUsedForCurrentQuestion = true }
    fun resetInfoButtonTapped() { infoUsedForCurrentQuestion = false }
}
