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
import com.goodstadt.john.language.exams.data.GrammarSheetMapping
import com.goodstadt.john.language.exams.data.ReferenceQuizSheetMapping
import com.goodstadt.john.language.exams.data.QuizHistoryManager
import com.goodstadt.john.language.exams.data.ReadinessAuditRepository
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statQuizNotOKCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statQuizOkCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statQuizTotalCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statUsageQuizNotOKCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statUsageQuizOkCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statUsageQuizTotalCount
import com.goodstadt.john.language.exams.data.repository.UsageQuizRepository
import com.goodstadt.john.language.exams.managers.BannerManager
import com.goodstadt.john.language.exams.managers.GlobalLoadingManager
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.managers.XpActionType
import com.goodstadt.john.language.exams.models.AudioPlaybackStatus
import com.goodstadt.john.language.exams.models.Format7or10File
import com.goodstadt.john.language.exams.models.UsageMastery
import com.goodstadt.john.language.exams.models.VocabQuizOutcome
import com.goodstadt.john.language.exams.packages.UsageQuiz.QuizQuestion
import com.goodstadt.john.language.exams.packages.UsageQuiz.QuizState
import com.goodstadt.john.language.exams.packages.UsageQuiz.QuizStatistics
import com.goodstadt.john.language.exams.packages.UsageQuiz.UsageQuizUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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
    private val auditRepository: ReadinessAuditRepository,
    private val vocabRepository: ContentRepository,
    private val globalLoadingManager: GlobalLoadingManager,
    private val userPreferencesRepository: com.goodstadt.john.language.exams.data.UserPreferencesRepository,
    private val playbackEventBus: com.goodstadt.john.language.exams.utils.PlaybackEventBus
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

    // True while a grammar sheet is being loaded; also gates the 2s-delayed global loading overlay.
    private val _isGrammarLoading = MutableStateFlow(false)
    val isGrammarLoading = _isGrammarLoading.asStateFlow()

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

    // Per-question SRS outcome tracking (keyed by question index; cleared on resetQuiz).
    private val questionTapCounts = mutableMapOf<Int, Int>()
    private val questionResolved = mutableSetOf<Int>()

    // Remembers the user's selection per question (keyed by the question's page id) so navigating back
    // re-shows the radio selection for review, until the quiz restarts. Cleared in resetQuiz.
    private val _selectedOptionByPage = mutableMapOf<Int, String>()
    private val _answerCorrectByPage = mutableMapOf<Int, Boolean>()

    /** Record the user's selection for a question (by page id) so it can be restored on back-navigation. */
    fun rememberSelection(page: Int, option: String, isCorrect: Boolean) {
        _selectedOptionByPage[page] = option
        _answerCorrectByPage[page] = isCorrect
    }

    /** The option last selected for the question at [page] this session, or null if unanswered. */
    fun selectedOptionFor(page: Int): String? = _selectedOptionByPage[page]

    /** Whether the question at [page] was last answered correctly this session, or null if unanswered. */
    fun answerCorrectFor(page: Int): Boolean? = _answerCorrectByPage[page]

    private val _fluency = mutableStateOf(UsageQuizRepository.QuizFluency.NEVER_DONE)
    val fluency: State<UsageQuizRepository.QuizFluency> = _fluency

    // "Auto-advance": when ON, a correct answer moves to the next question once its audio has finished.
    // Shared across all quiz types via the same saved preference (set on one screen, applies to all).
    private val _autoAdvance = MutableStateFlow(false)
    val autoAdvance = _autoAdvance.asStateFlow()
    private val autoAdvanceMaxWaitMs = 8000L
    private var autoAdvanceJob: Job? = null

    init {
        viewModelScope.launch {
            billingRepository.isPurchased.collect { purchasedStatus ->
                _isPremiumUser.value = purchasedStatus
                if (DEBUG) billingRepository.logCurrentStatus()
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.vocabQuizAutoAdvanceFlow.collect { _autoAdvance.value = it }
        }
    }

    /** Flip the shared auto-advance toggle and persist it (applies to all quiz types). */
    fun toggleAutoAdvance() {
        val newValue = !_autoAdvance.value
        _autoAdvance.value = newValue
        viewModelScope.launch { userPreferencesRepository.setVocabQuizAutoAdvance(newValue) }
    }

    /**
     * Call when the current question was answered CORRECTLY. If auto-advance is on, wait for the answer's
     * audio to finish (so the user can hear and read it together), then move to the next question - as
     * though the Next arrow had been tapped. No-op when off or already on the last question. A safety cap
     * advances anyway if no playback-finished event arrives.
     */
    fun onCorrectAnswered() {
        if (!_autoAdvance.value) return
        autoAdvanceJob?.cancel()
        val fromIndex = currentQuestionIndex.value
        autoAdvanceJob = viewModelScope.launch {
            withTimeoutOrNull(autoAdvanceMaxWaitMs) {
                playbackEventBus.events.first {
                    it is com.goodstadt.john.language.exams.utils.PlaybackEvent.Completed ||
                        it is com.goodstadt.john.language.exams.utils.PlaybackEvent.Failed
                }
            }
            if (currentQuestionIndex.value == fromIndex &&
                currentQuestionIndex.value < _questions.value.lastIndex
            ) {
                currentQuestionIndex.value = fromIndex + 1
                resetInfoButtonTapped() // new question starts without the "hint used" flag, like the arrow
            }
        }
    }

    /**
     * Load the one grammar file for [category] at [level]. Resolves the language-independent filename
     * key from [GrammarCatalog], then downloads the sheet (fileFormat 7/10) via [ContentRepository]:
     * memory cache -> disk cache -> Firestore, with the bundled asset as the final fallback. This
     * replaces the direct bundle read in [loadGrammarQuizObsolete] so grammar quizzes pick up Firestore
     * content just like the section and usage quizzes.
     *
     * The bundled filename is still resolved first, purely to keep [baseName] (the stats/mastery key)
     * identical to the old behaviour so existing progress continues to line up.
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

            val baseName = fileName.removeSuffix(".json") // e.g. "GrammarPresentSimple-en"

            // Download instead of reading the asset directly. e.g. "GrammarModalVerbs-de.json" + "A1"
            // -> "GermanA1ModalVerbs"; getFormat7or10Data falls back to the bundled asset if not on Firestore.
            val logicalName = GrammarSheetMapping.normalizeToLogicalName(fileName, level)

            loadFormat7or10Quiz(logicalName = logicalName, baseName = baseName, category = category, level = level)
        }
    }

    /**
     * Load a Reference-tab fileFormat-7 quiz into this SAME quiz screen (e.g. the "Adjectives" sub-tab).
     * [groupKey] is the language-independent group key ("Adjectives"), [displayTitle] the (possibly
     * localised) title to show, [level] the picked A1/A2/B1/B2.
     * The logical/Firestore doc name is `<Lang>Reference<Key><Level>Quiz` (via [ReferenceQuizSheetMapping]),
     * with the bundled Quizzes/Reference JSON as the download fallback - so grammar and reference quizzes
     * run through one screen/VM with no duplicated question/marking logic.
     */
    fun loadReferenceQuiz(groupKey: String, displayTitle: String, level: String) {
        viewModelScope.launch {
            val logicalName = ReferenceQuizSheetMapping.logicalName(groupKey, level)
            // The logical name is unique per level and stable, so it doubles as the stats/mastery key;
            // the (possibly localised) displayTitle is only what the quiz screen shows.
            loadFormat7or10Quiz(logicalName = logicalName, baseName = logicalName, category = displayTitle, level = level)
        }
    }

    /**
     * Shared loader for any fileFormat-7/10 quiz sheet: download (memory -> disk -> Firestore -> bundle),
     * populate state, build questions and apply filters. [logicalName] is the Firestore doc name to fetch,
     * [baseName] the stats/mastery key, [category]/[level] the display title and skill level. Extracted so
     * Grammar and Reference quizzes share one path.
     */
    private suspend fun loadFormat7or10Quiz(
        logicalName: String,
        baseName: String,
        category: String,
        level: String
    ) = coroutineScope {
        _isGrammarLoading.value = true
        // Show the global overlay only if the load is still running after 2s - so cached / in-memory /
        // bundle loads (the common case) don't flash it; only a real Firestore fetch does.
        val spinnerJob = launch {
            delay(2000)
            if (_isGrammarLoading.value) globalLoadingManager.show()
        }

        try {
            Timber.v("Quiz: loading '$logicalName'")
            val testData = vocabRepository.getFormat7or10Data(logicalName).getOrNull()
            if (testData == null) {
                Timber.e("Quiz: failed to load '$logicalName' from Firestore or bundle")
                _questions.value = emptyList()
                return@coroutineScope
            }

            currentCategory = category
            currentLevel = level

            _fluency.value = usageQuizRepository.getFluencyStatus(baseName)

            quizStatistics.value = quizStatistics.value.copy(
                title = category, filename = baseName, skillLevel = level, page = 1
            )
            _uiState.update { it.copy(format7or10ListRoot = testData) }

            _allQuestions = generateQuestionsFromData(testData)
            applyFilters()
            resetQuiz()
        } finally {
            _isGrammarLoading.value = false
            spinnerJob.cancel()          // if the load beat the 2s mark, never show the overlay
            globalLoadingManager.hide()  // and always clear it once done
        }
    }

    /**
     * ORIGINAL bundle-only loader, kept for reference. Reads the grammar JSON directly from the app's
     * assets folder with no Firestore/cache path. Superseded by [loadGrammarQuiz].
     */
    fun loadGrammarQuizObsolete(category: String, level: String) {
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
                    jsonParser.decodeFromString<Format7or10File>(it.readText())
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
            _uiState.update { it.copy(format7or10ListRoot = testData) }

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
                isPremiumUser = isPremiumUser.value
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

    private fun generateQuestionsFromData(testData: Format7or10File): List<QuizQuestion> {
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
        questionTapCounts.clear()
        questionResolved.clear()
        _selectedOptionByPage.clear()
        _answerCorrectByPage.clear()
        _activeFilters.value = emptySet()
    }

    // MARK: - Mastery Filter

    fun toggleFilter(level: UsageMastery) {
        // Single-select: a chip shows ONLY its own category's questions (not additive). Tapping the
        // already-selected chip clears back to All (empty set). The "All" chip uses selectAllFilters().
        _activeFilters.value = if (_activeFilters.value == setOf(level)) emptySet() else setOf(level)
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

        // Record THIS question's result for spaced repetition: first tap & no hint -> FLAWLESS;
        // first tap with hint -> ASSISTED; right only after a wrong tap -> STUMBLED; wrong -> FAILED.
        // Stop recording once solved so extra taps on the same option can't downgrade it.
        val currentPage = _questions.value.getOrNull(index)?.page ?: (index + 1)
        if (index !in questionResolved) {
            val priorTaps = questionTapCounts.getOrDefault(index, 0)
            questionTapCounts[index] = priorTaps + 1
            val outcome = when {
                !isCorrect -> VocabQuizOutcome.FAILED
                priorTaps == 0 && !infoUsedForCurrentQuestion -> VocabQuizOutcome.FLAWLESS
                priorTaps == 0 -> VocabQuizOutcome.ASSISTED
                else -> VocabQuizOutcome.STUMBLED
            }
            usageQuizRepository.recordQuestionResult(
                quizId = quizStatistics.value.filename,
                pageNumber = currentPage,
                outcome = outcome
            )
            if (isCorrect) questionResolved.add(index)
        }
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
