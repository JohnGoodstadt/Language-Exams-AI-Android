package com.goodstadt.john.language.exams.viewmodels

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.repository.*
import com.goodstadt.john.language.exams.managers.*
import com.goodstadt.john.language.exams.models.AppUIManifest
import com.goodstadt.john.language.exams.models.AudioPlaybackStatus
import com.goodstadt.john.language.exams.models.Format3File
import com.goodstadt.john.language.exams.models.SubTabDefinition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// Separate UI State for Format 2
data class Format3GroupedUiState(
    val title: String = "",
    val currentSheetName: String = "",
    val subTabs: List<SubTabDefinition> = emptyList(),
    val selectedSubTab: SubTabDefinition? = null,
    val isLoading: Boolean = false,
    val error: String? = null,

    // Content Data
    val currentFormat3File: Format3File? = null,

    // Reactive Red Dots
    val heardSentenceIDs: Set<String> = emptySet(),
    val lastUpdate: Long = System.currentTimeMillis()
)

@HiltViewModel
class Format3GroupedViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val appConfigRepository: AppConfigRepository,
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val historyManager: HistorySyncManager,
    private val audioCacheManager: AudioCacheManager,
    private val billingRepository: BillingRepository,
    private val ttsStatsRepository: TTSStatsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val parentTabId: String = savedStateHandle.get<String>("tabId")!!

    private val _uiState = MutableStateFlow(Format3GroupedUiState())
    val uiState = _uiState.asStateFlow()

    // Rate Limit Sheets
    private val _showRateLimitSheet = MutableStateFlow(false)
    val showRateLimitSheet = _showRateLimitSheet.asStateFlow()
    private val _showRateDailyLimitSheet = MutableStateFlow(false)
    val showRateDailyLimitSheet = _showRateDailyLimitSheet.asStateFlow()
    private val _showRateHourlyLimitSheet = MutableStateFlow(false)
    val showRateHourlyLimitSheet = _showRateHourlyLimitSheet.asStateFlow()

    // Cache to avoid re-fetching
    private val contentCache = mutableMapOf<String, Format3File>()

    init {
        initializeState(parentTabId)
        //observeHistory()
    }

    private fun initializeState(tabId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // 1. Get Manifest
                val manifest = appConfigRepository.getAppUiManifest()
                val myTabDefinition = manifest.sheetRegistry[tabId]
                val mySubTabs = myTabDefinition?.subTabs

                if (myTabDefinition != null && !mySubTabs.isNullOrEmpty()) {
                    val initialSubTab = mySubTabs.first()

                    _uiState.update {
                        it.copy(
                            title = myTabDefinition.title,
                            subTabs = mySubTabs,
                            selectedSubTab = initialSubTab
                        )
                    }
                    loadContentForSubTab(initialSubTab)
                } else {
                    _uiState.update { it.copy(error = "Configuration missing for $tabId", isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage, isLoading = false) }
            }
        }
    }

    fun onSubTabSelected(subTab: SubTabDefinition) {
        if (_uiState.value.selectedSubTab == subTab) return
        _uiState.update { it.copy(selectedSubTab = subTab) }
        loadContentForSubTab(subTab)
    }

    private fun loadContentForSubTab(subTab: SubTabDefinition) {
        val sheetName = subTab.firestoreDocumentId ?: return

        viewModelScope.launch {
            // Check Cache
            if (contentCache.containsKey(sheetName)) {
                _uiState.update {
                    it.copy(
                        currentFormat3File = contentCache[sheetName],
                        currentSheetName = sheetName,
                        isLoading = false, error = null
                    )
                }
                // Trigger stat refresh even on cache hit
                recalculateStats(contentCache[sheetName]!!, sheetName)
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, currentSheetName = sheetName) }

            // Fetch Format 2
            val result = contentRepository.getFormat3Data(sheetName)

            result.onSuccess { format3File ->
                contentCache[sheetName] = format3File

                // 1. Update Graph Stats
                recalculateStats(format3File, sheetName)

                // 2. Update UI
                _uiState.update {
                    it.copy(
                        currentFormat3File = format3File,
                        isLoading = false,
                        error = null
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.localizedMessage, isLoading = false) }
            }
        }
    }

    // MARK: - Stats Logic

    private fun recalculateStats(file: Format3File, sheetName: String) {
//        val allSentences = file.data
//            .flatMap { it.wordsAndSentences }
//            .flatMap { it.sentences }
//            .map { it.sentence }
//
//        audioCacheManager.recalculateReferenceStats(sheetName, allSentences)

        // Force history refresh (Red Dots)
  //      refreshRedDots(file)
    }

//    private fun observeHistory() {
//        viewModelScope.launch {
//            historyManager.historyState.collect { _ ->
//                // Whenever history updates, re-check the current file's dots
//                _uiState.value.currentFormat3File?.let { file ->
//                    refreshRedDots(file)
//                }
//            }
//        }
//    }

//    private fun refreshRedDots(file: Format3File) {
//        val allSentences = file.data
//            .flatMap { it.wordsAndSentences }
//            .flatMap { it.sentences }
//            .map { it.sentence }
//
//        val newHeardSet = mutableSetOf<String>()
//
//        // Check History Manager (Synchronous check against its cached map)
//        // Since we are inside the 'collect' block or load success, we have latest data
//        for (sentence in allSentences) {
//            val id = FirebaseAudioService.generateContentID(sentence)
//            if (historyManager.isHeard("Reference", id)) {
//                newHeardSet.add(id)
//            }
//        }
//
//        _uiState.update { it.copy(heardSentenceIDs = newHeardSet) }
//    }

    fun isHeard(sentence: String): Boolean {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        return historyManager.getPlayCount("Reference", contentID) > 0
    }
    fun getPlayCount(sentence:String): Int {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        return historyManager.getPlayCount("Reference", contentID)
    }
    private fun didPlayReferenceSentence(sentence: String,sheetName:String) {
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
           // val sheetTitle = sheetName
            val currentStats = audioCacheManager.getReferenceStats(sheetName)
            audioCacheManager.updateReferenceStats(
                key = sheetName,
                heard = currentStats.heard + 1,
                total = currentStats.total
            )
        }

        refreshUI()
    }
    // MARK: - Helpers & Billing
    private fun refreshUI() {
        _uiState.update { currentState ->
            currentState.copy(lastUpdate = System.currentTimeMillis())
        }
    }
    fun getAudioCacheManager() = audioCacheManager
    fun getAIParagraphCount() = audioCacheManager.getAIParagraphCount()
    fun getAIParagraphHeardCount() = audioCacheManager.getAIParagraphHeardCount()

    fun buyPremiumButtonPressed(activity: Activity) {
        viewModelScope.launch { billingRepository.launchPurchase(activity) }
    }

    fun hideDailyRateLimitSheet() { _showRateDailyLimitSheet.value = false }
    fun hideHourlyRateLimitSheet() { _showRateHourlyLimitSheet.value = false }
    fun hideRateOKLimitSheet() { _showRateLimitSheet.value = false }
    fun getCachedManifest(): AppUIManifest? {
        return appConfigRepository.getAppUiManifest()
    }

    fun incSideQuestStat() {
        val sheetName = _uiState.value.currentSheetName
        val statName = "${TTSStatsRepository.Companion.statSideQuestCount}_$sheetName"

        ttsStatsRepository.inc(
            TTSStatsRepository.fsDOC.GlobalStats,
            statName
        )
    }
    fun incQuizSheetStat() {
        val sheetName = _uiState.value.currentSheetName
        val statName = "${TTSStatsRepository.Companion.statSheetQuizCount}_$sheetName"

        ttsStatsRepository.inc(
            TTSStatsRepository.fsDOC.GlobalStats,
            statName
        )
    }
}