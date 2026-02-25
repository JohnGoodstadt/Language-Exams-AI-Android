package com.goodstadt.john.language.exams.viewmodels

//import android.graphics.Color
//import com.goodstadt.john.language.exams.managers.RateLimiterManager
//import com.google.gson.Gson

import android.app.Activity
import android.app.Application
import android.content.Context
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
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.UserStatsRepository
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.data.repository.PlaybackResult
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statQuizNotOKCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statQuizOkCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statRateLimiterDayForbidCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statRateLimiterForbidCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statRateLimiterHourForbidCount
import com.goodstadt.john.language.exams.data.repository.VocabQuizRepository
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.managers.XpActionType
import com.goodstadt.john.language.exams.models.AudioPlaybackStatus
import com.goodstadt.john.language.exams.models.VocabQuizOutcome
import com.goodstadt.john.language.exams.models.WordQuizRoot
import com.goodstadt.john.language.exams.packages.dailydictionary.DictionaryEntry
import com.goodstadt.john.language.exams.screens.reference.shared.QuizDetail
import com.goodstadt.john.language.exams.storage.UiEvent
import com.goodstadt.john.language.exams.utils.calcIsTodayFreePassDay
import com.goodstadt.john.language.exams.utils.calcIsTodayNotAFreePassDay
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.goodstadt.john.language.exams.utils.isTodayInstallDay
import com.goodstadt.john.language.exams.utils.readTestMyselfDataFromAssets
import dagger.hilt.android.internal.Contexts.getApplication
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Date
import javax.inject.Inject

enum class WordQuizState(val description: String) {
    NOT_STARTED("Not Started"),
    STARTED("Started"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed")
}

data class WordQuizStatistics(
    val timestamp: Date = Date(),
    var state: QuizState = QuizState.NOT_STARTED,
    val skillLevel: String,
    val quizNumber: Int,
    val title: String,
    var answered: Int = 0,
    var correct: Int = 0,
    var tries: Int = 0

) {
    override fun toString(): String {
        val dateFormatter =
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return "${dateFormatter.format(timestamp)} '${state.description}' '$skillLevel' level:$quizNumber answered:$answered tries:$tries correct:$correct"
    }


    fun update(answered: Int, correct: Int, tries: Int) =
        copy(answered = answered, correct = correct, tries = tries)
}

enum class WordQuizLevels(val quizzes: List<QuizDetail>) {
    PERSONAL(
        quizzes = listOf(
            QuizDetail(id = 1, baseName = "WordQuizPersonal1", title = "1. Personal"),
            QuizDetail(id = 2, baseName = "WordQuizPersonal2", title = "2. Personal"),
            QuizDetail(id = 3, baseName = "WordQuizPersonal3", title = "3. Personal"),
//            QuizDetail(id = 4, baseName = "WordQuiz4", title = "31 to 40"),
//            QuizDetail(id = 5, baseName = "WordQuiz5", title = "Quiz 5"),
//            QuizDetail(id = 6, baseName = "WordQuiz6", title = "Quiz 6"),
//            QuizDetail(id = 7, baseName = "WordQuiz7", title = "Quiz 7"),
//            QuizDetail(id = 8, baseName = "WordQuiz8", title = "Quiz 8"),
//            QuizDetail(id = 9, baseName = "WordQuiz9", title = "Quiz 9"),
//            QuizDetail(id = 10, baseName = "WordQuiz10", title = "Quiz 10"),
//
//            QuizDetail(id = 20, baseName = "WordQuiz20", title = "Quiz 20"),
//            QuizDetail(id = 21, baseName = "WordQuiz21", title = "Quiz 21"),
//            QuizDetail(id = 30, baseName = "WordQuiz30", title = "Quiz 30"),
//            QuizDetail(id = 32, baseName = "WordQuiz32", title = "Quiz 32"),
//            QuizDetail(id = 33, baseName = "WordQuiz33", title = "Quiz 33"),
//            QuizDetail(id = 34, baseName = "WordQuiz34", title = "Quiz 34"),
//            QuizDetail(id = 35, baseName = "WordQuiz35", title = "Quiz 35"),
//            QuizDetail(id = 37, baseName = "WordQuiz37", title = "Quiz 37"),

        )
    ),
    EDUCATION(
        quizzes = listOf(
            QuizDetail(id = 1, baseName = "WordQuizEducation1", title = "Education 1"),
            QuizDetail(id = 2, baseName = "WordQuizEducation2", title = "Education 2"),
            QuizDetail(id = 3, baseName = "WordQuizEducation3", title = "Education 3")
        )
    ),

    ADJECTIVES(
        quizzes = listOf(
            QuizDetail(id = 1, baseName = "WordQuizAdjectives1", title = "Adjectives 1"),
            QuizDetail(id = 2, baseName = "WordQuizAdjectives2", title = "Adjectives 2"),
            QuizDetail(id = 3, baseName = "WordQuizAdjectives3", title = "Adjectives 3")
        )
    ),
    HEALTH(
        quizzes = listOf(
            QuizDetail(id = 1, baseName = "WordQuizHealth1", title = "Health 1"),
            QuizDetail(id = 2, baseName = "WordQuizHealth2", title = "Health 2"),
            QuizDetail(id = 3, baseName = "WordQuizHealth3", title = "Health 3")
        )
    ),
    LEISURE(
        quizzes = listOf(
            QuizDetail(id = 1, baseName = "WordQuizLeisure1", title = "1. Leisure"),
            QuizDetail(id = 2, baseName = "WordQuizLeisure2", title = "2. Leisure"),
            QuizDetail(id = 3, baseName = "WordQuizLeisure3", title = "3. Leisure")
        )
    );

    val description: String
        get() = when (this) {
            PERSONAL -> "Personal"
            EDUCATION -> "Education"
            ADJECTIVES -> "Adjectives"
            HEALTH -> "Health"
            LEISURE -> "Leisure"
        }
}


data class WordQuizQuestion(
    val question: String,
    val answers: List<String>,
    val correctOption: String,
    val summary: String,
    val explain: DictionaryEntry?,
    val title: String,
)

sealed interface WordQuizUiState {
    object Loading : WordQuizUiState
    data class Success(
        //val categories: List<Category>,
        val selectedVoiceName: String = "" // Add a default empty value
    ) : WordQuizUiState

    data class Error(val message: String) : WordQuizUiState
    object NotAvailable : WordQuizUiState // For flavors like 'zh'
}

@HiltViewModel
class WordQuizViewModel @Inject constructor(
    private val application: Application,
    private val vocabRepository: ContentRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ttsStatsRepository: TTSStatsRepository,
    private val billingRepository: BillingRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val connectivityRepository: ConnectivityRepository,
    private val quizHistoryManager: QuizHistoryManager,
    private val xpManager: XPManager,
    private val vocabQuizRepository: VocabQuizRepository,
    private val audioPlaybackRepository: AudioPlaybackRepository

    ) : ViewModel() {
    private val appContext: Context = application.applicationContext

    private val _uiState99 = MutableStateFlow<WordQuizUiState>(WordQuizUiState.Loading)
    val uiState99 = _uiState99.asStateFlow()

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    // region State FLow
    private val _questions = MutableStateFlow<List<WordQuizQuestion>>(emptyList())
    val questions: StateFlow<List<WordQuizQuestion>> get() = _questions


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

    private val _isSectionMode = MutableStateFlow(false)
    val isSectionMode = _isSectionMode.asStateFlow()
    private val _availableSectionIndices = MutableStateFlow<List<Int>>(emptyList())
    val availableSectionIndices = _availableSectionIndices.asStateFlow()

    private val _currentSectionIndex = MutableStateFlow(1)
    val currentSectionIndex = _currentSectionIndex.asStateFlow()

    // Store the cleaned base name (e.g. "WordQuizTravel") so we can switch numbers easily
    private var currentSectionBaseName: String = ""


    private val jsonParser = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    // endregion

    val quizStatistics = mutableStateOf(
        WordQuizStatistics(
            skillLevel = WordQuizLevels.PERSONAL.description,
            quizNumber = 1,
            title = "Quiz 1"
        )
    )
    val selectedLevel = mutableStateOf(WordQuizLevels.PERSONAL)

    val selectedQuiz = mutableStateOf<QuizDetail?>(null)

    val selectedQuizNumber = mutableStateOf(1)
    val currentQuestionIndex = mutableStateOf(0)
    val userAnswers = mutableStateOf(mutableMapOf<Int, Boolean>())

    //Constants
    val quizFillInTheBlanks = 7 //in iOS these are ENUMs
    val quizQandA = 10
    val quizMultipleChoice = 11
    val quizDefinitions = 12
    val quizWordDefinition = 13
    val currentFileFormat =
        mutableStateOf(quizFillInTheBlanks) //either 7 (fill in the blank) or 10 (Multiple choice)

    // ❌ OLD (Static):
    val availableQuizzesObsolete = derivedStateOf { selectedLevel.value.quizzes }

    // ✅ NEW (Dynamic):
    // This starts with the default English titles, but we can overwrite them later
    private val _availableQuizzes =
        MutableStateFlow<List<QuizDetail>>(WordQuizLevels.PERSONAL.quizzes)
    val availableQuizzes = _availableQuizzes.asStateFlow()

    // 1. The Cache: Maps a Level (e.g. PERSONAL) to its list of localized QuizDetails
    private val quizTitleCache = mutableMapOf<WordQuizLevels, List<QuizDetail>>()

    private var _currentQuestionAttempts = 0

    private var infoUsedForCurrentQuestion = false
//    private var currentQuestionAttempts = 0
    private val _showInfoSheet = MutableStateFlow(false)
    val showInfoSheet = _showInfoSheet.asStateFlow()

    /**
     * A simple data class to hold the metadata for a single quiz.
     *
     * @param id A unique identifier for the quiz within its level (e.g., 1, 2, 3...).
     * @param baseName The name of the JSON asset file for this quiz.
     * @param title The human-readable display name for this quiz (e.g., "Quiz 1 - Simple Tenses").
     */
//    data class QuizDetail(
//        val id: Int,
//        val baseName: String, //e.g. "Quiz1Elementary-en"
//        val title: String
//    )


    init {
        // 1. Start background loading
        //preloadLocalizedTitles()

        selectedQuiz.value = selectedLevel.value.quizzes.firstOrNull()
       // loadQuestions()
        viewModelScope.launch {
            billingRepository.isPurchased.collect { purchasedStatus ->
                _isPremiumUser.value = purchasedStatus
                if (DEBUG) {
                    billingRepository.logCurrentStatus()
                }
            }
        }

        vocabQuizRepository.debugPrintStatus()
    }
    /**
     * Loads a quiz specifically for a Category Section (e.g. "Personal Information")
     */
    fun loadSectionQuiz(categoryTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSectionMode.value = true

            // 1. Sanitize Title
            val noB1Title = categoryTitle.replace(" (B1)", "") //personal title
            val cleanTitle = noB1Title.replace(" ", "").replace(Regex("[^A-Za-z0-9]"), "")
            val baseFilenamePrefix = "WordQuiz${cleanTitle}" // e.g. "WordQuizTravel"
            currentSectionBaseName = baseFilenamePrefix

            // 2. Scan Assets to find how many exist
            // We look for "WordQuizTravel1-en.json", "WordQuizTravel2-en.json", etc.
            val foundIndices = mutableListOf<Int>()

            try {
                // Get all files in Quizzes folder
                val allFiles = application.assets.list("Quizzes")?.toList() ?: emptyList()

                // Check 1..10 (Reasonable limit)
                for (i in 1..10) {
                    // Note: You might want to handle localized suffixes here too (-hi, etc)
                    // For now, assuming -en base checks
                    val target = "${baseFilenamePrefix}${i}-en.json"
                    if (allFiles.contains(target)) {
                        foundIndices.add(i)
                    }
                }
            } catch (e: Exception) {
                Timber.e("Error scanning assets for section quizzes")
            }

            _availableSectionIndices.value = foundIndices

            // 3. Load the first one (Default)
            if (foundIndices.isNotEmpty()) {
                loadSpecificSectionIndex(foundIndices.first())
            } else {
                // Handle empty case
                _questions.value = emptyList()
            }
        }
    }

    // ✅ NEW: Switch between 1, 2, 3
    fun onSectionIndexSelected(index: Int) {
        if (_currentSectionIndex.value == index) return
        loadSpecificSectionIndex(index)
    }

    private fun loadSpecificSectionIndex(index: Int) {
        viewModelScope.launch {
            _currentSectionIndex.value = index

            // Construct filename: "WordQuizTravel" + "2" + "-en"
            // (You should use your getLocalizedFileName helper here if you want translation support)
            val filename = "${currentSectionBaseName}${index}-en"

            val testData = readWordQuizDataFromAssets(application, filename)

            if (testData != null) {
                _questions.value = generateQuestionsFromData(testData)

                // Update title to show which number we are on
                quizStatistics.value = quizStatistics.value.copy(
                    title = "Quiz $index", // or fetch title from JSON
                    skillLevel = "Section Practice"
                )

                resetQuiz()
            }
        }
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
    fun handleTap(sentence: String) {
        // if (_playbackState.value is PlaybackState.Playing) return

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
            if (ttsStatsRepository.isFebOrMarch2026()) {
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.GlobalStats)
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
            }

        }
    }
    fun playTrack(sentence: String) {

        if (_playbackState.value is PlaybackState.Playing) return

        if (!connectivityRepository.isCurrentlyOnline()) {
            _playbackState.value = PlaybackState.Idle
            return
        }

        viewModelScope.launch {
            //AI Recommends ignore install day free
//            val todayIsNotAFreePassDay = calcIsTodayNotAFreePassDay(userPreferencesRepository)
            if (!isPremiumUser.value) { //if premium user don't check credits or is on day 1
                if (rateLimiter.doIForbidCall()) {
                    val failType = rateLimiter.canMakeCallWithResult()
                    Timber.v("${failType.canICallAPI}")
                    Timber.v("${failType.failReason}")
                    Timber.v("${failType.timeLeftToWait}")
                    if (!failType.canICallAPI) {
                        if (failType.failReason == SimpleRateLimiter.FailReason.DAILY) {
                            _showRateDailyLimitSheet.value = true
                            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statRateLimiterDayForbidCount)
                        } else {
                            _showRateHourlyLimitSheet.value = true
                            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats,statRateLimiterHourForbidCount)
                        }
                    } else {
                        _showRateLimitSheet.value = true
                    }
                    ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statRateLimiterForbidCount)
                    return@launch
                }
            }


            val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
            val uniqueSentenceId = generateUniqueSentenceId(sentence, currentVoiceName)

            val played = vocabRepository.playFromCacheIfFound(uniqueSentenceId)
            if (played) {//short cut so user cna play cached sentences with no Internet connection
                _playbackState.value = PlaybackState.Idle
                ttsStatsRepository.updateTTSStatsWithoutCosts()
                return@launch
            }


            val currentLanguageCode = userPreferencesRepository.selectedLanguageCodeFlow.first()

            val result = vocabRepository.playTextToSpeech(
                text = sentence,
                uniqueSentenceId = uniqueSentenceId,
                voiceName = currentVoiceName,
                languageCode = currentLanguageCode
            )

            when (result) {
                is PlaybackResult.PlayedFromNetworkAndCached -> {
//                    if (todayIsNotAFreePassDay) {
                        rateLimiter.recordCall()
//                    }
                    Timber.v(rateLimiter.printCurrentStatus)
                    ttsStatsRepository.updateTTSStatsWithCosts(sentence, currentVoiceName)
                }

                is PlaybackResult.PlayedFromLocalCache -> {
                    ttsStatsRepository.updateTTSStatsWithoutCosts()
                }

                is PlaybackResult.Failure -> {
                    _playbackState.value =
                        PlaybackState.Error(result.exception.message ?: "Playback failed")
                }

                PlaybackResult.CacheNotFound -> Timber.e("Cache found to exist but not played")
            }
            _playbackState.value = PlaybackState.Idle
        }

    }

    fun loadQuestions() {
        viewModelScope.launch {

            val quizDetail = selectedQuiz.value ?: selectedLevel.value.quizzes.first()
            val baseName =
                (selectedQuiz.value ?: selectedLevel.value.quizzes.first()).baseName //+ ".json"

            val finalFilename = getLocalizedFileName(appContext, baseName)

            val testData = readWordQuizDataFromAssets(appContext, finalFilename)

            if (testData == null) {
                Timber.wtf("Failed to parse JSON file: $finalFilename")
                return@launch
            }


            // val loadedTitle = testData.data.firstOrNull()?.title ?: quizDetail.title

            if (testData.title?.isNotEmpty() == true) {
                Timber.i("Sheet title is ${testData.title} ")
//                quizStatistics.value = quizStatistics.value.copy(
//                    title = testData.title
//                )
            }
            quizStatistics.value = quizStatistics.value.copy(
                title = quizDetail.title
            )

            _questions.value = generateQuestionsFromData(testData)


            Timber.v("${_questions.value.count()}")


            resetQuiz()

        }
    }

    // A new function for the UI to call when a different level is picked.
    fun onLevelSelectedObsolete(level: WordQuizLevels) {
        selectedLevel.value = level
        // When the level changes, reset the selected quiz to the first one of the new level.
        selectedQuiz.value = level.quizzes.firstOrNull()
        loadQuestions()
    }

    fun onLevelSelected(level: WordQuizLevels) {
        selectedLevel.value = level

        // 1. Update the list of quizzes (Async)
        refreshQuizTitlesForLevel(level)

        // 2. Reset selection to first (we use the Enum list temporarily until async finishes)
//        selectedQuiz.value = level.quizzes.firstOrNull()
        selectedQuiz.value = _availableQuizzes.value.firstOrNull()

        loadQuestions()
    }

    // A new function for the UI to call when a different quiz is picked from the dropdown.
    fun onQuizSelected(quizDetail: QuizDetail) {
        selectedQuiz.value = quizDetail
        loadQuestions()
    }


    private fun generateQuestionsFromData(testData: WordQuizRoot): List<WordQuizQuestion> {

        if (testData.fileFormat == quizQandA) {
            currentFileFormat.value = quizQandA
        } else if (testData.fileFormat == quizDefinitions) {
            currentFileFormat.value = quizDefinitions
        } else if (testData.fileFormat == quizMultipleChoice) {
            currentFileFormat.value = quizMultipleChoice
        } else if (testData.fileFormat == quizWordDefinition) {
            currentFileFormat.value = quizWordDefinition
        } else {
            currentFileFormat.value = quizFillInTheBlanks
        }

        val a = when (testData.fileFormat) {
            quizQandA -> quizQandA
            quizDefinitions -> quizDefinitions
            quizMultipleChoice -> quizMultipleChoice
            else -> quizFillInTheBlanks

        }

        //because spellings should follow each other
//        if (testData.fileFormat == quizFillInTheBlanks)
        testData.shuffleLists()


        return testData.data.flatMap { section ->
            section.sections.map { quizSection ->
                val shuffledWords = quizSection.answers.shuffled()
                val words = shuffledWords.map { it.answer }
                val correctOption = quizSection.answers.firstOrNull { it.ok }?.answer ?: ""
                val summary = quizSection.summary
                val explain = quizSection.explain ?: DictionaryEntry()
                val title = quizSection.title
                WordQuizQuestion(
                    quizSection.question,
                    words,
                    correctOption,
                    summary,
                    explain,
                    title
                )
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
        _currentQuestionAttempts = 0

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


    }

    private fun saveQuizState() {
        if (quizStatistics.value.state != QuizState.NOT_STARTED) {
            //save stats from previous quiz try

            if (userAnswers.value.count() == _questions.value.count()) {
                quizStatistics.value.state = QuizState.COMPLETED

            }
        }
    }

    private fun onQuizFinished() {

        val qs = quizStatistics
        val now = System.currentTimeMillis()

        // 1. Check History BEFORE saving
        val lastAttempt =
            quizHistoryManager.getLastAttempt(qs.value.skillLevel, qs.value.quizNumber)

        viewModelScope.launch {

            quizHistoryManager.saveAttempt(
                qs.value.skillLevel,
                qs.value.quizNumber,
                qs.value.title,
                qs.value.correct,
                qs.value.tries
            )

            // 3. Logic
            if (qs.value.correct < qs.value.tries) {
                xpManager.registerAction(XpActionType.CompleteQuiz)
            } else {
                // Perfect Score
                if (lastAttempt != null) {
                    val diff = now - lastAttempt.timestamp
                    val oneDayMillis = 1000 * 60 * 60 * 24

                    if (diff > oneDayMillis) {
                        // ✅ Memory Boost
                        xpManager.registerAction(XpActionType.MemoryBoost)
                    } else {
                        // ⚠️ Grinding
                        // Maybe just give 5 XP?
                        xpManager.registerAction(XpActionType.ReplaySentence) // Re-use low value or make new one
                    }
                } else {
                    // First Time Perfect
                    xpManager.registerAction(XpActionType.PerfectQuiz)
                }
            }

        }
    }

    fun updateAnswer(isCorrect: Boolean) {
        userAnswers.value[currentQuestionIndex.value] = isCorrect
        quizStatistics.value = quizStatistics.value.copy(
            answered = userAnswers.value.size,
            correct = userAnswers.value.count { it.value },
            tries = quizStatistics.value.tries + 1
        )
        if (quizStatistics.value.state == QuizState.NOT_STARTED) {
            quizStatistics.value = quizStatistics.value.copy(state = QuizState.IN_PROGRESS)
        }


        val currentQuestion = currentQuestionIndex.value + 1 // one based
        if (currentQuestion >= _questions.value.count()) { //completed
            quizStatistics.value = quizStatistics.value.copy(
                state = QuizState.COMPLETED,
                title = quizStatistics.value.title
            )

            onQuizFinished()
        }

    }

    fun readWordQuizDataFromAssetsObsolete(context: Context, fileName: String): WordQuizRoot? {
        return try {
            // Timber.v("reading json: $fileName")

            val finalFilename = "$fileName.json"
            val jsonString = context.assets.open("Quizzes/$finalFilename")
                .bufferedReader()
                .use { it.readText() }

            return jsonParser.decodeFromString<WordQuizRoot>(jsonString)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    fun readWordQuizDataFromAssets(context: Context, fileName: String): WordQuizRoot? {
        // 1. Sanitize input: Remove folder prefix if passed, handle extension
        val cleanName = File(fileName).name // Removes "Quizzes/" if passed accidentally
        val finalName = if (cleanName.endsWith(".json")) cleanName else "$cleanName.json"
        val fullPath = "Quizzes/$finalName"

        return try {
            // 2. Read
            val jsonString = context.assets.open(fullPath)
                .bufferedReader()
                .use { it.readText() }

            // 3. Decode
            jsonParser.decodeFromString<WordQuizRoot>(jsonString)

        } catch (e: FileNotFoundException) {
            Timber.e("❌ FILE NOT FOUND: '$fullPath'. (Check folder 'Quizzes' and case sensitivity)")

            // Debug aid: List what IS there to help you fix it
            val actualFiles = context.assets.list("Quizzes")?.joinToString() ?: "Empty/Missing Folder"
            Timber.e("   -> Available files: $actualFiles")
            null
        } catch (e: Exception) {
            Timber.e(e, "❌ Error parsing JSON in '$fullPath'")
            null
        }
    }
    // Call this whenever the Level changes (e.g. from Elementary to Inter)
    private fun refreshQuizTitlesForLevel(level: WordQuizLevels) {
        viewModelScope.launch {

            // 1. Get the list of default quizzes for this level
            val defaultQuizzes = level.quizzes

            // 2. Map them to potentially new titles by peeking at the JSON files
            val updatedQuizzes = defaultQuizzes.map { quizDetail ->

                // A. Resolve Filename (e.g. "...-hi.json")
                val filename = getLocalizedFileName(appContext, quizDetail.baseName)

                // B. Peek at the JSON to get the title
                // Note: This needs to be fast. If reading the whole file is too slow,
                // you might want to cache this or use a lighter "Metadata" read.
                val title = peekTitleFromJson(filename) ?: quizDetail.title

                // C. Return updated object
                quizDetail.copy(title = title)
            }

            // 3. Publish the new list to the UI
            _availableQuizzes.value = updatedQuizzes

            // 4. Also ensure the currently selected quiz object is updated if needed
            val currentId = selectedQuiz.value?.id
            if (currentId != null) {
                selectedQuiz.value = updatedQuizzes.find { it.id == currentId }
            }
        }
    }

    // Helper to read just the title
    private fun peekTitleFromJson(filename: String): String? {
        return try {
            // Reusing your existing reader logic, but maybe we can optimize later
            val data = readWordQuizDataFromAssets(appContext, filename)
            // Get the title from the root object if you added it there, or the first section
            data?.title // Assuming you added 'val title: String' to TestMyselfListRoot
        } catch (e: Exception) {
            null
        }
    }

    private fun preloadLocalizedTitles() {
        viewModelScope.launch(Dispatchers.IO) {

            // Loop through all Enum Levels (Elementary, Inter, etc.)
            WordQuizLevels.entries.forEach { level ->

                // Map the default quizzes to their localized versions
                val localizedList = level.quizzes.map { quizDetail ->

                    // A. Resolve Filename (e.g. "...-hi.json")
                    val finalFileName = getLocalizedFileName(appContext, quizDetail.baseName)

                    // B. Peek at the JSON to get the title
                    // Note: We catch errors here so one bad file doesn't break the whole loop
                    val newTitle = try {
                        val data = readWordQuizDataFromAssets(appContext, finalFileName)
                        // If file has a title, use it. Else fall back to Enum default.
                        data?.title ?: quizDetail.title
                    } catch (e: Exception) {
                        quizDetail.title
                    }

                    // Return the updated QuizDetail object
                    quizDetail.copy(title = newTitle)
                }

                // Save to Cache
                quizTitleCache[level] = localizedList
            }

            // ✅ UPDATE UI: Once loading is done, refresh the *currently* displayed list
            // so the user sees the change if they are already looking at the screen.
            withContext(Dispatchers.Main) {
                updateAvailableQuizzesFor(selectedLevel.value)

                // 2. FIX: Refresh the currently selected quiz text
                // We take the ID of the current selection (e.g. ID: 1, Title: "Simple Tenses")
                // And find its "Twin" in the new localized list (e.g. ID: 1, Title: "Tenses (Hindi)")
                val current = selectedQuiz.value

                if (current != null) {
                    val updatedVersion = _availableQuizzes.value.find { it.id == current.id }

                    if (updatedVersion != null) {
                        // This triggers the Dropdown to redraw with the new title
                        selectedQuiz.value = updatedVersion

                        // Optional: Update stats title to match if needed
                        // quizStatistics.value = quizStatistics.value.copy(title = updatedVersion.title)
                    }
                }

            }
        }
    }

    private fun updateAvailableQuizzesFor(level: WordQuizLevels) {
        // If cache is ready, use it. If not (still loading), use default English list.
        _availableQuizzes.value = quizTitleCache[level] ?: level.quizzes
    }

    fun WordQuizRoot.shuffleLists() {
        data.forEach { WordQuizList ->
            WordQuizList.sections = WordQuizList.sections.shuffled() // Shuffle sections
            WordQuizList.sections.forEach { section ->
                section.answers = section.answers.shuffled() // Shuffle words within each section
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
        val localizedName = "$baseName-$currentCode.json"
        val defaultName = "$baseName-en.json"

        // 3. Check if the localized file exists in Assets
        // We list files in the "Quizzes" folder to check existence efficiently
        val filesInAssets = try {
            context.assets.list("Quizzes")?.toList() ?: emptyList()
        } catch (e: IOException) {
            Timber.e("⚠️ val filesInAssets Error: $defaultName")
            return defaultName
        }

        // 4. Return localized if found, otherwise default
        return if (filesInAssets.contains(localizedName)) {
            // Timber.i("✅ Found localized quiz: $localizedName")
            localizedName
        } else {
            Timber.e("⚠️ Localized quiz not found, falling back to: $defaultName")
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

    fun incQuizStat(success: Boolean = true) {

        val baseName = (selectedQuiz.value ?: selectedLevel.value.quizzes.first()).baseName
        val finalName = getLocalizedName(appContext, baseName) //no json

        Timber.v(finalName)

        val statName =
            if (success) "${statQuizOkCount}_$finalName" else "${statQuizNotOKCount}_$finalName"

        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER, statName)
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statName)


        viewModelScope.launch {
            //TODO: for 1 month feb/march 2026, facebook ads manager campaign. see stats
            if (ttsStatsRepository.isFebOrMarch2026()) {
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.GlobalStats)
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
            }
        }

    }

    fun vocabQuizAttemptStats(isCorrect: Boolean, word: String) {

        //if this is called then an attempt has been made -- either OK or not OK

        //


        if (isCorrect) {
            // If they got it right on try #1, tries = 1.
            // If they got it right after 2 fails, tries = 3.

            // Note: You need to track 'triesForCurrentQuestion' locally in VM
            // because quizStatistics.value.tries might be total for the whole quiz.
            _currentQuestionAttempts++
            Timber.v("vocabQuizAttemptStats()  correct:$isCorrect word:$word  tries:$_currentQuestionAttempts")


           // vocabQuizRepository.recordResult(word, _currentQuestionAttempts)
        } else {
            _currentQuestionAttempts++
            Timber.v("vocabQuizAttemptStats()  correct:$isCorrect word:$word  tries:$_currentQuestionAttempts")

        }
    }

    fun resetCurrentQuestionAttempts() {
        _currentQuestionAttempts = 0
    }

    fun onInfoClicked() {
//        _showInfoSheet.value = true
        infoUsedForCurrentQuestion = true
    }

    fun markAnswerSelected(word: String,isCorrect:Boolean) {
        val tries = _currentQuestionAttempts // You need to track attempts count per word

        // ... Check if correct ...
        if (isCorrect) {

            // 1. Calculate Outcome
            val outcome = when {
                tries == 1 && !infoUsedForCurrentQuestion -> VocabQuizOutcome.FLAWLESS
                tries == 1 && infoUsedForCurrentQuestion -> VocabQuizOutcome.ASSISTED
                tries == 2 -> VocabQuizOutcome.STUMBLED
                else -> VocabQuizOutcome.FAILED
            }

            // 2. Save to Repo
            vocabQuizRepository.recordResult(word, outcome)

            // 3. Award XP (Ideas)
            awardXP(outcome)

            // Reset for next question
            infoUsedForCurrentQuestion = false
            _currentQuestionAttempts = 0
        } else {
            _currentQuestionAttempts++
        }
    }

    private fun awardXP(outcome: VocabQuizOutcome) {
        when (outcome) {
            VocabQuizOutcome.FLAWLESS -> xpManager.registerAction(XpActionType.PerfectWord, count = 5) // High Reward
            VocabQuizOutcome.ASSISTED -> xpManager.registerAction(XpActionType.MasterWord, count = 2) // Small Reward
            VocabQuizOutcome.STUMBLED -> xpManager.registerAction(XpActionType.MasterWord, count = 1) // Token Reward
            VocabQuizOutcome.FAILED -> { /* No XP, try again later */ }
        }
    }
}
