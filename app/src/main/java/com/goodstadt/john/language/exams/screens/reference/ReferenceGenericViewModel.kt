package com.goodstadt.john.language.exams.screens.reference

//package com.yourpackage.ui.reference.generic // Or your preferred package

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.BillingRepository
import com.goodstadt.john.language.exams.data.ConnectivityRepository
import com.goodstadt.john.language.exams.data.PlaybackResult
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.data.examsheets.ExamSheetRepository
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.VocabWord
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.goodstadt.john.language.exams.viewmodels.PlaybackState
import com.goodstadt.john.language.exams.viewmodels.PrepositionsUiState
// ... other necessary imports from your PrepositionsViewModel
//import com.yourpackage.data.repository.ExamSheetRepository
//import com.yourpackage.data.repository.VocabRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// 1. RENAMED: The UI State is now generic
sealed interface ReferenceGenericUiState {
    object Loading : ReferenceGenericUiState
    data class Success(
        val categories: List<Category>,
        val cachedAudioWordKeys: Set<String>,
        val selectedVoiceName: String = ""
    ) : ReferenceGenericUiState
    data class Error(val message: String) : ReferenceGenericUiState
    object NotAvailable : ReferenceGenericUiState
}

@HiltViewModel
class ReferenceGenericViewModel @Inject constructor(
    // 2. MODIFIED: Injected SavedStateHandle to get navigation arguments
    private val savedStateHandle: SavedStateHandle,
    // Keep all other dependencies that are still needed
    private val vocabRepository: VocabRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ttsStatsRepository: TTSStatsRepository,
    private val appScope: CoroutineScope,
    private val billingRepository: BillingRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val connectivityRepository: ConnectivityRepository,
    private val examSheetRepository: ExamSheetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReferenceGenericUiState>(ReferenceGenericUiState.Loading)
    val uiState = _uiState.asStateFlow()

    // --- All other state flows can remain the same ---
    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()
    private val _showRateLimitSheet = MutableStateFlow(false)
    val showRateLimitSheet = _showRateLimitSheet.asStateFlow()
    private val _showRateDailyLimitSheet = MutableStateFlow(false)
    val showRateDailyLimitSheet = _showRateDailyLimitSheet.asStateFlow()
    private val _showRateHourlyLimitSheet = MutableStateFlow(false)
    val showRateHourlyLimitSheet = _showRateHourlyLimitSheet.asStateFlow()

    init {
        // 3. MODIFIED: Get the documentId from the navigation arguments
        val firestoreDocumentId: String? = savedStateHandle.get("documentId")

        if (firestoreDocumentId != null) {
            loadVocabData(firestoreDocumentId)
        } else {
            _uiState.value = ReferenceGenericUiState.Error("Document ID not provided.")
        }

        viewModelScope.launch {
            billingRepository.isPurchased.collect { purchasedStatus ->
                _isPremiumUser.value = purchasedStatus
            }
        }
    }

    // 4. MODIFIED: Replaced loadPrepositions() with a generic function
    private fun loadVocabData(firestoreDocumentId: String) {
        viewModelScope.launch {
            _uiState.value = ReferenceGenericUiState.Loading

            try {
                // Use the passed-in documentId to fetch data.
                // The versioning and local bundle fallback logic has been removed
                // as it was specific to the Prepositions screen.
                Timber.i("ViewModel: Attempting to fetch generic vocab for '$firestoreDocumentId'...")
                val result = examSheetRepository.getExamSheetBy(firestoreDocumentId,false) // Generic screens typically don't need forced refreshes


                result.onSuccess { vocabFile ->
                    Timber.i("ViewModel: Successfully loaded ${vocabFile.categories.size} categories.")
                    val voiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
                    val cachedAudioKeys = vocabRepository.getSentenceKeysWithCachedAudio(vocabFile.categories, voiceName)
                    _uiState.value = ReferenceGenericUiState.Success(vocabFile.categories, cachedAudioKeys, voiceName)
                }

                result.onFailure { error ->
                    Timber.e(error, "ViewModel: ERROR fetching from repository for '$firestoreDocumentId'.")
                    _uiState.value = ReferenceGenericUiState.Error(error.localizedMessage ?: "Failed to load data")
                }

            } catch (e: Exception) {
                Timber.e(e, "ViewModel: CRITICAL ERROR in loadVocabData.")
                _uiState.value = ReferenceGenericUiState.Error("A critical error occurred.")
            }
        }
    }

    // --- ALL OTHER FUNCTIONS (playTrack, saveDataOnExit, hide...Sheet, etc.) ---
    // can be copied directly from PrepositionsViewModel as they are already generic enough.
    // They operate on VocabWord, Sentence, etc., and have no hardcoded logic.
    // (Omitted for brevity, but you should paste them here)
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
                if (currentState is ReferenceGenericUiState.Success) {
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
}