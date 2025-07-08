package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// State holder for the UI
data class ParagraphUiState(
    val isLoading: Boolean = false,
    val sentence: String = "Tap 'Generate' to begin.",
    val translation: String = "",
    val highlightedWords: Set<String> = emptySet(),
    val error: String? = null
)

@HiltViewModel
class ParagraphViewModel @Inject constructor(
//    private val openAIRepository: OpenAIRepository,
    private val vocabRepository: VocabRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParagraphUiState())
    val uiState = _uiState.asStateFlow()

    /**
     * The main entry point called from the UI to generate a new paragraph.
     */
    fun generateNewParagraph() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // 1. Generate the word list from local vocab data
                val (wordList, wordsToHighlight) = generateWords()

                // 2. Get current user settings
                val languageCode = userPreferencesRepository.selectedLanguageCodeFlow.first() // Example
                val skillLevel = userPreferencesRepository.selectedSkillLevelFlow.first() // Example

                // 3. Call the LLM with the generated words
                val response = openAIRepository.generateSentenceFromWords(
                        words = wordList,
                        languageCode = languageCode,
                        skillLevel = skillLevel
                )

                // 4. Update UI with the result
                _uiState.update {
                    it.copy(
                            isLoading = false,
                            sentence = response.sentence,
                            translation = response.translation,
                            highlightedWords = wordsToHighlight
                    )
                }

            } catch (e: Exception) {
                // 5. Handle any errors
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "An unknown error occurred")
                }
            }
        }
    }

    /**
     * Generates a random list of words from the user's vocabulary data.
     * This logic is moved from the SwiftUI View into the ViewModel.
     * @return A Pair containing the comma-separated string for the prompt and a Set for highlighting.
     */
    private suspend fun generateWords(): Pair<String, Set<String>> {
        val vocabData = vocabRepository.getVocabData() // Assuming this method exists
        val wordList = mutableListOf<String>()

        // This logic mimics the SwiftUI version, getting 8 random words from 8 random categories
        if (vocabData != null) {
            val randomCategories = vocabData.categories.shuffled().take(8)
            for (category in randomCategories) {
                category.words.randomOrNull()?.let {
                    wordList.add(it.word)
                }
            }
        }

        // Fallback if no words were found
        if (wordList.isEmpty()) {
            wordList.addAll(listOf("hello", "world", "Android", "is", "fun"))
        }

        val wordsToHighlight = wordList.toSet()
        val commaSeparatedWords = wordList.joinToString(",")

        return Pair(commaSeparatedWords, wordsToHighlight)
    }

    /**
     * Plays the current sentence using the injected repository.
     */
    fun speakSentence() {
        val sentenceToSpeak = _uiState.value.sentence
        if (sentenceToSpeak.isNotBlank() && sentenceToSpeak != "Tap 'Generate' to begin.") {
            viewModelScope.launch {
                vocabRepository.playTextToSpeech(sentenceToSpeak)
            }
        }
    }
}