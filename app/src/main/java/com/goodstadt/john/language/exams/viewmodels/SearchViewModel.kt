package com.goodstadt.john.language.exams.viewmodels

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.BillingRepository
import com.goodstadt.john.language.exams.data.ConnectivityRepository
import com.goodstadt.john.language.exams.data.PlaybackResult
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UserStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
//import com.goodstadt.john.language.exams.managers.RateLimiterManager
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.models.VocabWord
import com.goodstadt.john.language.exams.models.WordAndSentence
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// A simple data class to hold a word and its first sentence for the flat list
data class SearchResult(
    val word: VocabWord,
    val firstSentence: String
)

sealed interface SearchUiEvent {
    data class ShowSnackbar(val message: String, val actionLabel: String? = null) : SearchUiEvent
}

@OptIn(FlowPreview::class) // Needed for the debounce operator
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val userStatsRepository: UserStatsRepository,
    private val ttsStatsRepository : TTSStatsRepository,
    private val billingRepository: BillingRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val connectivityRepository: ConnectivityRepository,

    ) : ViewModel() {

    // Holds the complete list of all words from the current file
    private var allWords: List<VocabWord> = emptyList()

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    // State for the user's search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // State for the filtered search results
    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

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

    init {
        loadFullWordList()
        observeSearchQuery()
        viewModelScope.launch {
            billingRepository.isPurchased.collect { purchasedStatus ->
                _isPremiumUser.value = purchasedStatus
                if (DEBUG) {
                    billingRepository.logCurrentStatus()
                }
            }
        }
    }

    private fun loadFullWordList() {
        viewModelScope.launch {
            val fileName = userPreferencesRepository.selectedFileNameFlow.first()
            vocabRepository.getVocabData(fileName).onSuccess { vocabFile ->
                // Flatten the entire structure into a single list of words
                allWords = vocabFile.categories.flatMap { it.words } .sortedBy { it.word.lowercase() }

                _searchResults.value = allWords.mapNotNull { word ->
                    word.sentences.firstOrNull()?.let { sentence ->
                        SearchResult(word, sentence.sentence)
                    }
                }
            }
        }
    }
    private fun observeSearchQueryObsolete() {
        viewModelScope.launch {
            searchQuery
                .debounce(300L) // Wait for 300ms of no new input
                .distinctUntilChanged() // Only search if the text has actually changed
                .map { query ->


                    if (query.length < 2) {
                        emptyList() // Return empty list if query is too short
                    } else {
                        // Filter the master list
                        allWords.filter {
                            it.word.contains(query, ignoreCase = true)
                        }.mapNotNull { word ->
                            // Convert to SearchResult, filtering out words with no sentences
                            word.sentences.firstOrNull()?.let { sentence ->
                                SearchResult(word, sentence.sentence)
                            }
                        }
                    }
                }
                .collect { results ->
                    _searchResults.value = results
                }
        }
    }
    private fun observeSearchQuery() {
        viewModelScope.launch {
            searchQuery
                .debounce(300L)
                .distinctUntilChanged()
                .map { query ->
                    // --- THIS IS THE MAIN LOGIC FIX ---
                    if (query.isBlank()) { // Use isBlank() to handle empty strings and whitespace
                        // If the query is empty, return the full list.
                        allWords
                    } else {
                        // If the query has text, filter the master list.
                        allWords.filter {
                            it.word.contains(query, ignoreCase = true) ||
                                    it.translation.contains(query, ignoreCase = true) // Bonus: search translation too
                        }
                    }
                }
                .map { words ->
                    // This second map converts the filtered VocabWord list to the SearchResult list.
                    // This ensures the logic isn't duplicated.
                    words.mapNotNull { word ->
                        word.sentences.firstOrNull()?.let { sentence ->
                            SearchResult(word, sentence.sentence)
                        }
                    }
                }
                .collect { results ->
                    _searchResults.value = results
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun playTrack(searchResult: SearchResult) {
        if (_playbackState.value is PlaybackState.Playing) return

        if (!isPremiumUser.value) { //if premium user don't check credits
            if (rateLimiter.doIForbidCall()){
                val failType = rateLimiter.canMakeCallWithResult()
                Timber.v("${failType.canICallAPI}")
                Timber.v("${failType.failReason}")
                Timber.v("${failType.timeLeftToWait}")
                if (!failType.canICallAPI){
                    if (failType.failReason == SimpleRateLimiter.FailReason.DAILY){
                        _showRateDailyLimitSheet.value = true
                    }else {
                        _showRateHourlyLimitSheet.value = true
                    }
                } else {
                    _showRateLimitSheet.value = true
                }

                return
            }
        }

        viewModelScope.launch {
            val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
            val uniqueSentenceId = generateUniqueSentenceId(searchResult.word, searchResult.word.sentences.first(),currentVoiceName)
            _playbackState.value = PlaybackState.Playing(uniqueSentenceId)

            val played = vocabRepository.playFromCacheIfFound(uniqueSentenceId)
            if (played){//short cut so user cna play cached sentences with no Internet connection
                _playbackState.value = PlaybackState.Idle
                ttsStatsRepository.updateTTSStatsWithoutCosts()
                ttsStatsRepository.incWordStats(searchResult.word.word)
                return@launch
            }

            if (!connectivityRepository.isCurrentlyOnline()) {
                _uiEvent.emit(SearchUiEvent.ShowSnackbar("No internet connection", actionLabel = "Retry" ))
                return@launch
            }

            // Use .first() to get the most recent value from the Flow
            val currentLanguageCode = userPreferencesRepository.selectedLanguageCodeFlow.first()

            val result = vocabRepository.playTextToSpeech(
                    text = searchResult.firstSentence,
                    uniqueSentenceId = uniqueSentenceId,
                    voiceName = currentVoiceName,
                    languageCode = currentLanguageCode
            )

            when (result) {
                is PlaybackResult.PlayedFromNetworkAndCached -> {
                    rateLimiter.recordCall()
                    _playbackState.value = PlaybackState.Idle
                    ttsStatsRepository.updateTTSStatsWithCosts(searchResult.firstSentence, currentVoiceName)
                    ttsStatsRepository.incWordStats(searchResult.word.word)
                }
                is PlaybackResult.PlayedFromCache -> {
                    _playbackState.value = PlaybackState.Idle
                    ttsStatsRepository.updateTTSStatsWithoutCosts()
                    ttsStatsRepository.incWordStats(searchResult.word.word)
                }
                is PlaybackResult.Failure -> {
                    _playbackState.value = PlaybackState.Error(result.exception.message ?: "Playback failed")
                }
                PlaybackResult.CacheNotFound -> Timber.e("Cache found to exist but not played")
            }
            _playbackState.value = PlaybackState.Idle
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
}