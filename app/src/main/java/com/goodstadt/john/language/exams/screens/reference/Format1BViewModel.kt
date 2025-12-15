package com.goodstadt.john.language.exams.screens.reference

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.managers.AudioCacheManager
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.data.repository.FirebaseAudioService
import com.goodstadt.john.language.exams.managers.HistorySyncManager
import com.goodstadt.john.language.exams.models.HeaderWordsSentencesList
import com.goodstadt.john.language.exams.viewmodels.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface Format1BUiState {
    object Loading : Format1BUiState
    data class Success(
        val data: List<HeaderWordsSentencesList>,
        val playbackState: PlaybackState = PlaybackState.Idle,
        // ✅ NEW: A trigger to force Compose to redraw when History changes
        val lastUpdate: Long = System.currentTimeMillis()
    ) : Format1BUiState
    data class Error(val message: String) : Format1BUiState
}

@HiltViewModel
class Format1BViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val historyManager: HistorySyncManager,
    private val audioCacheManager: AudioCacheManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sheetName: String = savedStateHandle.get<String>("documentId")!!
    private val _uiState = MutableStateFlow<Format1BUiState>(Format1BUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        loadAndObserve()
    }

    private fun loadAndObserve() {
        viewModelScope.launch {
            _uiState.value = Format1BUiState.Loading

            // 1. Load Content
            val result = contentRepository.getFormat1Data(sheetName)

            result.onSuccess { format1File ->

                // Initialize Graph Stats logic (Totals)
                val allSentences = format1File.data.flatMap { it.wordsAndSentences }.map { it.sentence }
                audioCacheManager.recalculateReferenceStats(sheetName,allSentences)

                // Set Initial Success State
                _uiState.value = Format1BUiState.Success(data = format1File.data)

                // 2. ✅ LISTEN FOR HISTORY CHANGES
                // When History updates (e.g. after a tap), we update 'lastUpdate'.
                // This forces the Screen to recompose and call 'isHeard()' again.
                historyManager.historyState.collect {
                    _uiState.update { currentState ->
                        if (currentState is Format1BUiState.Success) {
                            currentState.copy(lastUpdate = System.currentTimeMillis())
                        } else currentState
                    }
                }
            }.onFailure { error ->
                _uiState.value = Format1BUiState.Error(error.localizedMessage ?: "Failed to load")
            }
        }
    }



    // ✅ HELPER: View calls this directly during rendering
    fun isHeard(sentence: String): Boolean {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        return historyManager.getPlayCount("Reference", contentID) > 0
    }

    // ✅ ACTION: View calls this on tap
    fun handleTap(sentence: String) {
        viewModelScope.launch {
            // 1. Play Audio (Waterfall)
            val success = audioPlaybackRepository.playTrackAndGetResult(
                sentence = sentence,
                level = "Reference",
                sheetName = sheetName
            )

            // 2. Update Graph Stats (If success)
            if (success) {
                didPlayReferenceSentence(sentence)
            }
        }
    }

    private fun didPlayReferenceSentenceObsolete(sentence: String) {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        val playCount = historyManager.getPlayCount("Reference", contentID)

        // Update AudioCacheManager if this is the first time hearing it
        // (Note: Repository has already incremented History count to at least 1)
        if (playCount == 1) {
            val currentStats = audioCacheManager.getReferenceStats(sheetName)
            audioCacheManager.updateReferenceStats(
                key = sheetName,
                heard = currentStats.heard + 1,
                total = currentStats.total
            )
        }

        // ✅ THE MISSING PIECE: Force the UI to Recompose
        // We update 'lastUpdate' (or just copy the state) to generate a new State Object.
        // This signals Compose that something changed, so it re-runs the screen
        // and calls 'isHeard()' again for every row.
        _uiState.update { currentState ->
            if (currentState is Format1BUiState.Success) {
                currentState.copy(lastUpdate = System.currentTimeMillis())
            } else currentState
        }

        // We don't need to force update UI here manually because 
        // historyManager.historyState.collect (in init) will catch the change and do it.
    }
    private fun didPlayReferenceSentence(sentence: String) {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        val levelName = "Reference"

        // 1. Check Previous Count
        val previousCount = historyManager.getPlayCount(levelName, contentID)
        val isFirstTime = previousCount == 0

        // 2. Update History (Source of Truth)
        // ✅ This triggers 'historyState' emission -> 'init' collector runs -> UI Recomposes
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

        // No manual _uiState.update needed here!
    }
    // ... recalculateReferenceStats helper ...
    // MARK: - Internal Helpers
// MARK: - Public Accessors for View

    fun getAudioCacheManager(): AudioCacheManager {
        return audioCacheManager
    }

    fun getAIParagraphCount(): Int {
        return audioCacheManager.getAIParagraphCount()
    }

    fun getAIParagraphHeardCount(): Int {
        return audioCacheManager.getAIParagraphHeardCount()
    }
    /**
     * Loops through the loaded data, checks History for each sentence,
     * and updates the AudioCacheManager stats (Heard/Total) for this sheet.
     */
    private fun recalculateReferenceStats(data: List<HeaderWordsSentencesList>) {
        // 1. Flatten the data to get all sentences
        val allSentences = data.flatMap { it.wordsAndSentences }.map { it.sentence }

        // 2. Count how many are already marked as "Heard" in History
        var heardCount = 0
        for (sentence in allSentences) {
            // Re-use our helper to check the "Reference" bucket
            if (isHeard(sentence)) {
                heardCount++
            }
        }

        // 3. Update the Manager (which updates the StateFlow -> UI Graph)
        audioCacheManager.updateReferenceStats(
            key = sheetName,
            heard = heardCount,
            total = allSentences.size
        )
    }
}