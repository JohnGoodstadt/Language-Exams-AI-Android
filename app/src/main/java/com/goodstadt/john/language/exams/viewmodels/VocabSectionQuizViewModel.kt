package com.goodstadt.john.language.exams.viewmodels

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
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.data.repository.PlaybackResult
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statQuizTotalCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statRateLimiterDayForbidCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statRateLimiterForbidCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statRateLimiterHourForbidCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statVocabQuizNotOKCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statVocabQuizOkCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statVocabQuizTotalCount
import com.goodstadt.john.language.exams.data.repository.VocabQuizRepository
import com.goodstadt.john.language.exams.managers.BannerManager
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.managers.XpActionType
import com.goodstadt.john.language.exams.models.AudioPlaybackStatus
import com.goodstadt.john.language.exams.models.VocabLearningState
import com.goodstadt.john.language.exams.models.VocabQuizOutcome
import com.goodstadt.john.language.exams.models.WordMasteryLevel
import com.goodstadt.john.language.exams.models.WordQuizRoot
import com.goodstadt.john.language.exams.packages.dailydictionary.DictionaryEntry
import com.goodstadt.john.language.exams.screens.CategoryTab.SectionQuizKeyMap
import com.goodstadt.john.language.exams.screens.UsageQuiz.QuizState
import com.goodstadt.john.language.exams.screens.reference.shared.QuizDetail
import com.goodstadt.john.language.exams.storage.UiEvent
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
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
/*
Note this file is used on TABS 1,2,3 for each section and the reference TAB Vocab Quiz Screen - for both Section quiz and Review Now quiz
 */
data class VocabQuizStatistics(
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

enum class VocabQuizLevels(val quizzes: List<QuizDetail>) {
    PERSONAL(
        quizzes = listOf(
            QuizDetail(id = 1, baseName = "WordQuizPersonal1", title = "1. Personal"),
            QuizDetail(id = 2, baseName = "WordQuizPersonal2", title = "2. Personal"),
            QuizDetail(id = 3, baseName = "WordQuizPersonal3", title = "3. Personal"),


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

sealed interface VocabQuizUiState {
    object Loading : VocabQuizUiState
    data class Success(
        //val categories: List<Category>,
        val selectedVoiceName: String = "" // Add a default empty value
    ) : VocabQuizUiState

    data class Error(val message: String) : VocabQuizUiState
    object NotAvailable : VocabQuizUiState // For flavors like 'zh'
}

private const val QUIZ_PATH = "Quizzes/SectionQuiz"

@HiltViewModel
class VocabSectionQuizViewModel @Inject constructor(
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
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val bannerManager: BannerManager,

    ) : ViewModel() {
    private val appContext: Context = application.applicationContext

    private val _uiState99 = MutableStateFlow<VocabQuizUiState>(VocabQuizUiState.Loading)
    val uiState99 = _uiState99.asStateFlow()

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    // region State FLow
    private val _questions = MutableStateFlow<List<WordQuizQuestion>>(emptyList())
    val questions: StateFlow<List<WordQuizQuestion>> get() = _questions

    // Mastery Filter
    private var _allQuestions: List<WordQuizQuestion> = emptyList()
    private val _activeFilters = MutableStateFlow<Set<WordMasteryLevel>>(emptySet())
    val activeFilters: StateFlow<Set<WordMasteryLevel>> = _activeFilters.asStateFlow()
    val totalQuestionCount: Int get() = if (_allSectionQuestions.isNotEmpty()) _allSectionQuestions.size else _allQuestions.size

    // Section-wide questions (all JSON files combined) and pagination
    private var _allSectionQuestions: List<WordQuizQuestion> = emptyList()
    private var _paginatedQuestions: List<List<WordQuizQuestion>> = emptyList()
    private val pageSize = 10
    val filteredQuestionCount: Int get() = _paginatedQuestions.sumOf { it.size }


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
    private var currentSectionTitle: String = ""
    private var currentSkillLevel: String = "B1" // Default

    // 2. This is what the BottomSheet observes.
    // This is purely local memory (not saved to disk yet).
    private val _sessionCorrectAnswers = MutableStateFlow(0)
    val sessionCorrectAnswers = _sessionCorrectAnswers.asStateFlow()

    // Keep a list of word IDs mastered during this specific session
    private val masteredInThisSession = mutableListOf<String>()

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    // endregion

    val quizStatistics = mutableStateOf(
        VocabQuizStatistics(
            skillLevel = VocabQuizLevels.PERSONAL.description, //TODO: Wrong
            quizNumber = 1,
            title = "Quiz 1"
        )
    )
    val selectedLevel = mutableStateOf(VocabQuizLevels.PERSONAL)

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
        MutableStateFlow<List<QuizDetail>>(VocabQuizLevels.PERSONAL.quizzes)
    val availableQuizzes = _availableQuizzes.asStateFlow()

    // 1. The Cache: Maps a Level (e.g. PERSONAL) to its list of localized QuizDetails
    private val quizTitleCache = mutableMapOf<VocabQuizLevels, List<QuizDetail>>()

    private var _currentQuestionAttempts = 0
    private var _currentQuestionWord: String? = null

    private var infoUsedForCurrentQuestion = false
//    private var currentQuestionAttempts = 0
    private val _showInfoSheet = MutableStateFlow(false)
    val showInfoSheet = _showInfoSheet.asStateFlow()

    // ✅ Helper flow to tell UI if data changed
    // We map userAnswers map size. If > 0, it's dirty.
//    val isDirty: StateFlow<Boolean> = snapshotFlow {
//        userAnswers.value.isNotEmpty()
//    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    private val _isDirty = MutableStateFlow(false)
    val isDirty = _isDirty.asStateFlow()

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

        vocabQuizRepository.debugPrintAllWordStates()
    }
    /**
     * Loads a quiz specifically for a Category Section (e.g. "Personal Information")
     */
    fun loadSectionQuiz(categoryTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSectionMode.value = true

            currentSkillLevel = userPreferencesRepository.selectedSkillLevelFlow.first()

            // 1. Resolve the language-independent quiz key via the per-flavour resolver.
            //    Each flavour ships its own SectionQuizKeyMap (title -> key, keyed by level);
            //    a null result means this category has no quiz at this level, so show nothing.
            //    currentSectionTitle uses the key so stats land in the same bucket in any language.
            val quizKey = SectionQuizKeyMap.keyFor(currentSkillLevel, categoryTitle)
            if (quizKey.isNullOrBlank()) {
                _questions.value = emptyList()
                _availableSectionIndices.value = emptyList()
                return@launch
            }
            currentSectionTitle = quizKey
            val baseFilenamePrefix = "WordQuiz$quizKey" // e.g. "WordQuizFood"
            currentSectionBaseName = baseFilenamePrefix

            // 2. Load ALL JSON files into a single combined list
            val allQuestions = mutableListOf<WordQuizQuestion>()

            try {

                val assetFolder = "${QUIZ_PATH}/$currentSkillLevel"
                val filesInFolder = application.assets.list(assetFolder)?.toList() ?: emptyList()

                for (i in 1..10) {
                    // Match whichever language suffix this flavour actually ships (-en / -de …)
                    val match = filesInFolder.firstOrNull {
                        it.startsWith("$baseFilenamePrefix$i-") && it.endsWith(".json")
                    } ?: break
                    val filename = match.removeSuffix(".json")
                    val testData = readWordQuizDataFromAssets(application, filename, currentSkillLevel)
                    if (testData != null) {
                        allQuestions.addAll(generateQuestionsFromData(testData))
                    }
                }
            } catch (e: Exception) {
                Timber.e("Error scanning/loading assets for section quizzes")
            }

            _allSectionQuestions = allQuestions

            // 3. Paginate and display
            if (allQuestions.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    applyFiltersAndPaginate()

                    quizStatistics.value = quizStatistics.value.copy(
                        title = "Quiz 1",
                        skillLevel = "Section Practice"
                    )
                }
            } else {
                _questions.value = emptyList()
                _availableSectionIndices.value = emptyList()
            }
        }
    }
    // Call this when "Rev iew Now" is tapped
    fun loadSmartReviewQuiz() {
        viewModelScope.launch(Dispatchers.IO) {
            _isSectionMode.value = true // Hide pickers
            _allSectionQuestions = emptyList() // Not a section quiz, don't use pagination

//            val level = userPreferencesRepository.selectedSkillLevelFlow.first()
            currentSkillLevel = userPreferencesRepository.selectedSkillLevelFlow.first()
            // 1. Get Due Items
            //val dueWordsTest = vocabQuizRepository.getDueWords(limit = 10)
            //Timber.i("${dueWordsTest}")

            val dueItems = vocabQuizRepository.getDueItems(limit = 10,currentSkillLevel)

            if (dueItems.isEmpty()) {
                // Fallback: Just load random questions from current level?
                // Or show "Nothing due!" message.
//                _uiState99.update { it.copy(error = "No words due for review!") }
                Timber.w("No words due for review!")
                return@launch
            }

            Timber.i("${dueItems}")

            // 2. Group by Category to minimize file reads
            // Map: "Personal Information" -> List<"Name", "Age">
            val itemsByCategory = dueItems.groupBy { it.category }
            Timber.i("${itemsByCategory}")

            val compiledQuestions = mutableListOf<WordQuizQuestion>()

            // 3. Iterate Categories and Load Files
            for ((categoryTitle, items) in itemsByCategory) {
                if (categoryTitle.isEmpty()) continue

                val cleanTitle = categoryTitle.replace(" ", "").replace(Regex("[^A-Za-z0-9]"), "")

                // Track words we still need to find questions for in this category
                val wordsToFind = items.map { it.word }.toMutableSet()

                // 🔄 LOOP through File Indexes (1, 2, 3...)
                var fileIndex = 1
                var fileExists = true

                while (fileExists && wordsToFind.isNotEmpty()) {

                    // Construct filename: "WordQuizPersonal1-en", "WordQuizPersonal2-en"...
                    val filename = "WordQuiz${cleanTitle}${fileIndex}-en"

                    // Try to load
                    val testData = readWordQuizDataFromAssets(application, filename, currentSkillLevel)

                    if (testData != null) {
                        // File exists, generate questions
                        val questionsInFile = generateQuestionsFromData(testData)

                        // Find matches
                        val matches = questionsInFile.filter { question ->
                            wordsToFind.contains(question.question) // OR question.question depending on your model mapping
                            // Note: In your previous code you used 'question.question'.
                            // Ensure this matches the WORD string exactly.
                        }

                        compiledQuestions.addAll(matches)

                        // Remove found words from the "To Find" list so we stop early if done
                        matches.forEach { wordsToFind.remove(it.title) /* or it.question */ }

                        // Move to next file (1 -> 2)
                        fileIndex++
                    } else {
                        // File returned null (e.g. File 4 doesn't exist), stop looking for this category
                        fileExists = false
                    }

                    // Safety break (unlikely to have > 10 files per category)
                    if (fileIndex > 10) fileExists = false
                }
            }

            // 5. Update UI
            if (compiledQuestions.isNotEmpty()) {
                _allQuestions = compiledQuestions.shuffled()
                applyFilters()

                quizStatistics.value = quizStatistics.value.copy(
                    title = "Smart Review (${compiledQuestions.size} words)",
                    skillLevel = "Mixed Review"
                )

                resetQuiz()
            } else {
//                _uiState99.update { it.copy(error = "Could not find questions for review items.") }
                Timber.w("No words due for review!")
            }
        }
    }
    // ✅ NEW: Switch between 1, 2, 3
    fun onSectionIndexSelected(index: Int) {
        if (_currentSectionIndex.value == index) return

        // If we have section-wide paginated data, use it directly
        if (_allSectionQuestions.isNotEmpty()) {
            _currentSectionIndex.value = index
            val pageIndex = index - 1 // Convert 1-based to 0-based
            _allQuestions = if (pageIndex in _paginatedQuestions.indices) {
                _paginatedQuestions[pageIndex]
            } else {
                emptyList()
            }
            _questions.value = _allQuestions
            currentQuestionIndex.value = 0
            userAnswers.value.clear()

            quizStatistics.value = quizStatistics.value.copy(
                title = "Quiz $index",
                skillLevel = "Section Practice"
            )
        } else {
            // Fallback to file-based loading (non-section mode)
            loadSpecificSectionIndex(index)
        }
    }

    private fun loadSpecificSectionIndex(index: Int) {
        viewModelScope.launch {
            _currentSectionIndex.value = index

            // Construct filename: "WordQuizTravel" + "2" + "-en"
            // (You should use your getLocalizedFileName helper here if you want translation support)
            val filename = "${currentSectionBaseName}${index}-en"

//            val level = userPreferencesRepository.selectedSkillLevelFlow.first()
            currentSkillLevel = userPreferencesRepository.selectedSkillLevelFlow.first()

            val testData = readWordQuizDataFromAssets(application, filename, currentSkillLevel)

            if (testData != null) {
                _allQuestions = generateQuestionsFromData(testData)
                applyFilters()

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
            if (ttsStatsRepository.isMarchOrApril2026()) {
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

            //val level = userPreferencesRepository.selectedSkillLevelFlow.first()
            currentSkillLevel = userPreferencesRepository.selectedSkillLevelFlow.first()


            val quizDetail = selectedQuiz.value ?: selectedLevel.value.quizzes.first()
            val baseName =
                (selectedQuiz.value ?: selectedLevel.value.quizzes.first()).baseName //+ ".json"

            val finalFilename = getLocalizedFileName(appContext, baseName)

            val testData = readWordQuizDataFromAssets(appContext, finalFilename,currentSkillLevel)

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
    fun onLevelSelectedObsolete(level: VocabQuizLevels) {
        selectedLevel.value = level
        // When the level changes, reset the selected quiz to the first one of the new level.
        selectedQuiz.value = level.quizzes.firstOrNull()
        loadQuestions()
    }

    fun onLevelSelected(level: VocabQuizLevels) {
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

        currentFileFormat.value = quizWordDefinition

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
        _activeFilters.value = emptySet()
        _paginatedQuestions = emptyList()

        //TODO: Do I need this?
        //_isDirty.value = false

    }

    // MARK: - Mastery Filter

    fun toggleFilter(level: WordMasteryLevel) {
        val current = _activeFilters.value.toMutableSet()
        if (current.contains(level)) {
            current.remove(level)
        } else {
            current.add(level)
        }
        _activeFilters.value = current
        if (_allSectionQuestions.isNotEmpty()) {
            applyFiltersAndPaginate()
        } else {
            applyFilters()
        }
    }

    fun selectAllFilters() {
        _activeFilters.value = emptySet()
        if (_allSectionQuestions.isNotEmpty()) {
            applyFiltersAndPaginate()
        } else {
            applyFilters()
        }
    }

    private fun applyFilters() {
        val filters = _activeFilters.value
        if (filters.isEmpty()) {
            _questions.value = _allQuestions
        } else {
            _questions.value = _allQuestions.filter { q ->
                val mastery = vocabQuizRepository.getWordStats(q.question).masteryLevel
                filters.contains(mastery)
            }
        }
        currentQuestionIndex.value = 0
    }

    /**
     * Filters all section questions by mastery level, then re-paginates into pages of [pageSize].
     * Updates availableSectionIndices so the UI shows the correct number of page buttons.
     */
    private fun applyFiltersAndPaginate() {
        val filters = _activeFilters.value
        val filtered = if (filters.isEmpty()) {
            _allSectionQuestions
        } else {
            _allSectionQuestions.filter { q ->
                val mastery = vocabQuizRepository.getWordStats(q.question).masteryLevel
                filters.contains(mastery)
            }
        }

        // Paginate into chunks of pageSize (10)
        _paginatedQuestions = filtered.chunked(pageSize)

        // Update available page indices (1-based)
        _availableSectionIndices.value = (1.._paginatedQuestions.size).toList()

        // Reset to first page
        _currentSectionIndex.value = if (_paginatedQuestions.isNotEmpty()) 1 else 0

        // Set current page questions
        _allQuestions = if (_paginatedQuestions.isNotEmpty()) _paginatedQuestions[0] else emptyList()
        _questions.value = _allQuestions

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
        val lastAttempt = quizHistoryManager.getLastAttempt(qs.value.skillLevel, qs.value.quizNumber)

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
                bannerManager.showBanner(
                    title = "Almost Perfect",
                    subtitle = "Not quite. Try again for a perfect score. Don't look at the Info first",
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
                        subtitle = "You finished it perfectly. You are fluent"
                    )
                }
            }

        }
    }

    fun updateAnswer(isCorrect: Boolean) {
        _isDirty.value = true
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
    fun readWordQuizDataFromAssets(context: Context, fileName: String, level:String): WordQuizRoot? {
        // 1. Sanitize input: Remove folder prefix if passed, handle extension
        val cleanName = File(fileName).name // Removes "Quizzes/" if passed accidentally
        val finalName = if (cleanName.endsWith(".json")) cleanName else "$cleanName.json"
        //val fullPathOld = "Quizzes/$level/$finalName"
        val fullPath = "${QUIZ_PATH}/$level/$finalName"  //e.g. Quizzes/SectionQuiz/B1/WordQuizPersonal1-en.json

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
    //TODO: I dont think I need this.
    private fun refreshQuizTitlesForLevel(level: VocabQuizLevels) {
        viewModelScope.launch {

            val skillLevel = userPreferencesRepository.selectedSkillLevelFlow.first()
            // 1. Get the list of default quizzes for this level
            val defaultQuizzes = level.quizzes

            // 2. Map them to potentially new titles by peeking at the JSON files
            val updatedQuizzes = defaultQuizzes.map { quizDetail ->

                // A. Resolve Filename (e.g. "...-hi.json")
                val filename = getLocalizedFileName(appContext, quizDetail.baseName)

                // B. Peek at the JSON to get the title
                // Note: This needs to be fast. If reading the whole file is too slow,
                // you might want to cache this or use a lighter "Metadata" read.
                val title = peekTitleFromJson(filename,skillLevel) ?: quizDetail.title

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
    private fun peekTitleFromJson(filename: String, level:String): String? {
        return try {
            // Reusing your existing reader logic, but maybe we can optimize later

            val data = readWordQuizDataFromAssets(appContext, filename,level)
            // Get the title from the root object if you added it there, or the first section
            data?.title // Assuming you added 'val title: String' to TestMyselfListRoot
        } catch (e: Exception) {
            null
        }
    }

    private fun preloadLocalizedTitles() {
        viewModelScope.launch(Dispatchers.IO) {

            // Loop through all Enum Levels (Elementary, Inter, etc.)
            VocabQuizLevels.entries.forEach { level ->

                // Map the default quizzes to their localized versions
                val localizedList = level.quizzes.map { quizDetail ->

                    // A. Resolve Filename (e.g. "...-hi.json")
                    val finalFileName = getLocalizedFileName(appContext, quizDetail.baseName)

                    // B. Peek at the JSON to get the title
                    // Note: We catch errors here so one bad file doesn't break the whole loop
                    val newTitle = try {
                        val data = readWordQuizDataFromAssets(appContext, finalFileName,"B1")
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

    private fun updateAvailableQuizzesFor(level: VocabQuizLevels) {
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
        return if (_questions.value.isNotEmpty() && _questions.value[currentQuestionIndex.value].summary.isNotEmpty()) {
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

//        val baseName = (selectedQuiz.value ?: selectedLevel.value.quizzes.first()).baseName
        val sectionName = currentSectionTitle//getLocalizedName(appContext, baseName) //no json

//        Timber.v(finalName)

        val statName = if (success) "${statVocabQuizOkCount}_${currentSkillLevel}$sectionName" else "${statVocabQuizNotOKCount}_${currentSkillLevel}$sectionName"

        //individual totals
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER, statName)
        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statName)

        if (success) { //Vocab totals
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER, statVocabQuizOkCount)
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statVocabQuizOkCount)
        }else{
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER, statVocabQuizNotOKCount)
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statVocabQuizNotOKCount)
        }

        //Vocab totals
        if (success) { //Only show success Usage totals -- 1 per question
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.USER, statVocabQuizTotalCount)
            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statVocabQuizTotalCount)
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
        // If the user navigated away with wrong attempts but never got it right,
        // record as FAILED so the word moves to Struggling
        if (_currentQuestionAttempts > 0 && _currentQuestionWord != null) {
            Timber.i("resetCurrentQuestionAttempts: recording FAILED for '${_currentQuestionWord}' (navigated away after $_currentQuestionAttempts wrong attempts)")
            vocabQuizRepository.recordResult(_currentQuestionWord!!, VocabQuizOutcome.FAILED, currentSectionTitle, currentSkillLevel)
        }
        _currentQuestionAttempts = 0
        _currentQuestionWord = null
        infoUsedForCurrentQuestion = false
    }

    fun onInfoClicked() {
//        _showInfoSheet.value = true
        infoUsedForCurrentQuestion = true
    }

    fun markAnswerSelected(word: String,isCorrect:Boolean) {
        _currentQuestionWord = word
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
//            val level = userPreferencesRepository.selectedSkillLevelFlow.first()
            vocabQuizRepository.recordResult(word, outcome,currentSectionTitle,currentSkillLevel)

            // 3. Award XP (Ideas)
            awardXP(outcome)

            // Reset for next question
            infoUsedForCurrentQuestion = false
            _currentQuestionAttempts = 0
            _currentQuestionWord = null
        } else {
            _currentQuestionAttempts++
        }
        Timber.i("markAnswerSelected()")
        vocabQuizRepository.debugPrintAllWordStates()
    }

    fun getSectionTitle():String {
        return currentSectionTitle
    }
    private fun awardXP(outcome: VocabQuizOutcome) {
        when (outcome) {
            VocabQuizOutcome.FLAWLESS -> xpManager.registerAction(XpActionType.PerfectWord, count = 5) // High Reward
            VocabQuizOutcome.ASSISTED -> xpManager.registerAction(XpActionType.MasterWord, count = 2) // Small Reward
            VocabQuizOutcome.STUMBLED -> xpManager.registerAction(XpActionType.MasterWord, count = 1) // Token Reward
            VocabQuizOutcome.FAILED -> { /* No XP, try again later */ }
        }
    }

    fun fluencyStats(word:String): Pair<String, Color> {

        val stats = vocabQuizRepository.getWordStats(word)
        Timber.v(stats.toString())

        return  vocabQuizRepository.getFluencyDisplay(stats.masteryLevel)

    }

    fun getWordStats(word: String): VocabLearningState {
        return vocabQuizRepository.getWordStats(word)
    }

//    fun getFormattedNextReviewTime(word: String): String {
//        val stats = vocabQuizRepository.getWordStats(word)
//
//        return if (stats.nextReviewTime > 0) {
//            vocabQuizRepository.getFormattedNextReviewTime(stats.word)
//        }else{
//            ""
//        }
//    }
}
