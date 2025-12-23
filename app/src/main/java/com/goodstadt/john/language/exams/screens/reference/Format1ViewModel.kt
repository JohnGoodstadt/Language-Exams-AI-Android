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
import timber.log.Timber
import javax.inject.Inject

sealed interface Format1UiState {
    object Loading : Format1UiState
    data class Success(
        val data: List<HeaderWordsSentencesList>,
        val playbackState: PlaybackState = PlaybackState.Idle,
        val lastUpdate: Long = System.currentTimeMillis()
    ) : Format1UiState
    data class Error(val message: String) : Format1UiState
}

@HiltViewModel
class Format1ViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val historyManager: HistorySyncManager,
    private val audioCacheManager: AudioCacheManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val sheetName: String = savedStateHandle.get<String>("documentId")!!
    private val _uiState = MutableStateFlow<Format1UiState>(Format1UiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.value = Format1UiState.Loading

            // 1. Load Content
            val result = contentRepository.getFormat1Data(sheetName)

            result.onSuccess { format1File ->

                // Initialize Graph Stats logic (Totals)
                val allSentences = format1File.data.flatMap { it.wordsAndSentences }.map { it.sentence }
                audioCacheManager.recalculateReferenceStats(sheetName,allSentences)

                // Set Initial Success State
                _uiState.value = Format1UiState.Success(data = format1File.data)

                // 2. ✅ LISTEN FOR HISTORY CHANGES
                // When History updates (e.g. after a tap), we update 'lastUpdate'.
                // This forces the Screen to recompose and call 'isHeard()' again.
//                historyManager.historyState.collect {
//                    _uiState.update { currentState ->
//                        if (currentState is Format1BUiState.Success) {
//                            currentState.copy(lastUpdate = System.currentTimeMillis())
//                        } else currentState
//                    }
//                }
            }.onFailure { error ->
                _uiState.value = Format1UiState.Error(error.localizedMessage ?: "Failed to load")
            }
        }
    }



    // ✅ HELPER: View calls this directly during rendering
    fun isHeard(sentence: String): Boolean {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        //Timber.i("Play Count:${historyManager.getPlayCount("Reference", contentID) } $sentence")
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
    private fun refreshUI() {
        _uiState.update { currentState ->
            if (currentState is Format1UiState.Success) {
                currentState.copy(lastUpdate = System.currentTimeMillis())
            } else currentState
        }
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

}