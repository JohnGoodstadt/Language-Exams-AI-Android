package com.goodstadt.john.language.exams.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.OpenAIRepository
import com.goodstadt.john.language.exams.data.PlaybackResult
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UserStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.managers.TokenTopUpOption
import com.goodstadt.john.language.exams.managers.TokenUsageManager
import com.goodstadt.john.language.exams.models.LlmModelInfo
import com.goodstadt.john.language.exams.models.VocabFile
import com.goodstadt.john.language.exams.models.calculateCallCost
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

//import com.goodstadt.john.language.exams.data.LlmResponse

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

//private const val TOKEN_LIMIT = 2000

@HiltViewModel
class ParagraphViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val openAIRepository: OpenAIRepository, // <-- INJECT THE REPOSITORY,
    private val userStatsRepository: UserStatsRepository,
    private val ttsStatsRepository : TTSStatsRepository,
    private val appConfigRepository: AppConfigRepository,


    ) : ViewModel() {


//    val totalTokenCount = userPreferencesRepository.totalTokenCountFlow
//        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _uiState = MutableStateFlow(ParagraphUiState(), )
    val uiState = _uiState.asStateFlow()

    private val _showTokenDialog = MutableStateFlow(false)
    val showTokenDialog = _showTokenDialog.asStateFlow()

    private val _canWait = MutableStateFlow(true)
    val canWait = _canWait.asStateFlow()

    private val _tokenBalance = MutableStateFlow(0)
    val tokenBalance = _tokenBalance.asStateFlow()

    val tokenLimit = TokenUsageManager.freeTokens
   // val tokenLimit = 4600 //gives about 20 calls

    private var retryAction: (() -> Unit)? = null

    init{
        loadLlmModels()
    }
    private fun fetchTokenBalance() {
        viewModelScope.launch {
            val balance = TokenUsageManager.getCurrentTokenBalance()
            _tokenBalance.value = balance

            Log.d("fetchTokenBalance","Balance :${_tokenBalance.value}")

        }
    }
    fun resetTokenBalanceForDebug() {
        viewModelScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .update(TokenUsageManager.firestoreCurrentToken, TokenUsageManager.freeTokens).await()
            fetchTokenBalance()
        }
    }
    fun generateNewParagraph() {
        viewModelScope.launch {


//            val currentTokenCount = userPreferencesRepository.totalTokenCountFlow.first()
//            if (currentTokenCount >= TOKEN_LIMIT) {
//                Log.w("ParagraphVM", "User has exceeded token limit of $TOKEN_LIMIT. Current: $currentTokenCount")
//                // Update the UI to show an error message
//                _uiState.update { it.copy(error = "You have reached your free generation limit.") }
//                return@launch // Stop execution immediately
//            }


           // _uiState.update { it.copy(isLoading = true, error = null) }

            val hasEnough = TokenUsageManager.checkTokenAvailability()

            if (hasEnough) {
                Log.d("ParagraphVM","We have some tokens left")
                _uiState.update { it.copy(isLoading = true, error = null) }
                generateNewParagraphInternal()
            } else {
                Log.d("ParagraphVM","No tokens left")
                _uiState.update { it.copy(isLoading = false, error = null) }
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
                val snapshot = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
                val lastTopUp = snapshot.getLong("lastTopUp") ?: 0L
                val now = System.currentTimeMillis()
                _canWait.value = now - lastTopUp >= TokenUsageManager.waitDurationMillis

                _showTokenDialog.value = true
                retryAction = { generateNewParagraph() }
            }
/*


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

                val systemMessage = LanguageConfig.LLMSystemText.replace("<skilllevel>", currentSkillLevel)

                //Do the call
                val llmResponse = openAIRepository.fetchOpenAIData(
                    llmEngine = llmEngine,
                    systemMessage = systemMessage,
                    userQuestion = userQuestion
                )

                val totalTokensUsed = llmResponse.totalTokensUsed
                val completionTokens = llmResponse.completionTokens
                val promptTokens = llmResponse.promptTokens

                if (DEBUG) {
                    val result = calculateCallCost(promptTokens, completionTokens)

                    println("Total tokens: ${result.totalTokens}")
                    println("Estimated characters (for TTS): ${result.estimatedCharacters}")
                    println("TTS cost: $${"%.6f".format(result.ttsCostUSD)}")
                    println("GPT input cost: $${"%.6f".format(result.gptInputCostUSD)}")
                    println("GPT output cost: $${"%.6f".format(result.gptOutputCostUSD)}")
                    println("Total cost: $${"%.6f".format(result.totalCostUSD)}")
                }

                userPreferencesRepository.incrementTokenCount(totalTokensUsed)
//                ttsStatsRepository.updateUserStatGPTTotalTokenCount(totalTokensUsed)

                // Phase 3: Update the UI with the response
                // Simple parsing, you can make this more robust
                val sentence = llmResponse.content.substringAfter("[").substringBefore("]")
               // val translation = llmResponse.content.substringAfter("{").substringBefore("}")


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
             */

        }
    }
    private suspend fun generateNewParagraphInternal() {
        try {
            val fileName = userPreferencesRepository.selectedFileNameFlow.first()
            val vocabFile = vocabRepository.getVocabData(fileName).getOrNull()
                ?: throw Exception("Could not load vocabulary file.")

            val wordsToHighlight = getLanguageSpecificWords(vocabFile)
            val wordsForPrompt = wordsToHighlight.joinToString(", ")
            val currentSkillLevel = userPreferencesRepository.selectedSkillLevelFlow.first()
            val llmEngine = uiState.value.currentLlmModel?.id ?: DEFAULT_GPT
            val systemMessage = LanguageConfig.LLMSystemText.replace("<skilllevel>", currentSkillLevel)
            val userQuestion = "Here is the comma delimited list of words surrounded by angled brackets <$wordsForPrompt.>"

            val llmResponse = openAIRepository.fetchOpenAIData(
                llmEngine = llmEngine,
                systemMessage = systemMessage,
                userQuestion = userQuestion
            )

            if (DEBUG) {
                val result = calculateCallCost(llmResponse.promptTokens, llmResponse.completionTokens)

                println("Total tokens: ${result.totalTokens}")
                println("Estimated characters (for TTS): ${result.estimatedCharacters}")
                println("TTS cost: $${"%.6f".format(result.ttsCostUSD)}")
                println("GPT input cost: $${"%.6f".format(result.gptInputCostUSD)}")
                println("GPT output cost: $${"%.6f".format(result.gptOutputCostUSD)}")
                println("Total cost: $${"%.6f".format(result.totalCostUSD)}")
            }

            val totalTokensUsed = llmResponse.totalTokensUsed
//            Log.d("paragraphVM","Balancec 1:${_tokenBalance.value}")
            TokenUsageManager.deductTokens(totalTokensUsed)
//            Log.d("paragraphVM","Balance 2:${_tokenBalance.value}")
            fetchTokenBalance()
//            Log.d("paragraphVM","Balance 3:${_tokenBalance.value}")

            val sentence = llmResponse.content.substringAfter("[").substringBefore("]")

            _uiState.update {
                it.copy(
                    isLoading = false,
                    generatedSentence = sentence.ifBlank { "Could not parse sentence." },
                    translation = "",
                    highlightedWords = wordsToHighlight.toSet()
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()
            _uiState.update { it.copy(isLoading = false, error = "LLM call failed. Please try again.") }
        }
    }
    fun onTokenTopUpSelected(option: TokenTopUpOption) {
        viewModelScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@launch
            val userDoc = FirebaseFirestore.getInstance().collection("users").document(uid)
            val now = System.currentTimeMillis()

            when (option) {
                TokenTopUpOption.FREE -> {
                    userDoc.update(
                        mapOf(
                            TokenUsageManager.firestoreCurrentToken to TokenUsageManager.bundle99Tokens,
                            "lastTopUp" to now
                        )
                    ).await()
                }
                TokenTopUpOption.BUY_099 -> {
                    // TODO: Implement real IAP logic
                    userDoc.update(TokenUsageManager.firestoreCurrentToken, TokenUsageManager.bundle99Tokens).await()
                }
                TokenTopUpOption.BUY_199 -> {
                    // TODO: Implement real IAP logic
                    userDoc.update(TokenUsageManager.firestoreCurrentToken, TokenUsageManager.bundle199Tokens).await()
                }
            }

            _showTokenDialog.value = false
            fetchTokenBalance()
            retryAction?.invoke()
            retryAction = null
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
                        ttsStatsRepository.updateUserTTSTokenCount(sentenceToSpeak.count())
                        Log.d("ParagraphViewModel","updateUserTTSTokenCount ${sentenceToSpeak.count()}")
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
//    fun getTokenLimit() : Int {
//        return TOKEN_LIMIT
//    }

    fun resetTokensUsed() {
        viewModelScope.launch {
            userPreferencesRepository.resetTokenCount()
        }
    }

}