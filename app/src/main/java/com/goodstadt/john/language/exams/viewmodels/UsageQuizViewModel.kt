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
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.UserStatsRepository
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statQuizNotOKCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statQuizOkCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statQuizTotalCount
import com.goodstadt.john.language.exams.data.repository.UsageQuizRepository
import com.goodstadt.john.language.exams.managers.BannerManager
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.managers.XpActionType
import com.goodstadt.john.language.exams.models.AudioPlaybackStatus
import com.goodstadt.john.language.exams.models.TestMyselfListRoot
import com.goodstadt.john.language.exams.screens.reference.shared.QuizDetail
import com.goodstadt.john.language.exams.storage.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException
import java.util.Date
import javax.inject.Inject
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statUsageQuizNotOKCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statUsageQuizOkCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statUsageQuizTotalCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statVocabQuizTotalCount
import java.util.Locale

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
    var tries: Int = 0,
    var page: Int = 1, //held for Dashboard
    var filename:String = "" //held for Dashboard

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
enum class QuizLevels(val quizzes: List<QuizDetail>) {
    ELEMENTARY(
        quizzes = listOf(
            QuizDetail(
                id = 1,
                baseName = "UsageQuiz1A1-en",
                title = "Sentence Structure"
            ),
            QuizDetail(
                id = 2,
                baseName = "UsageQuiz2A1-en",
                title = "Present Simple"
            ),
            QuizDetail(
                id = 3,
                baseName = "UsageQuiz3A1-en",
                title = "Past Simple"
            ),
            QuizDetail(
                id = 4,
                baseName = "UsageQuiz4A1-en",
                title = "Questions & Short Answers"
            ),
            QuizDetail(
                id = 5,
                baseName = "UsageQuiz5A1-en",
                title = "Prepositions"
            ),
            QuizDetail(
                id = 6,
                baseName = "UsageQuiz6A1-en",
                title = "Connectors"
            )
        )
    ),
    INTER(
        quizzes = listOf(
            QuizDetail(
                id = 1,
                baseName = "UsageQuiz1A2-en",
                title = "Future Forms"
            ),
            QuizDetail(
                id = 2,
                baseName = "UsageQuiz2A2-en",
                title = "Present Continuous"
            ),
            QuizDetail(3, "UsageQuiz3A2-en", "Comparatives & Superlatives"),
            QuizDetail(4, "UsageQuiz4A2-en", "Modal Verbs"),
            QuizDetail(5, "UsageQuiz5A2-en", "Verb Patterns"),
            QuizDetail(6, "UsageQuiz6A2-en", "Linking Words & If Clauses"),
        )
    ),
    UPPER(
        quizzes = listOf(
            QuizDetail(id = 1, baseName = "UsageQuiz1B1-en", title = "Tense Mastery"),
            QuizDetail(
                id = 2,
                baseName = "UsageQuiz2B1-en",
                title = "Real & Hypothetical Situations"
            ),
            QuizDetail(
                id = 3,
                baseName = "UsageQuiz3B1-en",
                title = "Formal & Official Language"
            ),
            QuizDetail(
                id = 4,
                baseName = "UsageQuiz4B1-en",
                title = "Reporting & Communication",
            ),
            QuizDetail(
                id = 5,
                baseName = "UsageQuiz5B1-en",
                title = "Structured Arguments"
            ),
            QuizDetail(6, "UsageQuiz6B1-en", "Functional Fluency")
        )
    ),
    ADVANCED(
        quizzes = listOf(
            QuizDetail(id = 1, baseName = "UsageQuiz1B2-en", title = "Aspect & Time Control"),
            QuizDetail(
                id = 2,
                baseName = "UsageQuiz2B2-en",
                title = "Hypothetical Reasoning"
            ),
            QuizDetail(3, "UsageQuiz3B2-en", "Formal Structural Control"),
            QuizDetail(4, "UsageQuiz4B2-en", "Academic Expression"),
            QuizDetail(5, "UsageQuiz5B2-en", "Argument Development"),
            QuizDetail(6, "UsageQuiz6B2-en", "Precision & Nuance")
        )
    );


    val description: String
        get() = when(this) {
            ELEMENTARY -> "Beginner"
            INTER -> "Elementary" // Explicitly string match if needed
            UPPER -> "Inter"
            ADVANCED -> "Advanced"
        }
    val ESOL: String
        get() = when(this) {
            ELEMENTARY -> "A1"
            INTER -> "A2"
            UPPER -> "B1"
            ADVANCED -> "B2"
        }
}

//data class WordOK(
//    val word: String,
//    val ok: Boolean
//)

data class QuizQuestion(
    val sentence: String,
    val words: List<String>,
    val correctOption: String,
    val summary: String,
    val explain: String,
    val title: String,
    val page:Int
)

//TODO: Do I need this?
sealed interface QuizUiState {
    object Loading : QuizUiState

    data class Success(
        val selectedVoiceName: String = "" // Add a default empty value
    ) : QuizUiState

    data class Error(val message: String) : QuizUiState
    object NotAvailable : QuizUiState // For flavors like 'zh'
}
data class UsageQuizUiState(
    val testMyselfListRoot:TestMyselfListRoot? = null
)
@HiltViewModel
class UsageQuizViewModel @Inject constructor(
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
        QuizStatistics(skillLevel = QuizLevels.UPPER.description, quizNumber = 1, title = "Quiz 1")
    )
    val selectedLevel = mutableStateOf(QuizLevels.UPPER)

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
    private val _availableQuizzes = MutableStateFlow<List<QuizDetail>>(QuizLevels.UPPER.quizzes)
    val availableQuizzes = _availableQuizzes.asStateFlow()

    // 1. The Cache: Maps a Level (e.g. ELEMENTARY) to its list of localized QuizDetails
    private val quizTitleCache = mutableMapOf<QuizLevels, List<QuizDetail>>()
    private var infoUsedForCurrentQuestion = false // ✅ Track hint usage for the CURRENT question

//    private val _quizFluency = mutableStateOf(UsageQuizRepository.QuizFluency.NEVER_DONE)
//    val quizFluency: State<UsageQuizRepository.QuizFluency> = _quizFluency

    private val _fluency = mutableStateOf(UsageQuizRepository.QuizFluency.NEVER_DONE)
    // Explicitly define the type to avoid ambiguity with other 'State' classes
    val fluency: State<UsageQuizRepository.QuizFluency> = _fluency


    init {
        // 1. Start background loading
      //  preloadLocalizedTitles()

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
            if (ttsStatsRepository.isFebOrMarch2026()) {
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.GlobalStats)
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
            }

        }
    }

    fun loadQuestions() {
        viewModelScope.launch {

            val quizDetail = selectedQuiz.value ?: selectedLevel.value.quizzes.first()
            val baseName = quizDetail.baseName//(selectedQuiz.value ?: selectedLevel.value.quizzes.first()).baseName //+ ".json"
            val regionCode = Locale.getDefault().country
            val localizedBaseName = resolveLocalizedBaseName(baseName, regionCode)
            val finalFilename = "$localizedBaseName.json"

            Timber.v("region $regionCode filename $finalFilename")
            //val finalFilename = "$baseName.json" //getLocalizedFileName(appContext, baseName)

            val testData = readTestMyselfDataFromAssets(appContext, finalFilename)

            if (testData == null) {
                Timber.wtf("Failed to parse JSON file: $finalFilename")
                return@launch
            }

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


            _questions.value = generateQuestionsFromData(testData)

            Timber.v("${_questions.value.count()}")



            resetQuiz()

        }
    }

    // A new function for the UI to call when a different level is picked.
    fun onLevelSelected(level: QuizLevels) {
        if (level == selectedLevel.value){
            return // already selected
        }
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
                    subtitle = "Not quite. Try again for a perfect score. Don't look at the Info first",
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

    fun updateAnswer(isCorrect: Boolean) {
        userAnswers.value[currentQuestionIndex.value] = isCorrect

        val currentTries = quizStatistics.value.tries + 1
        quizStatistics.value = quizStatistics.value.copy(
            answered = userAnswers.value.size,
            correct = userAnswers.value.count { it.value },
            tries = currentTries
        )


        if (quizStatistics.value.state == QuizState.NOT_STARTED) {
            quizStatistics.value = quizStatistics.value.copy(state = QuizState.IN_PROGRESS)
        }


        val currentQuestion = currentQuestionIndex.value + 1 // one based
        if (currentQuestion >= _questions.value.count()) { //completed
            quizStatistics.value = quizStatistics.value.copy(state = QuizState.COMPLETED, title = quizStatistics.value.title)

            onQuizFinished()
            val fieldValue =  "${quizStatistics.value.quizNumber}:${quizStatistics.value.answered}:${quizStatistics.value.correct}:${quizStatistics.value.tries}"

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


            val jsonString = context.assets.open("Quizzes/UsageQuiz/$fileName")
                .bufferedReader()
                .use { it.readText() }

            return jsonParser.decodeFromString<TestMyselfListRoot>(jsonString)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Call this whenever the Level changes (e.g. from Elementary to Inter)
    private fun refreshQuizTitlesForLevel(level: QuizLevels) {
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


    private fun updateAvailableQuizzesFor(level: QuizLevels) {
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
            if (ttsStatsRepository.isFebOrMarch2026()) {
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
    private fun resolveLocalizedBaseName(baseName: String, regionCode: String?): String {
        // If no region or it's already English, stick to base
        if (regionCode.isNullOrBlank() || regionCode.lowercase() == "gb" || regionCode.lowercase() == "us") {
            return baseName
        }

        // Only attempt swap if the filename follows the "-en" pattern
        if (baseName.endsWith("-en")) {
            val countrySuffix = regionCode.lowercase() // "vn", "in", etc.
            val candidateName = baseName.replace("-en", "-$countrySuffix")

            // Check if the file "candidateName.json" actually exists in Assets
            return if (assetExists("Quizzes/UsageQuiz/$candidateName.json")) {
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
}
