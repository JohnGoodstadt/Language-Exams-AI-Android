package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.OpenAIRepository
import com.goodstadt.john.language.exams.data.PlaybackResult
import com.goodstadt.john.language.exams.data.StatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.models.VocabFile
import com.goodstadt.john.language.exams.models.WordAndSentence
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// This data class will hold all the dynamic state for our screen later.
// For now, it's not used, but it's good practice to have it ready.
data class ParagraphUiState(
    val isLoading: Boolean = false, // <-- Add this for a loading indicator
    val isSpeaking: Boolean = false,
    val generatedSentence: String = "Tap 'Generate' to begin.",
    val translation: String = "",
    val highlightedWords: Set<String> = emptySet(), // <-- ADD THIS
    val error: String? = null // <-- Add this for showing errors
)

@HiltViewModel
class ParagraphViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val openAIRepository: OpenAIRepository, // <-- INJECT THE REPOSITORY,
    private val statsRepository: StatsRepository

) : ViewModel() {

    private val _uiState = MutableStateFlow(ParagraphUiState())
    // This line exposes the stream of data for the UI to listen to.
    val uiState = _uiState.asStateFlow()

    //private val _uiState = MutableStateFlow<ConjugationsUiState>(ConjugationsUiState.Loading)
    //val uiState = _uiState.asStateFlow()

    fun generateNewParagraph() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // Phase 1: Get words from local data
                val fileName = userPreferencesRepository.selectedFileNameFlow.first()
                val vocabFile = vocabRepository.getVocabData(fileName).getOrNull()
                    ?: throw Exception("Could not load vocabulary file.")


                val wordsToHighlight = getLanguageSpecificWords(vocabFile)
                val wordsForPrompt = wordsToHighlight.joinToString(", ")

                // Phase 2: Call the OpenAI API via the repository
//                val systemMessage = "You are a helpful language teacher..." // Define your system message
                val userQuestion = "Here is the comma delimited list of words surrounded by angled brackets <$wordsForPrompt.>"


                val systemMessage = "I am learning American English and I need to learn new words in a sentence. You are a teacher of American in America, and want to help me. I will give you a few words in American in America, and you will construct simple sentences using these words in any order. Do not give any extra words than the text you send back. Put the English response in square brackets []. give me a paragraph of text including the list of words at the level of A1. try to make the paragraph sensible. Fill between these words with verbs, adjectives, prepositions, other nouns etc at the level of A1. "
                val llmResponse = openAIRepository.fetchOpenAIData(
                        llmEngine = "gpt-4.1", // or another model
                        systemMessage = systemMessage,
                        userQuestion = userQuestion
                )

                // Phase 3: Update the UI with the response
                // Simple parsing, you can make this more robust
                val sentence = llmResponse.content.substringAfter("[").substringBefore("]")
                val translation = llmResponse.content.substringAfter("{").substringBefore("}")


                // --- CHANGE 2: Pass the highlightedWords to the state ---
                _uiState.update {
                    it.copy(
                            isLoading = false,
                            generatedSentence = sentence.ifBlank { "Could not parse sentence." },
                            translation = "",//translation.ifBlank { "Could not parse translation." },
                            highlightedWords = wordsToHighlight.toSet() // <-- SET THE WORDS HERE
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
    // We will add a StateFlow for the UI state here later.
    // For now, the ViewModel is empty as per the request.
    // Add this private function inside your ParagraphViewModel class

    /**
     * Replicates the Swift logic to get a list of random words, one from each of 8 random categories.
     * This version uses idiomatic Kotlin collection functions for conciseness and efficiency.
     * @param vocabFile The fully parsed vocabulary data.
     * @return A list of word strings.
     */
    private fun getLanguageSpecificWords(vocabFile: VocabFile): List<String> {
        // If there are no categories, return the default list immediately.
        if (vocabFile.categories.isEmpty()) {
            return listOf("hello", "I", "Father", "red", "breakfast", "how")
        }

        // This is the entire logic in a single, expressive chain:
        return vocabFile.categories
            .shuffled() // 1. Shuffle the list of Category objects randomly.
            .take(8)      // 2. Take the first 8 random categories from the shuffled list.
            .mapNotNull { category -> // 3. Transform each category into a word, discarding any that are empty.
                category.words.randomOrNull()?.word
            }
    }
    /**
     * Plays the audio for the current sentence by delegating to the VocabRepository.
     */
    fun speakSentence() {
        val sentenceToSpeak = _uiState.value.generatedSentence


        // Don't try to play placeholder text
        if (sentenceToSpeak.isBlank() || sentenceToSpeak == "Tap 'Generate' to begin." || _uiState.value.isSpeaking) {
            return // Prevent multiple clicks or playing placeholder text
        }

        viewModelScope.launch {

            try {
                val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
                val currentLanguageCode = LanguageConfig.languageCode

                val uniqueSentenceId = generateUniqueSentenceId(sentenceToSpeak,currentVoiceName)

                // --- CHANGE 2: The logic is now a single, clean call ---
                val result = vocabRepository.playTextToSpeech(
                        text = sentenceToSpeak,
                        uniqueSentenceId = uniqueSentenceId,
                        voiceName = currentVoiceName,
                        languageCode = currentLanguageCode,
                        onTTSApiCallStart = {
                            // This lambda is the communication channel.
                            // It will ONLY be executed by the repository if it's making a network call.
                            // NOW we show the progress indicator.
                            _uiState.update { it.copy(isLoading = true) }
                        },
                        onTTSApiCallComplete = {
                            _uiState.update { it.copy(isLoading = false) }
                        }
                )
//                result.onSuccess {
//                    //TODO: Do I want to save the paragraph?
//                    //statsRepository.fsUpdateSentenceHistoryIncCount(WordAndSentence(word.word, sentence.sentence))
//                }
//                result.onFailure { error ->
//                    _uiState.update { it.copy(error = "Text-to-speech failed: ${error.message}") }
//                }
                when (result) {
                    is PlaybackResult.PlayedFromNetworkAndCached -> {
                        // A new file was cached! Increment the count.
//                        _uiState.update {
//                            it.copy(
//                                playbackState = PlaybackState.Idle,
//                                // Increment the count
//                                cachedAudioCount = it.cachedAudioCount + 1,
//                                // Also add the word to the set of cached keys for the red dot
//                                wordsOnDisk = it.wordsOnDisk + word.word
//                            )
//                        }
                    }
                    is PlaybackResult.PlayedFromCache -> {
                        // The file was already cached, just reset the playback state.
//                        _uiState.update { it.copy(playbackState = PlaybackState.Idle) }
                    }
                    is PlaybackResult.Failure -> {
                        // Handle the error
//                        _uiState.update { it.copy(playbackState = PlaybackState.Error(result.exception.message ?: "Playback failed")) }
                        // Optionally reset to Idle after a delay
                        //_uiState.update { it.copy(playbackState = PlaybackState.Idle) }
                        _uiState.update { it.copy(error = "Text-to-speech failed: ${result.exception.message ?: "Playback failed"}") }
                    }
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }

        }
    }
}