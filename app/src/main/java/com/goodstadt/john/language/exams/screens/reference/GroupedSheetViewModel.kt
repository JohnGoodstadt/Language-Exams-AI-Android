package com.goodstadt.john.language.exams.screens.reference

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.BillingRepository
import com.goodstadt.john.language.exams.data.ConnectivityRepository
import com.goodstadt.john.language.exams.data.PlaybackResult
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.data.examsheets.ExamSheetRepository
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.SubTabDefinition
import com.goodstadt.john.language.exams.models.VocabWord
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import com.goodstadt.john.language.exams.viewmodels.PlaybackState
import com.goodstadt.john.language.exams.viewmodels.PrepositionsUiState
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
    val subTabs: List<SubTabDefinition> = emptyList(),
    val selectedSubTab: SubTabDefinition? = null,
    val contentState: ContentState = ContentState.Idle
    // You could also add PlaybackState and other sheet visibility booleans here
    // if this screen will also play audio, just like in your other ViewModels.
)

@HiltViewModel
class GroupedSheetViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val appConfigRepository: AppConfigRepository,
    private val examSheetRepository: ExamSheetRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val connectivityRepository: ConnectivityRepository,
    private val ttsStatsRepository : TTSStatsRepository,
    private val billingRepository: BillingRepository,
    private val rateLimiter: SimpleRateLimiter,
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
            // Get all tabs from the config to find our specific tab
            val allTabs = appConfigRepository.getReferenceTabs()
            val myTab = allTabs.find { it.id == tabId }

            if (myTab != null && !myTab.subTabs.isNullOrEmpty()) {
                val initialSubTab = myTab.subTabs.first()
                _uiState.update {
                    it.copy(
                        title = myTab.title,
                        subTabs = myTab.subTabs,
                        selectedSubTab = initialSubTab
                    )
                }
                // Load the content for the initially selected sub-tab
                loadContentForSubTab(initialSubTab)
            } else {
                _uiState.update { it.copy(contentState = ContentState.Error("Configuration for tab '$tabId' not found or is empty.")) }
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
            // Check the cache first
            val cachedCategories = contentCache[subTab.firestoreDocumentId]
            if (cachedCategories != null) {
                _uiState.update { it.copy(contentState = ContentState.Success(cachedCategories)) }
                return@launch
            }

            // If not in cache, show loading and fetch from the repository
            _uiState.update { it.copy(contentState = ContentState.Loading) }

            try {
                val result = examSheetRepository.getExamSheetBy(subTab.firestoreDocumentId, forceRefresh = false)
                result.onSuccess { vocabFile ->
                    val categories = vocabFile.categories
                    // Add to cache for next time
                    contentCache[subTab.firestoreDocumentId] = categories
                    // Update UI with success
                    _uiState.update { it.copy(contentState = ContentState.Success(categories)) }
                }
                result.onFailure { error ->
                    _uiState.update { it.copy(contentState = ContentState.Error(error.localizedMessage ?: "Failed to load content.")) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(contentState = ContentState.Error(e.localizedMessage ?: "An unexpected error occurred.")) }
            }
        }
    }

    fun playTrack(word: VocabWord, sentence: Sentence) {
        Timber.i("GroupedSheetViewModel.playTrack() ${word.word} ${sentence.sentence}")
        Log.i("GroupedSheetViewModel", "playTrack() ${word.word} ${sentence.sentence}")
        if (_playbackState.value is PlaybackState.Playing)
        {
            return
        }

        if (!connectivityRepository.isCurrentlyOnline()) {
            _playbackState.value = PlaybackState.Idle
            return
        }

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
            val currentLanguageCode =  userPreferencesRepository.selectedLanguageCodeFlow.first()

            val uniqueSentenceId = generateUniqueSentenceId(word, sentence, currentVoiceName)
            _playbackState.value = PlaybackState.Playing(uniqueSentenceId)
//maybe just de. remove any (zu dem)
            val cleanedSentence = sentence.sentence.replace("\\s*\\([^)]*\\)\\s*".toRegex(), " ")

            val played = vocabRepository.playFromCacheIfFound(uniqueSentenceId)
            if (played){//short cut so user cna play cached sentences with no Internet connection
                _playbackState.value = PlaybackState.Idle
                ttsStatsRepository.updateTTSStatsWithoutCosts()
                ttsStatsRepository.incWordStats(word.word)
                return@launch
            }

            //Dot shows before sound (lightening before thunder)
            /*
            //TODO:
            _uiState.update { currentState ->
                if (currentState is PrepositionsUiState.Success) {
                    val updatedKeys = currentState.cachedAudioWordKeys +  generateUniqueSentenceId(word, sentence, currentVoiceName)
                    currentState.copy(cachedAudioWordKeys = updatedKeys)
                } else {
                    currentState
                }
            }
             */


            val result = vocabRepository.playTextToSpeech(
                text = cleanedSentence,
                uniqueSentenceId = uniqueSentenceId,
                voiceName = currentVoiceName,
                languageCode = currentLanguageCode
            )
            when (result) {
                is PlaybackResult.PlayedFromNetworkAndCached -> {
                    _playbackState.value = PlaybackState.Idle

                    rateLimiter.recordCall()
                    Timber.v(rateLimiter.printCurrentStatus)
                    ttsStatsRepository.updateTTSStatsWithCosts(sentence, currentVoiceName)
                    ttsStatsRepository.incWordStats(word.word)
                    //TODO: not inc but update!
                    ttsStatsRepository.incProgressSize(userPreferencesRepository.selectedSkillLevelFlow.first())
                }
                is PlaybackResult.PlayedFromCache -> { //probably does not get executed as playFromCacheIfFound() already run
                    _playbackState.value = PlaybackState.Idle
                    ttsStatsRepository.updateTTSStatsWithoutCosts()
                    ttsStatsRepository.incWordStats(word.word)
                }
                is PlaybackResult.Failure -> {
                    _playbackState.value = PlaybackState.Idle
                    _playbackState.value = PlaybackState.Error(result.exception.message ?: "Playback failed")
                }
                PlaybackResult.CacheNotFound -> {
                    _playbackState.value = PlaybackState.Idle
                    Timber.e("Cache found to exist but not played")
                }
            }
            _playbackState.value = PlaybackState.Idle
        }
    }
}
