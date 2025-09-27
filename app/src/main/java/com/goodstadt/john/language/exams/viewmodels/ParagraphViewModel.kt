package com.goodstadt.john.language.exams.viewmodels

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.BillingRepository
import com.goodstadt.john.language.exams.data.ConnectivityRepository
import com.goodstadt.john.language.exams.data.CreditSystemConfig
import com.goodstadt.john.language.exams.data.CreditsRepository
import com.goodstadt.john.language.exams.data.FirestoreRepository
import com.goodstadt.john.language.exams.data.GeminiRepository
import com.goodstadt.john.language.exams.data.LLMProvider
import com.goodstadt.john.language.exams.data.LLMProviderManager
import com.goodstadt.john.language.exams.data.OpenAIRepository
import com.goodstadt.john.language.exams.data.PlaybackResult
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import com.goodstadt.john.language.exams.data.TTSStatsRepository.Companion.GeminiEstCostUSD
import com.goodstadt.john.language.exams.data.TTSStatsRepository.Companion.GeminiPremiumCallCount
import com.goodstadt.john.language.exams.data.TTSStatsRepository.Companion.OpenAIEstCostUSD
import com.goodstadt.john.language.exams.data.TTSStatsRepository.Companion.OpenAIPremiumCallCount
import com.goodstadt.john.language.exams.data.TTSStatsRepository.Companion.currentGoogleVoiceName
import com.goodstadt.john.language.exams.data.TTSStatsRepository.Companion.llmModel_
import com.goodstadt.john.language.exams.data.UserCredits
import com.goodstadt.john.language.exams.data.UserStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
//import com.goodstadt.john.language.exams.managers.RateLimiterManager
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.models.LlmModelInfo
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.VocabFile
import com.goodstadt.john.language.exams.models.calculateCallCost
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.google.ai.client.generativeai.type.GenerateContentResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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
//data class LLMResponseObsolete(
//    val content: String,
//    val totalTokens: Int,
//    val model: String
//)

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
    val userCredits: UserCredits = UserCredits(), // To display current credits

    val showIAPBottomSheet: Boolean = false, //IAP Info sheet
    val hourlyLimit: Int = 0,
    val dailyLimit: Int = 0,
)

private const val TOKEN_LIMIT = 2000

//sealed interface UiEvent {
//    data class ShowSnackbar(val message: String, val actionLabel: String? = null) : UiEvent
//    // You can add other one-off events here later
//}
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
    private val billingRepository: BillingRepository,
    private val firestoreRepository: FirestoreRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val connectivityRepository: ConnectivityRepository,

    ) : ViewModel() {

    val isPurchased = billingRepository.isPurchased
    val productDetails = billingRepository.productDetails
    val billingError = billingRepository.billingError

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    private val _uiState = MutableStateFlow(  ParagraphUiState())
    val uiState = _uiState.asStateFlow()


    //NOTE: rate Limiting
//    private val rateLimiter = RateLimiterManager.getInstance()

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

            val result = creditsRepository.initialFetchAndSetupCredits()
            result.onFailure { error ->
                _uiState.update { it.copy(
                    error = "Could not initialize user credits.",
                    areCreditsInitialized = true // Unblock UI even on failure
                )}
                Timber.e("Failed to setup free tier", error)
            }
            result.onSuccess {
                Timber.d("initialFetchAndSetupCredits OK")
                Timber.d("see if still in countdown?",)
                Timber.d("freeTierCredits:${creditsRepository.freeTierCredits.value}",)
                Timber.d("nextCreditRefillDate:${creditsRepository.nextCreditRefillDate.value}",)
                Timber.d("llmCurrentCredit:${uiState.value.userCredits.current}")
                Timber.d("llmNextCreditRefill:${uiState.value.userCredits.llmNextCreditRefill.toDate()}")
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

        viewModelScope.launch {
            delay(3000) //  wait until page has loaded
            if (!connectivityRepository.isCurrentlyOnline()) {
                _uiState.update { it.copy(error = "No internet connection. Please check your network and try again." ) }
                return@launch
            }
        }

        updateRateLimiterState()
    }

    private fun updateRateLimiterState() {
        _uiState.update {
            it.copy(
                hourlyLimit = rateLimiter.hourlyLimit,
                dailyLimit = rateLimiter.dailyLimit
            )
        }
    }

    fun generateNewParagraph() {

        vocabRepository.stopPlayback()

        if (!connectivityRepository.isCurrentlyOnline()) {
            _uiState.update { it.copy(error = "No internet connection. Please check your network and try again." ) }
            return
        }

        viewModelScope.launch {
            val currentState = _uiState.value

            val providerToUse = providerManager.getNextProviderAndIncrement()
            Timber.w("providerToUse:$providerToUse")


            if (!isPurchased.value){
                // --- THE NEW, ROBUST CHECK ---
                // 1. Wait until credits are initialized.
                // 2. Then check if the count is zero or less.
                if (currentState.areCreditsInitialized && currentState.userCredits.current <= 0) {
                    Timber.w("User is out of credits. Showing sheet.")
                    _uiState.update { it.copy(waitingForCredits = true,generatedSentence = "") }
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
                    _uiState.update { it.copy(waitingForCredits = true,generatedSentence = "") }
                    return@launch
                }
            }else{
                Timber.w("User is a Premium user")
            }


            val currentSkillLevel = userPreferencesRepository.selectedSkillLevelFlow.first()

            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // Phase 1: Get words from local data
                val fileName = userPreferencesRepository.selectedFileNameFlow.first()
                val vocabFile = vocabRepository.getVocabData(fileName).getOrNull()
                    ?: throw Exception("Could not load vocabulary file.")



                _uiState.update { it.copy(isLoading = true, error = null) }

                when(providerToUse) {
                    LLMProvider.OpenAI -> { /* ... */

                        Timber.e("Using OpenAi")

                        val wordsToHighlight = getLanguageSpecificWords(vocabFile,currentSkillLevel)
                        val wordsForPrompt = wordsToHighlight.joinToString(", ")
                        Timber.w("wordsToHighlight:$wordsToHighlight")
                        // Phase 2: Call the OpenAI API via the repository
//                val systemMessage = "You are a helpful language teacher..." // Define your system message
                        val userQuestion = "Here is the comma delimited list of words surrounded by angled brackets <$wordsForPrompt.>"
                        val systemMessage = LanguageConfig.LLMSystemText.replace("<skilllevel>", currentSkillLevel)

                        Timber.w(systemMessage)
                        Timber.w(userQuestion)

                        val openAIModel = _uiState.value.currentOpenAIModel
                        val llmEngine =  _uiState.value.currentOpenAIModel?.id ?: DEFAULT_GPT
                        Timber.w("$llmEngine skill:$currentSkillLevel")
                        _uiState.update { it.copy(lastUsedLLMModel = openAIModel?.title ?: "Unknown Open AI Model") }

                        val llmResponse = openAIRepository.fetchOpenAIData(
                            llmEngine = llmEngine,
                            systemMessage = systemMessage,
                            userQuestion = userQuestion
                        )

                        Timber.w(llmResponse.content)
                        val result = calculateCallCost(llmResponse.promptTokens, llmResponse.completionTokens)
                        val totalCostUSD = result.totalCostUSD

                        if (DEBUG) {
                            Timber.d(llmResponse.content)
                            Timber.v("Total tokens: ${result.totalTokens}")
                            Timber.v("Estimated characters (for TTS): ${result.estimatedCharacters}")
                            Timber.v("LLM cost: $${"%.6f".format(result.gptEstCallCostUSD)}")
                            Timber.v("GPT input cost: $${"%.6f".format(result.gptInputCostUSD)}")
                            Timber.v("GPT output cost: $${"%.6f".format(result.gptOutputCostUSD)}")
                            Timber.v("Total cost: $${"%.6f".format(totalCostUSD)}")
                        }


                        val totalTokensUsed = llmResponse.totalTokensUsed
                        ttsStatsRepository.uncUserOpenAITotalTokenCount(totalTokensUsed)
                        ttsStatsRepository.incUserStatDouble(OpenAIEstCostUSD,totalCostUSD)
                        val modelFieldName = "${llmModel_}${openAIModel?.title}" //e.g. llmModel_gemini-2.5-flash
                        ttsStatsRepository.updateUserStatField(modelFieldName)


                        if (!isPurchased.value) {
                            creditsRepository.decrementCredit(
                                llmResponse.promptTokens,
                                llmResponse.completionTokens,
                                totalTokensUsed
                            )

                            Timber.w("current credits: ${_uiState.value.userCredits.current} out of ${_uiState.value.userCredits.total}")
                            if (_uiState.value.userCredits.current <= 0){
                                _uiState.update { it.copy(generatedSentence = "") } //spare space ty show messages
                                Timber.w("seconds to go: ${secondsRemaining()}")
                            }

                        }else{
                            ttsStatsRepository.updateUserStatField(OpenAIPremiumCallCount)
                        }



                        // Phase 3: Update the UI with the response
                        // Simple parsing, you can make this more robust
                        // Sometimes LLM returns words surrounded by **
                        val sentence = llmResponse.content.substringAfter("[").substringBefore("]").replace(Regex("[<>]"), "").replace("*", "")

                        if (DEBUG){
                            Timber.i(sentence)
                        }
                        if(DEBUG) {
                            ttsStatsRepository.updateParagraph(sentence, openAIModel?.title ?: "Unknown Open AI Model", currentSkillLevel )
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

                            Timber.e("Using Gemini")
                            val selectedModel = _uiState.value.currentGeminiModel ?: return@launch

                            Timber.w("$selectedModel skill:$currentSkillLevel")

                            _uiState.update { it.copy(isLoading = true,lastUsedLLMModel = selectedModel.title) }

                            //val prompt = promptForLLM(vocabFile,currentSkillLevel)
                            val wordsToHighlight = getLanguageSpecificWords(vocabFile,currentSkillLevel)
                            val wordsForPrompt = wordsToHighlight.joinToString(", ")
                            Timber.w("wordsToHighlight:$wordsToHighlight")
                            val userQuestion =  "Here is the comma delimited list of words surrounded by angled brackets <$wordsForPrompt.>"
                            val systemMessage =  LanguageConfig.LLMSystemText.replace("<skilllevel>", currentSkillLevel)
                            val prompt = "$systemMessage$userQuestion"

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

                                    if (DEBUG) {
                                        Timber.v("Total tokens: ${cost.totalTokens}")
                                        Timber.v("Input tokens: ${cost.inputTokens}")
                                        Timber.v("Output tokens: ${cost.outputTokens}")
                                        Timber.v("GPT input cost: $${"%.6f".format(cost.gptInputCostUSD)}")
                                        Timber.v("GPT output cost: $${"%.6f".format(cost.gptOutputCostUSD)}")
                                        Timber.v("Total cost: $${"%.6f".format(cost.totalCostUSD)}")
                                    }

//                                    Timber.w("current credits: ${_uiState.value.userCredits.current} out of ${_uiState.value.userCredits.total}")
//                                    if (_uiState.value.userCredits.current <= 0){
//                                        Timber.v("seconds to go: ${secondsRemaining()}")
//                                    }

                                    val modelFieldName = "${llmModel_}${selectedModel.title}" //e.g. llmModel_gemini-2.5-flash
                                    ttsStatsRepository.updateUserStatField(modelFieldName)
                                    ttsStatsRepository.incUserGeminiTotalTokenCount(totalTokenCount)
                                    ttsStatsRepository.incUserStatDouble(GeminiEstCostUSD, cost.totalCostUSD.toDouble())

                                    if (!isPurchased.value) {
                                        creditsRepository.decrementCredit(
                                            inputTokens,
                                            outputTokens,
                                            totalTokenCount
                                        )

                                        Timber.w("current credits: ${_uiState.value.userCredits.current} out of ${_uiState.value.userCredits.total}")
                                        if (_uiState.value.userCredits.current <= 0){
                                            _uiState.update { it.copy(generatedSentence = "") }
                                            Timber.w("seconds to go: ${secondsRemaining()}")
                                        }
                                    }else{
                                        ttsStatsRepository.updateUserStatField(GeminiPremiumCallCount)
                                    }

                                    val sentence = generatedText.substringAfter("[").substringBefore("]").replace(Regex("[<>]"), "").replace("*", "")

                                    _uiState.update {
                                        it.copy(
                                            isLoading = false,
                                            generatedSentence = sentence, //"No text was generated.",
                                            translation = "",//translation.ifBlank { "Could not parse translation." },
                                            highlightedWords = wordsToHighlight.toSet(),
                                            waitingForCredits = uiState.value.userCredits.current <= 0 //trigger countdown
                                        )
                                    }

                                    if(DEBUG) {
                                        ttsStatsRepository.updateParagraph(sentence,modelFieldName,currentSkillLevel)
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
                                Timber.e("Error onFailure 1")
                                Timber.e(e.localizedMessage)
                                val rawMessage = e.localizedMessage ?: ""
                                val gptMessage : String = if (rawMessage.contains("The model is overloaded"))
                                    "The model is overloaded. Please try again later"
                                else
                                    rawMessage

                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        error = gptMessage ?: "An unknown error occurred."
                                    )
                                }
                                e.printStackTrace()
                            }



                        } catch (e: Exception) {
                            // Update the state with the error message
                            Timber.e("Error catch 2")
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
                Timber.e("Error catch 3")
                e.printStackTrace()
//                _uiState.update { it.copy(isLoading = false, error = e.message) }
                Timber.e("Error in call to openAI")
                Timber.e(e.localizedMessage)
                _uiState.update { it.copy(isLoading = false, error = "LLM call failed. Please try again.") }
//                _uiState.update { it.copy(isLoading = false, error = "LLM call failed. Please try again.${e.localizedMessage}") }
            }
        }
    }
    // --- Functions to be called from the Bottom Sheet ---

    private fun promptForLLM(vocabFile:VocabFile, skillLevel:String) : String {

//        try {
        val wordsToHighlight = getLanguageSpecificWords(vocabFile,skillLevel)
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
    private fun getLanguageSpecificWords(vocabFile: VocabFile, skillLevel:String): List<String> {
        // If there are no categories, return the default list immediately.
        if (vocabFile.categories.isEmpty()) {
            return listOf("hello", "I", "Father", "red", "breakfast", "how")
        }

        val wordCount = if (skillLevel.startsWith("B")) 5 else 8 //B1 or B2 smaller word list - paragraph too long

        // This is the entire logic in a single, expressive chain:
        return vocabFile.categories
            .shuffled() // 1. Shuffle the list of Category objects randomly.
            .take(wordCount)      // 2. Take the first 8 random categories from the shuffled list.
            .mapNotNull { category -> // 3. Transform each category into a word, discarding any that are empty.
                category.words.randomOrNull()?.word
            }
    }
    /**
     * Plays the audio for the current sentence by delegating to the VocabRepository.
     */
    fun speakSentence() {

        vocabRepository.stopPlayback()

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
                val currentLanguageCode =  userPreferencesRepository.selectedLanguageCodeFlow.first()

                val uniqueSentenceId = generateUniqueSentenceId(sentenceToSpeak,currentVoiceName)

                //TODO: not likely to find new sentence in cache - but user might hear it twice
                val played = vocabRepository.playFromCacheIfFound(uniqueSentenceId)
                if (played){//short cut so user cna play cached sentences with no Internet connection
                    ttsStatsRepository.updateTTSStatsWithoutCosts()
                    return@launch
                }

                if (!connectivityRepository.isCurrentlyOnline()) {
                    _uiState.update { it.copy(error = "No internet connection. Please check your network and try again." ) }
                    return@launch
                }

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
                        //wrong place to save word stats
                        //TODO: if we save paragraphs -- then do it here.
//                        ttsStatsRepository.incWordStats(sentenceToSpeak)
                        Timber.d("updateUserTTSTokenCount ${sentenceToSpeak.count()}")
                    }
                    is PlaybackResult.PlayedFromCache -> {
                        ttsStatsRepository.updateTTSStatsWithoutCosts()
                        //wrong place to save word stats
                        //TODO: if we save paragraphs -- then do it here.
//                        ttsStatsRepository.incWordStats(sentenceToSpeak)
                    }
                    is PlaybackResult.Failure -> {
                        _uiState.update { it.copy(error = "Text-to-speech failed: ${result.exception.message ?: "Playback failed"}") }
                    }
                    PlaybackResult.CacheNotFound -> Timber.e("Cache found to exist but not played")
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
        }else if(seconds > 60){
            val minutes = seconds / 60
            "%2dm".format(minutes)
        } else {
            val minutes = seconds / 60
            val secs = seconds % 60
            "%2ds".format( secs)
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
    fun buyButtonClicked() {
        _uiState.update { it.copy(showIAPBottomSheet = true) }
        firestoreRepository.fsIncUserProperty("premiumShownInfoSheet")
    }
    fun onBottomSheetDismissed() {
        _uiState.update { it.copy(showIAPBottomSheet = false) }
        firestoreRepository.fsIncUserProperty("premiumShownInfoSheetNotYet")
    }
    fun buyPremiumButtonPressed(activity: Activity) {
        Timber.i("purchasePremium()")

        viewModelScope.launch {
            billingRepository.launchPurchase(activity)
        }

        firestoreRepository.fsIncUserProperty("premiumShownBuyScreen")
    }

}