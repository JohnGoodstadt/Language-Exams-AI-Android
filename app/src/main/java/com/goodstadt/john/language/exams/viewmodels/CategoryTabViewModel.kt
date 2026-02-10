package com.goodstadt.john.language.exams.viewmodels


import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.data.FirestoreRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.data.repository.FirebaseAudioService
import com.goodstadt.john.language.exams.data.repository.RecallingRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.managers.AudioCacheManager
import com.goodstadt.john.language.exams.managers.GlobalLoadingManager
import com.goodstadt.john.language.exams.managers.HistorySyncManager
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.managers.XpActionType
import com.goodstadt.john.language.exams.models.AudioPlaybackStatus
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Format0Word
import com.goodstadt.john.language.exams.utils.CategoryProgress
import com.goodstadt.john.language.exams.utils.PlaybackEvent
import com.goodstadt.john.language.exams.utils.PlaybackEventBus
import com.goodstadt.john.language.exams.utils.RateLimitGuard
import com.goodstadt.john.language.exams.utils.calcIsTodayFreePassDay
import com.goodstadt.john.language.exams.utils.logging.TimberFault
import com.google.firebase.crashlytics.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    private val playbackEventBus: PlaybackEventBus,
    private val globalLoadingManager: GlobalLoadingManager,
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

    private val _showSpeakerSheet = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val showSpeakerSheet = _showSpeakerSheet.asSharedFlow()

    private val _showSPEAKERSheet = MutableStateFlow(false)
    val showSPEAKERSheet = _showSPEAKERSheet.asStateFlow()

    // MARK: - Show Help screen
    val showHelpSheet = _showHelpSheet.asStateFlow()
    private var lastPlayedSentence: String = ""

    // Internal session counter
    private var sessionPlayCount = 0
    private var hasSeenHelp = false

    // ✅ NEW: Expose the exam name to the UI
    val currentExamName = userPreferencesRepository.selectedExamNameFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "vocab_data_b1"
        )

    // ✅ NEW: Dynamic Banner State
    private val _celebrationTitle = MutableStateFlow("")
    val celebrationTitle = _celebrationTitle.asStateFlow()

    private val _celebrationSubtitle = MutableStateFlow("")
    val celebrationSubtitle = _celebrationSubtitle.asStateFlow()

    // ✅ 1. Local Cache for the keys
    private var currentRecalledKeys: Set<String> = emptySet()

    val hasSeenVoiceHelp: StateFlow<Boolean> = userPreferencesRepository.hasSeenVoiceHelp
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000), // Efficient lifecycle handling
            initialValue = false
        )

    val totalHeardFlow = audioCacheManager.totalExamWordsHeardOverall
    val totalCountFlow = audioCacheManager.totalExamWordCount

    init {
        observeHistoryChanges() //do I need this now?
        observeRecallingChanges()
        initializeBilling()
        viewModelScope.launch {
            hasSeenHelp = userPreferencesRepository.hasSeenHelpSheetFlow.first()

            playbackEventBus.events.collect { event ->
                if (event is PlaybackEvent.Completed) {
                    Timber.i("mp3 finished")

                    _showSpeakerSheet.tryEmit(Unit)

//                    nudgeStore.incrementPlayCount()
//
//                    val shown = nudgeStore.shownFlow.first()
//                    val count = nudgeStore.playCountFlow.first()
//
//                    if (!shown && count >= 15) {
//                        nudgeStore.markShown()
//                        _showSpeakerSheet.tryEmit(Unit)
//                    }
                }
            }
        }


//        if (BuildConfig.DEBUG) { //Just for Jan 2026
         //   firestoreRepository.updateUserInFirestore()
//        }

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

                        currentState.copy(
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
                // ✅ 2. Always update the local cache
                currentRecalledKeys = keys

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

//            val examName = userPreferencesRepository.selectedExamNameFlow.first()
            val voiceName = userPreferencesRepository.selectedVoiceNameFlow.first()

            // Cache the level for isHeard calls later
            currentLoadedLevel = userPreferencesRepository.selectedSkillLevelFlow.first()

            val result = contentRepository.getFormat0Data(currentExamName.value)

            result.onSuccess { vocabFile ->
                val tabCategories = vocabFile.categories.filter { it.tabNumber == tabNumber }

                // Initialize AudioCacheManager (Calculates Global Totals)
                audioCacheManager.setCurrentVocabFile(vocabFile, voiceName)

                // Initialize Recalling Set
               // val recalledKeys = recallingRepository.getAllRecalledKeys()

                // ✅ FIX: Calculate the initial Heard Count immediately
//                val initialHeardCount = calculateCurrentHeardCount(tabCategories)
                val (heardOnTab, total) = calculateTabSpecificStats(tabCategories)

                Timber.i("loadContentForTab() tabNumber:$tabNumber heardOnTab:$heardOnTab total:$total")

                // ✅ 3. Use the cached keys (or fetch fresh if empty/paranoid)
                // Since the collector in init started immediately, currentRecalledKeys
                // is likely already populated.

                // Fallback: If cache is empty, try a blocking fetch (optional safety)
                if (currentRecalledKeys.isEmpty()) {
                    currentRecalledKeys = recallingRepository.getAllRecalledKeys()
                }

                _uiState.value = CategoryTabUiState.Success(
                    categories = tabCategories,
                    totalWordsOnTab = total,
                    heardCountOnTab = heardOnTab,
                    recalledWordKeys = currentRecalledKeys //✅ USE THE CACHED VARIABLE
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

        lastPlayedSentence = sentence

        //if still playing handle it
        playbackJob?.cancel()
        contentRepository.stopPlayback()
        _uiState.update {
            if (it is CategoryTabUiState.Success) it.copy(playbackState = PlaybackState.Idle) else it
        }

        // 2. START NEW JOB
        //TODO: Should I check for local cache first?
        playbackJob = viewModelScope.launch {

            val levelName = userPreferencesRepository.selectedSkillLevelFlow.first() // e.g. "B1"

            val contentID = FirebaseAudioService.generateContentID(sentence)
            val wasAlreadyHeard = historyManager.isHeard(currentLoadedLevel, contentID)

            val success = false //TODO: forcing
            val result = audioPlaybackRepository.playTrackAndGetStatus(
                sentence = sentence,
                level = levelName,
                isPremiumUser = isPremiumUser.value // Replace with actual check if available
            )

//            if (success) {
//                // 2. ⚡️ NON OPTIMISTIC UPDATE (Lightning). Now that playback is async
//                // This turns the Red Dot ON immediately.
//                didPlayVocabSentence(sentence, category.title, category.tabNumber)
//
//                if (!wasAlreadyHeard) {
//                    checkSectionCompletionAfterNewSentence(category,sentence)
//                    _uiState.update { currentState ->
//                        if (currentState is CategoryTabUiState.Success) {
//                            currentState.copy(
//                                heardCountOnTab = currentState.heardCountOnTab + 1
//                            )
//                        } else currentState
//                    }
//                }
//
//                checkHelpTrigger()
//            }else if (false){
//                // ❌ FAILURE
//                _uiEvent.emit(UiEvent.ShowSnackbar("Playback failed"))
//                _uiState.update {
//                    if (it is CategoryTabUiState.Success) it.copy(playbackState = PlaybackState.Error("Failed")) else it
//                }
//            }

            when (result) {
                is AudioPlaybackStatus.PlayedFromTTSAPI,is AudioPlaybackStatus.PlayedFromLocalCache , is AudioPlaybackStatus.PlayedFromCloudStorage -> {
                    // Success! Stats already updated by Repo/AudioCacheManager.
                    // Just check for section completion.
                    // ⚡️ NON OPTIMISTIC UPDATE (Lightning). Now that playback is async
                    didPlayVocabSentence(sentence, category.title, category.tabNumber)



                    if (!wasAlreadyHeard) {
                        checkSectionCompletionAfterNewSentence(category, sentence)
                        _uiState.update { currentState ->
                            if (currentState is CategoryTabUiState.Success) {
                                currentState.copy(
                                    heardCountOnTab = currentState.heardCountOnTab + 1
                                )
                            } else currentState
                        }
                    }
                    checkHelpTrigger()


                    //TODO: could split off cloud storage here - if there are charges
                    if (result is AudioPlaybackStatus.PlayedFromLocalCache || result is AudioPlaybackStatus.PlayedFromCloudStorage){
                        ttsStatsRepository.updateTTSStatsWithoutCosts()
                    }else { // result is AudioPlaybackStatus.PlayedFromTTSAPI
                        val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
                        ttsStatsRepository.updateTTSStatsWithCosts(sentence, currentVoiceName)
                    }

                    if (calcIsTodayFreePassDay(userPreferencesRepository)){
                        //Let's see usage for hearing on Day 1 - immediately
                        ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.GlobalStats)
                        ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
                    }

                }

                is AudioPlaybackStatus.RateLimited -> {
                    // B. Show Limit Sheet
                    val failType = rateLimiter.canMakeCallWithResult()
                    Timber.w("Rate Limiter Triggered")
                    Timber.w("canICallAPI = %s", failType.canICallAPI)
                    Timber.w("failReason = %s", (failType.failReason))
                    Timber.w("timeLeftToWait = %s",failType.timeLeftToWait)
                    Timber.w(rateLimiter.printCurrentStatus)

                    if (result.failReason == SimpleRateLimiter.FailReason.DAILY) {
                        _showRateDailyLimitSheet.value = true
                    } else {
                        _showRateHourlyLimitSheet.value = true
                    }
                }

                is AudioPlaybackStatus.Failure -> {
                    _uiEvent.emit(UiEvent.ShowSnackbar("Playback failed. Please check your internet connection and try again"))
                    _uiState.update {
                        if (it is CategoryTabUiState.Success) it.copy(playbackState = PlaybackState.Error("Failed")) else it
                    }
                }
            }


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
    fun showSPEAKERSheet() {
        _showSPEAKERSheet.value = true
    }
    fun dismissSPEAKERSheet() {
        _showSPEAKERSheet.value = false
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

    private fun didPlayVocabSentence(sentence: String, categoryTitle: String, categoryTabNumber: Int) {

        viewModelScope.launch {
            val levelName = userPreferencesRepository.selectedSkillLevelFlow.first() // e.g. "B1"
            val contentID = FirebaseAudioService.generateContentID(sentence)

            val previousCount = historyManager.getPlayCount(levelName, contentID)
            val isFirstTime = previousCount == 0

            // 2. Update History (Source of Truth)
            // ✅ This triggers 'historyState' emission -> 'init' collector runs -> UI Recomposes -- inc heard by 1
           // historyManager.markSentenceHeard(levelName, contentID) //moved to playTrackAndGetResult()

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
            xpManager.registerAction(XpActionType.MasterWord)
        }
    }
    fun onFocusClickedObsolete(word: Format0Word) {
        viewModelScope.launch {
            // 1. Save to Disk
            recallingRepository.addWord(word)

            // 2. ✅ OPTIMISTIC UPDATE: Update UI State immediately
            // This turns the row Green instantly
            _uiState.update { currentState ->
                if (currentState is CategoryTabUiState.Success) {
                    val newSet = currentState.recalledWordKeys.toMutableSet()
                    newSet.add(word.word)
                    currentState.copy(recalledWordKeys = newSet)
                } else currentState
            }

            // 3. XP
            xpManager.registerAction(XpActionType.MasterWord)
        }
    }
    fun onCancelClickedObsolete(word: Format0Word) {
        viewModelScope.launch {
            recallingRepository.removeWord(word)
        }
    }
    fun onCancelClicked(word: Format0Word) {
        viewModelScope.launch {
            // 1. Update Repository (Source of Truth)
            // Note: Ensure your repo has a removeWord(Format0Word) or remove(String)
            recallingRepository.removeWord(word)

            // 2. ✅ OPTIMISTIC UPDATE: Update UI State immediately
            // This turns the row back to White instantly
            _uiState.update { currentState ->
                if (currentState is CategoryTabUiState.Success) {
                    val newSet = currentState.recalledWordKeys.toMutableSet()

                    // Remove the word from the set
                    newSet.remove(word.word)

                    currentState.copy(recalledWordKeys = newSet)
                } else currentState
            }

            // Optional: Log analytics or other actions
            // Timber.d("Removed focus: ${word.word}")
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
    fun hideHourlyRateLimitSheet() {
        _showRateHourlyLimitSheet.value = false
    }
    fun hideRateOKLimitSheet() { _showRateLimitSheet.value = false }

    fun calculateGrandTotalsOriginal(): Pair<Int, Int> {
        val heard = audioCacheManager.totalExamWordsHeardOverall.value
        val total = audioCacheManager.totalExamWordCount.value
        return Pair(heard, total)
    }

    fun buildCategoryProgress(): List<CategoryProgress> {
        return audioCacheManager.getAllCategoryProgress()
    }

    fun setTestExamGoal() {}


    fun calculateGrandTotalsNow(): Int {
//        Timber.i("The fresh total is: $freshHeard")
        return audioCacheManager.getFreshExamTotalHeard()
    }
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
    // UI State for Fireworks
    private val _showCelebration = MutableStateFlow(false)
    val showCelebration = _showCelebration.asStateFlow()

    // ...

    // MARK: - Completion Logic (Ported from iOS)

    private fun checkSectionCompletionAfterNewSentenceObsolete(category: Category,justPlayedSentence: String ) {
        val examName = runBlocking { userPreferencesRepository.selectedExamNameFlow.first() }
        val sectionKey = "${examName}|${category.title}" // Unique Key

        // 1. Check if already marked complete
        if (userPreferencesRepository.isSectionCompleted(examName, sectionKey)) {
            return
        }

        // 2. Check if all words in this category are heard
        val level = currentLoadedLevel // e.g. "B1"
        val justPlayedID = FirebaseAudioService.generateContentID(justPlayedSentence)

        var wordsHeard = 0

        for (wordEntry in category.words) {
            val sentence = wordEntry.sentences.firstOrNull()?.sentence ?: continue
            val contentID = FirebaseAudioService.generateContentID(sentence)

            // ✅ 2. THE FIX:
            // Check History OR Check if it is the sentence we just played.
            // This guarantees we count the current one even if History is 1ms slow.
            if (historyManager.isHeard(level, contentID)) {
                wordsHeard++
            }
            if (contentID == justPlayedID) {
                wordsHeard++
            }
        }

        // 3. If Complete
        if (wordsHeard == category.words.size) {
            // A. Save to Disk
            userPreferencesRepository.addCompletedSection(examName, sectionKey)

            // B. Award XP
            xpManager.registerAction(XpActionType.CompleteSection)

            // C. Trigger Firework UI
            triggerCelebration()

            Timber.i("🏆 Section Completed: ${category.title}")

            // D. Check Whole Sheet (Tab) Completion
            checkSheetCompletionIfNeeded(examName)
        }
    }
    private fun checkSectionCompletionAfterNewSentence(
        category: Category,
        justPlayedSentence: String
    ) {
        val examName = runBlocking { userPreferencesRepository.selectedExamNameFlow.first() }
        val sectionKey = "${examName}|${category.title}"

        // 1. Exit if already done
        if (userPreferencesRepository.isSectionCompleted(examName, sectionKey)) {
            return
        }

        val level = currentLoadedLevel // e.g. "B1"

        // ID of the sentence we just successfully played
        val justPlayedID = FirebaseAudioService.generateContentID(justPlayedSentence)

        // 2. THE INVERSE CHECK
        // We look for ANY item that is still "Unheard".
        // An item is "Unheard" if:
        // A. It is NOT in History
        // AND
        // B. It is NOT the item we just played

        val anyUnheardItem = category.words.find { wordEntry ->
            val sentence = wordEntry.sentences.firstOrNull()?.sentence

            if (sentence.isNullOrEmpty()) {
                false // Skip words with no sentences (they don't count against you)
            } else {
                val contentID = FirebaseAudioService.generateContentID(sentence)

                // Is this specific word unheard?
                // (It's unheard if History says 'No' AND it's not the one we just played)
                val isHeardInHistory = historyManager.isHeard(level, contentID)
                val isJustPlayed = (contentID == justPlayedID)

                // Return TRUE if we found an "Unheard" item
                !(isHeardInHistory || isJustPlayed)
            }
        }

        // Debugging Log
        if (anyUnheardItem != null) {
            Timber.d("🧐 Section '${category.title}' incomplete. Found unheard word: '${anyUnheardItem.word}'")
        }

        // 3. IF NOTHING IS UNHEARD -> COMPLETE!
        if (anyUnheardItem == null) {
            Timber.i("🏆 Section Completed: ${category.title}")

            // 1. Mark Complete
            userPreferencesRepository.addCompletedSection(examName, sectionKey)
            xpManager.registerAction(XpActionType.CompleteSection)

            // 2. ✅ CALCULATE LEVEL PROGRESS
            // We need the full list of categories for this Exam to know the Total.
            // AudioCacheManager holds the current full VocabFile.

            val allCategories = audioCacheManager.getCurrentVocabFile()?.categories ?: emptyList()


            val totalSections = allCategories.size

            // Count how many are done
            val completedCount = allCategories.count { cat ->
                val key = "${examName}|${cat.title}"
                userPreferencesRepository.isSectionCompleted(examName, key)
            }

            // 3. ✅ SET DYNAMIC BANNER TEXT
            if (completedCount == totalSections) {
                // LEVEL UP! (All sections done)
                _celebrationTitle.value = "LEVEL COMPLETE!"
                _celebrationSubtitle.value = "You mastered all $totalSections sections! +100 XP"
                xpManager.registerAction(XpActionType.CompletedSheet) // Big Bonus
            } else {
                // Standard Section
                _celebrationTitle.value = "Section Mastered!"
                _celebrationSubtitle.value = "$completedCount/$totalSections Completed • +20 XP"
            }

            // A. Save to Disk
            userPreferencesRepository.addCompletedSection(examName, sectionKey)

            // B. Award XP
            xpManager.registerAction(XpActionType.CompleteSection)

            // C. Trigger Firework UI
            triggerCelebration()

            // D. Check Whole Sheet
            checkSheetCompletionIfNeeded(examName)
        }
    }
    private fun checkSheetCompletionIfNeeded(examName: String) {
        // Logic: Get all categories in this tab from UI State
        val currentCategories = (uiState.value as? CategoryTabUiState.Success)?.categories ?: return

        val allSectionsComplete = currentCategories.all { cat ->
            val key = "${examName}|${cat.title}"
            userPreferencesRepository.isSectionCompleted(examName, key)
        }

        if (allSectionsComplete) {
            // Ideally, check if we already awarded the sheet badge to avoid duplicates
            // For now, just register the action
            xpManager.registerAction(XpActionType.CompletedSheet)
            Timber.i("🏆🏆 WHOLE TAB COMPLETED!")
        }
    }

    private fun triggerCelebration() {
        viewModelScope.launch {
            _showCelebration.value = true
            // Auto-hide handled in UI or via delay here
            kotlinx.coroutines.delay(4000)
            _showCelebration.value = false
        }
    }

    fun playSuccessSound() {
        globalLoadingManager.playSuccessSound(context)
    }
    fun getCurrentSkillLevel() : String {
        return currentLoadedLevel
    }
    fun getLatestSentence(): String {
        return lastPlayedSentence
    }
    // Combine the totals logic with the "seen" state

    fun markVoiceHelpAsSeen() {
        viewModelScope.launch {
            userPreferencesRepository.setHasSeenVoiceHelp()
        }
    }

    fun refreshGamificationStats() {
        audioCacheManager.forceStatsRecalculation()
    }
    suspend fun refreshGamificationStatsAndWait() {
        audioCacheManager.awaitFreshStats()
    }

    fun hideSheets() {
        _showRateDailyLimitSheet.value = false
        _showRateHourlyLimitSheet.value = false

    }

}