package com.goodstadt.john.language.exams.viewmodels

import android.util.Log
import android.util.Log.DEBUG
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.CreditSystemConfig
import com.goodstadt.john.language.exams.data.CreditSystemConfig.FREE_TIER_CREDITS
import com.goodstadt.john.language.exams.data.CreditsRepository
import com.goodstadt.john.language.exams.data.OpenAIRepository
import com.goodstadt.john.language.exams.data.PlaybackResult
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UserCredits
import com.goodstadt.john.language.exams.data.UserStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.models.LlmModelInfo
import com.goodstadt.john.language.exams.models.VocabFile
import com.goodstadt.john.language.exams.models.calculateCallCost
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.google.firebase.Timestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
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
    val currentLlmModel: LlmModelInfo? = null,
    val areCreditsInitialized: Boolean = false,
    val waitingForCredits: Boolean = false, // To control the bottom sheet
    val userCredits: UserCredits = UserCredits() // To display current credits
)

private const val TOKEN_LIMIT = 2000

@HiltViewModel
class ParagraphViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val openAIRepository: OpenAIRepository, // <-- INJECT THE REPOSITORY,
    private val userStatsRepository: UserStatsRepository,
    private val ttsStatsRepository : TTSStatsRepository,
    private val appConfigRepository: AppConfigRepository,
    private val creditsRepository: CreditsRepository,
    private val appScope: CoroutineScope

) : ViewModel() {

   // private val availableModels = listOf("gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano")
//    private val availableModels99 = LlmModelInfo.entries

//    val totalTokenCount = userPreferencesRepository.totalTokenCountFlow
//        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _uiState = MutableStateFlow(  ParagraphUiState())
    val uiState = _uiState.asStateFlow()

//    private val _nextCreditRefillDate = MutableStateFlow<Date?>(null)
//    val nextCreditRefillDate: StateFlow<Date?> = _nextCreditRefillDate.asStateFlow()

    init{
        loadLlmModels()

        // This listener ensures that any change to the in-memory state in the
        // repository is immediately reflected in this ViewModel's UI state.
        viewModelScope.launch {
            creditsRepository.userCreditsFlow.collect { credits ->
                // Once we receive the first valid credit object, we can consider it initialized.
                _uiState.update { it.copy(
                    userCredits = credits,
                    areCreditsInitialized = true
                )}
            }
        }

        // --- THIS IS THE UPDATED LOGIC ---
        // Call the new one-time fetch function to populate the repository's state.
        viewModelScope.launch {
            Log.d("ParagraphVM", "calling initialFetchAndSetupCredits()",)
            val result = creditsRepository.initialFetchAndSetupCredits()
            result.onFailure { error ->
                _uiState.update { it.copy(
                    error = "Could not initialize user credits.",
                    areCreditsInitialized = true // Unblock UI even on failure
                )}
                Log.e("ParagraphVM", "Failed to setup free tier", error)
            }
            result.onSuccess {
                Log.e("ParagraphVM", "initialFetchAndSetupCredits OK",)
                Log.d("ParagraphVM", "see if still in countdown?",)
                Log.e("ParagraphVM", "freeTierCredits:${creditsRepository.freeTierCredits.value}",)
                Log.e("ParagraphVM", "nextCreditRefillDate:${creditsRepository.nextCreditRefillDate.value}",)
                Log.e("ParagraphVM", "llmCurrentCredit:${uiState.value.userCredits.current}")
                Log.e("ParagraphVM", "llmNextCreditRefill:${uiState.value.userCredits.llmNextCreditRefill.toDate()}")
                Log.e("ParagraphVM", "waitingForCredits:${uiState.value.waitingForCredits}")

                if (uiState.value.userCredits.current <= 0){
//                    val targetDate: Date = uiState.value.userCredits.llmNextCreditRefill.toDate()
                    val targetDate: Date = creditsRepository.nextCreditRefillDate.value ?: Date()
                    val now = Date()
//
                    println("comparing  and $now and $targetDate")
                    if (now.before(targetDate)) {
                        // ts is in the past
                        println("Still counting down")
                        _uiState.update { it.copy(waitingForCredits = true,areCreditsInitialized = true ) }
                        var userCredits = uiState.value.userCredits
//                        userCredits.llmNextCreditRefill = Timestamp((0,0))
                        val nd = creditsRepository.nextCreditRefillDate
                        val td = targetDate
                        creditsRepository.setNextCreditRefillDate(targetDate)

                    } else {
                        println("Expired counting")
                    }
                }
            }




        }

    }
    //private val _uiState = MutableStateFlow<ConjugationsUiState>(ConjugationsUiState.Loading)
    //val uiState = _uiState.asStateFlow()

    fun generateNewParagraph() {
        viewModelScope.launch {
            val currentState = _uiState.value

            // --- THE NEW, ROBUST CHECK ---
            // 1. Wait until credits are initialized.
            // 2. Then check if the count is zero or less.
            if (currentState.areCreditsInitialized && currentState.userCredits.current <= 0) {
                Log.w("ParagraphVM", "User is out of credits. Showing sheet.")
                _uiState.update { it.copy(waitingForCredits = true) }
                return@launch
            }

            // Optionally, you can prevent generation if credits aren't initialized yet,
            // though the user would have to be incredibly fast to tap the button.
            if (!currentState.areCreditsInitialized) {
                _uiState.update { it.copy(error = "Initializing credits, please wait...") }
                return@launch
            }

            val currentCredits = _uiState.value.userCredits.current

            if (currentCredits <= 0) {
                Log.w("ParagraphVM", "User is out of credits. Showing sheet.")
                _uiState.update { it.copy(waitingForCredits = true) }
                return@launch
            }

//            val currentTokenCount = userPreferencesRepository.totalTokenCountFlow.first()
//            if (currentTokenCount >= TOKEN_LIMIT) {
//                Log.w("ParagraphVM", "User has exceeded token limit of $TOKEN_LIMIT. Current: $currentTokenCount")
//                // Update the UI to show an error message
//                _uiState.update { it.copy(error = "You have reached your free generation limit.") }
//                return@launch // Stop execution immediately
//            }

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

                val systemMessage = LanguageConfig.LLMSystemText.replace("<skilllevel>", currentSkillLevel)

                val llmResponse = openAIRepository.fetchOpenAIData(
                    llmEngine = llmEngine,
                    systemMessage = systemMessage,
                    userQuestion = userQuestion
                )

                if (BuildConfig.DEBUG) {
                    val result = calculateCallCost(llmResponse.promptTokens, llmResponse.completionTokens)

                    println("Total tokens: ${result.totalTokens}")
                    println("Estimated characters (for TTS): ${result.estimatedCharacters}")
                    println("TTS cost: $${"%.6f".format(result.ttsCostUSD)}")
                    println("GPT input cost: $${"%.6f".format(result.gptInputCostUSD)}")
                    println("GPT output cost: $${"%.6f".format(result.gptOutputCostUSD)}")
                    println("Total cost: $${"%.6f".format(result.totalCostUSD)}")
                }


                val totalTokensUsed = llmResponse.totalTokensUsed

                ttsStatsRepository.updateUserStatGPTTotalTokenCount(totalTokensUsed)

                println("current credits A: ${_uiState.value.userCredits.current}")

                creditsRepository.decrementCredit(
                    llmResponse.promptTokens,
                    llmResponse.completionTokens,
                    totalTokensUsed
                )

                println("current credits B: ${_uiState.value.userCredits.current}")
                //check seconds to go
//                val currentUIState = _uiState.value

                if (_uiState.value.userCredits.current <= 0){
                    val FRED = secondsRemaining()
                    println("seconds to go: $FRED")

                }


                // Phase 3: Update the UI with the response
                // Simple parsing, you can make this more robust
                val sentence = llmResponse.content.substringAfter("[").substringBefore("]").replace(Regex("[<>]"), "")
               // val translation = llmResponse.content.substringAfter("{").substringBefore("}")


                // --- CHANGE 2: Pass the highlightedWords to the state ---
                _uiState.update {
                    it.copy(
                            isLoading = false,
                            generatedSentence = sentence.ifBlank { "Could not parse sentence." },
                            translation = "",//translation.ifBlank { "Could not parse translation." },
                            highlightedWords = wordsToHighlight.toSet(),
                            waitingForCredits = uiState.value.userCredits.current <= 0 //trigger countdown
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
//                _uiState.update { it.copy(isLoading = false, error = e.message) }
                _uiState.update { it.copy(isLoading = false, error = "LLM call failed. Please try again.") }
            }
        }
    }
    // --- Functions to be called from the Bottom Sheet ---



    fun hideCreditsSheet() {
        _uiState.update { it.copy(waitingForCredits = false) }
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
                        ttsStatsRepository.updateUserTTSCounts(sentenceToSpeak.count())
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
            creditsRepository.purchaseCredits(CreditSystemConfig.FREE_TIER_CREDITS)
        }
    }
    fun saveDataOnExit() {
        appScope.launch {
            if (ttsStatsRepository.checkIfStatsFlushNeeded(forced = true)) {
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.TTSStats)
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
            }
        }
    }
    fun onPurchaseCredits(amount: Int) {
        viewModelScope.launch {
            creditsRepository.purchaseCredits(amount)
            hideCreditsSheet()
        }
    }





    //from swift
    fun secondsRemaining(now: Date = Date()): Int {
//        val NCRD = _uiState.value.userCredits.llmNextCreditRefill
        val NCRD = creditsRepository.nextCreditRefillDate.value

        println("secondsRemaining.nextCreditRefillDate: $NCRD ")
        val target = NCRD ?: return 0
        Log.d("PVM","${((target.time - now.time) / 1000).coerceAtLeast(0).toInt()}")
        return ((target.time - now.time) / 1000).coerceAtLeast(0).toInt()
    }
    fun formattedCountdown(now: Date = Date()): String {
        val seconds = secondsRemaining(now)
        return if (seconds >= 3600) {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            "%02dh %02dm".format(hours, minutes)
        } else {
            val minutes = seconds / 60
            val secs = seconds % 60
            "%02dm %02ds".format(minutes, secs)
        }
    }
    fun formatDateSmart(date: Date): String {
        println("formatDateSmart: $date")
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply { time = date }

        val isToday = now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)

        return if (isToday) {
            // Show only hour and minute
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            // Or for 12-hour with AM/PM: SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
        } else {
            // Show day and month
            SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)
        }
    }
    suspend fun clearWaitPeriod() {

        val freeTierCredits = UserCredits(
            current = CreditSystemConfig.FREE_TIER_CREDITS,
            total = CreditSystemConfig.FREE_TIER_CREDITS
        )

        _uiState.update {
            it.copy(waitingForCredits = false,userCredits = freeTierCredits)
        }

//        _nextCreditRefillDate.value = null

//        creditsRepository.setCredits(FREE_TIER_CREDITS)

        creditsRepository.clearWaitPeriod()
    }

    fun getFormattedCreditRepositoryDate() : String {
        return formatDateSmart(creditsRepository.nextCreditRefillDate.value ?: Date())
    }
//    fun getCreditRepositoryDate() : Date {
//        return creditsRepository.nextCreditRefillDate.value ?: Date()
//    }


}