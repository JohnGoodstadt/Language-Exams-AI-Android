package com.goodstadt.john.language.exams.screens.reference


import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.BillingRepository
import com.goodstadt.john.language.exams.data.ConnectivityRepository
import com.goodstadt.john.language.exams.data.ContentRepository
import com.goodstadt.john.language.exams.data.PlaybackResult
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.models.Format2File
import com.goodstadt.john.language.exams.models.HeaderWordsSentencesList
import com.goodstadt.john.language.exams.utils.calcIsTodayNotAFreePassDay
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.goodstadt.john.language.exams.viewmodels.ConjugationsUiState
import com.goodstadt.john.language.exams.viewmodels.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// 1. Define the UI State for this specific screen.
// A sealed interface is the best practice for representing distinct states.
sealed interface Format1UiState {
    object Loading : Format1UiState
    data class Success(
        val data: List<HeaderWordsSentencesList>,
        val playbackState: PlaybackState = PlaybackState.Idle,
        val cachedAudioWordKeys: Set<String> = emptySet() // For the red dots
    ) : Format1UiState
    data class Error(val message: String) : Format1UiState
}

@HiltViewModel
class Format1ViewModel @Inject constructor(
//    private val vocabRepository: ContentRepository,
    private val contentRepository: ContentRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val connectivityRepository: ConnectivityRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val ttsStatsRepository: TTSStatsRepository,
    private val billingRepository: BillingRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow<Format1UiState>(Format1UiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    private val _showRateLimitSheet = MutableStateFlow(false)
    val showRateLimitSheet = _showRateLimitSheet.asStateFlow()

    private val _showRateDailyLimitSheet = MutableStateFlow(false)
    val showRateDailyLimitSheet = _showRateDailyLimitSheet.asStateFlow()

    private val _showRateHourlyLimitSheet = MutableStateFlow(false)
    val showRateHourlyLimitSheet = _showRateHourlyLimitSheet.asStateFlow()

    // 3. Get the documentId from the navigation arguments via SavedStateHandle.
    //    The key "documentId" MUST match the argument name in your NavHost route.
    private val documentId: String = savedStateHandle.get<String>("documentId")!!

    init {
        // 4. Trigger the data loading process as soon as the ViewModel is created.
        loadData()
    }
    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = Format1UiState.Loading

            // ✅ SIMPLIFIED: All the complex logic is gone.
            // We just make one simple, type-safe call to our orchestrator.
            val result = contentRepository.getFormat1Data(documentId)

            result.onSuccess { format1File ->
                _uiState.value = Format1UiState.Success(format1File.data)
            }
            result.onFailure { error ->
                _uiState.value = Format1UiState.Error(error.localizedMessage ?: "Failed to load content")
            }
        }
    }
    fun playTrack(sentence: String) {
        val currentState = _uiState.value
        if (currentState !is Format1UiState.Success) return
        if (currentState.playbackState is PlaybackState.Playing) return
        if (!connectivityRepository.isCurrentlyOnline()) return

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
//            val currentVoiceName = _uiState.value.selectedVoiceName
            val uniqueSentenceId = generateUniqueSentenceId(sentence, currentVoiceName)

            _uiState.update {
                if (it is Format1UiState.Success) {
                    it.copy(playbackState = PlaybackState.Playing(uniqueSentenceId))
                } else it
            }

            val played = contentRepository.playFromCacheIfFound(uniqueSentenceId)
            if (played) {//short cut so user cna play cached sentences with no Internet connection
                _uiState.update {
                    if (it is Format1UiState.Success) it.copy(playbackState = PlaybackState.Idle) else it
                }
                ttsStatsRepository.updateTTSStatsWithoutCosts()
                return@launch
            }


            _uiState.update {
                if (it is Format1UiState.Success) {
                    val updatedKeys = it.cachedAudioWordKeys + uniqueSentenceId
                    it.copy(cachedAudioWordKeys = updatedKeys)
                } else it
            }

            val currentLanguageCode =  userPreferencesRepository.selectedLanguageCodeFlow.first()

            val result = contentRepository.playTextToSpeech(
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
//                    ttsStatsRepository.incWordStats(word)
                    //TODO: not inc but update!
                    ttsStatsRepository.incProgressSize(userPreferencesRepository.selectedSkillLevelFlow.first())
                }

                is PlaybackResult.PlayedFromCache -> {
                    ttsStatsRepository.updateTTSStatsWithoutCosts()
                }

                is PlaybackResult.Failure -> {
                    _uiState.update {
                        if (it is Format1UiState.Success) {
                            it.copy(playbackState = PlaybackState.Error(result.exception.message ?: "Playback failed"))
                        } else it
                    }
                }

                PlaybackResult.CacheNotFound -> {
                    Timber.e("Cache found to exist but not played")
                }
            }

            _uiState.update {
                if (it is Format1UiState.Success) it.copy(playbackState = PlaybackState.Idle) else it
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
