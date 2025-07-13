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
import javax.inject.Inject

// This data class represents everything the UI needs to draw itself.
data class CategoryTabUiState(
    val isLoading: Boolean = true,
    val recalledWordKeys: Set<String> = emptySet(),
    val playbackState: PlaybackState = PlaybackState.Idle,
   // private val recallingItemsManager: RecallingItems
)

@HiltViewModel
class CategoryTabViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val vocabRepository: VocabRepository, // For getting categories/words and playing audio
    private val recallingItemsManager: RecallingItems,
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

    private fun loadContentObsolete() {
        viewModelScope.launch {
            // Load the list of categories for this tab from your repository
            // I'm assuming a function like this exists in your VocabRepository
         //   val categories = vocabRepository.getTab1Categories() // or getTab2Categories etc.

            // Load the recalled items from storage
            // TODO: Use a real exam key from user preferences
            val currentExamKey = "Spanish A1Vocab"
            //recallingItemsManager.load(currentExamKey)
            val recalledKeys = recallingItemsManager.items.value.map { it.key }.toSet()

            _uiState.update {
                it.copy(
                    isLoading = false,
//                    categories = categories,
                    recalledWordKeys = recalledKeys
                )
            }
        }
    }

    // --- User Action Handlers ---

    fun onFocusClickedObsolete(word: VocabWord) {
        val key = word.word
        if (!recallingItemsManager.amIRecalling(key)) {
            recallingItemsManager.add(key, text = word.translation, imageId = "", additionalText = word.romanisation)
            recallingItemsManager.recalledOK(key)
            recallingItemsManager.save("Spanish A1Vocab")
            _uiState.update { it.copy(recalledWordKeys = it.recalledWordKeys + key) }
            // TODO: Add notification logic here
        }
    }
    fun onFocusClickedObsolete2(word: VocabWord) {
        recallingItemsManager.add(word.word, text = word.translation, imageId = "", additionalText = word.romanisation)
        recallingItemsManager.recalledOK(word.word)
        recallingItemsManager.save("SpanishA1Vocab")
    }
    fun onCancelClickedObsolete(word: VocabWord) {
        val key = word.word
        if (recallingItemsManager.amIRecalling(key)) {
            recallingItemsManager.remove(key)
            recallingItemsManager.save("Spanish A1Vocab")
            _uiState.update { it.copy(recalledWordKeys = it.recalledWordKeys - key) }
            // TODO: Add notification/badge logic here
        }
    }
    fun onCancelClickeObsoleted(word: VocabWord) {
        recallingItemsManager.remove(word.word)
        recallingItemsManager.save("SpanishA1Vocab")
    }

    fun onFocusClicked(word: VocabWord) {
        viewModelScope.launch {
            recallingItemsManager.add(word.word, text = word.translation, imageId = "", additionalText = word.romanisation)
            recallingItemsManager.recalledOK(word.word)
            // --- 2. THE FIX ---
            val currentExamKey = userPreferencesRepository.selectedFileNameFlow.first()
            recallingItemsManager.save(currentExamKey)
        }
    }

    fun onCancelClicked(word: VocabWord) {
        viewModelScope.launch {
            recallingItemsManager.remove(word.word)
            // --- 2. THE FIX ---
            val currentExamKey = userPreferencesRepository.selectedFileNameFlow.first()
            recallingItemsManager.save(currentExamKey)
        }
    }
    fun onRowTapped(word: VocabWord, sentence: Sentence) {
        viewModelScope.launch {

            val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
            val uniqueSentenceId = generateUniqueSentenceId(word, sentence,currentVoiceName)
//            _playbackState.value = PlaybackState.Playing(uniqueSentenceId)

            // Update playback state
            _uiState.update { it.copy(playbackState = PlaybackState.Playing(uniqueSentenceId)) }

            // Call your repository to play the audio
             vocabRepository.playTextToSpeech(
                 sentence.sentence,
                 uniqueSentenceId = uniqueSentenceId,
                 voiceName = currentVoiceName,
                 languageCode = LanguageConfig.languageCode//,
                 //onTTSApiCallStart = null,
                 //onTTSApiCallComplete = null
             )

            // Update state back to idle when done
            _uiState.update { it.copy(playbackState = PlaybackState.Idle) }
        }
    }
}