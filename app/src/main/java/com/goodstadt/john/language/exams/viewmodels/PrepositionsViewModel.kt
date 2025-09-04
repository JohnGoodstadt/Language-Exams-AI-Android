package com.goodstadt.john.language.exams.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.BillingRepository
import com.goodstadt.john.language.exams.data.PlaybackResult
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UserStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.managers.RateLimiterManager
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
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// This UI State can be reused, but let's give it a specific name for clarity
sealed interface PrepositionsUiState {
    object Loading : PrepositionsUiState
    data class Success(val categories: List<Category>,
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
    private val billingRepository: BillingRepository
) : ViewModel() {

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    private val _uiState = MutableStateFlow<PrepositionsUiState>(PrepositionsUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()

    //NOTE: rate Limiting
    private val rateLimiter = RateLimiterManager.getInstance()
    private val _showRateLimitSheet = MutableStateFlow(false)
    val showRateLimitSheet = _showRateLimitSheet.asStateFlow()

    private val _showRateDailyLimitSheet = MutableStateFlow(false)
    val showRateDailyLimitSheet = _showRateDailyLimitSheet.asStateFlow()

    private val _showRateHourlyLimitSheet = MutableStateFlow(false)
    val showRateHourlyLimitSheet = _showRateHourlyLimitSheet.asStateFlow()

    init {
        loadPrepositionsData()

        viewModelScope.launch {
            billingRepository.isPurchased.collect { purchasedStatus ->
                // This block runs AUTOMATICALLY whenever the value in the
                // BillingRepository's 'isPurchased' flow changes.
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
            val fileName = LanguageConfig.prepositionsFileName

            if (fileName == null) {
                _uiState.value = PrepositionsUiState.NotAvailable
                return@launch
            }

            _uiState.value = PrepositionsUiState.Loading
            val result = vocabRepository.getVocabData(fileName)

            result.onSuccess { vocabFile ->
                _uiState.value = PrepositionsUiState.Success(vocabFile.categories)
            }.onFailure { error ->
                _uiState.value = PrepositionsUiState.Error(error.localizedMessage ?: "Failed to load file")
            }
        }
    }

    fun playTrack(word: VocabWord, sentence: Sentence) {
        if (_playbackState.value is PlaybackState.Playing) return

        if (isPremiumUser.value) {
            Timber.i("playTrack() User is a isPremiumUser")
        }else{
            Timber.i("playTrack() User is NOT a isPremiumUser")
        }

        if (!isPremiumUser.value) { //if premium user don't check credits
            if (rateLimiter.doIForbidCall()){
                val failType = rateLimiter.canMakeCallWithResult()
                println(failType.canICallAPI)
                println(failType.failReason)
                println(failType.timeLeftToWait)
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
            val currentLanguageCode = LanguageConfig.languageCode

            val uniqueSentenceId = generateUniqueSentenceId(word, sentence, currentVoiceName)
            _playbackState.value = PlaybackState.Playing(uniqueSentenceId)
//maybe just de. remove any (zu dem)
            val cleanedSentence = sentence.sentence.replace("\\s*\\([^)]*\\)\\s*".toRegex(), " ")

            val result = vocabRepository.playTextToSpeech(
                text = cleanedSentence,
                uniqueSentenceId = uniqueSentenceId,
                voiceName = currentVoiceName,
                languageCode = currentLanguageCode
            )
            when (result) {
                is PlaybackResult.PlayedFromNetworkAndCached -> {
                    rateLimiter.recordCall()
                    Timber.v(rateLimiter.printCurrentStatus)
                    ttsStatsRepository.updateTTSStatsWithCosts(sentence, currentVoiceName)
                    //TODO: not inc but update!
                    ttsStatsRepository.incProgressSize(userPreferencesRepository.selectedSkillLevelFlow.first())
                }
                is PlaybackResult.PlayedFromCache -> {
                    ttsStatsRepository.updateTTSStatsWithoutCosts()
                }
                is PlaybackResult.Failure -> {
                    _playbackState.value = PlaybackState.Error(result.exception.message ?: "Playback failed")
                }
            }
            _playbackState.value = PlaybackState.Idle
        }
    }
    fun saveDataOnExit() {
        // We use appScope to ensure this save operation completes even if the
        // viewModelScope is paused or cancelled as the user navigates away.
        appScope.launch {
            Timber.d("Saving data because screen is no longer active.")
            if (ttsStatsRepository.checkIfStatsFlushNeeded(forced = true)) {
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.TTSStats)
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
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
}