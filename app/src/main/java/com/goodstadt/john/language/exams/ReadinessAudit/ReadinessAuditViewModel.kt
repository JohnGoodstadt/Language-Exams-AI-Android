package com.goodstadt.john.language.exams.ReadinessAudit
import com.goodstadt.john.language.exams.viewmodels.QuizUiState
import com.goodstadt.john.language.exams.viewmodels.PlaybackState
import com.goodstadt.john.language.exams.viewmodels.UsageQuizUiState
import com.goodstadt.john.language.exams.viewmodels.QuizState
import com.goodstadt.john.language.exams.viewmodels.QuizStatistics
import com.goodstadt.john.language.exams.viewmodels.QuizQuestion

//import android.graphics.Color
//import com.goodstadt.john.language.exams.managers.RateLimiterManager
//import com.google.gson.Gson

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
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
import com.goodstadt.john.language.exams.data.ConnectivityRepository
import com.goodstadt.john.language.exams.data.QuizHistoryManager
import com.goodstadt.john.language.exams.data.ReadinessAuditRepository
import com.goodstadt.john.language.exams.data.ReadinessQuizAttemptState
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.UserStatsRepository
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
import com.goodstadt.john.language.exams.managers.AuditEngine
import com.goodstadt.john.language.exams.managers.BannerManager
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.managers.XpActionType
import com.goodstadt.john.language.exams.models.AudioPlaybackStatus
import com.goodstadt.john.language.exams.models.TestMyselfListRoot
import com.goodstadt.john.language.exams.models.UsageMastery
import com.goodstadt.john.language.exams.screens.reference.shared.ReadinessAuditDetail
import com.goodstadt.john.language.exams.storage.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException
import java.util.Locale
import javax.inject.Inject

data class AuditStats(
    val confidence: Int = 0,
    val readiness: Int = 0
)

enum class ReadinessAuditLevels(val quizzes: List<ReadinessAuditDetail>) {
    ELEMENTARY(
        quizzes = listOf(
            ReadinessAuditDetail(
                id = 1,
                baseName = "BaselineAudit-en",
                title = "Sentence Structure"
            ),
            ReadinessAuditDetail(
                id = 1,
                baseName = "BaselineAudit2-en",
                title = "Another Title"
            ),
        )
    ),
    INTER(
        quizzes = listOf(
            ReadinessAuditDetail(
                id = 2,
                baseName = "LogicAudit-en",
                title = "Present Simple"
            ),
        )
    ),
    UPPER(
        quizzes = listOf(
            ReadinessAuditDetail(
                id = 3,
                baseName = "LexisAudit-en",
                title = "Past Simple"
            ),
        )
    ),
    ADVANCED(
        quizzes = listOf(
            ReadinessAuditDetail(
                id = 4,
                baseName = "CoreAudit-en",
                title = "Questions & Short Answers"
            )
        )
    );


    val description: String
        get() = when(this) {
            ELEMENTARY -> "Baseline"
            INTER -> "Logic"
            UPPER -> "Lexis"
            ADVANCED -> "Core"
        }
    /** Compact label for tight horizontal pickers on small screens */
    val shortLabel: String
        get() = when(this) {
            ELEMENTARY -> "Base."
            INTER -> "Logic"
            UPPER -> "Lex."
            ADVANCED -> "Core"
        }
    val ESOL: String
        get() = when(this) {
            ELEMENTARY -> "A1"
            INTER -> "A2"
            UPPER -> "B1"
            ADVANCED -> "B2"
        }
}
@HiltViewModel
class ReadinessAuditViewModel @Inject constructor(
    private val application: Application,
    private val vocabRepository: ContentRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val userStatsRepository: UserStatsRepository,
    private val ttsStatsRepository: TTSStatsRepository,
    private val billingRepository: BillingRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val connectivityRepository: ConnectivityRepository,
    private val quizHistoryManager: QuizHistoryManager,
    private val xpManager: XPManager,
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val usageQuizRepository:UsageQuizRepository,
    private val bannerManager: BannerManager,
    private val auditRepository: ReadinessAuditRepository

) : ViewModel() {

    private val appContext: Context = application.applicationContext

    //Real uiState
    private val _uiState = MutableStateFlow(UsageQuizUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiState99 = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val uiState99 = _uiState99.asStateFlow()


    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    // region State FLow
    private val _questions = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val questions: StateFlow<List<QuizQuestion>> get() = _questions

    // Mastery Filter
    private var _allQuestions: List<QuizQuestion> = emptyList()
    private val _activeFilters = MutableStateFlow<Set<UsageMastery>>(emptySet())
    val activeFilters: StateFlow<Set<UsageMastery>> = _activeFilters.asStateFlow()
    val totalQuestionCount: Int get() = _allQuestions.size

    private val _showUpgradeAppSheet = MutableStateFlow(false)
    val showUpgradeAppSheet = _showUpgradeAppSheet.asStateFlow()

    private val _showForceUpgradeAppSheet = MutableStateFlow(false)
    val showForceUpgradeAppSheet = _showForceUpgradeAppSheet.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
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
    // endregion

    val quizStatistics = mutableStateOf(
        QuizStatistics(skillLevel = ReadinessAuditLevels.ELEMENTARY.description, quizNumber = 1, title = "Quiz 1")
    )
    val selectedLevel = mutableStateOf(ReadinessAuditLevels.ELEMENTARY)
    val selectedQuiz = mutableStateOf<ReadinessAuditDetail?>(null)

    val selectedQuizNumber = mutableStateOf(1)
    val currentQuestionIndex = mutableStateOf(0)
    val userAnswers = mutableStateOf(mutableMapOf<Int, Boolean>())

    //Constants
    val quizFillInTheBlanks = 7 //in iOS these are ENUMs
    val quizQandA = 10
    val quizMultipleChoice = 11
    val quizDefinitions = 12
    val currentFileFormat = mutableStateOf(quizFillInTheBlanks) //either 7 (fill in the blank) or 10 (Multiple choice)

    // ❌ OLD (Static):
    val availableQuizzesObsolete = derivedStateOf { selectedLevel.value.quizzes }

    // ✅ NEW (Dynamic):
    // This starts with the default English titles, but we can overwrite them later
    private val _availableQuizzes = MutableStateFlow<List<ReadinessAuditDetail>>(ReadinessAuditLevels.ELEMENTARY.quizzes)
    val availableQuizzes = _availableQuizzes.asStateFlow()

    // 1. The Cache: Maps a Level (e.g. ELEMENTARY) to its list of localized ReadinessAuditDetails
    private val quizTitleCache = mutableMapOf<ReadinessAuditLevels, List<ReadinessAuditDetail>>()
    private var infoUsedForCurrentQuestion = false // ✅ Track hint usage for the CURRENT question

//    private val _quizFluency = mutableStateOf(UsageQuizRepository.QuizFluency.NEVER_DONE)
//    val quizFluency: State<UsageQuizRepository.QuizFluency> = _quizFluency

    private val _fluency = mutableStateOf(UsageQuizRepository.QuizFluency.NEVER_DONE)
    // Explicitly define the type to avoid ambiguity with other 'State' classes
    val fluency: State<UsageQuizRepository.QuizFluency> = _fluency

    // 1. We create a StateFlow that the UI will listen to
    private val _auditStats = MutableStateFlow(AuditStats())
    val auditStats: StateFlow<AuditStats> = _auditStats.asStateFlow()

    // Question index -> the option the user locked in for the CURRENT quiz. Once present, that
    // question is answered for good (read-only); once every question is present the quiz is done.
    private val _lockedAnswers = MutableStateFlow<Map<Int, String>>(emptyMap())
    val lockedAnswers: StateFlow<Map<Int, String>> = _lockedAnswers.asStateFlow()

    // True once the current quiz has been fully completed today - locked (read-only) until tomorrow.
    private val _isQuizLockedForToday = MutableStateFlow(false)
    val isQuizLockedForToday: StateFlow<Boolean> = _isQuizLockedForToday.asStateFlow()

    // Levels the user is currently allowed to attempt. Levels must be cleared in order:
    // ELEMENTARY -> INTER -> UPPER -> ADVANCED.
    private val _unlockedLevels = MutableStateFlow<Set<ReadinessAuditLevels>>(setOf(ReadinessAuditLevels.ELEMENTARY))
    val unlockedLevels: StateFlow<Set<ReadinessAuditLevels>> = _unlockedLevels.asStateFlow()

    private val _currentVersion = MutableStateFlow(1)
    val currentVersion = _currentVersion.asStateFlow()

    init {
        // 1. Start background loading
      //  preloadLocalizedTitles()

        selectedQuiz.value = selectedLevel.value.quizzes.firstOrNull()
        refreshUnlockedLevels()
        loadQuestions()
        viewModelScope.launch {
            billingRepository.isPurchased.collect { purchasedStatus ->
                _isPremiumUser.value = purchasedStatus
                if (DEBUG) {
                    billingRepository.logCurrentStatus()
                }
            }
        }
        viewModelScope.launch {
            quizHistoryManager.historyUpdates.collect {
                refreshUnlockedLevels()
            }
        }
        loadCurrentAuditStats()
    }

    fun hideDailyRateLimitSheet() {
        _showRateDailyLimitSheet.value = false
    }

    fun hideHourlyRateLimitSheet() {
        _showRateHourlyLimitSheet.value = false
    }

    fun hideRateOKLimitSheet() {
        _showRateLimitSheet.value = false
    }
    fun startNewAuditVersion() {
        viewModelScope.launch {
            // Switch to version 2
            _currentVersion.value = 2

            // Reset the local UI state for a fresh start
            resetQuiz()

            // Load the new files
            loadQuestions()

            // Note: You might want to reset the AuditRepository scores
            // if a "New Audit" should clear the old 40%/65% stats.
            auditRepository.resetAll()
        }
    }



    fun handleTap(sentence: String) {

        audioPlaybackRepository.stopPlayback()


        viewModelScope.launch {

            //All stats updated in playTrackAndGetStatus()
            val result = audioPlaybackRepository.playTrackAndGetStatus(
                sentence = sentence,
                level = "Quiz",
                isPremiumUser = false
            )

            when (result) {
                is AudioPlaybackStatus.PlayedFromTTSAPI,is AudioPlaybackStatus.PlayedFromLocalCache , is AudioPlaybackStatus.PlayedFromCloudStorage -> {

                }
                is AudioPlaybackStatus.RateLimited -> {
                    // Show Paywall logic
                    Timber.i("Format1ViewModel.handleTap().AudioPlaybackStatus.RateLimited ")
                    val failType = rateLimiter.canMakeCallWithResult()
                    Timber.w("Rate Limiter Triggered")
                    Timber.w("canICallAPI = %s", failType.canICallAPI)
                    Timber.w("failReason = %s", (failType.failReason))
                    Timber.w("timeLeftToWait = %s",failType.timeLeftToWait)
                    Timber.w(rateLimiter.printCurrentStatus)

                    if (result.failReason == SimpleRateLimiter.FailReason.DAILY) {
                        _showRateDailyLimitSheet.value = true
                    } else {
                        _showRateHourlyLimitSheet.value = true
                    }
                }

                AudioPlaybackStatus.Failure -> {
                    //TODO: Show Snackbar logic
                    Timber.i("QuizViewModel.handleTap().AudioPlaybackStatus.Failure")
                }
            }
            //TODO: for 1 month feb/march 2026, facebook ads manager campaign. see stats
            if (ttsStatsRepository.isMarchOrApril2026()) {
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.GlobalStats)
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
            }

        }
    }

    fun loadQuestions() {
        viewModelScope.launch {

            val quizDetail = selectedQuiz.value ?: selectedLevel.value.quizzes.first()
            val baseName = quizDetail.baseName//(selectedQuiz.value ?: selectedLevel.value.quizzes.first()).baseName //+ ".json"
//            val regionCode = "IN"//Locale.getDefault().country
            val localizedBaseName = resolveLocalizedBaseName(baseName)
            val finalFilename = "$localizedBaseName.json"

            Timber.v("filename $finalFilename")
            //val finalFilename = "$baseName.json" //getLocalizedFileName(appContext, baseName)

            val testData = readTestMyselfDataFromAssets(appContext, finalFilename)

            if (testData == null) {
                Timber.wtf("Failed to parse JSON file: $finalFilename")
                return@launch
            }

            Timber.v("data $testData")

            val stats = usageQuizRepository.getStatsForQuiz(baseName)

            _fluency.value = usageQuizRepository.getFluencyStatus(baseName)

            Timber.v(stats.toString())
            if (testData.title?.isNotEmpty() == true){
                Timber.i("Sheet title is ${testData.title} ")
            }
            quizStatistics.value = quizStatistics.value.copy(
                title = quizDetail.title, filename = baseName,page = 1
            )

            _uiState.update { it.copy(testMyselfListRoot = testData)}

            _allQuestions = generateQuestionsFromData(testData)
            applyFilters()

            Timber.v("${_questions.value.count()}")

            resetQuiz()
            restorePersistedAttemptState()

            // Record how many questions this quiz has so Confidence can be computed for every
            // part, even ones not currently loaded.
            auditRepository.saveTotalQuestions(quizAttemptKey(selectedLevel.value, quizDetail), _allQuestions.size)
            loadCurrentAuditStats()
        }
    }

    // A new function for the UI to call when a different level is picked.
    fun onLevelSelected(level: ReadinessAuditLevels) {
        if (level == selectedLevel.value){
            return // already selected
        }

        if (!isLevelUnlocked(level)) {
            val previousLevel = ReadinessAuditLevels.entries[level.ordinal - 1]
            viewModelScope.launch {
                _uiEvent.emit(UiEvent.ShowToast("Complete the ${previousLevel.description} test first"))
            }
            return
        }

        selectedLevel.value = level

        // 1. Update the list of quizzes (Async)
        refreshQuizTitlesForLevel(level)

        // 2. Reset selection to first (we use the Enum list temporarily until async finishes)
//        selectedQuiz.value = level.quizzes.firstOrNull()
        selectedQuiz.value = _availableQuizzes.value.firstOrNull()

        loadQuestions()
    }

    /** Whether [level] may currently be attempted - levels must be cleared in order. */
    fun isLevelUnlocked(level: ReadinessAuditLevels): Boolean = _unlockedLevels.value.contains(level)

    /**
     * Recomputes which levels are unlocked. A level unlocks once every quiz in the previous
     * level has at least one completed attempt on record.
     */
    private fun refreshUnlockedLevels() {
        val levels = ReadinessAuditLevels.entries
        val unlocked = mutableSetOf(levels.first())

        for (i in 1 until levels.size) {
            val previousLevel = levels[i - 1]
            val previousCompleted = previousLevel.quizzes.all { quiz ->
                quizHistoryManager.getLastAttempt(previousLevel.description, quiz.id) != null
            }
            if (!previousCompleted) break // levels must be completed in order
            unlocked.add(levels[i])
        }

        _unlockedLevels.value = unlocked
    }


    // A new function for the UI to call when a different quiz is picked from the dropdown.
    fun onQuizSelected(quizDetail: ReadinessAuditDetail) {
        selectedQuiz.value = quizDetail
        loadQuestions()
    }
    private fun generateQuestionsFromData(testData: TestMyselfListRoot): List<QuizQuestion> {

        if (testData.fileFormat == quizQandA) {
            currentFileFormat.value = quizQandA
        }else if (testData.fileFormat == quizDefinitions) {
            currentFileFormat.value = quizDefinitions
        }else if (testData.fileFormat == quizMultipleChoice) {
            currentFileFormat.value = quizMultipleChoice
        } else {
            currentFileFormat.value = quizFillInTheBlanks
        }

        //because spellings should follow each other
//        if (testData.fileFormat == quizFillInTheBlanks) testData.shuffleLists()
        //TODO: for 1B1 quiz want the first question to be the same - to match ads manager
       // testData.shuffleLists()

        return testData.data.flatMap { section ->
            section.sections.map { quizSection ->
                val shuffledWords = quizSection.words.shuffled()
                val words = shuffledWords.map { it.word }
                val correctOption = quizSection.words.firstOrNull { it.ok }?.word ?: ""
                val summary = quizSection.summary
                val explain = quizSection.explain
                val title = quizSection.title
                val page = quizSection.page
                QuizQuestion(quizSection.sentence, words, correctOption, summary,explain,title,page)
            }
        }
    }

    private fun resetQuiz() {

        saveQuizState()

        // Use the selectedLevel enum for the name (e.g. "Advanced")
        val currentLevelName = selectedLevel.value.description
        // Use the selectedQuiz for the ID (e.g. 6), fallback to 1 if null
        val currentQuizId = selectedQuiz.value?.id ?: 1
        val currentTitle = selectedQuiz.value?.title ?: "Quiz $currentQuizId"

        quizStatistics.value = quizStatistics.value.copy(
            state = QuizState.NOT_STARTED,

            // ✅ FIX: Update Level and ID here
            skillLevel = currentLevelName,
            quizNumber = currentQuizId,
            title = currentTitle,

            answered = 0,
            correct = 0,
            tries = 0
        )

        currentQuestionIndex.value = 0
        userAnswers.value.clear()
        _activeFilters.value = emptySet()

    }

    fun isQuizComplete(): Boolean {
        return quizStatistics.value.state == QuizState.COMPLETED
    }
    // MARK: - Answer locking (order enforcement / once-per-day / one-shot answers)

    private fun quizAttemptKey(level: ReadinessAuditLevels, quiz: ReadinessAuditDetail): String =
        "${level.name}_${quiz.id}"

    // Maps a level onto the 1-4 "part index" the AuditEngine/ReadinessAuditRepository score by.
    private fun partIndexFor(level: ReadinessAuditLevels): Int = level.ordinal + 1

    /** True once [index] already has a locked-in answer - it can be read but not changed. */
    fun isQuestionLocked(index: Int): Boolean = _lockedAnswers.value.containsKey(index)

    /**
     * Restores locked answers for the current level/quiz from disk so a question that was
     * already answered (today, or an earlier unfinished session) stays locked and reviewable.
     * If the quiz was completed on a previous day, the attempt is cleared so a fresh run begins.
     */
    private suspend fun restorePersistedAttemptState() {
        val level = selectedLevel.value
        val quiz = selectedQuiz.value ?: return
        val key = quizAttemptKey(level, quiz)

        var state = auditRepository.getQuizAttemptState(key)
        val completedAt = state.completedAt
        if (completedAt != null && !auditRepository.isSameDay(completedAt, System.currentTimeMillis())) {
            auditRepository.clearQuizAttempt(key)
            state = ReadinessQuizAttemptState()
        }

        _lockedAnswers.value = state.answers
        _isQuizLockedForToday.value = state.completedAt != null

        val questionsList = _questions.value
        val rebuiltAnswers = mutableMapOf<Int, Boolean>()
        state.answers.forEach { (index, option) ->
            questionsList.getOrNull(index)?.let { question ->
                rebuiltAnswers[index] = (option == question.correctOption)
            }
        }
        userAnswers.value = rebuiltAnswers

        val answeredCount = rebuiltAnswers.size
        val quizState = when {
            questionsList.isNotEmpty() && answeredCount >= questionsList.size -> QuizState.COMPLETED
            answeredCount > 0 -> QuizState.IN_PROGRESS
            else -> QuizState.NOT_STARTED
        }
        quizStatistics.value = quizStatistics.value.copy(
            state = quizState,
            answered = answeredCount,
            correct = rebuiltAnswers.count { it.value },
            tries = answeredCount
        )

        // Resume at the first unanswered question, or review from the start if fully complete.
        currentQuestionIndex.value = if (answeredCount in 1 until questionsList.size) {
            (0 until questionsList.size).firstOrNull { it !in rebuiltAnswers } ?: 0
        } else {
            0
        }
    }

    /**
     * DEBUG-only: wipes all audit scores, unlocks every quiz (clears the "locked until
     * tomorrow" state and every locked-in answer), clears the Readiness Audit's completion
     * history so level order-locking (Baseline -> Logic -> Lexis -> Core) resets, AND clears the
     * "has seen the first-launch intro sheet" flag so the app behaves like a brand new install
     * on the next cold start. Only this feature's history is cleared - other quiz features'
     * history/stats are untouched. No-op in release builds.
     */
    fun resetAuditForDebug() {
        if (!DEBUG) return

        viewModelScope.launch {
            auditRepository.resetAll()
            quizHistoryManager.clearHistoryForSkillLevels(ReadinessAuditLevels.entries.map { it.description })
            userPreferencesRepository.setHasSeenReadinessAuditIntro(false)

            _lockedAnswers.value = emptyMap()
            _isQuizLockedForToday.value = false
            quizStatistics.value = quizStatistics.value.copy(
                state = QuizState.NOT_STARTED,
                answered = 0,
                correct = 0,
                tries = 0
            )
            currentQuestionIndex.value = 0
            userAnswers.value.clear()

            loadCurrentAuditStats()
        }
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

    /**
     * Returns the mastery display (label, color) for the current question.
     */
    fun getQuestionMasteryDisplay(page: Int): Pair<String, Color> {
        val currentQuizFileName = quizStatistics.value.filename
        val mastery = usageQuizRepository.getQuestionMastery(currentQuizFileName, page)
        return usageQuizRepository.getMasteryDisplay(mastery)
    }

    /*
    6. CLAUDE Mutable data class mutation won't trigger recomposition
File: QuizViewModel.kt line 629

quizStatistics.value.state = QuizState.COMPLETED

This directly mutates a property on the data class held in mutableStateOf. Compose won't detect this change because the object reference hasn't changed. Recomposition won't fire.

Fix: Always use .copy(): quizStatistics.value = quizStatistics.value.copy(state = QuizState.COMPLETED)


     */
    private fun saveQuizState() {
        if (quizStatistics.value.state != QuizState.NOT_STARTED) {
            //save stats from previous quiz try

            if (userAnswers.value.count() == _questions.value.count()) {
//                quizStatistics.value.state = QuizState.COMPLETED
                quizStatistics.value = quizStatistics.value.copy(state = QuizState.COMPLETED)
            }
        }
    }

    private fun onQuizFinished() {

        val qs = quizStatistics
        val now = System.currentTimeMillis()

        // 1. Check History BEFORE saving
        val lastAttempt = quizHistoryManager.getLastAttempt(qs.value.skillLevel, qs.value.quizNumber)

        viewModelScope.launch {

            quizHistoryManager.saveAttempt(qs.value.skillLevel, qs.value.quizNumber, qs.value.title, qs.value.correct, qs.value.tries)

            // 3. Logic
            if (qs.value.correct < qs.value.tries) {
                xpManager.registerAction(XpActionType.CompleteQuiz)
                bannerManager.showBanner(
                    title = "Almost Perfect",
                    subtitle = "Not quite. Try again for a perfect score. But you can try the next one",
                    seconds = 8 //so they can read it
                )
            } else {
                // Perfect Score
                if (lastAttempt != null) {
                    val diff = now - lastAttempt.timestamp
                    val oneDayMillis = 1000 * 60 * 60 * 24

                    if (diff > oneDayMillis) {
                        // ✅ Memory Boost
                        xpManager.registerAction(XpActionType.MemoryBoost)
                        bannerManager.showBanner(
                            title = "Master!",
                            subtitle = "You finally finished it perfectly. You are fluent",
                        )
                    } else {
                        // ⚠️ Grinding
                        // Maybe just give 5 XP?
                        xpManager.registerAction(XpActionType.ReplaySentence) // Re-use low value or make new one
                        bannerManager.showBanner(
                            title = "Too Early for Mastery",
                            subtitle = "Too early for a perfect score. Wait till tomorrow to try again",
                            seconds = 8
                        )
                    }

                } else {
                    // First Time Perfect
                    xpManager.registerAction(XpActionType.PerfectQuiz)
                    bannerManager.showBanner(
                        title = "Section Master!",
                        subtitle = "You finished it perfectly. You are fluent",
                    )
                }
            }

            val currentQuizFileName = quizStatistics.value.filename
            usageQuizRepository.finishQuiz(
                quizId = currentQuizFileName, // e.g. "UsageQuiz1A1"
                finalScore = qs.value.correct

            )
        }
    }

    fun updateAnswer(selectedOption: String, isCorrect: Boolean) {
        val index = currentQuestionIndex.value
        if (isQuestionLocked(index)) return // one attempt per question - already locked in

        userAnswers.value[index] = isCorrect

        val currentTries = quizStatistics.value.tries + 1
        quizStatistics.value = quizStatistics.value.copy(
            answered = userAnswers.value.size,
            correct = userAnswers.value.count { it.value },
            tries = currentTries
        )


        if (quizStatistics.value.state == QuizState.NOT_STARTED) {
            quizStatistics.value = quizStatistics.value.copy(state = QuizState.IN_PROGRESS)
        }

        _lockedAnswers.value = _lockedAnswers.value + (index to selectedOption)

        val currentPart = selectedLevel.value.ordinal + 1
        val progress = (index + 1).toFloat() / _questions.value.size.toFloat()

        val level = selectedLevel.value
        val quiz = selectedQuiz.value
        val isLastQuestion = (index + 1) >= _questions.value.size

        if (quiz != null) {
            val key = quizAttemptKey(level, quiz)
            val partIndex = partIndexFor(level)

            viewModelScope.launch {
                // 1. Always save the individual answer and the progress fraction (e.g. 0.1, 0.2)
                auditRepository.saveAnswer(key, index, selectedOption)
                auditRepository.saveLiveProgress(currentPart, progress)

                // 2. ONLY save the final score when the 10th question is answered
                if (isLastQuestion) {
                    val finalScore = userAnswers.value.count { it.value }
                    // This marks the part as "Done", jumping confidence to the cap (e.g. 40%)
                    auditRepository.saveScore(partIndex, finalScore)
                    auditRepository.markQuizCompleted(key)
                    _isQuizLockedForToday.value = true
                }

                // 3. Refresh stats (this will now see progress for Q1-9 and Score for Q10)
                loadCurrentAuditStats()
            }
        }

        if (isLastQuestion) { //completed
            quizStatistics.value = quizStatistics.value.copy(state = QuizState.COMPLETED, title = quizStatistics.value.title)

            onQuizFinished()
        }

        val page = quizStatistics.value.page
        val currentQuizFileName = quizStatistics.value.filename
        usageQuizRepository.recordQuestionResult(
            quizId = currentQuizFileName, // e.g. "UsageQuiz1A1"
            pageNumber = page,      // e.g. 5
            attemptsTaken = currentTries,
            infoUsedForCurrentQuestion
        )

    }

    fun readTestMyselfDataFromAssets(context: Context, fileName: String): TestMyselfListRoot? {
        return try {


            val jsonString = context.assets.open("Quizzes/ReadinessAudit/$fileName")
                .bufferedReader()
                .use { it.readText() }

            return jsonParser.decodeFromString<TestMyselfListRoot>(jsonString)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Call this whenever the Level changes (e.g. from Elementary to Inter)
    private fun refreshQuizTitlesForLevel(level: ReadinessAuditLevels) {
        viewModelScope.launch {


            _availableQuizzes.value =  level.quizzes//.updatedQuizzes

            // 4. Also ensure the currently selected quiz object is updated if needed
            val currentId = selectedQuiz.value?.id
            if (currentId != null) {
                selectedQuiz.value = level.quizzes.find { it.id == currentId }
            }
        }
    }



    // Helper to read just the title
    private fun peekTitleFromJsonObsolete(filename: String): String? {
        return try {
            // Reusing your existing reader logic, but maybe we can optimize later
            val data = readTestMyselfDataFromAssets(appContext, filename)
            // Get the title from the root object if you added it there, or the first section
            data?.title // Assuming you added 'val title: String' to TestMyselfListRoot
        } catch (e: Exception) {
            null
        }
    }


    private fun updateAvailableQuizzesFor(level: ReadinessAuditLevels) {
        // If cache is ready, use it. If not (still loading), use default English list.
        _availableQuizzes.value = quizTitleCache[level] ?: level.quizzes
    }
    fun TestMyselfListRoot.shuffleLists() {
        data.forEach { testMyselfList ->
            testMyselfList.sections = testMyselfList.sections.shuffled() // Shuffle sections
            testMyselfList.sections.forEach { section ->
                section.words = section.words.shuffled() // Shuffle words within each section
            }
        }
    }

    fun doIHaveCurrentQuestionInfo(): Boolean {
        return if (_questions.value[currentQuestionIndex.value].summary.isNotEmpty()) {
            true
        } else {
            false
        }
    }

    fun buyPremiumButtonPressed(activity: Activity) {
        Timber.i("purchasePremium()")
        viewModelScope.launch {
            billingRepository.launchPurchase(activity)
        }
    }

    /**
     * Creates an AnnotatedString by finding and highlighting a specific word within a sentence.
     *
     * @param sentence The full sentence to be styled.
     * @param wordToHighlight The specific word to find and apply styling to.
     * @param highlightColor The color to use for the highlighted word.
     * @return A styled `AnnotatedString`. If the word is not found, it returns an
     *   un-styled `AnnotatedString` of the original sentence.
     */
    fun highlightWordInSentence(
        sentence: String,
        wordToHighlight: String,
        highlightColor: Color // You can change the default color here
    ): AnnotatedString {
        // Use the `buildAnnotatedString` builder, the equivalent of Swift's AttributedString
        return buildAnnotatedString {

            // Find the starting index of the word, ignoring case
            val startIndex = sentence.indexOf(wordToHighlight, ignoreCase = true)

            // If the word was not found, just append the plain sentence and we're done.
            if (startIndex == -1) {
                append(sentence)
                return@buildAnnotatedString
            }

            val endIndex = startIndex + wordToHighlight.length

            // 1. Append the part of the sentence BEFORE the highlighted word
            append(sentence.substring(0, startIndex))

            // 2. Append the highlighted word using `withStyle`
            withStyle(
                style = SpanStyle(
                    color = highlightColor,
                    fontWeight = FontWeight.Bold
                )
            ) {
                // This appends the substring from the original sentence to preserve its casing
                append(sentence.substring(startIndex, endIndex))
            }

            // 3. Append the part of the sentence AFTER the highlighted word
            append(sentence.substring(endIndex))
        }
    }
    // In QuizViewModel.kt
    private fun getLocalizedFileName(context: Context, baseName: String): String {
        // 1. Get current language code (e.g., "hi", "es", "zh")
        // Use your UserPreferences or system default
        // val currentCode = userPreferencesRepository.selectedLanguageCodeFlow.value // if available
        val currentCode = java.util.Locale.getDefault().language

        // 2. Construct the localized filename
//        val localizedName = "$baseName-$currentCode.json"
        val localizedName = "$baseName.json"
        val defaultName = "$baseName.json"

        // 3. Check if the localized file exists in Assets
        // We list files in the "Quizzes" folder to check existence efficiently
        val filesInAssets = try {
            context.assets.list("Quizzes")?.toList() ?: emptyList()
        } catch (e: IOException) {
            return defaultName
        }

        // 4. Return localized if found, otherwise default
        return if (filesInAssets.contains(localizedName)) {
           // Timber.i("✅ Found localized quiz: $localizedName")
            localizedName
        } else {
            Timber.i("⚠️ Localized quiz not found, falling back to: $defaultName")
            defaultName
        }
    }
    private fun getLocalizedName(context: Context, baseName: String): String {
        // 1. Get current language code (e.g., "hi", "es", "zh")
        // Use your UserPreferences or system default
        // val currentCode = userPreferencesRepository.selectedLanguageCodeFlow.value // if available
        val currentCode = java.util.Locale.getDefault().language

        // 2. Construct the localized filename
        val localizedName = "$baseName-$currentCode"
        val defaultName = "$baseName-en"

        // 3. Check if the localized file exists in Assets
        // We list files in the "Quizzes" folder to check existence efficiently
        val filesInAssets = try {
            context.assets.list("Quizzes")?.toList() ?: emptyList()
        } catch (e: IOException) {
            return defaultName
        }

        // 4. Return localized if found, otherwise default
        return if (filesInAssets.contains(localizedName)) {
            Timber.i("✅ Found localized quiz: $localizedName")
            localizedName
        } else {
            Timber.i("⚠️ Localized quiz not found, falling back to: $defaultName")
            defaultName
        }
    }
    fun incQuizStat(success:Boolean = true) {

        val baseName = (selectedQuiz.value ?: selectedLevel.value.quizzes.first()).baseName
        //val finalName = getLocalizedName(appContext, baseName) //no json

      //  Timber.v(finalName)

        val statName = if (success ) "${statQuizOkCount}_$baseName" else "${statQuizNotOKCount}_$baseName"

        //individual totals
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER, statName)
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statName)

        if (success) { //Usage totals
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER, statUsageQuizOkCount)
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statUsageQuizOkCount)
        }else{
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER, statUsageQuizNotOKCount)
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statUsageQuizNotOKCount)
        }

        //Usage totals
        if (success) { //Only show success Usage totals -- 1 per question
                ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER, statUsageQuizTotalCount)
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statUsageQuizTotalCount)
        }

        //Global Totals
        if (success) { //Only show success totals -- 1 per question
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER, statQuizTotalCount)
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statQuizTotalCount)
        }

        viewModelScope.launch {
            //TODO: for 1 month feb/march 2026, facebook ads manager campaign. see stats
            if (ttsStatsRepository.isMarchOrApril2026()) {
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.GlobalStats)
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
            }
        }

    }
    // 1. Call this when user taps the "i" button
    fun onInfoButtonTapped() {
        infoUsedForCurrentQuestion = true
    }
    fun resetInfoButtonTapped() {
        infoUsedForCurrentQuestion = false
    }

    fun selectQuizById(quizId: Int) {
        viewModelScope.launch {
            val quizDetail = availableQuizzes.value.first { it.id == quizId }
            onQuizSelected(quizDetail)
        }
    }
    private fun resolveLocalizedBaseNameoriginal(baseName: String, regionCode: String?): String {
        // If no region or it's already English, stick to base
        if (regionCode.isNullOrBlank() || regionCode.lowercase() == "gb" || regionCode.lowercase() == "us") {
            return baseName
        }

        // Only attempt swap if the filename follows the "-en" pattern
        if (baseName.endsWith("-en")) {
            val countrySuffix = regionCode.lowercase() // "vn", "in", etc.
            val candidateName = baseName.replace("-en", "-$countrySuffix")

            // Check if the file "candidateName.json" actually exists in Assets
            return if (assetExists("Quizzes/ReadinessAudit/$candidateName.json")) {
                candidateName // Found specialized file!
            } else {
                baseName // Fallback to English
            }
        }

        return baseName
    }

    /**
     * Helper to check if a file exists in the assets folder
     */
    private fun assetExists(fileName: String): Boolean {
        return try {
            val stream = appContext.assets.open(fileName)
            stream.close()
            true
        } catch (e: Exception) {
            false
        }
    }
    private fun resolveLocalizedBaseName(baseName: String): String {
        val locale = Locale.getDefault()
        val regionCode = "en"

        // 1. If no region or it's a standard English region, stick to baseName
        val englishDefaults = listOf("gb", "us", "au", "ca")
        if (regionCode.isBlank() || englishDefaults.contains(regionCode)) {
            return baseName
        }

        // 2. Only attempt swap if the filename follows the "-en" pattern
        if (baseName.endsWith("-en")) {
            // Construct the candidate (e.g., "UsageQuiz1A1-in")
            val versionSuffix = if (_currentVersion.value == 1) "" else "2"
            val candidateName = baseName.replace("-en", "$versionSuffix-$regionCode")

            // 3. Check if the file "candidateName.json" actually exists in Assets
            // Adjust the path to match your specific folder structure
            //val assetPath = "Quizzes/ReadinessAudit/${candidateName}$versionSuffix.json"
            val assetPath = "Quizzes/ReadinessAudit/$candidateName.json"

            return if (assetExists(assetPath)) {
                candidateName // Found tailored region file!
            } else {
                baseName // Fallback to standard English
            }
        }

        return baseName
    }
    // 2. Update the filename resolver logic
    private fun resolveLocalizedBaseName99(baseName: String): String {
        val regionCode = "en" // Or your Locale logic
        val versionSuffix = if (_currentVersion.value == 1) "" else "2"

        // Logic: "BaselineAudit" + "2" + "-" + "en" = "BaselineAudit2-en"
        val finalBaseName = "$baseName$versionSuffix-$regionCode"

        // Check for localization (your existing logic)
        // ... check if -in, -vn etc exists, else use finalBaseName

        return finalBaseName
    }

    /**
     * Helper to check if a file exists in the Assets folder
     */
    private fun assetExistsContext(path: String): Boolean {
        return try {
            // We attempt to open the stream; if it succeeds, the file exists.
            appContext.assets.open(path).use { true }
        } catch (e: IOException) {
            false
        }
    }
    fun getConfidenceLabel(confidence: Int): String = AuditEngine.getConfidenceLabel(confidence)

    /**
     * Fraction (0f-1f) of each part's questions answered so far, keyed by part index (1-4).
     * Drives Confidence - unlike Score, it doesn't care whether answers were correct, only how
     * much of the audit has been covered.
     *
     * A part that has ever been fully completed (per QuizHistoryManager) always counts as 1f,
     * even if its daily answer-lock has since cleared for a new attempt - otherwise Confidence
     * would dip every time a finished part becomes retakeable the next day.
     */
    private suspend fun computeConfidenceProgress(): Map<Int, Float> {
        val progress = mutableMapOf<Int, Float>()
        ReadinessAuditLevels.entries.forEach { level ->
            val quiz = level.quizzes.firstOrNull() ?: return@forEach
            val partIndex = partIndexFor(level)
            val everCompleted = quizHistoryManager.getLastAttempt(level.description, quiz.id) != null

            if (everCompleted) {
                progress[partIndex] = 1f
                return@forEach
            }

            val state = auditRepository.getQuizAttemptState(quizAttemptKey(level, quiz))
            val total = state.totalQuestions
            if (total != null && total > 0) {
                progress[partIndex] = state.answers.size.toFloat() / total
            }
        }
        return progress
    }

    fun loadCurrentAuditStats() {
        viewModelScope.launch {
            // 1. Pull the combined flow from repo (Scores + Live Progress)
            // We use .first() to get a one-time snapshot for the current calculation
            val auditData = auditRepository.auditDataFlow.first()

            // 2. Use the corrected Engine
            val report = AuditEngine.calculate(
                testScores = auditData.scores,
                partProgress = auditData.activeProgress
            )

            // 3. Update the UI StateFlow
            _auditStats.value = AuditStats(
                confidence = report.confidence,
                readiness = report.readiness
            )
        }
    }

    // Helper for the UI text we added in the previous step
    fun getAuditorVerdictText(): String {
        return AuditEngine.getReadinessVerdict(_auditStats.value.readiness)
    }

    //State Machine for ending Audit readiness

    private val _showSummary = MutableStateFlow(false)
    val showSummary = _showSummary.asStateFlow()

    fun onAuditStepFinished(isEarlyExit: Boolean = false) {
        viewModelScope.launch {
            if (isEarlyExit) {
                // Path 1: User just wants to get to the app
//                _uiEvent.emit(UiEvent.NavigateToDashboard)
                Timber.e("navigate to NavigateToDashboard")
            } else {
                // Path 2 & 3: Completed at least Part 1
                _showSummary.value = true
            }
        }
    }

    fun adjustAppLevel(newLevel: String) {
        viewModelScope.launch {
            // Update the main app level setting
//            userPreferencesRepository.saveSelectedLevel(newLevel)
            Timber.e("Save new level")
            _showSummary.value = false
//            _uiEvent.emit(UiEvent.NavigateToDashboard)
            Timber.e("navigate to NavigateToDashboard")
        }
    }
}
