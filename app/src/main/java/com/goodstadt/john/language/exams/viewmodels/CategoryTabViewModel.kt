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
import com.goodstadt.john.language.exams.data.PlaybackSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

// This data class represents everything the UI needs to draw itself.
data class CategoryTabUiState(
    val isLoading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val recalledWordKeys: Set<String> = emptySet(),
    val playbackState: PlaybackState = PlaybackState.Idle,
    val wordsOnDisk: Set<String> = emptySet()
   // private val recallingItemsManager: RecallingItems
)

@HiltViewModel
class CategoryTabViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val vocabRepository: VocabRepository, // For getting categories/words and playing audio
    private val recallingItemsManager: RecallingItems,
    @ApplicationContext private val context: Context,
    private val application: Application // Needed for RecallingItems SharedPreferences
) : ViewModel() {

    // This manager handles the logic for recalling items (focus/cancel)

    private val _uiState = MutableStateFlow(CategoryTabUiState())
    val uiState = _uiState.asStateFlow()

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

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categories = categories,
                        // Update the newly named state with the result of the disk check.
                        wordsOnDisk = cachedKeys
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }


    // --- User Action Handlers ---

//    fun onFocusClickedObsolete3(word: VocabWord) {
//        val key = word.word
//        if (!recallingItemsManager.amIRecalling(key)) {
//            recallingItemsManager.add(key, text = word.translation, imageId = "", additionalText = word.romanisation)
//            recallingItemsManager.recalledOK(key)
//            recallingItemsManager.save("Spanish A1Vocab")
//            _uiState.update { it.copy(recalledWordKeys = it.recalledWordKeys + key) }
//            // TODO: Add notification logic here
//        }
//    }
//    fun onFocusClickedObsolete2(word: VocabWord) {
//        recallingItemsManager.add(word.word, text = word.translation, imageId = "", additionalText = word.romanisation)
//        recallingItemsManager.recalledOK(word.word)
//        recallingItemsManager.save("SpanishA1Vocab")
//    }
//    fun onCancelClickedObsolete4(word: VocabWord) {
//        val key = word.word
//        if (recallingItemsManager.amIRecalling(key)) {
//            recallingItemsManager.remove(key)
//            recallingItemsManager.save("Spanish A1Vocab")
//            _uiState.update { it.copy(recalledWordKeys = it.recalledWordKeys - key) }
//            // TODO: Add notification/badge logic here
//        }
//    }
//    fun onCancelClickeObsoleted(word: VocabWord) {
//        recallingItemsManager.remove(word.word)
//        recallingItemsManager.save("SpanishA1Vocab")
//    }

//    fun onFocusClickedObsolete(word: VocabWord) {
//        viewModelScope.launch {
//            recallingItemsManager.add(word.word, text = word.translation, imageId = "", additionalText = word.romanisation)
//            recallingItemsManager.recalledOK(word.word)
//            // --- 2. THE FIX ---
//            val currentExamKey = userPreferencesRepository.selectedFileNameFlow.first()
//            recallingItemsManager.save(currentExamKey)
//        }
//    }
//
//    fun onCancelClickedObsolete(word: VocabWord) {
//        viewModelScope.launch {
//            recallingItemsManager.remove(word.word)
//            // --- 2. THE FIX ---
//            val currentExamKey = userPreferencesRepository.selectedFileNameFlow.first()
//            recallingItemsManager.save(currentExamKey)
//        }
//    }
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
                languageCode = LanguageConfig.languageCode
            )

            result.onSuccess { playbackSource ->
                if (playbackSource == PlaybackSource.NETWORK) {
                    // ...add the word's key to our set of cached keys to show the red dot.
                    _uiState.update { currentState ->
                        currentState.copy(
                            wordsOnDisk = currentState.wordsOnDisk + word.word
                        )
                    }
                }
                _uiState.update { it.copy(playbackState = PlaybackState.Idle) }
            }
            result.onFailure { error ->
                // Optionally handle the error state in the UI
                _uiState.update { it.copy(playbackState = PlaybackState.Error(error.message ?: "Playback failed")) }
                // After a short delay or user action, you might want to reset to Idle
                // For now, we can just set it to Idle.
                _uiState.update { it.copy(playbackState = PlaybackState.Idle) }
            }
        }
    }
}