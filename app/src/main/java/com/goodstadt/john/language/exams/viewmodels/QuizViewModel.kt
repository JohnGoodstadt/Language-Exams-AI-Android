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
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.ConnectivityRepository
import com.goodstadt.john.language.exams.data.QuizHistoryManager
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.data.repository.PlaybackResult
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.UserStatsRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statQuizNotOKCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statQuizOkCount
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.managers.XpActionType
import com.goodstadt.john.language.exams.models.TestMyselfListRoot
import com.goodstadt.john.language.exams.storage.UiEvent
import com.goodstadt.john.language.exams.utils.calcIsTodayFreePassDay
import com.goodstadt.john.language.exams.utils.calcIsTodayNotAFreePassDay
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.goodstadt.john.language.exams.viewmodels.QuizViewModel.QuizDetail
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
import java.io.IOException
import java.util.Date
import javax.inject.Inject

enum class QuizState(val description: String) {
    NOT_STARTED("Not Started"),
    STARTED("Started"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed")
}

data class QuizStatistics(
    val timestamp: Date = Date(),
    var state: QuizState = QuizState.NOT_STARTED,
    val skillLevel: String,
    val quizNumber: Int,
    val title:String,
    var answered: Int = 0,
    var correct: Int = 0,
    var tries: Int = 0

) {
    override fun toString(): String {
        val dateFormatter =
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return "${dateFormatter.format(timestamp)} '${state.description}' '$skillLevel' level:$quizNumber answered:$answered tries:$tries correct:$correct"
    }

    val readyForDB: String
        get() {
            val dateFormatter =
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            return "${dateFormatter.format(timestamp)}:${state.description}:$skillLevel:$quizNumber:$answered:$tries:$correct"
        }

    fun update(answered: Int, correct: Int, tries: Int) =
        copy(answered = answered, correct = correct, tries = tries)
}

enum class QuizLevelsNew(val quizzes: List<QuizDetail>) {
    ELEMENTARY(
        quizzes = listOf(
            QuizDetail(
                id = 1,
                baseName = "Quiz1Elementary",
                title = "Quiz 1 - Simple Tenses"
            ),
            QuizDetail(
                id = 2,
                baseName = "Quiz2Elementary",
                title = "Quiz 2 - Word Pairs"
            ),
            QuizDetail(
                id = 3,
                baseName = "Quiz3Elementary",
                title = "Quiz 3 - Word Order"
            ),
            QuizDetail(
                id = 4,
                baseName = "Quiz4Elementary",
                title = "Quiz 4 - Spelling 1"
            ),
            QuizDetail(
                id = 5,
                baseName = "Quiz5Elementary",
                title = "Quiz 5 - Spelling 2"
            ),
            QuizDetail(
                id = 6,
                baseName = "Quiz6Elementary",
                title = "Quiz 6 - A vs An"
            )
        )
    ),
    INTER(
        quizzes = listOf(
            QuizDetail(
                id = 1,
                baseName = "Quiz1Inter",
                title = "Quiz 1 - Simple Tenses"
            ),
            QuizDetail(
                id = 2,
                baseName = "Quiz2Inter",
                title = "Quiz 2 - Word Pairs"
            ),
            QuizDetail(2, "Quiz2Inter", "Quiz 2 - Word Pairs"),
            QuizDetail(3, "Quiz3Inter", "Quiz 3 - Word Order"),
            QuizDetail(4, "Quiz4Inter", "Quiz 4 - Spelling 1"),
            QuizDetail(5, "Quiz5Inter", "Quiz 5 - Spelling 2"),
//					QuizDetail(id: 6, sheetName: "Quiz6Inter", title: "Quiz 6 - Superlatives"),
        )
    ),
    UPPER(
        quizzes = listOf(
            QuizDetail(id = 1, baseName = "Quiz1Upper", title = "Quiz 1 - Tenses"),
            QuizDetail(
                id = 2,
                baseName = "Quiz2Upper",
                title = "Quiz 2 - Word Pairs"
            ),
            QuizDetail(
                id = 3,
                baseName = "Quiz3Upper",
                title = "Quiz 3 - Word Order"
            ),
            QuizDetail(
                id = 4,
                baseName = "Quiz4Upper",
                title = "Quiz 4 - Spelling 1"
            ),
            QuizDetail(
                id = 5,
                baseName = "Quiz5Upper",
                title = "Quiz 5 - Spelling 2"
            ),
            QuizDetail(6, "Quiz6Upper", "Quiz 6 - Pronounce 'the'"),
        )
    ),
    ADVANCED(
        quizzes = listOf(
            QuizDetail(id = 1, baseName = "Quiz1Advanced", title = "Quiz 1 - Tenses"),
            QuizDetail(
                id = 2,
                baseName = "Quiz2Advanced",
                title = "Quiz 2 - Word Pairs"
            ),
            QuizDetail(3, "Quiz3Advanced", "Quiz 3 - Word Order"),
            QuizDetail(4, "Quiz4Advanced", "Quiz 4 - Spelling 1"),
            QuizDetail(5, "Quiz5Advanced", "Quiz 5 - Spelling 2"),
            QuizDetail(6, "Quiz6Advanced", "Quiz 6 - Adv. Words"),
        )
    );

    // This description property remains the same and is correct.
//    val description: String
//        get() = name.lowercase().replaceFirstChar { it.uppercase() }
    val description: String
        get() = when(this) {
            ELEMENTARY -> "Elementary"
            INTER -> "Inter" // Explicitly string match if needed
            UPPER -> "Upper"
            ADVANCED -> "Advanced"
        }
}

data class WordOK(
    val word: String,
    val ok: Boolean
)

data class QuizQuestion(
    val sentence: String,
    val words: List<String>,
    val correctOption: String,
    val summary: String,
    val explain: String,
    val title: String,
)

sealed interface QuizUiState {
    object Loading : QuizUiState
    data class Success(
        //val categories: List<Category>,
        val selectedVoiceName: String = "" // Add a default empty value
    ) : QuizUiState

    data class Error(val message: String) : QuizUiState
    object NotAvailable : QuizUiState // For flavors like 'zh'
}

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val application: Application,
    private val vocabRepository: ContentRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val userStatsRepository: UserStatsRepository,
    private val ttsStatsRepository: TTSStatsRepository,
    private val billingRepository: BillingRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val connectivityRepository: ConnectivityRepository,
    private val quizHistoryManager: QuizHistoryManager,
    private val xpManager: XPManager

) : ViewModel() {
    private val appContext: Context = application.applicationContext

    private val _uiState99 = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val uiState99 = _uiState99.asStateFlow()
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    // region State FLow
    private val _questions = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val questions: StateFlow<List<QuizQuestion>> get() = _questions


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
        QuizStatistics(skillLevel = QuizLevelsNew.UPPER.description, quizNumber = 1, title = "Quiz 1")
    )
    val selectedLevel = mutableStateOf(QuizLevelsNew.UPPER)

    val selectedQuiz = mutableStateOf<QuizDetail?>(null)

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
    private val _availableQuizzes = MutableStateFlow<List<QuizDetail>>(QuizLevelsNew.UPPER.quizzes)
    val availableQuizzes = _availableQuizzes.asStateFlow()

    // 1. The Cache: Maps a Level (e.g. ELEMENTARY) to its list of localized QuizDetails
    private val quizTitleCache = mutableMapOf<QuizLevelsNew, List<QuizDetail>>()


    /**
     * A simple data class to hold the metadata for a single quiz.
     *
     * @param id A unique identifier for the quiz within its level (e.g., 1, 2, 3...).
     * @param baseName The name of the JSON asset file for this quiz.
     * @param title The human-readable display name for this quiz (e.g., "Quiz 1 - Simple Tenses").
     */
    data class QuizDetail(
        val id: Int,
        val baseName: String, //e.g. "Quiz1Elementary-en"
        val title: String
    )


    init {
        // 1. Start background loading
        preloadLocalizedTitles()

        selectedQuiz.value = selectedLevel.value.quizzes.firstOrNull()
        loadQuestions()
        viewModelScope.launch {
            billingRepository.isPurchased.collect { purchasedStatus ->
                _isPremiumUser.value = purchasedStatus
                if (DEBUG) {
                    billingRepository.logCurrentStatus()
                }
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

    fun playTrack(sentence: String) {

        if (_playbackState.value is PlaybackState.Playing) return

        if (!connectivityRepository.isCurrentlyOnline()) {
            _playbackState.value = PlaybackState.Idle
            return
        }

        viewModelScope.launch {
            val todayIsNotAFreePassDay = calcIsTodayNotAFreePassDay(userPreferencesRepository)
            if (!isPremiumUser.value && todayIsNotAFreePassDay) { //if premium user don't check credits or is on day 1
                if (rateLimiter.doIForbidCall()) {
                    val failType = rateLimiter.canMakeCallWithResult()
                    Timber.v("${failType.canICallAPI}")
                    Timber.v("${failType.failReason}")
                    Timber.v("${failType.timeLeftToWait}")
                    if (!failType.canICallAPI) {
                        if (failType.failReason == SimpleRateLimiter.FailReason.DAILY) {
                            _showRateDailyLimitSheet.value = true
                        } else {
                            _showRateHourlyLimitSheet.value = true
                        }
                    } else {
                        _showRateLimitSheet.value = true
                    }

                    return@launch
                }
            }



            val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
            val uniqueSentenceId = generateUniqueSentenceId(sentence, currentVoiceName)

            //  _playbackState.value = PlaybackState.Playing(uniqueSentenceId)

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
                    if (todayIsNotAFreePassDay){
                        rateLimiter.recordCall()
                    }
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
            val baseName = (selectedQuiz.value ?: selectedLevel.value.quizzes.first()).baseName //+ ".json"

            val finalFilename = getLocalizedFileName(appContext, baseName)

            val testData = readTestMyselfDataFromAssets(appContext, finalFilename)

            if (testData == null) {
                Timber.wtf("Failed to parse JSON file: $finalFilename")
                return@launch
            }



           // val loadedTitle = testData.data.firstOrNull()?.title ?: quizDetail.title

            if (testData.title?.isNotEmpty() == true){
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
    fun onLevelSelectedObsolete(level: QuizLevelsNew) {
        selectedLevel.value = level
        // When the level changes, reset the selected quiz to the first one of the new level.
        selectedQuiz.value = level.quizzes.firstOrNull()
        loadQuestions()
    }
    fun onLevelSelected(level: QuizLevelsNew) {
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
    private fun generateQuestionsFromJson(context: Context, fileName: String): List<QuizQuestion> {
        val testData = readTestMyselfDataFromAssets(context, fileName)

        if (testData == null) {
            Timber.wtf("Failed to parse JSON file: $fileName")
            return emptyList()
        }

        if (testData.title?.isNotEmpty() == true){
            Timber.i("Sheet title is ${testData.title}")
        }

        if (testData.fileFormat == quizQandA) {
            currentFileFormat.value = quizQandA
        }else if (testData.fileFormat == quizDefinitions) {
            currentFileFormat.value = quizDefinitions
        }else if (testData.fileFormat == quizMultipleChoice) {
            currentFileFormat.value = quizMultipleChoice
        } else {
            currentFileFormat.value = quizFillInTheBlanks
        }

        val a  = when (testData.fileFormat) {
            quizQandA -> quizQandA
            quizDefinitions -> quizDefinitions
            quizMultipleChoice -> quizMultipleChoice
            else -> quizFillInTheBlanks

        }

        //because spellings should follow each other
        if (testData.fileFormat == quizFillInTheBlanks) testData.shuffleLists()


        return testData.data.flatMap { section ->
            section.sections.map { quizSection ->
                val shuffledWords = quizSection.words.shuffled()
                val words = shuffledWords.map { it.word }
                val correctOption = quizSection.words.firstOrNull { it.ok }?.word ?: ""
                val summary = quizSection.summary
                val explain = quizSection.explain
                val title = quizSection.title
                QuizQuestion(quizSection.sentence, words, correctOption, summary,explain,title)
            }
        }
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

        val a  = when (testData.fileFormat) {
            quizQandA -> quizQandA
            quizDefinitions -> quizDefinitions
            quizMultipleChoice -> quizMultipleChoice
            else -> quizFillInTheBlanks

        }

        //because spellings should follow each other
        if (testData.fileFormat == quizFillInTheBlanks) testData.shuffleLists()


        return testData.data.flatMap { section ->
            section.sections.map { quizSection ->
                val shuffledWords = quizSection.words.shuffled()
                val words = shuffledWords.map { it.word }
                val correctOption = quizSection.words.firstOrNull { it.ok }?.word ?: ""
                val summary = quizSection.summary
                val explain = quizSection.explain
                val title = quizSection.title
                QuizQuestion(quizSection.sentence, words, correctOption, summary,explain,title)
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

            quizHistoryManager.saveAttempt(qs.value.skillLevel, qs.value.quizNumber, qs.value.title, qs.value.correct, qs.value.tries)

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
            quizStatistics.value = quizStatistics.value.copy(state = QuizState.COMPLETED, title = quizStatistics.value.title)

            onQuizFinished()
            val fieldValue =
                "${quizStatistics.value.quizNumber}:${quizStatistics.value.answered}:${quizStatistics.value.correct}:${quizStatistics.value.tries}"
        }

    }

    fun readTestMyselfDataFromAssets(context: Context, fileName: String): TestMyselfListRoot? {
        return try {
           // Timber.v("reading json: $fileName")

            val jsonString = context.assets.open("Quizzes/$fileName")
                .bufferedReader()
                .use { it.readText() }

            return jsonParser.decodeFromString<TestMyselfListRoot>(jsonString)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Call this whenever the Level changes (e.g. from Elementary to Inter)
    private fun refreshQuizTitlesForLevel(level: QuizLevelsNew) {
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
            val data = readTestMyselfDataFromAssets(appContext, filename)
            // Get the title from the root object if you added it there, or the first section
            data?.title // Assuming you added 'val title: String' to TestMyselfListRoot
        } catch (e: Exception) {
            null
        }
    }

    private fun preloadLocalizedTitles() {
        viewModelScope.launch(Dispatchers.IO) {

            // Loop through all Enum Levels (Elementary, Inter, etc.)
            QuizLevelsNew.entries.forEach { level ->

                // Map the default quizzes to their localized versions
                val localizedList = level.quizzes.map { quizDetail ->

                    // A. Resolve Filename (e.g. "...-hi.json")
                    val finalFileName = getLocalizedFileName(appContext, quizDetail.baseName)

                    // B. Peek at the JSON to get the title
                    // Note: We catch errors here so one bad file doesn't break the whole loop
                    val newTitle = try {
                        val data = readTestMyselfDataFromAssets(appContext, finalFileName)
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
    private fun updateAvailableQuizzesFor(level: QuizLevelsNew) {
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
        val localizedName = "$baseName-$currentCode.json"
        val defaultName = "$baseName-en.json"

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
        val finalName = getLocalizedName(appContext, baseName) //no json

        Timber.v(finalName)

        val statName = if (success ) "${statQuizOkCount}_$finalName" else "${statQuizNotOKCount}_$finalName"

        ttsStatsRepository.inc(  TTSStatsRepository.fsDOC.USER,   statName  )
        ttsStatsRepository.inc(  TTSStatsRepository.fsDOC.GlobalStats,   statName  )


        viewModelScope.launch {
            if (calcIsTodayFreePassDay(userPreferencesRepository)){
                //Let's see usage for Quiz on Day 1 - immediately
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.GlobalStats)
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
            }
        }

    }


}
