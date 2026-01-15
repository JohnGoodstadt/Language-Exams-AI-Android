package com.goodstadt.john.language.exams.screens.reference

import android.app.Activity
import android.util.Log
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
import com.goodstadt.john.language.exams.data.repository.PlaybackResult
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.managers.AudioCacheManager
import com.goodstadt.john.language.exams.managers.HistorySyncManager
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.models.AppUIManifest
import com.goodstadt.john.language.exams.models.AudioPlaybackStatus
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Format0Word
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.SubTabDefinition
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

sealed interface ContentState {
    object Idle : ContentState // The initial state before anything is loaded
    object Loading : ContentState
    data class Success(val categories: List<Category>) : ContentState
    data class Error(val message: String) : ContentState
}

// 2. The main UI State data class for the entire screen
data class GroupedSheetUiState(
    val title: String = "", // The main title for the screen (e.g., "Adjectives")
    val currentSheetName: String = "",
    val subTabs: List<SubTabDefinition> = emptyList(),
    val selectedSubTab: SubTabDefinition? = null,
    val contentState: ContentState = ContentState.Idle,
    val lastUpdate: Long = System.currentTimeMillis()
    // You could also add PlaybackState and other sheet visibility booleans here
    // if this screen will also play audio, just like in your other ViewModels.
)

@HiltViewModel
class GroupedSheetViewModel @Inject constructor(
    private val vocabRepository: ContentRepository,
    private val appConfigRepository: AppConfigRepository,
//    private val examSheetRepository: ExamSheetRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val connectivityRepository: ConnectivityRepository,
    private val ttsStatsRepository: TTSStatsRepository,
    private val billingRepository: BillingRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val historyManager: HistorySyncManager,
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val audioCacheManager: AudioCacheManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupedSheetUiState())
    val uiState = _uiState.asStateFlow()

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    private val _showRateLimitSheet = MutableStateFlow(false)
    val showRateLimitSheet = _showRateLimitSheet.asStateFlow()

    private val _showRateDailyLimitSheet = MutableStateFlow(false)
    val showRateDailyLimitSheet = _showRateDailyLimitSheet.asStateFlow()

    private val _showRateHourlyLimitSheet = MutableStateFlow(false)
    val showRateHourlyLimitSheet = _showRateHourlyLimitSheet.asStateFlow()

    // A simple in-memory cache to avoid re-fetching data when a user taps back and forth
    private val contentCache = mutableMapOf<String, List<Category>>()


    init {
        // Get the parent tab's ID from the navigation arguments
        val tabId: String? = savedStateHandle.get("tabId")
        if (tabId != null) {
            initializeState(tabId)
        } else {
            _uiState.update { it.copy(contentState = ContentState.Error("Parent Tab ID was not provided.")) }
        }
    }

    private fun initializeState(tabId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(contentState = ContentState.Loading) }
            Timber.d("GroupedVM: Initializing for tabId: '$tabId'")


            try {
                // 1. Get the entire, up-to-date manifest from the repository.
                //    This single call gets all the configuration data we need.
                val manifest = appConfigRepository.getAppUiManifest()

                Timber.d("GroupedVM: Manifest fetched. Registry contains ${manifest.sheetRegistry.size} items.")

                // 2. Look up our specific tab's definition in the registry using the tabId.
                val myTabDefinition = manifest.sheetRegistry[tabId]

                // 3. Get the list of sub-tabs from that definition.
                val mySubTabs = myTabDefinition?.subTabs

                // 4. Check if we found everything we need.
                if (myTabDefinition != null && !mySubTabs.isNullOrEmpty()) {

                    // --- SUCCESS PATH ---

                    val initialSubTab = mySubTabs.first()
                    Timber.d("GroupedVM: Success! Found ${mySubTabs.size} sub-tabs. Initial selection is '${initialSubTab.title}'.")

                    // Update the UI state with the list of sub-tabs and the initial selection.
                    _uiState.update {
                        it.copy(
                            title = myTabDefinition.title,
                            subTabs = mySubTabs,
                            selectedSubTab = initialSubTab
                        )
                    }

                    // Now that the state is initialized, load the actual content for the first sub-tab.
                    loadContentForSubTab(initialSubTab)

                } else {

                    // --- FAILURE PATH ---

                    // Log detailed errors to help with debugging.
                    if (myTabDefinition == null) {
                        Timber.e("GroupedVM: FATAL! Could not find definition for '$tabId' in the sheetRegistry.")
                        Timber.e("GroupedVM: Available keys in registry are: ${manifest.sheetRegistry.keys}")
                    }
                    if (mySubTabs.isNullOrEmpty()) {
                        Timber.e("GroupedVM: FATAL! Found definition for '$tabId', but its 'subTabs' array is null or empty.")
                    }

                    _uiState.update { it.copy(contentState = ContentState.Error("Configuration for tab '$tabId' is invalid or missing.")) }
                }
            } catch (e: Exception) {
                // Catch any other unexpected errors during the process.
                Timber.e(e, "Group_VM: A critical exception occurred during initialization.")
                _uiState.update {
                    it.copy(
                        contentState = ContentState.Error(
                            e.localizedMessage ?: "An unknown error occurred."
                        )
                    )
                }
            }
        }
    }

    /**
     * Called by the UI when the user selects a different sub-tab from the picker.
     */
    fun onSubTabSelected(subTab: SubTabDefinition) {
        // Do nothing if the user taps the already selected tab
        if (_uiState.value.selectedSubTab == subTab) return

        _uiState.update { it.copy(selectedSubTab = subTab) }
        loadContentForSubTab(subTab)
    }

    /**
     * Loads the vocab data for a given sub-tab, utilizing an in-memory cache.
     */

    private fun loadContentForSubTab(subTab: SubTabDefinition) {
        viewModelScope.launch {
            val sheetName = subTab.firestoreDocumentId


            val cachedCategories = contentCache[subTab.firestoreDocumentId]
            if (cachedCategories != null) {
                _uiState.update { it.copy(contentState = ContentState.Success(cachedCategories)) }
                return@launch
            }

            _uiState.update {
                it.copy(
                    contentState = ContentState.Loading,
                    currentSheetName = sheetName
                )
            }

            try {
                // --- VERSION CHECK LOGIC ---
                // 1. Get all remote versions.
                val remoteVersions = appConfigRepository.getRemoteSheetVersions()
                val remoteVersion = remoteVersions[sheetName] ?: 1
                val localVersion = appConfigRepository.getLocalVersion(sheetName)
                val forceRefresh = remoteVersion > localVersion
                Timber.d("GroupedVM: Sheet '$sheetName' -> Remote v$remoteVersion, Local v$localVersion, Force refresh: $forceRefresh")

                // 5. Fetch from the repository with the forceRefresh flag.

                Timber.i("GroupedSheetViewModel: Attempting to fetch generic vocab for '$sheetName'...")
//                val result = examSheetRepository.getVocabSheet(sheetName, forceRefresh = forceRefresh)
                val result = vocabRepository.getFormat0Data(sheetName)
                result.onSuccess { vocabFile ->
                    val categories = vocabFile.categories
                    contentCache[sheetName] = categories
                    _uiState.update { it.copy(contentState = ContentState.Success(categories)) }

                    val allSentences = vocabFile.categories //sort out stats
                        .flatMap { it.words }
                        .flatMap { it.sentences }
                        .map { it.sentence }

                    audioCacheManager.recalculateReferenceStats(sheetName, allSentences)


                    // 6. If we refreshed, update the local version.
                    if (forceRefresh) {
                        appConfigRepository.updateLocalVersion(sheetName, remoteVersion)
                    }
                }
                result.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            contentState = ContentState.Error(
                                error.localizedMessage ?: "Failed to load content."
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        contentState = ContentState.Error(
                            e.localizedMessage ?: "An unexpected error occurred."
                        )
                    )
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

    fun isHeard(sentence: String): Boolean {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        return historyManager.getPlayCount("Reference", contentID) > 0
    }

    fun getPlayCount(sentence: String): Int {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        return historyManager.getPlayCount("Reference", contentID)
    }

    // ✅ ACTION: View calls this on tap
//    fun handleTapObsolete(sentence: String) {
//        viewModelScope.launch {
//            // 1. Play Audio (Waterfall)
//            val success = audioPlaybackRepository.playTrackAndGetResult(
//                sentence = sentence,
//                level = "Reference",
//                sheetName = _uiState.value.currentSheetName
//            )
//            // 2. Update Graph Stats (If success)
//            if (success) {
//                didPlayReferenceSentence(sentence)
//            }
//        }
//        historyManager.debugPrintAllHistory()
//    }
    fun handleTap(sentence: String) {
        viewModelScope.launch {

            // 1. CALL REPOSITORY
            // The Repository handles everything: Playback, History, XP, and Graph Stats.
            val status = audioPlaybackRepository.playTrackAndGetStatus(
                sentence = sentence,
                level = "Reference",
                sheetName = _uiState.value.currentSheetName, // Important: Pass this so Graph Stats update!
                isPremiumUser = false
            )

            when (status) {
                // Group all success cases together
                is AudioPlaybackStatus.PlayedFromLocalCache,
                is AudioPlaybackStatus.PlayedFromCloudStorage,
                is AudioPlaybackStatus.PlayedFromTTSAPI -> {
                    refreshUI()
                }

                is AudioPlaybackStatus.RateLimited -> {
                    // Show Paywall logic
                    Timber.i("Format1ViewModel.handleTap().AudioPlaybackStatus.RateLimited ")
                }

                is AudioPlaybackStatus.Failure -> {
                    // Show Snackbar logic
                    Timber.i("Format1ViewModel.handleTap().AudioPlaybackStatus.Failure")
                }
            }
        }
    }
    // Keep this helper to force redraw
    private fun refreshUI() {
        _uiState.update { currentState ->
//            if (currentState is GroupedSheetUiState) {
                currentState.copy(lastUpdate = System.currentTimeMillis())
//            } else currentState
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
            val sheetTitle = _uiState.value.currentSheetName
            val currentStats = audioCacheManager.getReferenceStats(sheetTitle)
            audioCacheManager.updateReferenceStats(
                key = sheetTitle,
                heard = currentStats.heard + 1,
                total = currentStats.total
            )
        }
    }
    fun getAudioCacheManager(): AudioCacheManager = audioCacheManager
    fun getAIParagraphCount(): Int = audioCacheManager.getAIParagraphCount()
    fun getAIParagraphHeardCount(): Int = audioCacheManager.getAIParagraphHeardCount()
    fun getCachedManifest(): AppUIManifest? {
        return appConfigRepository.getAppUiManifest()
    }

    fun incSideQuestStat() {
        val sheetName = uiState.value.currentSheetName
        val statName = "${TTSStatsRepository.Companion.statSideQuestCount}_$sheetName"

        ttsStatsRepository.inc(
            TTSStatsRepository.fsDOC.GlobalStats,
            statName
        )
    }

    fun incQuizSheetStat() {
        val sheetName = uiState.value.currentSheetName
        val statName = "${TTSStatsRepository.Companion.statSheetQuizCount}_$sheetName"

        ttsStatsRepository.inc(
            TTSStatsRepository.fsDOC.GlobalStats,
            statName
        )
    }
}
