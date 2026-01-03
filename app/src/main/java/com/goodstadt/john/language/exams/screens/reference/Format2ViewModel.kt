package com.goodstadt.john.language.exams.screens.reference


import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.ConnectivityRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.data.repository.PlaybackResult
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.FirebaseAudioService
import com.goodstadt.john.language.exams.managers.AudioCacheManager
import com.goodstadt.john.language.exams.managers.HistorySyncManager
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.models.Format2File
import com.goodstadt.john.language.exams.utils.calcIsTodayNotAFreePassDay
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.goodstadt.john.language.exams.viewmodels.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// 1. Define the UI State for this specific screen.
// A sealed interface is the best practice for representing distinct states.

sealed interface Format2UiState {
    object Loading : Format2UiState
    data class Success(
        val format2File: Format2File,
        val playbackState: PlaybackState = PlaybackState.Idle,
        val lastUpdate: Long = System.currentTimeMillis()
    ) : Format2UiState

    data class Error(val message: String) : Format2UiState
}


@HiltViewModel
class Format2ViewModel @Inject constructor(
    private val vocabRepository: ContentRepository,
    private val contentRepository: ContentRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val connectivityRepository: ConnectivityRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val ttsStatsRepository: TTSStatsRepository,
    private val billingRepository: BillingRepository,
    private val historyManager: HistorySyncManager,
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val audioCacheManager: AudioCacheManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow<Format2UiState>(Format2UiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    private val _showRateLimitSheet = MutableStateFlow(false)
    val showRateLimitSheet = _showRateLimitSheet.asStateFlow()

    private val _showRateDailyLimitSheet = MutableStateFlow(false)
    val showRateDailyLimitSheet = _showRateDailyLimitSheet.asStateFlow()

    private val _showRateHourlyLimitSheet = MutableStateFlow(false)
    val showRateHourlyLimitSheet = _showRateHourlyLimitSheet.asStateFlow()

    // 3. Get the documentId from the navigation arguments via SavedStateHandle.
    //    The key "documentId" MUST match the argument name in your NavHost route.
    private val sheetName: String = savedStateHandle.get<String>("documentId")!!

    init {
        // 4. Trigger the data loading process as soon as the ViewModel is created.
        loadData()
    }
    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = Format2UiState.Loading

            // ✅ SIMPLIFIED: All the complex logic is gone.
            // We just make one simple, type-safe call to our orchestrator.
            val result = vocabRepository.getFormat2Data(sheetName)

            result.onSuccess { format2File ->

                val allSentences = format2File.data
                    .flatMap { it.wordsAndSentences } // Flatten Levels -> Entries
                    .flatMap { it.sentences }         // Flatten Entries -> Sentence Objects (All of them)
                    .map { it.sentence }

                Timber.v("${allSentences.count()}")
                audioCacheManager.recalculateReferenceStats(sheetName,allSentences)
                _uiState.value = Format2UiState.Success(format2File)
            }
            result.onFailure { error ->
                _uiState.value = Format2UiState.Error(error.localizedMessage ?: "Failed to load content")
            }
        }
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

    private fun refreshUI() {
        _uiState.update { currentState ->
            if (currentState is Format2UiState.Success) {
                currentState.copy(lastUpdate = System.currentTimeMillis())
            } else currentState
        }
    }
    fun isHeard(sentence: String): Boolean {
        val contentID = FirebaseAudioService.generateContentID(sentence)
       // Timber.i("Play Count:${historyManager.getPlayCount("Reference", contentID) } $sentence")
        return historyManager.getPlayCount("Reference", contentID) > 0
    }
    fun getPlayCount(sentence:String): Int {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        return historyManager.getPlayCount("Reference", contentID)
    }
    // ✅ ACTION: View calls this on tap
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
                sheetName = sheetName
            )
            // 2. Update Graph Stats (If success)
//            if (success) {
//                didPlayReferenceSentence(sentence)
//            }
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
    fun onResume() {
        // If data changed while app was backgrounded (e.g. sync), this ensures we see it
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

    fun getAudioCacheManager(): AudioCacheManager {
        return audioCacheManager
    }

    fun getAIParagraphCount(): Int {
        return audioCacheManager.getAIParagraphCount()
    }

    fun getAIParagraphHeardCount(): Int {
        return audioCacheManager.getAIParagraphHeardCount()
    }
}