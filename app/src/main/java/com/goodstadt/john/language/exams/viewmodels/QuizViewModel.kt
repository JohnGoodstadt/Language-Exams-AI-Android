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
import com.goodstadt.john.language.exams.data.BillingRepository
import com.goodstadt.john.language.exams.data.ConnectivityRepository
import com.goodstadt.john.language.exams.data.ContentRepository
import com.goodstadt.john.language.exams.data.PlaybackResult
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.UserStatsRepository
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.models.TestMyselfListRoot
import com.goodstadt.john.language.exams.storage.UiEvent
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.goodstadt.john.language.exams.viewmodels.QuizViewModel.QuizDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber
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

/*
in this viewmodel can you change generateSampleQuestions() to read questions from a supplied JSON file called "TestMyselfQuiz1Elementary-en.json".
 */
enum class QuizLevelsold(
    val sheetNameQ1: String,
    val sheetNameQ2: String,
    val sheetNameQ3: String,
    val sheetNameQ4: String,
    val sheetNameQ5: String
) {
    ELEMENTARY(
        "TestMyselfQuiz1Elementary-en",
        "TestMyselfQuiz2Elementary-en",
        "TestMyselfQuiz3Elementary-en",
        "TestMyselfQuiz4Elementary-en",
        "TestMyselfQuiz5Elementary-en"
    ),
    INTER(
        "TestMyselfQuiz1Inter-en",
        "TestMyselfQuiz2Inter-en",
        "TestMyselfQuiz3Inter-en",
        "TestMyselfQuiz4Inter-en",
        "TestMyselfQuiz5Inter-en"
    ),
    UPPER(
        "TestMyselfQuiz1Upper-en",
        "TestMyselfQuiz2Upper-en",
        "TestMyselfQuiz3Upper-en",
        "TestMyselfQuiz4Upper-en",
        "TestMyselfQuiz5Upper-en"
    ),
    ADVANCED(
        "TestMyselfQuiz1Advanced-en",
        "TestMyselfQuiz2Advanced-en",
        "TestMyselfQuiz3Advanced-en",
        "TestMyselfQuiz4Advanced-en",
        "TestMyselfQuiz5Advanced-en"
    );

    val description: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

enum class QuizLevelsNew(val quizzes: List<QuizDetail>) {
    ELEMENTARY(
        quizzes = listOf(
            QuizDetail(
                id = 1,
                sheetName = "TestMyselfQuiz1Elementary-en",
                title = "Quiz 1 - Simple Tenses"
            ),
            QuizDetail(
                id = 2,
                sheetName = "TestMyselfQuiz2Elementary-en",
                title = "Quiz 2 - Word Pairs"
            ),
            QuizDetail(
                id = 3,
                sheetName = "TestMyselfQuiz3Elementary-en",
                title = "Quiz 3 - Word Order"
            ),
            QuizDetail(
                id = 4,
                sheetName = "TestMyselfQuiz4Elementary-en",
                title = "Quiz 4 - Spelling 1"
            ),
            QuizDetail(
                id = 5,
                sheetName = "TestMyselfQuiz5Elementary-en",
                title = "Quiz 5 - Spelling 2"
            ),
            QuizDetail(
                id = 7,
                sheetName = "TestMyselfQuiz7Elementary-en",
                title = "Quiz 6 - A vs An"
            )
        )
    ),
    INTER(
        quizzes = listOf(
            QuizDetail(
                id = 1,
                sheetName = "TestMyselfQuiz1Inter-en",
                title = "Quiz 1 - Simple Tenses"
            ),
            QuizDetail(
                id = 2,
                sheetName = "TestMyselfQuiz2Inter-en",
                title = "Quiz 2 - Word Pairs"
            ),
            QuizDetail(2, "TestMyselfQuiz2Inter-en", "Quiz 2 - Word Pairs"),
            QuizDetail(3, "TestMyselfQuiz3Inter-en", "Quiz 3 - Word Order"),
            QuizDetail(4, "TestMyselfQuiz4Inter-en", "Quiz 4 - Spelling 1"),
            QuizDetail(5, "TestMyselfQuiz5Inter-en", "Quiz 5 - Spelling 2"),
//					QuizDetail(id: 6, sheetName: "TestMyselfQuiz6Inter-en", title: "Quiz 6 - Superlatives"),
        )
    ),
    UPPER(
        quizzes = listOf(
            QuizDetail(id = 1, sheetName = "TestMyselfQuiz1Upper-en", title = "Quiz 1 - Tenses"),
            QuizDetail(
                id = 2,
                sheetName = "TestMyselfQuiz2Upper-en",
                title = "Quiz 2 - Word Pairs"
            ),
            QuizDetail(
                id = 3,
                sheetName = "TestMyselfQuiz3Upper-en",
                title = "Quiz 3 - Word Order"
            ),
            QuizDetail(
                id = 4,
                sheetName = "TestMyselfQuiz4Upper-en",
                title = "Quiz 4 - Spelling 1"
            ),
            QuizDetail(
                id = 5,
                sheetName = "TestMyselfQuiz5Upper-en",
                title = "Quiz 5 - Spelling 2"
            ),
            QuizDetail(6, "TestMyselfQuiz6Upper-en", "Quiz 6 - Pronounce 'the'"),
        )
    ),
    ADVANCED(
        quizzes = listOf(
            QuizDetail(id = 1, sheetName = "TestMyselfQuiz1Advanced-en", title = "Quiz 1 - Tenses"),
            QuizDetail(
                id = 2,
                sheetName = "TestMyselfQuiz2Advanced-en",
                title = "Quiz 2 - Word Pairs"
            ),
            QuizDetail(3, "TestMyselfQuiz3Advanced-en", "Quiz 3 - Word Order"),
            QuizDetail(4, "TestMyselfQuiz4Advanced-en", "Quiz 4 - Spelling 1"),
            QuizDetail(5, "TestMyselfQuiz5Advanced-en", "Quiz 5 - Spelling 2"),
            QuizDetail(6, "TestMyselfQuiz6Advanced-en", "Quiz 6 - Adv. Words"),
        )
    );

    // This description property remains the same and is correct.
    val description: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
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

    //NOTE: rate Limiting
//    private val rateLimiter = RateLimiterManager.getInstance()
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
        QuizStatistics(skillLevel = QuizLevelsNew.ELEMENTARY.description, quizNumber = 1)
    )
    val selectedLevel = mutableStateOf(QuizLevelsNew.ELEMENTARY)
    val availableQuizzes = derivedStateOf { selectedLevel.value.quizzes }
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


    /**
     * A simple data class to hold the metadata for a single quiz.
     *
     * @param id A unique identifier for the quiz within its level (e.g., 1, 2, 3...).
     * @param sheetName The name of the JSON asset file for this quiz.
     * @param title The human-readable display name for this quiz (e.g., "Quiz 1 - Simple Tenses").
     */
    data class QuizDetail(
        val id: Int,
        val sheetName: String,
        val title: String
    )


    init {
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

    fun showAppUpgradeSheet() {
        _showUpgradeAppSheet.value = true
    }

    fun showForceAppUpgradeSheet() {
        _showForceUpgradeAppSheet.value = true
    }

    fun hideAppUpgradeSheet() {
        _showUpgradeAppSheet.value = false
    }

    fun hideForceAppUpgradeSheet() {
        _showForceUpgradeAppSheet.value = false
    }

    /**
     * Called when the user taps the "play" icon on a SoundData item.
     * NOTE: Assumes mp3 exists on the local disk
     * 1) Mark that sound as "isPlayed = true" in the UI state.
     * 2) Actually play the MP3.
     */
//    fun isPlaying() {
//        _playbackState.value = true
//    }
//    fun isNotPlaying() {
//        _playbackState.value = true
//    }


    fun playTrack(sentence: String) {

        if (_playbackState.value is PlaybackState.Playing) return

        if (!connectivityRepository.isCurrentlyOnline()) {
            _playbackState.value = PlaybackState.Idle
            return
        }

        if (!isPremiumUser.value) { //if premium user don't check credits
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

                return
            }
        }

        viewModelScope.launch {

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
                    rateLimiter.recordCall()
                    Timber.v(rateLimiter.printCurrentStatus)
                    ttsStatsRepository.updateTTSStatsWithCosts(sentence, currentVoiceName)
                }

                is PlaybackResult.PlayedFromCache -> {
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

//            val fileName = when (selectedQuizNumber.value) {
//                1 -> selectedLevel.value.sheetNameQ1
//                2 -> selectedLevel.value.sheetNameQ2
//                3 -> selectedLevel.value.sheetNameQ3
//                4 -> selectedLevel.value.sheetNameQ4
//                5 -> selectedLevel.value.sheetNameQ5
//                else -> selectedLevel.value.sheetNameQ1
//            } + ".json" // Append the JSON file extension

            val fileName99 = when (selectedQuizNumber.value) {
                1 -> selectedLevel.value
                2 -> selectedLevel.value
                3 -> selectedLevel.value
                4 -> selectedLevel.value
                5 -> selectedLevel.value
                else -> selectedLevel.value
            }// + ".json" // Append the JSON file extension

            val fn = selectedQuizNumber.value //?: selectedLevel.value.quizzes.first()) + ".json"
            // Fall back to the first quiz in the level if none is selected.
            val jsonName =
                (selectedQuiz.value ?: selectedLevel.value.quizzes.first()).sheetName + ".json"

            //  val jsonName = "${fn}.json"
            _questions.value = generateQuestionsFromJson(appContext, jsonName)
            Timber.v("${_questions.value.count()}")

            resetQuiz()

        }
    }

    // A new function for the UI to call when a different level is picked.
    fun onLevelSelected(level: QuizLevelsNew) {
        selectedLevel.value = level
        // When the level changes, reset the selected quiz to the first one of the new level.
        selectedQuiz.value = level.quizzes.firstOrNull()
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

    fun resetQuiz() {

        saveQuizState()

        quizStatistics.value = quizStatistics.value.copy(
            state = QuizState.NOT_STARTED,
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
            quizStatistics.value = quizStatistics.value.copy(state = QuizState.COMPLETED)
            val fieldValue =
                "${quizStatistics.value.quizNumber}:${quizStatistics.value.answered}:${quizStatistics.value.correct}:${quizStatistics.value.tries}"
            // val fieldKEY = "${StatsManager.QUIZ_COMPLETE}${selectedLevel.value}" //combine both quiz number and level
            //statsManager.update(StatsManager.fsDOC.USER, fieldKEY, fieldValue)

        }

    }

    fun readTestMyselfDataFromAssets(context: Context, fileName: String): TestMyselfListRoot? {
        return try {
            Timber.v("reading json: $fileName")

            val jsonString = context.assets.open("Quizzes/$fileName")
                .bufferedReader()
                .use { it.readText() }

            return jsonParser.decodeFromString<TestMyselfListRoot>(jsonString)

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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
}
