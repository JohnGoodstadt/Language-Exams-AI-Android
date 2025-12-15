package com.goodstadt.john.language.exams.screens.reference

//package com.yourpackage.ui.reference.generic // Or your preferred package

// ... other necessary imports from your PrepositionsViewModel
//import com.yourpackage.data.repository.ExamSheetRepository
//import com.yourpackage.data.repository.VocabRepository
import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.ConnectivityRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.data.repository.FirebaseAudioService
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.managers.AudioCacheManager
import com.goodstadt.john.language.exams.managers.HistorySyncManager
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.viewmodels.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// 1. RENAMED: The UI State is now generic
sealed interface ReferenceGenericUiState {
    object Loading : ReferenceGenericUiState
    data class Success(
        val categories: List<Category>,
        val cachedAudioWordKeys: Set<String>,
        val selectedVoiceName: String = ""
    ) : ReferenceGenericUiState

    data class Error(val message: String) : ReferenceGenericUiState
    object NotAvailable : ReferenceGenericUiState
}

// ✅ ADD THIS SEALED INTERFACE
/**
 * Represents the different states for the ReferenceGenericScreen UI.
 * A sealed interface is perfect for this, as it forces the `when` block in the
 * Composable to handle all possible states.
 */
sealed interface GenericVocabUiState999 {
    /**
     * The initial state, while data is being fetched from the repository.
     */
    object Loading : GenericVocabUiState999

    /**
     * The state representing a successful data load.
     * It holds all the data the UI needs to render the list.
     *
     * Note: In your old PrepositionsUiState, you had other properties like
     * cachedAudioWordKeys and selectedVoiceName. We will add those here as well
     * for consistency, as your SectionedVocabList composable will likely need them.
     */
    data class Success(
        val categories: List<Category>,
        val cachedAudioWordKeys: Set<String> = emptySet(),
        val selectedVoiceName: String = ""
    ) : GenericVocabUiState999

    /**
     * The state representing a failure to load data.
     * It holds an error message to display to the user.
     */
    data class Error(val message: String) : GenericVocabUiState999
    object NotAvailable : GenericVocabUiState999
}

sealed interface GenericVocabUiState {
    object Loading : GenericVocabUiState
    data class Success(
        val title: String,
        val categories: List<Category>,
        val playbackState: PlaybackState = PlaybackState.Idle,
        // Trigger to force recomposition when History changes
        val lastUpdate: Long = System.currentTimeMillis()
    ) : GenericVocabUiState

    data class Error(val message: String) : GenericVocabUiState
}

@HiltViewModel
class ReferenceGenericViewModel @Inject constructor(
    // 2. MODIFIED: Injected SavedStateHandle to get navigation arguments
    private val savedStateHandle: SavedStateHandle,
    // Keep all other dependencies that are still needed
    private val vocabRepository: ContentRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ttsStatsRepository: TTSStatsRepository,
    private val appScope: CoroutineScope,
    private val billingRepository: BillingRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val connectivityRepository: ConnectivityRepository,
    private val contentRepository: ContentRepository,
    private val audioCacheManager: AudioCacheManager,
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val xpManager: XPManager,
    private val historyManager: HistorySyncManager,
//    private val examSheetRepository: ExamSheetRepository,
    private val appConfigRepository: AppConfigRepository
) : ViewModel() {


    private val _uiState = MutableStateFlow<GenericVocabUiState>(GenericVocabUiState.Loading)
    val uiState = _uiState.asStateFlow()

    // --- All other state flows can remain the same ---
    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()
    private val _showRateLimitSheet = MutableStateFlow(false)
    val showRateLimitSheet = _showRateLimitSheet.asStateFlow()
    private val _showRateDailyLimitSheet = MutableStateFlow(false)
    val showRateDailyLimitSheet = _showRateDailyLimitSheet.asStateFlow()
    private val _showRateHourlyLimitSheet = MutableStateFlow(false)
    val showRateHourlyLimitSheet = _showRateHourlyLimitSheet.asStateFlow()

    val currentVoiceName: StateFlow<String> = userPreferencesRepository.selectedVoiceNameFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ""
        )

    private val documentId: String = savedStateHandle.get<String>("documentId")!!

    init {
        // 3. MODIFIED: Get the documentId from the navigation arguments
        //   val firestoreDocumentId: String? = savedStateHandle.get("documentId")

        loadAndObserve()

        viewModelScope.launch {
            billingRepository.isPurchased.collect { purchasedStatus ->
                _isPremiumUser.value = purchasedStatus
            }
        }
    }

    //    private fun loadData() {
//        viewModelScope.launch {
//            _uiState.value = GenericVocabUiState.Loading
//
//            val logicalName: String = savedStateHandle.get<String?>("documentId").toString() ?: ""
//
//            Timber.i("ReferenceGenericViewModel: Attempting to fetch generic vocab for '$logicalName'...")
//            val result = vocabRepository.getFormat0Data(logicalName)
//
//
//
//            // ✅ NO CASTING NEEDED! The result is already the correct type.
//            result.onSuccess { vocabFile ->
//                _uiState.value = GenericVocabUiState.Success(vocabFile.categories, /*...other params...*/)
//            }
//            result.onFailure { error ->
//                _uiState.value = GenericVocabUiState.Error(error.localizedMessage ?: "Failed to load data")
//            }
//        }
//
//    }
    private fun loadAndObserve() {
        viewModelScope.launch {
            _uiState.value = GenericVocabUiState.Loading


            // 1. Fetch Data
            val result = contentRepository.getFormat0Data(documentId)

            result.onSuccess { vocabFile ->
                // 2. Initialize Graph Stats
                val allSentences = vocabFile.categories
                    .flatMap { it.words }
                    .flatMap { it.sentences }
                    .map { it.sentence }

//                audioCacheManager.recalculateReferenceStats(documentId,allSentences)
                recalculateReferenceStats(allSentences)

                // 3. Set Initial State
                _uiState.value = GenericVocabUiState.Success(
                    title = documentId, // Or derive a pretty title if available
                    categories = vocabFile.categories
                )

                // 4. Listen for History Changes (Red Dot updates)
                historyManager.historyState.collect {
                    _uiState.update { currentState ->
                        if (currentState is GenericVocabUiState.Success) {
                            currentState.copy(lastUpdate = System.currentTimeMillis())
                        } else currentState
                    }
                }
            }.onFailure { error ->
                _uiState.value =
                    GenericVocabUiState.Error(error.localizedMessage ?: "Failed to load")
            }
        }
    }

    // 4. MODIFIED: Replaced loadPrepositions() with a generic function
    fun isHeard(sentence: String): Boolean {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        // Reference sheets check the "Reference" bucket
        return historyManager.getPlayCount("Reference", contentID) > 0
    }

    fun playCount(sentence: String): Int {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        return historyManager.getPlayCount("Reference", contentID)
    }
    /*
       // --- ALL OTHER FUNCTIONS (playTrack, saveDataOnExit, hide...Sheet, etc.) ---
        // can be copied directly from PrepositionsViewModel as they are already generic enough.
        // They operate on VocabWord, Sentence, etc., and have no hardcoded logic.
        // (Omitted for brevity, but you should paste them here)
    //    fun playTrack(word: Format0Word, sentence: Sentence) {
    //        if (_playbackState.value is PlaybackState.Playing)
    //        {
    //            return
    //        }
    //
    //        if (!connectivityRepository.isCurrentlyOnline()) {
    //            _playbackState.value = PlaybackState.Idle
    //            return
    //        }
    //
    //        viewModelScope.launch {
    //            val todayIsNotAFreePassDay = calcIsTodayNotAFreePassDay(userPreferencesRepository)
    //            if (!isPremiumUser.value && todayIsNotAFreePassDay) { //if premium user don't check credits or is on day
    //                if (rateLimiter.doIForbidCall()) {
    //                    val failType = rateLimiter.canMakeCallWithResult()
    //                    Timber.v("${failType.canICallAPI}")
    //                    Timber.v("${failType.failReason}")
    //                    Timber.v("${failType.timeLeftToWait}")
    //                    if (!failType.canICallAPI) {
    //                        if (failType.failReason == SimpleRateLimiter.FailReason.DAILY) {
    //                            _showRateDailyLimitSheet.value = true
    //                        } else {
    //                            _showRateHourlyLimitSheet.value = true
    //                        }
    //                    } else {
    //                        _showRateLimitSheet.value = true
    //                    }
    //
    //                    return@launch
    //                }
    //            }
    //
    //
    //
    //            val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
    //            val currentLanguageCode =  userPreferencesRepository.selectedLanguageCodeFlow.first()
    //
    //            val uniqueSentenceId = generateUniqueSentenceId(word, sentence, currentVoiceName)
    //            _playbackState.value = PlaybackState.Playing(uniqueSentenceId)
    ////maybe just de. remove any (zu dem)
    //            val cleanedSentence = sentence.sentence.replace("\\s*\\([^)]*\\)\\s*".toRegex(), " ")
    //
    //            val played = vocabRepository.playFromCacheIfFound(uniqueSentenceId)
    //            if (played){//short cut so user cna play cached sentences with no Internet connection
    //                _playbackState.value = PlaybackState.Idle
    //                ttsStatsRepository.updateTTSStatsWithoutCosts()
    //                ttsStatsRepository.incWordStats(word.word)
    //                return@launch
    //            }
    //
    //            //Dot shows before sound (lightening before thunder)
    //            _uiState.update { currentState ->
    //                if (currentState is GenericVocabUiState.Success) {
    //                    val updatedKeys = currentState.cachedAudioWordKeys +  generateUniqueSentenceId(word, sentence, currentVoiceName)
    //                    currentState.copy(cachedAudioWordKeys = updatedKeys)
    //                } else {
    //                    currentState
    //                }
    //            }
    //
    //            val result = vocabRepository.playTextToSpeech(
    //                text = cleanedSentence,
    //                uniqueSentenceId = uniqueSentenceId,
    //                voiceName = currentVoiceName,
    //                languageCode = currentLanguageCode
    //            )
    //            when (result) {
    //                is PlaybackResult.PlayedFromNetworkAndCached -> {
    //                    _playbackState.value = PlaybackState.Idle
    //
    //                    if (todayIsNotAFreePassDay){
    //                        rateLimiter.recordCall()
    //                    }
    //                    Timber.v(rateLimiter.printCurrentStatus)
    //                    ttsStatsRepository.updateTTSStatsWithCosts(sentence, currentVoiceName)
    //                    ttsStatsRepository.incWordStats(word.word)
    //                    //TODO: not inc but update!
    //                    ttsStatsRepository.incProgressSize(userPreferencesRepository.selectedSkillLevelFlow.first())
    //                }
    //                is PlaybackResult.PlayedFromCache -> { //probably does not get executed as playFromCacheIfFound() already run
    //                    _playbackState.value = PlaybackState.Idle
    //                    ttsStatsRepository.updateTTSStatsWithoutCosts()
    //                    ttsStatsRepository.incWordStats(word.word)
    //                }
    //                is PlaybackResult.Failure -> {
    //                    _playbackState.value = PlaybackState.Idle
    //                    _playbackState.value = PlaybackState.Error(result.exception.message ?: "Playback failed")
    //                }
    //                PlaybackResult.CacheNotFound -> {
    //                    _playbackState.value = PlaybackState.Idle
    //                    Timber.e("Cache found to exist but not played")
    //                }
    //            }
    //            _playbackState.value = PlaybackState.Idle
    //        }
    //    }
     */

    fun handleTap(sentence: String) {
        viewModelScope.launch {
            // 1. Play Audio (Waterfall)
            val success = audioPlaybackRepository.playTrackAndGetResult(
                sentence = sentence,
                level = "Reference",
                sheetName = documentId,
                isPremiumUser = false // Inject actual status
            )

            // 2. Update Stats on Success
            if (success) {
                didPlayReferenceSentence(sentence)
            }
            historyManager.debugPrintAllHistory()
        }
    }

    private fun didPlayReferenceSentence(sentence: String) {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        val levelName = "Reference"

        // 1. Check Previous Count
        val previousCount = historyManager.getPlayCount(levelName, contentID)
        val isFirstTime = previousCount == 0

        // 2. Update History (Source of Truth)
        // ✅ This triggers 'historyState' emission -> 'init' collector runs -> UI Recomposes -- inc heard by 1
        historyManager.markSentenceHeard(levelName, contentID)


        // 3. Update Graph Stats (If new)
        if (isFirstTime) {
            val sheetTitle = documentId
            val currentStats = audioCacheManager.getReferenceStats(sheetTitle)
            audioCacheManager.updateReferenceStats(
                key = sheetTitle,
                heard = currentStats.heard + 1,
                total = currentStats.total
            )

        }
    }

    // MARK: - Helpers

    private fun recalculateReferenceStats(sentences: List<String>) {
        var heardCount = 0
        for (sentence in sentences) {
            if (isHeard(sentence)) heardCount++
        }
        audioCacheManager.updateReferenceStats(
            key = documentId,
            heard = heardCount,
            total = sentences.size
        )
    }


    fun saveDataOnExit() {
        // We use appScope to ensure this save operation completes even if the
        // viewModelScope is paused or cancelled as the user navigates away.
        if (false) {
            appScope.launch {
                Timber.d("Saving data because screen is no longer active.")
                if (ttsStatsRepository.checkIfStatsFlushNeeded(forced = true)) {
                    ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.TTSStats)
                    ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
                }
            }
        }
    }

    fun hideDailyRateLimitSheet() {
        _showRateDailyLimitSheet.value = false
    }

    fun hideHourlyRateLimitSheet() {
        _showRateHourlyLimitSheet.value = false
    }

    fun hideRateOKLimitSheet() {
        _showRateLimitSheet.value = false
    }

    fun buyPremiumButtonPressed(activity: Activity) {
        Timber.i("purchasePremium()")
        viewModelScope.launch {
            billingRepository.launchPurchase(activity)
        }
    }

    fun hideRateLimitSheet() {
        _showRateLimitSheet.value = false
    }

    fun getAudioCacheManager(): AudioCacheManager = audioCacheManager
    fun getAIParagraphCount(): Int = audioCacheManager.getAIParagraphCount()
    fun getAIParagraphHeardCount(): Int = audioCacheManager.getAIParagraphHeardCount()


}