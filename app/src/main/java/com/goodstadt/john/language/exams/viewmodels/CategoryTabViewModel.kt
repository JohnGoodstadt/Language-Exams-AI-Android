package com.goodstadt.john.language.exams.viewmodels

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.RecallingItems
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import com.goodstadt.john.language.exams.data.ConnectivityRepository
import com.goodstadt.john.language.exams.data.PlaybackResult
import com.goodstadt.john.language.exams.data.PlaybackSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import javax.inject.Inject

// This data class represents everything the UI needs to draw itself.
data class CategoryTabUiState(
    val isLoading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val recalledWordKeys: Set<String> = emptySet(),
    val playbackState: PlaybackState = PlaybackState.Idle,
    val wordsOnDisk: Set<String> = emptySet(),
    val downloadingSentenceId: String? = null,
    val cachedAudioCount: Int = 0,
    val totalWordsInTab: Int = 0
)

sealed interface UiEvent {
    data class ShowSnackbar(val message: String, val actionLabel: String? = null) : UiEvent
    // You can add other one-off events here later
}
@HiltViewModel
class CategoryTabViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val vocabRepository: VocabRepository, // For getting categories/words and playing audio
    private val connectivityRepository: ConnectivityRepository,
    private val recallingItemsManager: RecallingItems,
    @ApplicationContext private val context: Context,
    private val application: Application // Needed for RecallingItems SharedPreferences
) : ViewModel() {



    private val _uiState = MutableStateFlow(CategoryTabUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    private data class LastAction(val word: VocabWord, val sentence: Sentence, val voiceName: String)
    private val _lastFailedAction = MutableStateFlow<LastAction?>(null)

    init {
        // Load all data when the ViewModel is first created
        viewModelScope.launch {
            recallingItemsManager.items.collect { updatedItems ->
                // This will run whenever the list in RecallingItems changes.
                val recalledKeys = updatedItems.map { it.key }.toSet()
                _uiState.update { currentState ->
                    currentState.copy(recalledWordKeys = recalledKeys)
                }
            }
        }

        // This block handles the one-time load for this tab's specific categories.
        viewModelScope.launch {
            //val categories = vocabRepository.getTab1Categories() // or getTab2Categories etc.
            //_uiState.update { it.copy(isLoading = false, categories = categories) }
        }
    }

    fun loadContentForTab(tabIdentifier: String, voiceName: String) {
        if (!_uiState.value.isLoading && _uiState.value.categories.isNotEmpty()) return

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val categories = vocabRepository.getCategoriesForTab(tabIdentifier)

            if (categories.isNotEmpty()) {
                // --- THIS IS THE NEW LOGIC ---
                // After getting the categories, ask the repository to check the disk cache for them.
                val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
                val cachedKeys = vocabRepository.getWordKeysWithCachedAudio(categories, currentVoiceName)
                val totalWords = categories.flatMap { it.words }.size

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categories = categories,
                        // Update the newly named state with the result of the disk check.
                        wordsOnDisk = cachedKeys,
                        cachedAudioCount = cachedKeys.size,
                        totalWordsInTab = totalWords
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }


    fun onFocusClicked(word: VocabWord) {
        viewModelScope.launch {
            // We create a new, single function in RecallingItems for this
            recallingItemsManager.focusOnWord(word)
        }
    }

    fun onCancelClicked(word: VocabWord) {
        viewModelScope.launch {
            recallingItemsManager.remove(word.word)
        }
    }
    fun onRowTapped(word: VocabWord, sentence: Sentence) {


        if (_uiState.value.playbackState is PlaybackState.Playing) return

        viewModelScope.launch {

            if (!connectivityRepository.isCurrentlyOnline()) {
                // Instead of a boolean, we will emit an event
                // This is a common pattern to trigger one-off UI actions
                _lastFailedAction.value = LastAction(word, sentence, "selectedVoiceName")

                _uiEvent.emit(UiEvent.ShowSnackbar("No internet connection", actionLabel = "Retry" ))
                return@launch
            }

            _lastFailedAction.value = null
            val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
            val uniqueSentenceId = generateUniqueSentenceId(word, sentence,currentVoiceName)
//            _playbackState.value = PlaybackState.Playing(uniqueSentenceId)

            // Update playback state
            _uiState.update { it.copy(playbackState = PlaybackState.Playing(uniqueSentenceId)) }

            // Call your repository to play the audio
            val result = vocabRepository.playTextToSpeech(
                text = sentence.sentence,
                uniqueSentenceId = uniqueSentenceId,
                voiceName = currentVoiceName,
                languageCode = LanguageConfig.languageCode,
                onTTSApiCallStart = {
                    // This lambda is the communication channel.
                    // It will ONLY be executed by the repository if it's making a network call.
                    // NOW we show the progress indicator.
                    _uiState.update { it.copy(downloadingSentenceId = uniqueSentenceId) }
                },
                onTTSApiCallComplete = {
                    _uiState.update { it.copy(downloadingSentenceId = null) }
//                    _uiState.update { it.copy(isLoading = false) }
//                    _uiState.update { it.copy(isLoading = true,playbackState = PlaybackState.Idle) }
                }

            )

//            result.onSuccess { playbackSource ->
//                if (playbackSource == PlaybackSource.NETWORK) {
//                    // ...add the word's key to our set of cached keys to show the red dot.
//                    _uiState.update { currentState ->
//                        currentState.copy(
//                            wordsOnDisk = currentState.wordsOnDisk + word.word
//                        )
//                    }
//                }
//                _uiState.update { it.copy(playbackState = PlaybackState.Idle) }
//            }
//            result.onFailure { error ->
//                // Optionally handle the error state in the UI
//                _uiState.update { it.copy(playbackState = PlaybackState.Error(error.message ?: "Playback failed")) }
//                // After a short delay or user action, you might want to reset to Idle
//                // For now, we can just set it to Idle.
//                _uiState.update { it.copy(playbackState = PlaybackState.Idle) }
//            }
            when (result) {
                is PlaybackResult.PlayedFromNetworkAndCached -> {
                    // A new file was cached! Increment the count.
                    _uiState.update {
                        it.copy(
                            playbackState = PlaybackState.Idle,
                            // Increment the count
                            cachedAudioCount = it.cachedAudioCount + 1,
                            // Also add the word to the set of cached keys for the red dot
                            wordsOnDisk = it.wordsOnDisk + word.word
                        )
                    }
                }
                is PlaybackResult.PlayedFromCache -> {
                    // The file was already cached, just reset the playback state.
                    _uiState.update { it.copy(playbackState = PlaybackState.Idle) }
                }
                is PlaybackResult.Failure -> {
                    // Handle the error
                    _uiState.update { it.copy(playbackState = PlaybackState.Error(result.exception.message ?: "Playback failed")) }
                    // Optionally reset to Idle after a delay
                    _uiState.update { it.copy(playbackState = PlaybackState.Idle) }
                }
            }
        }
    }
}