package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.VocabWord
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ConjugationsUiState>(ConjugationsUiState.Loading)
    val uiState = _uiState.asStateFlow()

    // Reuse the playback state logic
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()

    init {
        loadConjugationsData()
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

            result.onFailure { error ->
                _playbackState.value = PlaybackState.Error(error.localizedMessage ?: "Playback failed")
            }
            _playbackState.value = PlaybackState.Idle
        }
    }
    //fun getCurrentGoogleVoice() : String {
//        return userPreferencesRepository.selectedVoiceNameFlow.first()
      //  return  "en-GB-Neural2-C"
   // }

}