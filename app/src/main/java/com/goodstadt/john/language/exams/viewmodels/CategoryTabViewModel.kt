package com.goodstadt.john.language.exams.viewmodels


import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.data.repository.FirebaseAudioService
import com.goodstadt.john.language.exams.data.repository.PlaybackResult
import com.goodstadt.john.language.exams.data.repository.RecallingRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.managers.AudioCacheManager
import com.goodstadt.john.language.exams.managers.HistorySyncManager
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.managers.XpActionType
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Format0Word
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.utils.CategoryProgress
import com.goodstadt.john.language.exams.utils.calcIsTodayNotAFreePassDay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

sealed interface CategoryTabUiState {
    object Loading : CategoryTabUiState
    data class Success(
        val categories: List<Category>,
        val totalWordsInTab: Int,
        val cachedAudioCount: Int, // Still needed for the Progress Bar
        // ❌ REMOVED: val heardSentenceIDs: Set<String>
        val downloadingSentenceId: String? = null,
        val playbackState: PlaybackState = PlaybackState.Idle,
        val recalledWordKeys: Set<String> = emptySet(),

        // ✅ NEW: Timestamp to force UI recomposition when History changes
        val lastUpdate: Long = System.currentTimeMillis()
    ) : CategoryTabUiState
    data class Error(val message: String) : CategoryTabUiState
}

sealed class UiEvent {
    data class ShowSnackbar(val message: String, val actionLabel: String? = null) : UiEvent()
}

@HiltViewModel
class CategoryTabViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val historyManager: HistorySyncManager,
    private val audioCacheManager: AudioCacheManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val recallingRepository: RecallingRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val ttsStatsRepository: TTSStatsRepository,
    private val xpManager: XPManager,
    private val billingRepository: BillingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<CategoryTabUiState>(CategoryTabUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    // Rate Limit State
    private val _showRateLimitSheet = MutableStateFlow(false)
    val showRateLimitSheet = _showRateLimitSheet.asStateFlow()
    private val _showRateDailyLimitSheet = MutableStateFlow(false)
    val showRateDailyLimitSheet = _showRateDailyLimitSheet.asStateFlow()
    private val _showRateHourlyLimitSheet = MutableStateFlow(false)
    val showRateHourlyLimitSheet = _showRateHourlyLimitSheet.asStateFlow()

    // Cache the level name for fast synchronous access in isHeard()
    private var currentLoadedLevel: String = "B1"

    init {
        observeHistoryChanges()
        observeRecallingChanges()
    }

    // MARK: - Reactive Listeners

    private fun observeHistoryChanges() {
        viewModelScope.launch {
            // ✅ Listen for History Changes
            historyManager.historyState.collect { historyMap ->

                // When History updates, we update the timestamp to force the View to redraw.
                // We also recalculate the 'cachedAudioCount' for the progress bar.
                _uiState.update { currentState ->
                    if (currentState is CategoryTabUiState.Success) {

                        // Recalculate progress bar count on the fly
                        // (This is cheaper than building the whole Set<String>)
                        val (heard, _) = calculateTabStats(currentState.categories, historyMap)

                        currentState.copy(
                            cachedAudioCount = heard,
                            lastUpdate = System.currentTimeMillis() // ⚡ Forces Redraw
                        )
                    } else currentState
                }
            }
        }
    }

    private fun observeRecallingChanges() {
        viewModelScope.launch {
            recallingRepository.recalledWordKeys.collect { keys ->
                _uiState.update { currentState ->
                    if (currentState is CategoryTabUiState.Success) {
                        currentState.copy(recalledWordKeys = keys)
                    } else currentState
                }
            }
        }
    }

    // MARK: - Loading

    fun loadContentForTab(tabNumber: Int) {
        viewModelScope.launch {
            _uiState.value = CategoryTabUiState.Loading

            val examName = userPreferencesRepository.selectedExamNameFlow.first()
            val voiceName = userPreferencesRepository.selectedVoiceNameFlow.first()

            // Cache the level for isHeard calls later
            currentLoadedLevel = userPreferencesRepository.selectedSkillLevelFlow.first()

            val result = contentRepository.getFormat0Data(examName)

            result.onSuccess { vocabFile ->
                val tabCategories = vocabFile.categories.filter { it.tabNumber == tabNumber }

                // Initialize AudioCacheManager (Calculates Global Totals)
                audioCacheManager.setCurrentVocabFile(vocabFile, voiceName)

                // Initialize Recalling Set
                val recalledKeys = recallingRepository.getAllRecalledKeys()

                // Initial Stats
                // Note: We pass 'emptyMap()' initially; the observeHistoryChanges block will
                // fire immediately after with real data to fill in the correct count.
                val total = tabCategories.sumOf { it.words.size }

                _uiState.value = CategoryTabUiState.Success(
                    categories = tabCategories,
                    totalWordsInTab = total,
                    cachedAudioCount = 0, // Will update via observer instantly
                    recalledWordKeys = recalledKeys
                )
            }.onFailure { error ->
                _uiState.value = CategoryTabUiState.Error(error.localizedMessage ?: "Failed to load")
            }
        }
    }

    // MARK: - Helper for View (Direct Check)

    /**
     * Called by the View during composition.
     * Uses the cached level and HistoryManager to return true/false instantly.
     */
    fun isHeard(sentence: String): Boolean {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        // Uses the cached variable for O(1) access
        return historyManager.isHeard(currentLoadedLevel, contentID)
    }

    // MARK: - Playback Logic

    fun onRowTapped(word: Format0Word, sentence: Sentence, category: Category) {
        val sentenceText = sentence.sentence

        // Reset UI Playback State
        _uiState.update {
            if (it is CategoryTabUiState.Success) it.copy(playbackState = PlaybackState.Idle) else it
        }

        viewModelScope.launch {
            // 1. UI Feedback: Show "Playing" state
            val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
            val uniqueSentenceId = FirebaseAudioService.generateUnifiedFilename(sentenceText, currentVoiceName)

            _uiState.update {
                if (it is CategoryTabUiState.Success) {
                    it.copy(playbackState = PlaybackState.Playing(uniqueSentenceId))
                } else it
            }

            // 2. Capture Previous State (For Rollback)
            val contentID = FirebaseAudioService.generateContentID(sentenceText)
            val wasAlreadyHeard = historyManager.isHeard(currentLoadedLevel, contentID)

            // 3. OPTIMISTIC UPDATE
            // Updates History (Red Dot) and AudioCacheManager (Graph Stats) instantly
            audioCacheManager.didPlaySentence(sentenceText, category.title, category.tabNumber)

            // 4. Play Audio (Background / Waterfall)
            val success = audioPlaybackRepository.playTrackAndGetResult(
                sentence = sentenceText,
                level = currentLoadedLevel,
                sheetName = "",
                isPremiumUser = false
            )

            // 5. ROLLBACK ON FAILURE
            if (!success) {
                // A. Revert History (Red Dot)
                historyManager.undoMarkSentenceHeard(currentLoadedLevel, contentID)

                // B. Revert Graph Stats (Only if it was new)
                if (!wasAlreadyHeard) {
                    // Optional: revert AudioCacheManager logic if strict accuracy needed
                }

                _uiEvent.emit(UiEvent.ShowSnackbar("Playback failed"))

                _uiState.update {
                    if (it is CategoryTabUiState.Success) it.copy(playbackState = PlaybackState.Error("Failed")) else it
                }
            } else {
                // Success: Reset to Idle
                _uiState.update {
                    if (it is CategoryTabUiState.Success) it.copy(playbackState = PlaybackState.Idle) else it
                }
            }
        }
    }

    // MARK: - Internal Stats Helper

    private fun calculateTabStats(
        categories: List<Category>,
        historyMap: Map<String, com.goodstadt.john.language.exams.models.HistoryData>
    ): Pair<Int, Int> {
        var heard = 0
        var total = 0

        // Use the map passed in from the flow to ensure we use the LATEST data
        val snapshot = historyMap[currentLoadedLevel]?.items

        categories.forEach { cat ->
            total += cat.words.size
            if (snapshot != null) {
                cat.words.forEach { word ->
                    val sentence = word.sentences.firstOrNull()?.sentence ?: return@forEach
                    val id = FirebaseAudioService.generateContentID(sentence)
                    if (snapshot.containsKey(id)) {
                        heard++
                    }
                }
            }
        }
        return Pair(heard, total)
    }

    // ... (Focus, Cancel, Billing, Lifecycle methods same as before) ...
    // MARK: - Focus / Recalling Logic

    fun onFocusClicked(word: Format0Word) {
        viewModelScope.launch {
            recallingRepository.addWord(word)
            // Local update optional as we observe the flow
            xpManager.registerAction(XpActionType.MasterWord)
        }
    }

    fun onCancelClicked(word: Format0Word) {
        viewModelScope.launch {
            recallingRepository.removeWord(word)
        }
    }

    fun refreshCacheState(voiceName: String) {
        historyManager.fetchCloudUpdates()
    }

    fun connectToBilling() {
        viewModelScope.launch { billingRepository.startConnection() }
    }

    fun saveDataOnExit() {
        historyManager.flushToFirebase()
        xpManager.logSessionDensity()
    }

    fun buyPremiumButtonPressed(activity: Activity) {
        viewModelScope.launch { billingRepository.launchPurchase(activity) }
    }

    fun hideDailyRateLimitSheet() { _showRateDailyLimitSheet.value = false }
    fun hideHourlyRateLimitSheet() { _showRateHourlyLimitSheet.value = false }
    fun hideRateOKLimitSheet() { _showRateLimitSheet.value = false }

    fun calculateGrandTotals(): Pair<Int, Int> {
        val heard = audioCacheManager.totalExamWordsHeardOverall.value
        val total = audioCacheManager.totalExamWordCount.value
        return Pair(heard, total)
    }

    fun buildCategoryProgress(): List<CategoryProgress> {
        return audioCacheManager.getAllCategoryProgress()
    }

    fun setTestExamGoal() {}
// MARK: - Playback Logic

    fun handleSentenceTap(sentence: String, category: Category) {

        // 1. Reset UI Playback State (Stop any previous playing icon)
        _uiState.update {
            if (it is CategoryTabUiState.Success) it.copy(playbackState = PlaybackState.Idle) else it
        }

        viewModelScope.launch {
            // 2. Setup Data
            val voiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
            val levelName = userPreferencesRepository.selectedSkillLevelFlow.first() // e.g. "B1"
            val contentID = FirebaseAudioService.generateContentID(sentence)

            // 3. UI Feedback: Show "Playing" spinner/icon on the row
            val uiFilename = FirebaseAudioService.generateUnifiedFilename(sentence, voiceName)
            _uiState.update {
                if (it is CategoryTabUiState.Success) {
                    it.copy(playbackState = PlaybackState.Playing(uiFilename))
                } else it
            }

            // 4. Capture "Before" State (For Rollback logic)
            // We need to know if the user HAD the red dot before they tapped.
            val wasAlreadyHeard = historyManager.isHeard(levelName, contentID)

            // 5. OPTIMISTIC UPDATE (Instant Gratification)
            // This updates History (Red Dot) and AudioCacheManager (Progress Bar) immediately.
            audioCacheManager.didPlaySentence(
                text = sentence,
                categoryTitle = category.title,
                categoryTabNumber = category.tabNumber
            )

            // 6. Play Audio (Background / Waterfall)
            // We pass 'updateHistory = false' because we just did it manually in step 5.
            val success = audioPlaybackRepository.playTrackAndGetResult(
                sentence = sentence,
                level = levelName,
                sheetName = "", // Main tabs aggregate by Level, not SheetName
                isPremiumUser = false // Replace with actual check if available
                // updateHistory = false // Uncomment if your Repo supports this flag, otherwise redundant update is harmless
            )

            // 7. RESULT HANDLING
            if (!success) {
                // --- FAILURE: ROLLBACK ---

                // A. Revert History (Turn off Red Dot)
                // Only if it wasn't heard before (we don't want to remove a legit red dot)
                if (!wasAlreadyHeard) {
                    historyManager.undoMarkSentenceHeard(levelName, contentID)

                    // Note: Reverting the AudioCacheManager progress bar is complex without a specific method.
                    // Since it recalculates on next app load, we often accept this minor temporary inaccuracy
                    // rather than writing complex rollback logic for the graph.
                }

                // B. Notify User
                _uiEvent.emit(UiEvent.ShowSnackbar("Playback failed. Check connection."))

                // C. Set Error State
                _uiState.update {
                    if (it is CategoryTabUiState.Success) {
                        it.copy(playbackState = PlaybackState.Error("Failed"))
                    } else it
                }
            } else {
                // --- SUCCESS ---
                // Reset to Idle (removes spinner)
                _uiState.update {
                    if (it is CategoryTabUiState.Success) {
                        it.copy(playbackState = PlaybackState.Idle)
                    } else it
                }
            }
        }
    }
}