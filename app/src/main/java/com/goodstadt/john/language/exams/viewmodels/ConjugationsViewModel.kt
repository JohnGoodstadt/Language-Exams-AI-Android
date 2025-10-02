package com.goodstadt.john.language.exams.viewmodels

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.BillingRepository
import com.goodstadt.john.language.exams.data.ConnectivityRepository
import com.goodstadt.john.language.exams.data.PlaybackResult
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
//import com.goodstadt.john.language.exams.managers.RateLimiterManager
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.VocabWord
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// A UI state for this specific screen
sealed interface ConjugationsUiState {
    object Loading : ConjugationsUiState
//    val cachedAudioWordKeys: Set<String>
//        get() = emptySet()

    data class Success(
        val categories: List<Category>,
        val cachedAudioWordKeys: Set<String>,
        val selectedVoiceName: String = "" // TODO: do I need this?
    ) : ConjugationsUiState

    data class Error(val message: String) : ConjugationsUiState
    object NotAvailable : ConjugationsUiState // For flavors like 'zh'
}


@HiltViewModel
class ConjugationsViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ttsStatsRepository: TTSStatsRepository,
    private val appScope: CoroutineScope,
    private val billingRepository: BillingRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val connectivityRepository: ConnectivityRepository,

    ) : ViewModel() {

    private val _uiState = MutableStateFlow<ConjugationsUiState>(ConjugationsUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _selectedConjugation = MutableStateFlow(LanguageConfig.conjugationOptions.first())
    val selectedConjugation = _selectedConjugation.asStateFlow()

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    // Reuse the playback state logic
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
        loadConjugationsData(LanguageConfig.getConjugationFileName(_selectedConjugation.value))

        viewModelScope.launch {
            billingRepository.isPurchased.collect { purchasedStatus ->
                _isPremiumUser.value = purchasedStatus
                if (DEBUG) {
                    billingRepository.logCurrentStatus()
                }
            }
        }
    }

//    private fun loadCachedSentencesForDot() {
//
//
//
//    }

    private fun loadConjugationsData(fileName: String?) {
        viewModelScope.launch {
       //     val fileName = LanguageConfig.conjugationsFileName

            if (fileName == null) {
                _uiState.value = ConjugationsUiState.NotAvailable
                return@launch
            }

            _uiState.value = ConjugationsUiState.Loading
            val result = vocabRepository.getVocabData(fileName)

            result.onSuccess { vocabFile ->

                val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
                val cachedKeys = vocabRepository.getSentenceKeysWithCachedAudio(vocabFile.categories, currentVoiceName)

                _uiState.value = ConjugationsUiState.Success(vocabFile.categories,cachedKeys, selectedVoiceName = currentVoiceName)
            }.onFailure { error ->
                _uiState.value =
                    ConjugationsUiState.Error(error.localizedMessage ?: "Failed to load file")
            }
        }
    }
    fun onConjugationSelected(option: String) {
        if (_selectedConjugation.value != option) {
            _selectedConjugation.value = option
            val fileName = LanguageConfig.getConjugationFileName(option)
            loadConjugationsData(fileName)
        }
    }
    // This function is almost identical to the ones in our other ViewModels
    fun playTrack(word: VocabWord, sentence: Sentence) {
        if (_playbackState.value is PlaybackState.Playing) {
            return
        }

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
//            val currentVoiceName = _uiState.value.selectedVoiceName
            val uniqueSentenceId = generateUniqueSentenceId(word, sentence, currentVoiceName)

            _playbackState.value = PlaybackState.Playing(uniqueSentenceId)

            val played = vocabRepository.playFromCacheIfFound(uniqueSentenceId)
            if (played) {//short cut so user cna play cached sentences with no Internet connection
                _playbackState.value = PlaybackState.Idle
                ttsStatsRepository.updateTTSStatsWithoutCosts()
                ttsStatsRepository.incWordStats(word.word)
                return@launch
            }



            _uiState.update { currentState ->
                if (currentState is ConjugationsUiState.Success) {
                    val updatedKeys = currentState.cachedAudioWordKeys +  generateUniqueSentenceId(word, sentence, currentVoiceName)//word.word
                    currentState.copy(cachedAudioWordKeys = updatedKeys)
                } else {
                    currentState
                }
            }

            val currentLanguageCode =  userPreferencesRepository.selectedLanguageCodeFlow.first()

            val result = vocabRepository.playTextToSpeech(
                text = sentence.sentence,
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

                is PlaybackResult.PlayedFromCache -> {
                    _playbackState.value = PlaybackState.Idle
                    ttsStatsRepository.updateTTSStatsWithoutCosts()
                    ttsStatsRepository.incWordStats(word.word)
                }

                is PlaybackResult.Failure -> {
                    _playbackState.value = PlaybackState.Idle
                    // Handle the error
//                    _uiState.update { it.copy(playbackState = PlaybackState.Error(result.exception.message ?: "Playback failed")) }
                    // Optionally reset to Idle after a delay
//                    _uiState.update { it.copy(playbackState = PlaybackState.Idle) }
                    _playbackState.value =
                        PlaybackState.Error(result.exception.message ?: "Playback failed")
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

    fun hideDailyRateLimitSheet() {
        _showRateDailyLimitSheet.value = false
    }

    fun hideHourlyRateLimitSheet() {
        _showRateHourlyLimitSheet.value = false
    }

    fun hideRateOKLimitSheet() {
        _showRateLimitSheet.value = false
    }

    fun buyPremiumButtonPressed(activity: Activity) {
        Timber.i("purchasePremium()")
        viewModelScope.launch {
            billingRepository.launchPurchase(activity)
        }
    }
}