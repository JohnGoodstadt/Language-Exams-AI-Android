package com.goodstadt.john.language.exams.viewmodels


import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.data.repository.FirebaseAudioService
import com.goodstadt.john.language.exams.data.repository.RecallingRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.managers.AudioCacheManager
import com.goodstadt.john.language.exams.managers.DAILY_LIMIT
import com.goodstadt.john.language.exams.managers.GlobalLoadingManager
import com.goodstadt.john.language.exams.managers.HOURLY_LIMIT
import com.goodstadt.john.language.exams.managers.HistorySyncManager
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Format0Word
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.utils.AnalyticsHelper
import com.goodstadt.john.language.exams.utils.CategoryProgress
import com.goodstadt.john.language.exams.utils.calcIsTodayNotAFreePassDay
import com.goodstadt.john.language.exams.utils.getDaysSinceInstall
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface CategoryTabUiState {
    object Loading : CategoryTabUiState
    data class Success(
        val categories: List<Category>,
        val currentTabNumber: Int = 1,
        val totalWordsOnTab: Int,
        val heardCountOnTab: Int, // Still needed for the Progress Bar
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
    @ApplicationContext private val context: Context,
    private val contentRepository: ContentRepository,
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val historyManager: HistorySyncManager,
    private val audioCacheManager: AudioCacheManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val recallingRepository: RecallingRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val ttsStatsRepository: TTSStatsRepository,
    private val xpManager: XPManager,
    private val billingRepository: BillingRepository,
    private val loadingManager: GlobalLoadingManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<CategoryTabUiState>(CategoryTabUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()
    
    // Rate Limit State
    private val _showRateLimitSheet = MutableStateFlow(false)
    val showRateLimitSheet = _showRateLimitSheet.asStateFlow()
    private val _showRateDailyLimitSheet = MutableStateFlow(false)
    val showRateDailyLimitSheet = _showRateDailyLimitSheet.asStateFlow()
    private val _showRateHourlyLimitSheet = MutableStateFlow(false)
    val showRateHourlyLimitSheet = _showRateHourlyLimitSheet.asStateFlow()

    // Cache the level name for fast synchronous access in isHeard()
    private var currentLoadedLevel: String = "B1"
    private var playbackJob: Job? = null
    private val _showHelpSheet = MutableStateFlow(false)

    // MARK: - Show Help screen
    val showHelpSheet = _showHelpSheet.asStateFlow()

    // Internal session counter
    private var sessionPlayCount = 0
    private var hasSeenHelp = false

    init {
        observeHistoryChanges() //do I need this now?
        observeRecallingChanges()
        initializeBilling()
        viewModelScope.launch {
            hasSeenHelp = userPreferencesRepository.hasSeenHelpSheetFlow.first()
        }
    }

    // MARK: - Reactive Listeners

    private fun observeHistoryChanges() {
        viewModelScope.launch {
            // ✅ Listen for History Changes
            historyManager.historyState.collect { _ ->

                // When History updates, we update the timestamp to force the View to redraw.
                // We also recalculate the 'cachedAudioCount' for the progress bar.
                _uiState.update { currentState ->
                    if (currentState is CategoryTabUiState.Success) {

                        // Recalculate progress bar count on the fly
                        // (This is cheaper than building the whole Set<String>)
//                        val (heard, _) = calculateTabStats(currentState.categories, historyMap)
//                        val heardCount = calculateCurrentHeardCount(currentState.categories)
//                        val (heard, total) = calculateTabSpecificStats(currentState.categories)

                        currentState.copy(
//                            heardCountOnTab = heard,
//                            totalWordsOnTab = total,
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

                // ✅ FIX: Calculate the initial Heard Count immediately
//                val initialHeardCount = calculateCurrentHeardCount(tabCategories)
                val (heardOnTab, total) = calculateTabSpecificStats(tabCategories)

                Timber.i("loadContentForTab() tabNumber:$tabNumber heardOnTab:$heardOnTab total:$total")
                Timber.i("")
                // Initial Stats
                // Note: We pass 'emptyMap()' initially; the observeHistoryChanges block will
                // fire immediately after with real data to fill in the correct count.
//                val total = tabCategories.sumOf { it.words.size }

                _uiState.value = CategoryTabUiState.Success(
                    categories = tabCategories,
                    totalWordsOnTab = total,
                    heardCountOnTab = heardOnTab,
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
    private fun refreshUI() {
        _uiState.update { currentState ->
            if (currentState is CategoryTabUiState.Success) {
                currentState.copy(lastUpdate = System.currentTimeMillis())
            } else currentState
        }
    }
    fun isHeard(sentence: String): Boolean {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        return historyManager.isHeard(currentLoadedLevel, contentID)
    }
    fun getPlayCount(sentence:String): Int {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        return historyManager.getPlayCount(currentLoadedLevel, contentID)
    }
    // MARK: - Playback Logic
    fun handleTap(sentence: String, category: Category) {
//        val contentID = FirebaseAudioService.generateContentID(sentence)
        //val wasAlreadyHeard = historyManager.isHeard("Reference", contentID)

        //if still playing handle it
        playbackJob?.cancel()
        contentRepository.stopPlayback()
        _uiState.update {
            if (it is CategoryTabUiState.Success) it.copy(playbackState = PlaybackState.Idle) else it
        }


        //Do rate limiting checks



//        // 2. ⚡️ OPTIMISTIC UPDATE (Lightning)
//        // This turns the Red Dot ON immediately.
//        didPlayVocabSentence(sentence, category.title, category.tabNumber)

        // 2. START NEW JOB
        playbackJob = viewModelScope.launch {
            val todayIsNotAFreePassDay = calcIsTodayNotAFreePassDay(userPreferencesRepository)
            if (!isPremiumUser.value && todayIsNotAFreePassDay) { //if premium user don't check credits or is on day 1
                if (rateLimiter.doIForbidCall()) {
                    val failType = rateLimiter.canMakeCallWithResult()
                    Timber.v("${failType.canICallAPI}")
                    Timber.v("${failType.failReason}")
                    Timber.v("${failType.timeLeftToWait}")
                    if (!failType.canICallAPI) {
                        if (failType.failReason == SimpleRateLimiter.FailReason.DAILY) {
                            _showRateDailyLimitSheet.value = true
                        } else {
                            _showRateHourlyLimitSheet.value = true
                        }
                    } else {
                        _showRateLimitSheet.value = true
                    }

                    val dayNum = getDaysSinceInstall(context)
                    val limitType = if (failType.failReason == SimpleRateLimiter.FailReason.DAILY) "daily" else "hourly"
                    // 2. Log the Hit
                    // This answers: "Are 50/150 too strict?"
                    AnalyticsHelper.logRateLimitHit(
                        context = context,
                        limitType = limitType,
                        currentCount = if (limitType == "daily") DAILY_LIMIT else HOURLY_LIMIT
                    )

                    return@launch
                }
            }


            val levelName = userPreferencesRepository.selectedSkillLevelFlow.first() // e.g. "B1"
            val loadingJob = launch {
                delay(2000) // Wait 1 second
                // If we haven't been cancelled yet, show the spinner
                loadingManager.show()
            }
            val success = audioPlaybackRepository.playTrackAndGetResult(
                sentence = sentence,
                level = levelName,
                sheetName = "", // Main tabs aggregate by Level, not SheetName
                isPremiumUser = isPremiumUser.value // Replace with actual check if available
            )


            loadingJob.cancel() // ✅ Cancel the 1s timer if it's still running
            loadingManager.hide() // ✅ Hide the spinner if it was showing

            if (success) {
                // 2. ⚡️ NON OPTIMISTIC UPDATE (Lightning). Now that playback is async
                // This turns the Red Dot ON immediately.
                didPlayVocabSentence(sentence, category.title, category.tabNumber)

                _uiState.update { currentState ->
                    if (currentState is CategoryTabUiState.Success) {
                        currentState.copy(
                            heardCountOnTab = currentState.heardCountOnTab + 1
                        )
                    } else currentState
                }


                checkHelpTrigger()
            }else{
                // ❌ FAILURE
                _uiEvent.emit(UiEvent.ShowSnackbar("Playback failed"))
                _uiState.update {
                    if (it is CategoryTabUiState.Success) it.copy(playbackState = PlaybackState.Error("Failed")) else it
                }
            }
//            else{
//                if (!wasAlreadyHeard) {
//                    historyManager.undoMarkSentenceHeard(levelName, contentID)
//                }
//            }

            refreshUI()
        }
    }

    private fun checkHelpTrigger() {
        if (hasSeenHelp) return // Already seen it forever

        sessionPlayCount++

        if (sessionPlayCount >= 5) {
            viewModelScope.launch {
                // 1. Mark as seen in DB immediately so it doesn't trigger again
                hasSeenHelp = true
                userPreferencesRepository.setHasSeenHelpSheet(true)

                // 2. Wait a moment so the user isn't overwhelmed instantly after tapping
                delay(1500) // 1.5 seconds delay

                // 3. Show Sheet
                _showHelpSheet.value = true
            }
        }
    }

    fun dismissHelpSheet() {
        _showHelpSheet.value = false
    }

    fun onResume() {
        // If data changed while app was backgrounded (e.g. sync), this ensures we see it
        refreshUI()
    }
    private fun undoPlayReferenceSentenceTODO(sentence: String) {
        /*
                val contentID = FirebaseAudioService.generateContentID(sentence)
        val levelName = ""

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
         */

    }
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
            didPlayVocabSentence(sentenceText, category.title, category.tabNumber)

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

    private fun didPlayVocabSentence(sentence: String, categoryTitle: String, categoryTabNumber: Int) {

        viewModelScope.launch {
            val levelName = userPreferencesRepository.selectedSkillLevelFlow.first() // e.g. "B1"
            val contentID = FirebaseAudioService.generateContentID(sentence)

            val previousCount = historyManager.getPlayCount(levelName, contentID)
            val isFirstTime = previousCount == 0

            // 2. Update History (Source of Truth)
            // ✅ This triggers 'historyState' emission -> 'init' collector runs -> UI Recomposes -- inc heard by 1
            historyManager.markSentenceHeard(levelName, contentID)

            // 3. Update Graph Stats (If new)
            if (isFirstTime) {
                audioCacheManager.updateVocabStats(
                    categoryTitle = categoryTitle,
                    tabNumber = categoryTabNumber
                )
            }
            refreshUI()
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
    fun saveDataOnExit() {
        historyManager.flushToFirebase()
        xpManager.logSessionDensity()
    }
    
    
    // MARK: - Billing for IAP
    fun connectToBilling() {
        viewModelScope.launch { billingRepository.startConnection() }
    }

    fun buyPremiumButtonPressed(activity: Activity) {
        viewModelScope.launch { billingRepository.launchPurchase(activity) }
    }
    private fun initializeBilling() {
        viewModelScope.launch {
            try {
                billingRepository.connect()
                billingRepository.checkPurchases()
                billingRepository.logCurrentStatus()  // Debug log on init
            } catch (e: Exception) {
                Timber.e("${e.message}")
                FirebaseCrashlytics.getInstance().recordException(Exception("CategoryTabViewModel.initializeBilling().catch. ${e.localizedMessage}"))

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
            val wasAlreadyHeard = historyManager.isHeard(levelName, contentID)

            // 3. UI Feedback: Show "Playing" spinner/icon on the row
//            val uiFilename = FirebaseAudioService.generateUnifiedFilename(sentence, voiceName)
//            _uiState.update {
//                if (it is CategoryTabUiState.Success) {
//                    it.copy(playbackState = PlaybackState.Playing(uiFilename))
//                } else it
//            }

            // 4. Capture "Before" State (For Rollback logic)
            // We need to know if the user HAD the red dot before they tapped.


            // 5. OPTIMISTIC UPDATE (Instant Gratification)
            // This updates History (Red Dot) and AudioCacheManager (Progress Bar) immediately.
            audioCacheManager.didPlayVocabSentence(
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

//    private fun calculateCurrentHeardCount(categories: List<Category>): Int {
//        var count = 0
//        val level = currentLoadedLevel // e.g. "B1"
//
//        for (cat in categories) {
//            for (word in cat.words) {
//                val sentence = word.sentences.firstOrNull()?.sentence ?: continue
//                val contentID = FirebaseAudioService.generateContentID(sentence)
//
//                // Check History directly
//                if (historyManager.isHeard(level, contentID)) {
//                    count++
//                }
//            }
//        }
//        return count
//    }
    /**
     * Calculates stats strictly for the provided list of categories.
     * Since 'categories' in our State is already filtered by Tab, this gives Tab-specific numbers.
     */
    private fun calculateTabSpecificStats(categories: List<Category>): Pair<Int, Int> {
        var heardCount = 0
        var totalCount = 0



        // We use the cached level (e.g. "B1")
        val level = currentLoadedLevel

        for (category in categories) {
            // 1. Sum up Total words in this category
            totalCount += category.words.size

//            if (category.tabNumber != tabNumber) {
//                continue
//            }
            // 2. Sum up Heard words in this category
            for (word in category.words) {
                val sentence = word.sentences.firstOrNull()?.sentence ?: continue
                val contentID = FirebaseAudioService.generateContentID(sentence)

                // Check History
                if (historyManager.isHeard(level, contentID)) {
                    heardCount++
                }
            }
        }

        return Pair(heardCount, totalCount)
    }
    fun getPlayCount(word: Format0Word): Int {
        word.sentences.firstOrNull()?.let { sentenceEntry ->
            return getPlayCount(sentenceEntry.sentence)
        }
        return 0
    }
}