package com.goodstadt.john.language.exams.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.BillingRepository
import com.goodstadt.john.language.exams.data.CreditSystemConfig
import com.goodstadt.john.language.exams.data.CreditsRepository
import com.goodstadt.john.language.exams.data.GeminiRepository
import com.goodstadt.john.language.exams.data.LLMProvider
import com.goodstadt.john.language.exams.data.LLMProviderManager
import com.goodstadt.john.language.exams.data.OpenAIRepository
import com.goodstadt.john.language.exams.data.PlaybackResult
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import com.goodstadt.john.language.exams.data.TTSStatsRepository.Companion.GeminiEstCostUSD
import com.goodstadt.john.language.exams.data.TTSStatsRepository.Companion.OpenAIEstCostUSD
import com.goodstadt.john.language.exams.data.TTSStatsRepository.Companion.llmModel_
import com.goodstadt.john.language.exams.data.UserCredits
import com.goodstadt.john.language.exams.data.UserStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.managers.RateLimiterManager
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.models.LlmModelInfo
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.VocabFile
import com.goodstadt.john.language.exams.models.calculateCallCost
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.google.ai.client.generativeai.type.GenerateContentResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject



// Data class to hold the parsed response, matching the Swift LLMResponse
data class LLMResponseObsolete(
    val content: String,
    val totalTokens: Int,
    val model: String
)

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
    val availableOpenAIModels: List<LlmModelInfo> = emptyList(),
    val currentOpenAIModel: LlmModelInfo? = null,

    val lastUsedLLMModel: String = "",

    val availableGeminiModels: List<LlmModelInfo> = emptyList(),
    val currentGeminiModel: LlmModelInfo? = null,

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
    private val geminiRepository: GeminiRepository,
    private val providerManager: LLMProviderManager,
    private val appScope: CoroutineScope,
    private val billingRepository: BillingRepository

    ) : ViewModel() {

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    private val _uiState = MutableStateFlow(  ParagraphUiState())
    val uiState = _uiState.asStateFlow()

    //NOTE: rate Limiting
    private val rateLimiter = RateLimiterManager.getInstance()

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
            Timber.d("calling initialFetchAndSetupCredits()",)
            val result = creditsRepository.initialFetchAndSetupCredits()
            result.onFailure { error ->
                _uiState.update { it.copy(
                    error = "Could not initialize user credits.",
                    areCreditsInitialized = true // Unblock UI even on failure
                )}
                Timber.e("Failed to setup free tier", error)
            }
            result.onSuccess {
                Timber.e("initialFetchAndSetupCredits OK",)
                Timber.d("see if still in countdown?",)
                Timber.e("freeTierCredits:${creditsRepository.freeTierCredits.value}",)
                Timber.e("nextCreditRefillDate:${creditsRepository.nextCreditRefillDate.value}",)
                Timber.e("llmCurrentCredit:${uiState.value.userCredits.current}")
                Timber.e("llmNextCreditRefill:${uiState.value.userCredits.llmNextCreditRefill.toDate()}")
                Timber.e("waitingForCredits:${uiState.value.waitingForCredits}")

                if (uiState.value.userCredits.current <= 0){
//                    val targetDate: Date = uiState.value.userCredits.llmNextCreditRefill.toDate()
                    val targetDate: Date = creditsRepository.nextCreditRefillDate.value ?: Date()
                    val now = Date()
//
                    Timber.v("comparing  and $now and $targetDate")
                    if (now.before(targetDate)) {
                        // ts is in the past
                        Timber.v("Still counting down")
                        _uiState.update { it.copy(waitingForCredits = true,areCreditsInitialized = true ) }
                        var userCredits = uiState.value.userCredits
//                        userCredits.llmNextCreditRefill = Timestamp((0,0))
                        val nd = creditsRepository.nextCreditRefillDate
                        val td = targetDate
                        creditsRepository.setNextCreditRefillDate(targetDate)

                    } else {
                        Timber.v("Expired counting")
                    }
                }
            }
        }

        viewModelScope.launch {
            billingRepository.isPurchased.collect { purchasedStatus ->
                // This block runs AUTOMATICALLY whenever the value in the
                // BillingRepository's 'isPurchased' flow changes.
                _isPremiumUser.value = purchasedStatus
                if (DEBUG) {
                    billingRepository.logCurrentStatus()
                }
            }
        }

    }
    //private val _uiState = MutableStateFlow<ConjugationsUiState>(ConjugationsUiState.Loading)
    //val uiState = _uiState.asStateFlow()

    fun generateNewParagraph() {
        viewModelScope.launch {
            val currentState = _uiState.value

            if (isPremiumUser.value) {
                Timber.i("generateNewParagraph() User is a paid user !")
            }else{
                Timber.i("generateNewParagraph() User is a FREE user")
            }



            val providerToUse = providerManager.getNextProviderAndIncrement()
            Timber.w("$providerToUse")

            // --- THE NEW, ROBUST CHECK ---
            // 1. Wait until credits are initialized.
            // 2. Then check if the count is zero or less.
            if (currentState.areCreditsInitialized && currentState.userCredits.current <= 0) {
                Timber.w("User is out of credits. Showing sheet.")
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
                Timber.w("User is out of credits. Showing sheet.")
                _uiState.update { it.copy(waitingForCredits = true) }
                return@launch
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
                val systemMessage = LanguageConfig.LLMSystemText.replace("<skilllevel>", currentSkillLevel)


                val openAIModel = _uiState.value.currentOpenAIModel
                val llmEngine =  _uiState.value.currentOpenAIModel?.id ?: DEFAULT_GPT
                Timber.d("$llmEngine skill:$currentSkillLevel")
                Timber.d("$systemMessage")
                Timber.d("$userQuestion")

                _uiState.update { it.copy(isLoading = true, error = null,lastUsedLLMModel = openAIModel?.title ?: "Unknown Open AI Model") }

               // val p = providerManager.getCurrentProviderInfo()
                when(providerToUse) {
                    LLMProvider.OpenAI -> { /* ... */

                        val llmResponse = openAIRepository.fetchOpenAIData(
                            llmEngine = llmEngine,
                            systemMessage = systemMessage,
                            userQuestion = userQuestion
                        )

                        val result = calculateCallCost(llmResponse.promptTokens, llmResponse.completionTokens)
                        val totalCostUSD = result.totalCostUSD

                        if (BuildConfig.DEBUG) {
                            Timber.d(llmResponse.content)
                            Timber.v("Total tokens: ${result.totalTokens}")
                            Timber.v("Estimated characters (for TTS): ${result.estimatedCharacters}")
                            Timber.v("LLM cost: $${"%.6f".format(result.gptEstCallCostUSD)}")
                            Timber.v("GPT input cost: $${"%.6f".format(result.gptInputCostUSD)}")
                            Timber.v("GPT output cost: $${"%.6f".format(result.gptOutputCostUSD)}")
                            Timber.v("Total cost: $${"%.6f".format(totalCostUSD)}")
                        }

                        //TODO: update gpt call costs
                        val totalTokensUsed = llmResponse.totalTokensUsed
                        ttsStatsRepository.updateUserOpenAITotalTokenCount(totalTokensUsed)
                        ttsStatsRepository.updateUserStatDouble(OpenAIEstCostUSD,totalCostUSD)
                        val modelFieldName = "${llmModel_}${openAIModel?.title}" //e.g. llmModel_gemini-2.5-flash
                        ttsStatsRepository.updateUserStatField(modelFieldName)


                        creditsRepository.decrementCredit(
                            llmResponse.promptTokens,
                            llmResponse.completionTokens,
                            totalTokensUsed
                        )

                        Timber.v("current credits B: ${_uiState.value.userCredits.current}")
                        if (_uiState.value.userCredits.current <= 0){
                            val FRED = secondsRemaining()
                            Timber.v("seconds to go: $FRED")

                        }

                        // Phase 3: Update the UI with the response
                        // Simple parsing, you can make this more robust
                        val sentence = llmResponse.content.substringAfter("[").substringBefore("]").replace(Regex("[<>]"), "")

                        if (DEBUG){
                            Timber.i("${sentence}")
                        }

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
                    } //: OpenAI call
                    LLMProvider.Gemini -> { /* ... */

                        try {

                            val selectedModel = _uiState.value.currentGeminiModel ?: return@launch

                            _uiState.update { it.copy(isLoading = true,lastUsedLLMModel = selectedModel.title) }

                            val prompt = promptForLLM(vocabFile,currentSkillLevel)

                            val result = geminiRepository.generateContent(prompt, selectedModel.id)

                            result.onSuccess { response: GenerateContentResponse ->
                                val generatedText = response.text ?: "No text was generated due to safety filters or an error."

                                val usageMetadata = response.usageMetadata
                                if (usageMetadata != null) {
                                    val inputTokens = usageMetadata.promptTokenCount
                                    val outputTokens = usageMetadata.candidatesTokenCount
                                    val totalTokenCount = usageMetadata.totalTokenCount

                                    Timber.d("Input Tokens: $inputTokens, Output Tokens: $outputTokens totalTokenCount: $totalTokenCount")

                                    val cost = geminiRepository.calculateGeminiCallCost(
                                        inputTokens = inputTokens,
                                        outputTokens = outputTokens,
                                        inputPricePerMillion = uiState.value.currentGeminiModel?.inputPrice ?: 0.0F,
                                        outputPricePerMillion = uiState.value.currentGeminiModel?.outputPrice ?: 0.0F
                                    )

                                    if (BuildConfig.DEBUG) {
                                        Timber.v("Total tokens: ${cost.totalTokens}")
                                        Timber.v("Input tokens: ${cost.inputTokens}")
                                        Timber.v("Output tokens: ${cost.outputTokens}")
                                        Timber.v("GPT input cost: $${"%.6f".format(cost.gptInputCostUSD)}")
                                        Timber.v("GPT output cost: $${"%.6f".format(cost.gptOutputCostUSD)}")
                                        Timber.v("Total cost: $${"%.6f".format(cost.totalCostUSD)}")
                                    }

                                    Timber.v("current credits A: ${_uiState.value.userCredits.current}")
                                    if (_uiState.value.userCredits.current <= 0){
                                        val FRED = secondsRemaining()
                                        Timber.v("seconds to go: $FRED")

                                    }

                                    val modelFieldName = "${llmModel_}${selectedModel.title}" //e.g. llmModel_gemini-2.5-flash
                                    ttsStatsRepository.updateUserStatField(modelFieldName)
                                    ttsStatsRepository.updateUserGeminiTotalTokenCount(totalTokenCount)
                                    ttsStatsRepository.updateUserStatDouble(GeminiEstCostUSD, cost.totalCostUSD.toDouble())


                                    creditsRepository.decrementCredit(
                                        inputTokens,
                                        outputTokens,
                                        totalTokenCount
                                    )

                                    val sentence = generatedText.substringAfter("[").substringBefore("]").replace(Regex("[<>]"), "")

                                    _uiState.update {
                                        it.copy(
                                            isLoading = false,
                                            generatedSentence = sentence, //"No text was generated.",
                                            translation = "",//translation.ifBlank { "Could not parse translation." },
                                            highlightedWords = wordsToHighlight.toSet(),
                                            waitingForCredits = uiState.value.userCredits.current <= 0 //trigger countdown
                                        )
                                    }

                                }
                                else{ //should not happen
                                    Timber.w("Usage metadata was null in the response.")
                                    _uiState.update {
                                        it.copy(
                                            isLoading = false,
                                            generatedSentence = generatedText
                                        )
                                    }
                                }
                                // Update the state with the successful response

                            }
                            result.onFailure { e ->
                                Timber.e(e.localizedMessage)
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        error = e.localizedMessage ?: "An unknown error occurred."
                                    )
                                }
                                e.printStackTrace()
                            }



                        } catch (e: Exception) {
                            // Update the state with the error message
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = e.localizedMessage ?: "An unknown error occurred."
                                )
                            }
                            e.printStackTrace()
                        }
                    }
                }



            } catch (e: Exception) {
                e.printStackTrace()
//                _uiState.update { it.copy(isLoading = false, error = e.message) }
                _uiState.update { it.copy(isLoading = false, error = "LLM call failed. Please try again.") }
            }
        }
    }
    // --- Functions to be called from the Bottom Sheet ---

    fun promptForLLM(vocabFile:VocabFile,skillLevel:String) : String {

//        try {
        val wordsToHighlight = getLanguageSpecificWords(vocabFile)
        val wordsForPrompt = wordsToHighlight.joinToString(", ")

        // Phase 2: Call the OpenAI API via the repository
//                val systemMessage = "You are a helpful language teacher..." // Define your system message
        val userQuestion =  "Here is the comma delimited list of words surrounded by angled brackets <$wordsForPrompt.>"
        val systemMessage =  LanguageConfig.LLMSystemText.replace("<skilllevel>", skillLevel)

        val prompt = "$systemMessage$userQuestion"
        return prompt

//        } catch (e: Exception) {
//            Timber.e("${e.localizedMessage}")
//            return ""
//        }


    }

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

        //For Paragraph screen don't deny rateLimiting - credits will do that
        if (!isPremiumUser.value) { //if premium user don't check credits
            if (rateLimiter.doIForbidCall()){
                val failType = rateLimiter.canMakeCallWithResult()
                Timber.v("${failType.canICallAPI}")
                Timber.v("${failType.failReason}")
                Timber.v("${failType.timeLeftToWait}")
                if (!failType.canICallAPI){
                    if (failType.failReason == SimpleRateLimiter.FailReason.DAILY){
                        Timber.v("User would fail DAILY rate limiting")
                    }else {
                        Timber.v("User would fail HOURLY rate limiting")
                    }
                }

                return
            }
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
                            Timber.d("onTTSApiCallStart()")
                            _uiState.update { it.copy(isLoading = true) }
                        },
                        onTTSApiCallComplete = {
                            Timber.d("onTTSApiCallComplete()")
                            _uiState.update { it.copy(isLoading = false) }
                        }
                )

                when (result) {
                    is PlaybackResult.PlayedFromNetworkAndCached -> {
                        //NOTE: record call but don't disallow on Paragraph screen
                        rateLimiter.recordCall()
                        Timber.v(rateLimiter.printCurrentStatus)
                        ttsStatsRepository.updateTTSStatsWithCosts(Sentence(sentenceToSpeak,""), currentVoiceName)
                        ttsStatsRepository.incWordStats(sentenceToSpeak)
                        Timber.d("updateUserTTSTokenCount ${sentenceToSpeak.count()}")
                    }
                    is PlaybackResult.PlayedFromCache -> {
                        ttsStatsRepository.updateTTSStatsWithoutCosts()
                        ttsStatsRepository.incWordStats(sentenceToSpeak)
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

    private fun loadLlmModels() {
        viewModelScope.launch {
            val models = appConfigRepository.getAvailableOpenAIModels()
            _uiState.update {
                it.copy(
                    availableOpenAIModels = models,
                    currentOpenAIModel = models.find { it.isDefault } ?: models.firstOrNull()
                )
            }

            val geminiModels = appConfigRepository.getAvailableGeminiModels()
            _uiState.update {
                it.copy(
                    availableGeminiModels = geminiModels,
                    currentGeminiModel = geminiModels.find { it.isDefault } ?: geminiModels.firstOrNull()
                )
            }

            Timber.d("AI Model: ${uiState.value.currentOpenAIModel?.title} Gemini Model: ${_uiState.value.currentGeminiModel?.title}")

        }
    }

    fun stopPlayback() {
        Timber.d("ViewModel cleared. Stopping audio playback.")
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
        if (false) {
            appScope.launch {
                if (ttsStatsRepository.checkIfStatsFlushNeeded(forced = true)) {
                    ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.TTSStats)
                    ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
                }
            }
        }
    }


    //from swift
    fun secondsRemaining(now: Date = Date()): Int {
//        val NCRD = _uiState.value.userCredits.llmNextCreditRefill
        val NCRD = creditsRepository.nextCreditRefillDate.value

        Timber.v("secondsRemaining.nextCreditRefillDate: $NCRD ")
        val target = NCRD ?: return 0
        Timber.d("${((target.time - now.time) / 1000).coerceAtLeast(0).toInt()}")
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
        Timber.v("formatDateSmart: $date")
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