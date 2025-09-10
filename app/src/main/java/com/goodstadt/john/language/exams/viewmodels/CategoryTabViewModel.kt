package com.goodstadt.john.language.exams.viewmodels

import android.app.Activity
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.data.BillingRepository
import com.goodstadt.john.language.exams.data.ConnectivityRepository
import com.goodstadt.john.language.exams.data.PlaybackResult
import com.goodstadt.john.language.exams.data.TTSStatsRepository
//import com.goodstadt.john.language.exams.managers.RateLimiterManager
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import javax.inject.Inject

// This data class represents everything the UI needs to draw itself.
data class CategoryTabUiState(
    val isLoading: Boolean = true,
    val categories: List<Category> = emptyList(),
    val loadedForIdentifier: String? = null, //fix guard bug - track which json is loaded
    val recalledWordKeys: Set<String> = emptySet(),
    val playbackState: PlaybackState = PlaybackState.Idle,
    val cachedAudioWordKeys: Set<String> = emptySet(),
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
    private val ttsStatsRepository : TTSStatsRepository,
//    @ApplicationContext private val context: Context,
//    private val ttsCreditsRepository: TtsCreditsRepository,
    private val appScope: CoroutineScope,// Inject a non-cancellable, app-level scope
    private val billingRepository: BillingRepository,
    private val rateLimiter: SimpleRateLimiter,
) : ViewModel() {


    //val isPurchased = billingRepository.isPurchased
    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    private val _uiState = MutableStateFlow(CategoryTabUiState())
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    //NOTE: rate Limiting
//    private val rateLimiter = RateLimiterManager.getInstance()

    private val _showRateLimitSheet = MutableStateFlow(false)
    val showRateLimitSheet = _showRateLimitSheet.asStateFlow()

    private val _showRateDailyLimitSheet = MutableStateFlow(false)
    val showRateDailyLimitSheet = _showRateDailyLimitSheet.asStateFlow()

    private val _showRateHourlyLimitSheet = MutableStateFlow(false)
    val showRateHourlyLimitSheet = _showRateHourlyLimitSheet.asStateFlow()

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
            delay(3000) //  wait until page has loaded
            if (!connectivityRepository.isCurrentlyOnline()) {
                _uiEvent.emit(UiEvent.ShowSnackbar("No internet connection", actionLabel = "Please Connect" ))
                return@launch
            }
        }
        initializeBilling()

    }

    private fun initializeBilling() {
        viewModelScope.launch {
            try {
                billingRepository.connect()
                billingRepository.checkPurchases()
                billingRepository.logCurrentStatus()  // Debug log on init
            } catch (e: Exception) {
                Timber.e("${e.message}")
               // billingRepository._billingError.value = e.message
            }

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
    fun connectToBilling() { //if was offline and comes online this can get called - ON_RESUME
        billingRepository.connect()
    }

    fun loadContentForTab(tabIdentifier: String, voiceName: String) {

        //if (!_uiState.value.isLoading && _uiState.value.categories.isNotEmpty()) return
       // if (_uiState.value.loadedForIdentifier == tabIdentifier && !_uiState.value.isLoading) {
//        if (uiState.value.categories.isNotEmpty()) {
//            Timber.e("BUG NOT EMPTY")
//            return
//        }

        _uiState.update { it.copy(isLoading = true) }



        viewModelScope.launch {
            val categories = vocabRepository.getCategoriesForTab(tabIdentifier)

            if (categories.isNotEmpty()) {
                //Fix A — Always hand Compose fresh, immutable instances
                // Deep copy to ensure new identities (and avoid future in-place mutation):
                // Assuming Category & VocabWord are data classes.
                val freshCategories: List<Category> = categories.map { c ->
                    c.copy(words = c.words.toList()) // words List cloned too
                }.toList() // clone outer list

                // --- THIS IS THE NEW LOGIC ---
                // After getting the categories, ask the repository to check the disk cache for them.
                val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
                val cachedKeys = vocabRepository.getWordKeysWithCachedAudio(categories, currentVoiceName)
                val totalWords = categories.flatMap { it.words }.size

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categories = freshCategories, //deep copy
                        // Update the newly named state with the result of the disk check.
                        cachedAudioWordKeys = cachedKeys.toSet(),       //New set
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
                _uiEvent.emit(UiEvent.ShowSnackbar("No internet connection", actionLabel = "Retry" ))
                return@launch
            }

//            if (isPremiumUser.value) {
//                Timber.i("User is a paid user!")
//            }else{
//                Timber.i("User is a FREE user")
//            }

            if (!isPremiumUser.value) { //if premium user don't check credits
                if (rateLimiter.doIForbidCall()){
                    val failType = rateLimiter.canMakeCallWithResult()
                    Timber.w("canICallAPI = %s", failType.canICallAPI)
                    Timber.w("failReason = %s", (failType.failReason))
                    Timber.w("timeLeftToWait = %s",failType.timeLeftToWait)

                    if (!failType.canICallAPI){
                        if (failType.failReason == SimpleRateLimiter.FailReason.DAILY){
                            _showRateDailyLimitSheet.value = true
                        }else {
                            _showRateHourlyLimitSheet.value = true
                        }
                    } else {
                        _showRateLimitSheet.value = true
                    }

                    return@launch
                }
            }




            val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
            val uniqueSentenceId = generateUniqueSentenceId(word, sentence,currentVoiceName)

            _uiState.update { it.copy(playbackState = PlaybackState.Playing(uniqueSentenceId)) }


            val result = vocabRepository.playTextToSpeech(
                text = sentence.sentence,
                uniqueSentenceId = uniqueSentenceId,
                voiceName = currentVoiceName,
                languageCode = LanguageConfig.languageCode,
                onTTSApiCallStart = {
                    _uiState.update { it.copy(downloadingSentenceId = uniqueSentenceId) }
                },
                onTTSApiCallComplete = {
                    _uiState.update { it.copy(downloadingSentenceId = null) }
                }

            )


            when (result) {
                is PlaybackResult.PlayedFromNetworkAndCached -> {
                    _uiState.update {
                        it.copy(
                            playbackState = PlaybackState.Idle,
                            cachedAudioCount = it.cachedAudioCount + 1,
                            cachedAudioWordKeys = it.cachedAudioWordKeys + word.word
                        )
                    }

                    rateLimiter.recordCall()
                    Timber.w(rateLimiter.printCurrentStatus)
                    ttsStatsRepository.updateTTSStatsWithCosts(sentence, currentVoiceName)
                    ttsStatsRepository.incWordStats(word.word)
                    val currentSkillLevel = userPreferencesRepository.selectedSkillLevelFlow.first()

                    ttsStatsRepository.incProgressSize(currentSkillLevel)

                }
                is PlaybackResult.PlayedFromCache -> {
                    _uiState.update { it.copy(playbackState = PlaybackState.Idle) }
                    ttsStatsRepository.updateTTSStatsWithoutCosts()
//                    ttsStatsRepository.printStats(TTSStatsRepository.fsDOC.WORDSTATS)
                    ttsStatsRepository.incWordStats(word.word)
//                    ttsStatsRepository.printStats(TTSStatsRepository.fsDOC.WORDSTATS)
                }
                is PlaybackResult.Failure -> {
                    // Handle the error
                   // _uiState.update { it.copy(playbackState = PlaybackState.Error(result.exception.message ?: "Playback failed")) }
                    // Optionally reset to Idle after a delay
                    _uiEvent.emit(UiEvent.ShowSnackbar("Could not play audio. Please check your connection."))
                    _uiState.update { it.copy(playbackState = PlaybackState.Idle) }
                    Timber.e("Playback failed", result.exception)
                }
            }
        }
    }


    // --- ADD THIS NEW FUNCTION ---
    fun loadContentForCategory(categoryTitle: String, voiceName: String) {
        if (!_uiState.value.isLoading && _uiState.value.categories.isNotEmpty()) return

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            // Use the new repository function
            val category = vocabRepository.getCategoryByTitle(categoryTitle)

            // The rest of the logic is very similar to loadContentForTab
            if (category != null) {
                val categoriesList = listOf(category) // Wrap it in a list for the UI
                val cachedKeys = vocabRepository.getWordKeysWithCachedAudio(categoriesList, voiceName)
                val totalWords = category.words.size

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        categories = categoriesList,
                        cachedAudioWordKeys = cachedKeys,
                        cachedAudioCount = cachedKeys.size,
                        totalWordsInTab = totalWords
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Re-checks the disk for cached audio files and updates the UI state.
     * This is useful for refreshing the screen when it becomes visible again.
     */
    fun refreshCacheState(voiceName: String) {
        // We only proceed if data is already loaded and we have a valid voice name.
        if (_uiState.value.isLoading || _uiState.value.categories.isEmpty() || voiceName.isEmpty()) {
            return
        }

        viewModelScope.launch {
            //Timber.d("Refreshing cache state...")
            // Get the current categories from the state
            val currentCategories = _uiState.value.categories

            // Ask the repository to re-check the disk with the current data
            val cachedKeys =
                vocabRepository.getWordKeysWithCachedAudio(currentCategories, voiceName)

            // Update the UI state with the fresh cache information
            _uiState.update {
                it.copy(
                    cachedAudioWordKeys = cachedKeys,
                    cachedAudioCount = cachedKeys.size
                )
            }
        }
    }
    fun resetState() {
        // Reset the UI state back to its initial, default values.
        _uiState.value = CategoryTabUiState()
    }
    fun saveDataOnExit() {
        // We use appScope to ensure this save operation completes even if the
        // viewModelScope is paused or cancelled as the user navigates away.
        if (false) {
            appScope.launch {
                if (ttsStatsRepository.checkIfStatsFlushNeeded(forced = true)) {
                    ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.TTSStats)
                    ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
                }
            }
        }
    }
    /** Fire-and-forget: compute + store in repo var (off main thread inside). */
    fun recalcProgress(voiceName: String) = viewModelScope.launch {
        val categories = vocabRepository.getCategories()


        ttsStatsRepository.recalcProgress(categories, voiceName)
//        Timber.d("progressStats: ${ttsStatsRepository.progressStats}")
        // Optionally: trigger your Firebase repo here to upload using statsRepo.progressStats
        // firebaseRepo.uploadA1Progress(statsRepo.progressStats)
    }
    fun hideDailyRateLimitSheet(){
        _showRateDailyLimitSheet.value = false
    }
    fun hideHourlyRateLimitSheet(){
        _showRateHourlyLimitSheet.value = false
    }
    fun hideRateOKLimitSheet(){
        _showRateLimitSheet.value = false
    }

    fun buyPremiumButtonPressed(activity: Activity) {
        Timber.i("purchasePremium()")
        viewModelScope.launch {
            billingRepository.launchPurchase(activity)
        }
    }

}