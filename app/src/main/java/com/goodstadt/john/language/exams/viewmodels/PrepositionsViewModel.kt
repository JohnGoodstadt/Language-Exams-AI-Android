package com.goodstadt.john.language.exams.viewmodels

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.BillingRepository
import com.goodstadt.john.language.exams.data.ConnectivityRepository
import com.goodstadt.john.language.exams.data.PlaybackResult
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UserStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.data.examsheets.ExamSheetRepository
//import com.goodstadt.john.language.exams.managers.RateLimiterManager
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.VocabWord
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.goodstadt.john.language.exams.utils.logging.TimberFault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

// This UI State can be reused, but let's give it a specific name for clarity
sealed interface PrepositionsUiState {
    object Loading : PrepositionsUiState
    data class Success(val categories: List<Category>,
                       val cachedAudioWordKeys: Set<String>,
                       val selectedVoiceName: String = "" )
        : PrepositionsUiState
    data class Error(val message: String) : PrepositionsUiState
    object NotAvailable : PrepositionsUiState
}

@HiltViewModel
class PrepositionsViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val userStatsRepository: UserStatsRepository,
    private val ttsStatsRepository : TTSStatsRepository,
    private val appScope: CoroutineScope,
    private val billingRepository: BillingRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val connectivityRepository: ConnectivityRepository,
    private val examSheetRepository: ExamSheetRepository,
    private val appConfigRepository: AppConfigRepository,
) : ViewModel() {

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    private val _uiState = MutableStateFlow<PrepositionsUiState>(PrepositionsUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()

    //NOTE: rate Limiting
//    private val rateLimiter = RateLimiterManager.getInstance()
    private val _showRateLimitSheet = MutableStateFlow(false)
    val showRateLimitSheet = _showRateLimitSheet.asStateFlow()

    private val _showRateDailyLimitSheet = MutableStateFlow(false)
    val showRateDailyLimitSheet = _showRateDailyLimitSheet.asStateFlow()

    private val _showRateHourlyLimitSheet = MutableStateFlow(false)
    val showRateHourlyLimitSheet = _showRateHourlyLimitSheet.asStateFlow()


    init {
//        loadPrepositionsData()
        loadPrepositions() //new cache locally originally from firestore

        viewModelScope.launch {
            billingRepository.isPurchased.collect { purchasedStatus ->
                _isPremiumUser.value = purchasedStatus
                if (DEBUG) {
                    billingRepository.logCurrentStatus()
                }
            }
        }
    }

    private fun loadPrepositionsData() {
        viewModelScope.launch {
            // *** THE ONLY LOGICAL CHANGE IS HERE ***
            val fileName = LanguageConfig.prepositionsBundleFileName

            if (fileName == null) {
                _uiState.value = PrepositionsUiState.NotAvailable
                return@launch
            }

            _uiState.value = PrepositionsUiState.Loading
            val result = vocabRepository.getVocabData(fileName)

            result.onSuccess { vocabFile ->

                val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
                val cachedKeys = vocabRepository.getSentenceKeysWithCachedAudio(vocabFile.categories, currentVoiceName)

                _uiState.value = PrepositionsUiState.Success(vocabFile.categories,cachedKeys,selectedVoiceName = currentVoiceName)
            }.onFailure { error ->
                _uiState.value = PrepositionsUiState.Error(error.localizedMessage ?: "Failed to load file")
            }
        }
    }

    private fun loadPrepositions() {

//        try {
//            // Simulate a dangerous operation that can fail.
//            // Throwing a common exception like IOException is a good example.
//            throw IOException("Simulated critical failure: Could not read a required file.")
//
//        } catch (e: Exception) {
//            val errorMessage = "A test fault was triggered from the debug menu."
//
//            // --- THIS IS THE TEST ---
//            // Call your TimberFault logger with the exception and a message.
//           // TimberFault.f(e.localizedMessage ?: "localizedMessage is null", errorMessage)
//
//            TimberFault.f(
//                t = e,
//                message = "Failed to save user progress after level completion.",
//                secondaryText = "User ID: 12345, Level ID: 7B",
//                area = "LevelCompleteViewModel"
//            )
//        }


        viewModelScope.launch {
            _uiState.value = PrepositionsUiState.Loading

            val remoteVersion = appConfigRepository.getPrepositionsDataVersion()
            val localVersion = userPreferencesRepository.prepositionsLocalVersionFlow.first()
            val forceRefresh = remoteVersion > localVersion
            Timber.d("Prepositions load: Remote version=$remoteVersion, Local version=$localVersion, Force refresh=$forceRefresh")

            val bundleFallbackFileName = LanguageConfig.prepositionsBundleFileName
            val firestoreName = LanguageConfig.prepositionsFirestoreName
            // This assumes the exam name is stored in your user preferences
           // val examName = userPreferencesRepository.selectedPrepositionsExamNameFlow.first() // You'll need to create this flow

            try {
                // --- THIS IS THE CORE ORCHESTRATION LOGIC ---
                // 1. First, try to get data from the repository (which handles cache/network).
                Timber.i("ViewModel: Attempting to fetch prepositions from repository for '$firestoreName'...")
                val result = examSheetRepository.getExamSheetBy(firestoreName,forceRefresh)

                result.onSuccess { vocabFile ->
                    // 2. If it succeeds, update the UI with the fresh data.
                    Timber.i("ViewModel: Successfully loaded ${vocabFile.categories.size} categories from repository.")
                    if (forceRefresh) {
                        userPreferencesRepository.updatePrepositionsLocalVersion(remoteVersion)
                    }
                    // We can also fetch the cached audio keys here to update the red dots
                    val voiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
                    val cachedAudioKeys = vocabRepository.getSentenceKeysWithCachedAudio(vocabFile.categories, voiceName)
                    _uiState.value = PrepositionsUiState.Success(vocabFile.categories, cachedAudioKeys, voiceName)
                }

                result.onFailure { error ->
                    // 3. If the repository fails, fall back to the local bundle.
//                    Timber.f(error as Throwable, "ViewModel: ERROR fetching from repository. Falling back to local JSON.")
//                    Timber.fault("")
//                    Timber.f("")

                    val fallbackCategories = loadFromLocalBundle(bundleFallbackFileName)
                    _uiState.value = PrepositionsUiState.Success(fallbackCategories, emptySet(), "")
                }

            } catch (e: Exception) {
                TimberFault.f(
                    message = "CRITICAL ERROR in loadPrepositions. Falling back to local JSON",
                    localizedMessage = e.localizedMessage ?: "null localizedMessage",
                    secondaryText = "getExamSheetBy($firestoreName)",
                    area = "PrepositionsViewModel.loadPrepositions()"
                )

                // Catch any unexpected exceptions from the flow itself
//                TimberFault.f(e, "ViewModel: CRITICAL ERROR in loadPrepositions. Falling back to local JSON.")
                val fallbackCategories = loadFromLocalBundle(bundleFallbackFileName)
                _uiState.value = PrepositionsUiState.Success(fallbackCategories, emptySet(), "")
            }
        }
    }
    fun playTrack(word: VocabWord, sentence: Sentence) {
        if (_playbackState.value is PlaybackState.Playing)
        {
            return
        }

        if (!connectivityRepository.isCurrentlyOnline()) {
            _playbackState.value = PlaybackState.Idle
            return
        }

        if (!isPremiumUser.value) { //if premium user don't check credits
            if (rateLimiter.doIForbidCall()){
                val failType = rateLimiter.canMakeCallWithResult()
                Timber.v("${failType.canICallAPI}")
                Timber.v("${failType.failReason}")
                Timber.v("${failType.timeLeftToWait}")
                if (!failType.canICallAPI){
                    if (failType.failReason == SimpleRateLimiter.FailReason.DAILY){
                        _showRateDailyLimitSheet.value = true
                    }else {
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
            val currentLanguageCode =  userPreferencesRepository.selectedLanguageCodeFlow.first()

            val uniqueSentenceId = generateUniqueSentenceId(word, sentence, currentVoiceName)
            _playbackState.value = PlaybackState.Playing(uniqueSentenceId)
//maybe just de. remove any (zu dem)
            val cleanedSentence = sentence.sentence.replace("\\s*\\([^)]*\\)\\s*".toRegex(), " ")

            val played = vocabRepository.playFromCacheIfFound(uniqueSentenceId)
            if (played){//short cut so user cna play cached sentences with no Internet connection
                _playbackState.value = PlaybackState.Idle
                ttsStatsRepository.updateTTSStatsWithoutCosts()
                ttsStatsRepository.incWordStats(word.word)
                return@launch
            }

            //Dot shows before sound (lightening before thunder)
            _uiState.update { currentState ->
                if (currentState is PrepositionsUiState.Success) {
                    val updatedKeys = currentState.cachedAudioWordKeys +  generateUniqueSentenceId(word, sentence, currentVoiceName)
                    currentState.copy(cachedAudioWordKeys = updatedKeys)
                } else {
                    currentState
                }
            }

            val result = vocabRepository.playTextToSpeech(
                text = cleanedSentence,
                uniqueSentenceId = uniqueSentenceId,
                voiceName = currentVoiceName,
                languageCode = currentLanguageCode
            )
            when (result) {
                is PlaybackResult.PlayedFromNetworkAndCached -> {
                    _playbackState.value = PlaybackState.Idle

                    rateLimiter.recordCall()
                    Timber.v(rateLimiter.printCurrentStatus)
                    ttsStatsRepository.updateTTSStatsWithCosts(sentence, currentVoiceName)
                    ttsStatsRepository.incWordStats(word.word)
                    //TODO: not inc but update!
                    ttsStatsRepository.incProgressSize(userPreferencesRepository.selectedSkillLevelFlow.first())
                }
                is PlaybackResult.PlayedFromCache -> { //probably does not get executed as playFromCacheIfFound() already run
                    _playbackState.value = PlaybackState.Idle
                    ttsStatsRepository.updateTTSStatsWithoutCosts()
                    ttsStatsRepository.incWordStats(word.word)
                }
                is PlaybackResult.Failure -> {
                    _playbackState.value = PlaybackState.Idle
                    _playbackState.value = PlaybackState.Error(result.exception.message ?: "Playback failed")
                }
                PlaybackResult.CacheNotFound -> {
                    _playbackState.value = PlaybackState.Idle
                    Timber.e("Cache found to exist but not played")
                }
            }
            _playbackState.value = PlaybackState.Idle
        }
    }
    fun saveDataOnExit() {
        // We use appScope to ensure this save operation completes even if the
        // viewModelScope is paused or cancelled as the user navigates away.
        if (false) {
            appScope.launch {
                Timber.d("Saving data because screen is no longer active.")
                if (ttsStatsRepository.checkIfStatsFlushNeeded(forced = true)) {
                    ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.TTSStats)
                    ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
                }
            }
        }
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

    fun buyPremiumButtonPressed(activity: Activity) {
        Timber.i("purchasePremium()")
        viewModelScope.launch {
            billingRepository.launchPurchase(activity)
        }
    }
    /**
     * A private helper function to load and parse the local JSON file as a fallback.
     */
    private suspend fun loadFromLocalBundle(fileName: String): List<Category> {
        return try {
            // Because this is now a suspend function, we can safely call another suspend function.
            val result = vocabRepository.getVocabData(fileName)

            // Return the categories on success, or an empty list on failure.
            result.getOrNull()?.categories ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "ViewModel: CRITICAL - Failed to load local fallback JSON.")
            _uiState.value = PrepositionsUiState.Error("Failed to load data.")
            emptyList()
        }
    }
}

//private fun Timber.Forest.fault(s: String) {
//    Timber.i("")
//}
//private fun Timber.Forest.f(s: String) {
//    Timber.i("")
//}
