package com.goodstadt.john.language.exams.viewmodels

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.ConnectivityRepository
import com.goodstadt.john.language.exams.data.repository.PlaybackResult
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.data.repository.FirebaseAudioService
import com.goodstadt.john.language.exams.managers.AudioCacheManager
import com.goodstadt.john.language.exams.managers.HistorySyncManager
//import com.goodstadt.john.language.exams.managers.RateLimiterManager
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.models.AudioPlaybackStatus
import com.goodstadt.john.language.exams.models.Format0Word
import com.goodstadt.john.language.exams.utils.calcIsTodayNotAFreePassDay
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface SearchUiState {
    object Loading : SearchUiState
    data class Success(
        val results: List<SearchResult>,
        val lastUpdate: Long = System.currentTimeMillis()
    ) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

// A simple data class to hold a word and its first sentence for the flat list
data class SearchResult(
    val word: Format0Word,
    val firstSentence: String,
    val categoryTitle: String,
    val categoryTabNumber: Int
)

sealed interface SearchUiEvent {
    data class ShowSnackbar(val message: String, val actionLabel: String? = null) : SearchUiEvent
}

@OptIn(FlowPreview::class) // Needed for the debounce operator
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val vocabRepository: ContentRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ttsStatsRepository : TTSStatsRepository,
    private val billingRepository: BillingRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val connectivityRepository: ConnectivityRepository,
    private val historyManager: HistorySyncManager,
    private val audioCacheManager: AudioCacheManager,
    private val audioPlaybackRepository: AudioPlaybackRepository,
    ) : ViewModel() {

    // Holds the complete list of all words from the current file
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // B. The Master Copy (Source of Truth)
    // Holds 100% of the data. Does NOT trigger UI updates when changed.
    private var allSearchResults: List<SearchResult> = emptyList()

    // C. The Live Feed (UI State)
    // Holds the *filtered* subset. Triggers UI updates.
//    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
//    val searchResults = _searchResults.asStateFlow()

    // Loading State
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()




    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Loading)
    val uiState = _uiState.asStateFlow()


    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    // Re-use the playback state from the TabsViewModel
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<SearchUiEvent>()
//    val uiEvent = _uiEvent.asSharedFlow()

    //NOTE: rate Limiting
//    private val rateLimiter = RateLimiterManager.getInstance()
    private val _showRateLimitSheet = MutableStateFlow(false)
    val showRateLimitSheet = _showRateLimitSheet.asStateFlow()

    private val _showRateDailyLimitSheet = MutableStateFlow(false)
    val showRateDailyLimitSheet = _showRateDailyLimitSheet.asStateFlow()

    private val _showRateHourlyLimitSheet = MutableStateFlow(false)
    val showRateHourlyLimitSheet = _showRateHourlyLimitSheet.asStateFlow()
    private var currentLoadedLevel: String = "B1"

    init {
        observeSearchQuery()
        loadData()

        viewModelScope.launch {
            currentLoadedLevel = userPreferencesRepository.selectedSkillLevelFlow.first()
            billingRepository.isPurchased.collect { purchasedStatus ->
                _isPremiumUser.value = purchasedStatus
                if (DEBUG) {
                    billingRepository.logCurrentStatus()
                }
            }
        }
    }
    // MARK: - 2. Data Loading (Populating the Master)

    private fun loadData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true

            // Get the current Exam Name (e.g. "EnglishB1Vocab")
            val examName = userPreferencesRepository.selectedExamNameFlow.first()

            val result = vocabRepository.getFormat0Data(examName)

            result.onSuccess { vocabFile ->

                // --- TRANSFORM DATA ---
                // We flatten the hierarchy into a linear list of SearchResult objects.
                // We capture the Category metadata here so it persists during filtering.
                val flatList = vocabFile.categories.flatMap { category ->
                    category.words.mapNotNull { word ->
                        // Only include words that have at least one sentence
                        val firstSentence = word.sentences.firstOrNull()?.sentence

                        if (firstSentence != null) {
                            SearchResult(
                                word = word,
                                firstSentence = firstSentence,
                                categoryTitle = category.title,
                                categoryTabNumber = category.tabNumber
                            )
                        } else {
                            null
                        }
                    }
                }
                    .sortedBy { it.word.word.lowercase() } // Alphabetical sort

                // --- ASSIGN TO MASTER ---
                allSearchResults = flatList

                // --- INITIALIZE LIVE FEED ---
                // If query is empty, show everything. If they typed while loading, filter it.
                if (_searchQuery.value.isBlank()) {
//                    _searchResults.value = allSearchResults
                    _uiState.value = SearchUiState.Success(results = allSearchResults)
                } else {
                    // Manually trigger filter if text exists
//                    _searchResults.value = filterList(flatList, _searchQuery.value)
                    _uiState.value = SearchUiState.Success(results = allSearchResults)
                }

                _isLoading.value = false
            }

            result.onFailure {
                // Handle error (e.g. show empty list or error state)
                _isLoading.value = false
            }
        }
    }
    // MARK: - 3. The Search Engine (Connecting Query to Results)

    private fun observeSearchQuery() {
        searchQuery
            .debounce(300L) // Wait 300ms for user to stop typing
            .distinctUntilChanged() // Don't re-process if text hasn't changed
            .map { query ->
                // This runs whenever 'searchQuery' changes
                filterList(allSearchResults, query)
            }
            .flowOn(Dispatchers.Default) // ⚡️ Run filtering on Background Thread (CPU optimized)
            .onEach { filteredList ->
                // Update the UI
//                _searchResults.value = filteredResults
                _uiState.update {
                    SearchUiState.Success(results = filteredList)
                }
            }
            .launchIn(viewModelScope)
    }

    // Helper logic to keep the Flow clean
    private fun filterList(list: List<SearchResult>, query: String): List<SearchResult> {
        if (query.isBlank()) {
            return list // Restore from Master
        } else {
            return list.filter { result ->
                result.word.word.contains(query, ignoreCase = true) ||
                        result.word.translation.contains(query, ignoreCase = true)
            }
        }
    }
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
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

    fun isHeard(sentence: String): Boolean {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        return historyManager.isHeard(currentLoadedLevel, contentID)
    }
    fun getPlayCount(sentence:String): Int {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        return historyManager.getPlayCount(currentLoadedLevel, contentID)
    }
    //fun handleTap(sentence: String, category: Category) {
//    fun handleTapObaolete(sentence: String,categoryTitle:String,categoryTabNumber:Int) {
//        val contentID = FirebaseAudioService.generateContentID(sentence)
//        val wasAlreadyHeard = historyManager.isHeard("Reference", contentID)
//
//        // 2. ⚡️ OPTIMISTIC UPDATE (Lightning)
//        // This turns the Red Dot ON immediately.
//        didPlayVocabSentence(sentence,categoryTitle,categoryTabNumber)
//
//        viewModelScope.launch {
//            val levelName = userPreferencesRepository.selectedSkillLevelFlow.first() // e.g. "B1"
//
//            val success = audioPlaybackRepository.playTrackAndGetResult(
//                sentence = sentence,
//                level = levelName,
//                sheetName = "", // Main tabs aggregate by Level, not SheetName
//                isPremiumUser = isPremiumUser.value // Replace with actual check if available
//            )
//
//            if (success) {
//                _uiState.update { currentState ->
//                    if (currentState is SearchUiState.Success) {
//                        currentState.copy(lastUpdate = System.currentTimeMillis())
//                    } else currentState
//                }
//            }else{
//                if (!wasAlreadyHeard) {
//                    historyManager.undoMarkSentenceHeard(levelName, contentID)
//                }
//            }
//
//            refreshUI()
//        }
//    }
    fun handleTap(sentence: String,categoryTitle:String,categoryTabNumber:Int) {
        viewModelScope.launch {

            // 1. CALL REPOSITORY
            // The Repository handles everything: Playback, History, XP, and Graph Stats.

            val levelName = userPreferencesRepository.selectedSkillLevelFlow.first() // e.g. "B1"
            val status = audioPlaybackRepository.playTrackAndGetStatus(
                sentence = sentence,
                level = levelName,
                isPremiumUser = isPremiumUser.value // Replace with actual check if available
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
                    Timber.i("SearchViewModel.handleTap().AudioPlaybackStatus.RateLimited ")
                    val failType = rateLimiter.canMakeCallWithResult()
                    Timber.w("Rate Limiter Triggered")
                    Timber.w("canICallAPI = %s", failType.canICallAPI)
                    Timber.w("failReason = %s", (failType.failReason))
                    Timber.w("timeLeftToWait = %s",failType.timeLeftToWait)
                    Timber.w(rateLimiter.printCurrentStatus)

                    if (status.failReason == SimpleRateLimiter.FailReason.DAILY) {
                        _showRateDailyLimitSheet.value = true
                    } else {
                        _showRateHourlyLimitSheet.value = true
                    }
                }

                is AudioPlaybackStatus.Failure -> {
                    // Show Snackbar logic
                    Timber.i("Format1ViewModel.handleTap().AudioPlaybackStatus.Failure")
                }
            }
        }
    }
    private fun didPlayVocabSentence(sentence: String,categoryTitle:String,categoryTabNumber:Int) {

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
    private fun refreshUI() {
        _uiState.update { currentState ->
            if (currentState is SearchUiState.Success) {
                currentState.copy(lastUpdate = System.currentTimeMillis())
            } else currentState
        }
    }
}