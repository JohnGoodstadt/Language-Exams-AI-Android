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
import javax.inject.Inject

// A UI state for this specific screen
sealed interface ConjugationsUiState {
    object Loading : ConjugationsUiState
    data class Success(
        val categories: List<Category>,
        val selectedVoiceName: String = "" // Add a default empty value
    ) : ConjugationsUiState
    data class Error(val message: String) : ConjugationsUiState
    object NotAvailable : ConjugationsUiState // For flavors like 'zh'
}

@HiltViewModel
class ConjugationsViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val userStatsRepository: UserStatsRepository,
    private val ttsStatsRepository: TTSStatsRepository,
    private val appScope: CoroutineScope,
    private val billingRepository: BillingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConjugationsUiState>(ConjugationsUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    // Reuse the playback state logic
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()

    init {
        loadConjugationsData()
        viewModelScope.launch {
            billingRepository.isPurchased.collect { purchasedStatus ->
                _isPremiumUser.value = purchasedStatus
                if (DEBUG) {
                    billingRepository.logCurrentStatus()
                }
            }
        }
    }

    private fun loadConjugationsData() {
        viewModelScope.launch {
            val fileName = LanguageConfig.conjugationsFileName

            if (fileName == null) {
                _uiState.value = ConjugationsUiState.NotAvailable
                return@launch
            }

            _uiState.value = ConjugationsUiState.Loading
            val result = vocabRepository.getVocabData(fileName)
           // val selectedVoice = userPreferencesRepository.selectedVoiceNameFlow.first()

            result.onSuccess { vocabFile ->
                _uiState.value = ConjugationsUiState.Success(vocabFile.categories)
            }.onFailure { error ->
                _uiState.value = ConjugationsUiState.Error(error.localizedMessage ?: "Failed to load file")
            }
        }
    }

//    fun currentVoiceName() : String {
//        userPreferencesRepository.selectedVoiceNameFlow.first()
//    }
    // This function is almost identical to the ones in our other ViewModels
    fun playTrack(word: VocabWord, sentence: Sentence) {
        if (_playbackState.value is PlaybackState.Playing) return

        if (isPremiumUser.value) {
            Log.i("ConjugationsViewModel","playTrack() User is a isPremiumUser")
        }else{
            Log.i("ConjugationsViewModel","playTrack() User is NOT a isPremiumUser")
        }


    viewModelScope.launch {
//            val googleVoice = "en-GB-Neural2-C"

            val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
            val uniqueSentenceId = generateUniqueSentenceId(word, sentence,currentVoiceName)

            _playbackState.value = PlaybackState.Playing(uniqueSentenceId)

            // Use .first() to get the most recent value from the Flow

            val currentLanguageCode = LanguageConfig.languageCode

            val result = vocabRepository.playTextToSpeech(
                    text = sentence.sentence,
                    uniqueSentenceId = uniqueSentenceId,
                    voiceName = currentVoiceName,
                    languageCode = currentLanguageCode
            )
//            result.onSuccess {
//                //TODO: Could be many "I have..." do I need this?
//                //statsRepository.fsUpdateSentenceHistoryIncCount(WordAndSentence(word.word, sentence.sentence))
//            }
//            result.onFailure { error ->
//                _playbackState.value = PlaybackState.Error(error.localizedMessage ?: "Playback failed")
//            }
            when (result) {
                is PlaybackResult.PlayedFromNetworkAndCached -> {
                    ttsStatsRepository.updateGlobalTTSStats( sentence.sentence,currentVoiceName)
                    ttsStatsRepository.updateUserPlayedSentenceCount()
                    // A new file was cached! Increment the count.
//                    _uiState.update {
//                        it.copy(
//                            playbackState = PlaybackState.Idle,
                            // Increment the count
//                            cachedAudioCount = it.cachedAudioCount + 1,
//                            // Also add the word to the set of cached keys for the red dot
//                            wordsOnDisk = it.wordsOnDisk + word.word
//                        )
//                    }
                }
                is PlaybackResult.PlayedFromCache -> {
                    ttsStatsRepository.updateUserPlayedSentenceCount()
                    // The file was already cached, just reset the playback state.
                  //  _uiState.update { it.copy(playbackState = PlaybackState.Idle) }
                }
                is PlaybackResult.Failure -> {
                    // Handle the error
//                    _uiState.update { it.copy(playbackState = PlaybackState.Error(result.exception.message ?: "Playback failed")) }
                    // Optionally reset to Idle after a delay
//                    _uiState.update { it.copy(playbackState = PlaybackState.Idle) }
                    _playbackState.value = PlaybackState.Error(result.exception.message ?: "Playback failed")
//                    _uiState.update { it.copy(error = "Text-to-speech failed: ${result.exception.message ?: "Playback failed"}") }
                }
            }

            _playbackState.value = PlaybackState.Idle
        }
    }
    fun saveDataOnExit() {
        // We use appScope to ensure this save operation completes even if the
        // viewModelScope is paused or cancelled as the user navigates away.
        appScope.launch {
            Log.d("ViewModelLifecycle", "Saving data because screen is no longer active.")
            if (ttsStatsRepository.checkIfStatsFlushNeeded(forced = true)) {
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.TTSStats)
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
            }
        }
    }
}