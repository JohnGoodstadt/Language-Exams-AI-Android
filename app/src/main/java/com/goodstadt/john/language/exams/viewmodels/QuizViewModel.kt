package com.goodstadt.john.language.exams.viewmodels

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.PlaybackResult
import com.goodstadt.john.language.exams.data.StatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.data.api.GoogleCloudTTS
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.LanguagesControlFile
import com.goodstadt.john.language.exams.models.TestMyselfListRoot
import com.goodstadt.john.language.exams.models.WordAndSentence
import com.goodstadt.john.language.exams.storage.UiEvent
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
//import com.google.gson.Gson

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*
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
        val dateFormatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return "${dateFormatter.format(timestamp)} '${state.description}' '$skillLevel' level:$quizNumber answered:$answered tries:$tries correct:$correct"
    }
    val readyForDB: String
        get() {
            val dateFormatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            return "${dateFormatter.format(timestamp)}:${state.description}:$skillLevel:$quizNumber:$answered:$tries:$correct"
        }

    fun update(answered: Int, correct: Int, tries: Int) = copy(answered = answered, correct = correct, tries = tries)
}
/*
in this viewmodel can you change generateSampleQuestions() to read questions from a supplied JSON file called "TestMyselfQuiz1Elementary-en.json".
 */
enum class QuizLevels(val sheetNameQ1: String, val sheetNameQ2: String, val sheetNameQ3: String, val sheetNameQ4: String, val sheetNameQ5: String) {
    ELEMENTARY("TestMyselfQuiz1Elementary-en", "TestMyselfQuiz2Elementary-en", "TestMyselfQuiz3Elementary-en", "TestMyselfQuiz4Elementary-en", "TestMyselfQuiz5Elementary-en"),
    INTER("TestMyselfQuiz1Inter-en", "TestMyselfQuiz2Inter-en", "TestMyselfQuiz3Inter-en","TestMyselfQuiz4Inter-en","TestMyselfQuiz5Inter-en"),
    UPPER("TestMyselfQuiz1Upper-en", "TestMyselfQuiz2Upper-en", "TestMyselfQuiz3Upper-en","TestMyselfQuiz4Upper-en","TestMyselfQuiz5Upper-en"),
    ADVANCED("TestMyselfQuiz1Advanced-en", "TestMyselfQuiz2Advanced-en", "TestMyselfQuiz3Advanced-en","TestMyselfQuiz4Advanced-en","TestMyselfQuiz5Advanced-en");

    val description: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

data class WordOK (
        val word: String,
        val ok: Boolean
)
data class QuizQuestion(
    val sentence: String,
    val words: List<String>,
    val correctOption: String,
    val summary: String,
    val explain: String
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
    private val vocabRepository: VocabRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val statsRepository: StatsRepository
//    private val pronounceSharedPreferences: PronounceSharedPreferences,
//    private val remoteConfigRepository: RemoteConfigRepository,
//    private val musicPlayer: MusicPlayer,
//    private val statsManager: StatsManager
) : ViewModel() {
    private val appContext: Context = application.applicationContext
   // private val rateLimiter = RateLimiterManager.getInstance()

    private val _uiState99 = MutableStateFlow<QuizUiState>(QuizUiState.Loading)
    val uiState99 = _uiState99.asStateFlow()
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()

    // region State FLow
    private val _questions = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val questions: StateFlow<List<QuizQuestion>> get() = _questions
//    private val _playbackState = MutableStateFlow(false)
//    val playbackState: StateFlow<Boolean> = _playbackState
    private val _showRateLimitSheet = MutableStateFlow(false)
    val showRateLimitSheet = _showRateLimitSheet.asStateFlow()

    private val _showRateDailyLimitSheet = MutableStateFlow(false)
    val showRateDailyLimitSheet = _showRateDailyLimitSheet.asStateFlow()

    private val _showRateHourlyLimitSheet = MutableStateFlow(false)
    val showRateHourlyLimitSheet = _showRateHourlyLimitSheet.asStateFlow()

    private val _showUpgradeAppSheet = MutableStateFlow(false)
    val showUpgradeAppSheet = _showUpgradeAppSheet.asStateFlow()

    private val _showForceUpgradeAppSheet = MutableStateFlow(false)
    val showForceUpgradeAppSheet = _showForceUpgradeAppSheet.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    // endregion

    val quizStatistics = mutableStateOf(
            QuizStatistics(skillLevel = QuizLevels.ELEMENTARY.description, quizNumber = 1)
    )
    val selectedLevel = mutableStateOf(QuizLevels.ELEMENTARY)
    val selectedQuizNumber = mutableStateOf(1)
    val currentQuestionIndex = mutableStateOf(0)
    val userAnswers = mutableStateOf(mutableMapOf<Int, Boolean>())

    //Constants
    val quizFillInTheBlanks = 7 //in iOS these are ENUMs
    val quizMultipleChoice = 10
    val currentFileFormat = mutableStateOf(quizFillInTheBlanks) //either 7 (fill in the blank) or 10 (Multiple choice)

    init {
        loadQuestions()
    }
    fun hideDailyRateLimitSheet(){
        _showRateDailyLimitSheet.value = false
    }
    fun hideHourlyRateLimitSheet(){
        _showRateHourlyLimitSheet.value = false
    }
    fun hideRateOKLimitSheet(){
        _showRateLimitSheet.value = false
    }
    fun showAppUpgradeSheet(){
        _showUpgradeAppSheet.value = true
    }
    fun showForceAppUpgradeSheet(){
        _showForceUpgradeAppSheet.value = true
    }
    fun hideAppUpgradeSheet(){
        _showUpgradeAppSheet.value = false
    }
    fun hideForceAppUpgradeSheet(){
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

        viewModelScope.launch {

            val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
            val uniqueSentenceId = generateUniqueSentenceId(sentence,currentVoiceName)

          //  _playbackState.value = PlaybackState.Playing(uniqueSentenceId)

            // Use .first() to get the most recent value from the Flow

            val currentLanguageCode = LanguageConfig.languageCode

            val result = vocabRepository.playTextToSpeech(
                    text = sentence,
                    uniqueSentenceId = uniqueSentenceId,
                    voiceName = currentVoiceName,
                    languageCode = currentLanguageCode
            )
//            result.onSuccess {
//                //TODO: Do I want to save the quiz?
//                //statsRepository.fsUpdateSentenceHistoryIncCount(WordAndSentence(word.word, sentence.sentence))
//            }
//            result.onFailure { error ->
//                _playbackState.value = PlaybackState.Error(error.localizedMessage ?: "Playback failed")
//            }
            when (result) {
                is PlaybackResult.PlayedFromNetworkAndCached -> {}
                is PlaybackResult.PlayedFromCache -> {}
                is PlaybackResult.Failure -> {
                    _playbackState.value = PlaybackState.Error(result.exception.message ?: "Playback failed")
                }
            }
            _playbackState.value = PlaybackState.Idle
        }

    }


    /*
    private fun playMP3File(googleVoiceName: String, sentence: String) {
        val musicDir = application.getMusicDir()
        val musicFile = File(musicDir, BusinessUtils.encodeMP3Filename(googleVoiceName, sentence))
        musicPlayer.playMP3File(musicFile) {}
    }

     */

    /**
     * Called when the user taps the "play" icon on a SoundData item.
     * 1) Mark that sound as "isPlayed = true" in the UI state.
     * 2) Actually play the MP3.
     */
    /*
    fun playMP3(mp3: File) {
        _playbackState.value = true

        musicPlayer.playMP3File(mp3) {
            _playbackState.value = false
        }
    }

     */
    /*
    fun getCachedAudioIfExists(textToSpeak: String) : File? {

//        val currentGoogleVoice = pronounceSharedPreferences.googleVoiceName?.voice()
//        val filename = BusinessUtils.replaceInvalidChars( "[$currentGoogleVoice]${textToSpeak}.mp3")
        val filename = BusinessUtils.encodeMP3Filename(pronounceSharedPreferences.googleVoiceName?.noGender(),textToSpeak)

        val musicDir = application.getMusicDir()
        val musicFile = File(musicDir, filename )
        if (musicFile.exists() && musicFile.length() > 0L) {
            try {
                return musicFile
            } catch (e: Exception) {
                return null
            }
        }
        return null
    }

     */
    fun loadQuestions() {
        viewModelScope.launch {

            val fileName = when (selectedQuizNumber.value) {
                1 -> selectedLevel.value.sheetNameQ1
                2 -> selectedLevel.value.sheetNameQ2
                3 -> selectedLevel.value.sheetNameQ3
                4 -> selectedLevel.value.sheetNameQ4
                5 -> selectedLevel.value.sheetNameQ5
                else -> selectedLevel.value.sheetNameQ1
            } + ".json" // Append the JSON file extension


            _questions.value = generateQuestionsFromJson(appContext, fileName)
            println(_questions.value.count())

            resetQuiz()


//            if (_questions.value[0] != null){
//                println("First question: ${_questions.value[0]}")
//            }
        }
    }
    private fun generateQuestionsFromJson(context: Context, fileName: String): List<QuizQuestion> {
        val testData = readTestMyselfDataFromAssets(context, fileName)

        if (testData == null) {
            println("Failed to parse JSON file: $fileName")
            return emptyList()
        }

//        val fileFormat = testData.fileFormat
        if ( testData.fileFormat != quizFillInTheBlanks) {
            currentFileFormat.value = quizMultipleChoice
        }else{
            currentFileFormat.value = quizFillInTheBlanks
        }

        if ( testData.fileFormat == quizFillInTheBlanks) testData.shuffleLists()


        return testData.data.flatMap { section ->
            section.sections.map { quizSection ->
                val words = quizSection.words.map { it.word }
                val correctOption = quizSection.words.firstOrNull { it.ok }?.word ?: ""
                val summary = quizSection.summary
                val explain = quizSection.explain
                QuizQuestion(quizSection.sentence, words, correctOption, summary,explain)
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
        if (quizStatistics.value.state != QuizState.NOT_STARTED ) {
            //save stats from previous quiz try

            if ( userAnswers.value.count() == _questions.value.count()) {
                quizStatistics.value.state = QuizState.COMPLETED
            }
            //fbUpdateUserQuizStatProperty( quizStatistics.value.readyForDB)
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

//        when (isCorrect) {
//            true -> statsManager.inc(StatsManager.fsDOC.USER, StatsManager.QUIZ_QUESTION_SUCCESS_COUNT, 1)
//            false -> statsManager.inc(StatsManager.fsDOC.USER, StatsManager.QUIZ_QUESTION_FAIL_COUNT, 1)
//        }

//        println("${currentQuestionIndex.value} and ${_questions.value.count()}")
//        println("selectedLevel: ${selectedLevel.value}")

        val currentQuestion = currentQuestionIndex.value + 1 // one based
        if (currentQuestion >= _questions.value.count() ) { //completed
            quizStatistics.value = quizStatistics.value.copy(state = QuizState.COMPLETED)
            val fieldValue = "${quizStatistics.value.quizNumber}:${quizStatistics.value.answered}:${quizStatistics.value.correct}:${quizStatistics.value.tries}"
           // val fieldKEY = "${StatsManager.QUIZ_COMPLETE}${selectedLevel.value}" //combine both quiz number and level
            //statsManager.update(StatsManager.fsDOC.USER, fieldKEY, fieldValue)

        }

    }



    fun readTestMyselfDataFromAssets(context: Context, fileName: String): TestMyselfListRoot? {
        return try {
            println("reading json: $fileName")

            val jsonString = context.assets.open("Quizzes/$fileName")
                .bufferedReader()
                .use { it.readText() }

            return jsonParser.decodeFromString<TestMyselfListRoot>(jsonString)

//            val json = context.getJsonString("Quizzes/TestMyselfQuiz1Elementary-en.json")
           // val json = context.getJsonString("Quizzes/$fileName")
//            Gson().fromJson(json, TestMyselfListRoot::class.java)
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
    /*
 fun checkIfAppUpgradeCheckStillToDoToday(): Boolean {
     return !checkIfAppUpgradeCheckAlreadyDoneToday()
 }

 private fun checkIfAppUpgradeCheckAlreadyDoneToday() : Boolean{
     return statsManager.checkIfAppUpgradeCheckAlreadyDoneToday()
 }
 fun adviseUpgradeApp() : Boolean {

     val currentAppVersion =  BuildConfig.VERSION_NAME
     val latestVersion = remoteConfigRepository.getMinimumAppVersion()

     return isUserVersionOlder(currentAppVersion,latestVersion)
//
//       if ( remoteConfigRepository.getAppUpdateNeeded()) {
//        println("getAppUpdateNeeded")
//        return true
//       }else{
//           println("getAppUpdateNeeded false")
//           return false
//       }
 }
 fun forceUpgradeApp()  : Boolean {
     val currentAppVersion = BuildConfig.VERSION_NAME
     val latestVersion = remoteConfigRepository.getMinimumAppVersion()

     return isUserVersionVeryOld(currentAppVersion,latestVersion)
//
//        if ( remoteConfigRepository.getAppUpdateNeeded()) {
//            println("getAppUpdateNeeded")
//            return true
//        }else{
//            println("getAppUpdateNeeded false")
//            return false
//        }
 }

 fun screenStatsInc() {
     statsManager.inc(StatsManager.fsDOC.TTSStats, StatsManager.viewQuizCount)
 }

  */

    fun doIHaveCurrentQuestionInfo(): Boolean {
        return if (_questions.value[currentQuestionIndex.value].summary.isNotEmpty()){
            true
        }else{
            false
        }
    }

}
