package com.goodstadt.john.language.exams.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.OpenAIRepository
import com.goodstadt.john.language.exams.data.PlaybackResult
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UserStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.models.LlmModelInfo
import com.goodstadt.john.language.exams.models.VocabFile
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


val DEFAULT_GPT = "GPT-4.1-nano"
// This data class will hold all the dynamic state for our screen later.
// For now, it's not used, but it's good practice to have it ready.
data class ParagraphUiState(
    val isLoading: Boolean = false, // <-- Add this for a loading indicator
    val isSpeaking: Boolean = false,
    val generatedSentence: String = "Tap 'Generate' to begin.",
    val translation: String = "",
    val highlightedWords: Set<String> = emptySet(), // <-- ADD THIS
    val error: String? = null, // <-- Add this for showing errors
    val availableModels: List<LlmModelInfo> = emptyList(),
    val currentLlmModel: LlmModelInfo? = null
)

private const val TOKEN_LIMIT = 1000

@HiltViewModel
class ParagraphViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val openAIRepository: OpenAIRepository, // <-- INJECT THE REPOSITORY,
    private val userStatsRepository: UserStatsRepository,
    private val ttsStatsRepository : TTSStatsRepository,
    private val appConfigRepository: AppConfigRepository,

    ) : ViewModel() {

   // private val availableModels = listOf("gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano")
//    private val availableModels99 = LlmModelInfo.entries

    val totalTokenCount = userPreferencesRepository.totalTokenCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _uiState = MutableStateFlow(
        ParagraphUiState(),

    )
    val uiState = _uiState.asStateFlow()

    init{
        loadLlmModels()
    }
    //private val _uiState = MutableStateFlow<ConjugationsUiState>(ConjugationsUiState.Loading)
    //val uiState = _uiState.asStateFlow()

    fun generateNewParagraph() {
        viewModelScope.launch {


            val currentTokenCount = userPreferencesRepository.totalTokenCountFlow.first()
            if (currentTokenCount >= TOKEN_LIMIT) {
                Log.w("ParagraphVM", "User has exceeded token limit of $TOKEN_LIMIT. Current: $currentTokenCount")
                // Update the UI to show an error message
                _uiState.update { it.copy(error = "You have reached your free generation limit.") }
                return@launch // Stop execution immediately
            }

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

                val currentSkillLevel = userPreferencesRepository.selectedSkillLevelFlow.first()

                val llmEngine =  _uiState.value.currentLlmModel?.id ?: DEFAULT_GPT
                Log.d("ParagraphViewModel","$llmEngine skill:$currentSkillLevel")

//                val systemMessage = "I am learning American English and I need to learn new words in a sentence. You are a teacher of American in America, and want to help me. I will give you a few words in American in America, and you will construct simple sentences using these words in any order. Do not give any extra words than the text you send back. Put the English response in square brackets []. give me a paragraph of text including the list of words at the level of A1. try to make the paragraph sensible. Fill between these words with verbs, adjectives, prepositions, other nouns etc at the level of A1. "
                val systemMessage = LanguageConfig.LLMSystemText.replace("<skilllevel>", currentSkillLevel)
//                val llmResponse = openAIRepository.fetchOpenAIData(
////                        llmEngine = "gpt-4.1", // or another model
//                        llmEngine = llmEngine,
//                        systemMessage = systemMessage,
//                        userQuestion = userQuestion
//                )

                val llmResponse = openAIRepository.fetchOpenAIData(
//                        llmEngine = "gpt-4.1", // or another model
                    llmEngine = llmEngine,
                    systemMessage = systemMessage,
                    userQuestion = userQuestion
                )


                userPreferencesRepository.incrementTokenCount(llmResponse.totalTokensUsed)

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
//                _uiState.update { it.copy(isLoading = false, error = e.message) }
                _uiState.update { it.copy(isLoading = false, error = "LLM call failed. Please try again.") }
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

        if (sentenceToSpeak.isBlank() || sentenceToSpeak == "Tap 'Generate' to begin." || _uiState.value.isSpeaking) {
            return // Prevent multiple clicks or playing placeholder text
        }

        viewModelScope.launch {
            try {
                val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
                val currentLanguageCode = LanguageConfig.languageCode

                val uniqueSentenceId = generateUniqueSentenceId(sentenceToSpeak,currentVoiceName)

                // --- CHANGE 2: The logic is now a single, clean call ---
                _uiState.update { it.copy(isLoading = false) }
                val result = vocabRepository.playTextToSpeech(
                        text = sentenceToSpeak,
                        uniqueSentenceId = uniqueSentenceId,
                        voiceName = currentVoiceName,
                        languageCode = currentLanguageCode,
                        onTTSApiCallStart = {
                            Log.d("ParagraphViewModel","onTTSApiCallStart()")
                            _uiState.update { it.copy(isLoading = true) }
                        },
                        onTTSApiCallComplete = {
                            Log.d("ParagraphViewModel","onTTSApiCallComplete()")
                            _uiState.update { it.copy(isLoading = false) }
                        }
                )

                when (result) {
                    is PlaybackResult.PlayedFromNetworkAndCached -> {
                        ttsStatsRepository.updateTTSStats( sentenceToSpeak,currentVoiceName)
                        ttsStatsRepository.updateUserPlayedSentenceCount()
                    }
                    is PlaybackResult.PlayedFromCache -> {
                        ttsStatsRepository.updateUserPlayedSentenceCount()
                    }
                    is PlaybackResult.Failure -> {
                        _uiState.update { it.copy(error = "Text-to-speech failed: ${result.exception.message ?: "Playback failed"}") }
                    }
                }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }

        }
    }
    /**
     * Cycles to the next available LLM engine in the list for debugging.
     */
//    fun cycleLlmEngine88() {
//        val currentState = _uiState.value
//        val currentIndex = availableModels.indexOf(currentState.currentLlmEngine)
//
//        // Use the modulus operator to wrap around to the beginning of the list
//        val nextIndex = (currentIndex + 1) % availableModels.size
//
//        val newModel = availableModels[nextIndex]
//
//        _uiState.update { it.copy(currentLlmEngine = newModel) }
//    }
    private fun loadLlmModels() {
        viewModelScope.launch {
            val models = appConfigRepository.getAvailableLlmModels()
            _uiState.update {
                it.copy(
                    availableModels = models,
                    // Set the current model to the one marked as default, or the first one
                    currentLlmModel = models.find { it.isDefault } ?: models.firstOrNull()
                )
            }
        }
    }
    fun cycleLlmEngine() {
        val currentState = _uiState.value

        // 2. Use a guard clause with the elvis operator (?:) to handle the null case.
        //    If currentLlmModel is null, just 'return' and exit the function.
        val currentModel = currentState.currentLlmModel ?: return

        // 3. Get the list of available models from the state.
        val models = currentState.availableModels

        // 4. Add another guard for the edge case of an empty list.
        if (models.isEmpty()) return

        // --- THE COMPILER ERROR IS NOW GONE ---
        // The compiler now knows that 'currentModel' is a non-nullable LlmModelInfo
        // because of the guard clause above.
        val currentIndex = models.indexOf(currentModel)

        // Check if the item was found (indexOf returns -1 if not found)
        if (currentIndex == -1) return

        // The rest of your logic is correct.
        val nextIndex = (currentIndex + 1) % models.size

        _uiState.update { it.copy(currentLlmModel = models[nextIndex]) }
    }

    fun stopPlayback() {
        Log.d("ParagraphViewModel", "ViewModel cleared. Stopping audio playback.")
        // Call the new stop function in the repository.
        vocabRepository.stopPlayback()
    }
    fun getTokenLimit() : Int {
        return TOKEN_LIMIT
    }

    fun resetTokensUsed() {
        viewModelScope.launch {
            userPreferencesRepository.resetTokenCount()
        }
    }

}