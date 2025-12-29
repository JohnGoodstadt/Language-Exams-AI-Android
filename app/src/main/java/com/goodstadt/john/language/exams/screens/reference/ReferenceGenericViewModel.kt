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

    private val sheetName: String = savedStateHandle.get<String>("documentId")!!

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


    private fun loadAndObserve() {
        viewModelScope.launch {
            _uiState.value = GenericVocabUiState.Loading


            // 1. Fetch Data
            val result = contentRepository.getFormat0Data(sheetName)

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
                    title = sheetName, // Or derive a pretty title if available
                    categories = vocabFile.categories
                )

                // 4. Listen for History Changes (Red Dot updates)
//                historyManager.historyState.collect {
//                    _uiState.update { currentState ->
//                        if (currentState is GenericVocabUiState.Success) {
//                            currentState.copy(lastUpdate = System.currentTimeMillis())
//                        } else currentState
//                    }
//                }
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

    private fun refreshUI() {
        _uiState.update { currentState ->
            if (currentState is GenericVocabUiState.Success) {
                currentState.copy(lastUpdate = System.currentTimeMillis())
            } else currentState
        }
    }
    fun handleTap(sentence: String) {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        val wasAlreadyHeard = historyManager.isHeard("Reference", contentID)

        // 2. ⚡️ OPTIMISTIC UPDATE (Lightning)
        // This turns the Red Dot ON immediately.
        didPlayReferenceSentence(sentence)

        viewModelScope.launch {
            // 1. Play Audio (Waterfall)
            val success = audioPlaybackRepository.playTrackAndGetResult(
                sentence = sentence,
                level = "Reference",
                sheetName = sheetName,
                isPremiumUser = false // Inject actual status
            )

            // 2. Update Stats on Success
            if (!success) {
                Timber.w("Playback failed. Rolling back Red Dot.")

                // Only undo if it wasn't there before this specific tap
                if (!wasAlreadyHeard) {
                    undoPlayReferenceSentence(sentence)
                }
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
            val sheetTitle = sheetName
            val currentStats = audioCacheManager.getReferenceStats(sheetTitle)
            audioCacheManager.updateReferenceStats(
                key = sheetTitle,
                heard = currentStats.heard + 1,
                total = currentStats.total
            )
        }

        refreshUI()
    }
    private fun undoPlayReferenceSentence(sentence: String) {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        val levelName = "Reference"

        // 1. Revert History (Decrements count)
        // Ensure you added 'undoMarkSentenceHeard' to HistorySyncManager in the previous steps
        historyManager.undoMarkSentenceHeard(levelName, contentID)

        // 2. Revert Graph Stats
        // Since we only call this if !wasAlreadyHeard, we know we definitely incremented the graph.
        // So we must decrement it back.
        val currentStats = audioCacheManager.getReferenceStats(sheetName)

        // Safety check to ensure we don't go below 0
        if (currentStats.heard > 0) {
            audioCacheManager.updateReferenceStats(
                key = sheetName,
                heard = currentStats.heard - 1,
                total = currentStats.total
            )
        }

        // 3. Update UI (Dot disappears)
        refreshUI()
    }
    fun onResume() {
        // If data changed while app was backgrounded (e.g. sync), this ensures we see it
        refreshUI()
    }
        // 3. Update UI (Dot disappears)

        // MARK: - Helpers

    private fun recalculateReferenceStats(sentences: List<String>) {
        var heardCount = 0
        for (sentence in sentences) {
            if (isHeard(sentence)) heardCount++
        }
        audioCacheManager.updateReferenceStats(
            key = sheetName,
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
                    ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.GlobalStats)
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