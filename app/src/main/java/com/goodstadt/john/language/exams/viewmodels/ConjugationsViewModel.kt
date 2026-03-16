package com.goodstadt.john.language.exams.viewmodels

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.ConnectivityRepository
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
import com.goodstadt.john.language.exams.models.Category
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// A UI state for this specific screen
sealed interface ConjugationsUiState {
    object Loading : ConjugationsUiState
    data class Success(
        val categories: List<Category>,
        val currentSheetName : String = "",
        val selectedVoiceName: String = "", // TODO: do I need this?
        val lastUpdate: Long = System.currentTimeMillis()
    ) : ConjugationsUiState

    data class Error(val message: String) : ConjugationsUiState
    object NotAvailable : ConjugationsUiState // For flavors like 'zh'
}


@HiltViewModel
class ConjugationsViewModel @Inject constructor(
    private val vocabRepository: ContentRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ttsStatsRepository: TTSStatsRepository,
    private val appScope: CoroutineScope,
    private val billingRepository: BillingRepository,
    private val rateLimiter: SimpleRateLimiter,
    private val connectivityRepository: ConnectivityRepository,
    private val audioCacheManager: AudioCacheManager,
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val historyManager: HistorySyncManager,

    ) : ViewModel() {

    private val _uiState = MutableStateFlow<ConjugationsUiState>(ConjugationsUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _selectedConjugation = MutableStateFlow(LanguageConfig.conjugationOptions.first())
    val selectedConjugation = _selectedConjugation.asStateFlow()

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    // Reuse the playback state logic
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()

    //NOTE: rate Limiting
//    private val rateLimiter = RateLimiterManager.getInstance()
    private val _showRateLimitSheet = MutableStateFlow(false)
    val showRateLimitSheet = _showRateLimitSheet.asStateFlow()

    private val _showRateDailyLimitSheet = MutableStateFlow(false)
    val showRateDailyLimitSheet = _showRateDailyLimitSheet.asStateFlow()

    private val _showRateHourlyLimitSheet = MutableStateFlow(false)
    val showRateHourlyLimitSheet = _showRateHourlyLimitSheet.asStateFlow()

    init {
        val bundleName = LanguageConfig.getConjugationBundleFileName(_selectedConjugation.value)
        val sheet_name = LanguageConfig.getConjugationFirestoreSheetName(_selectedConjugation.value)

        loadConjugationsData(bundleName,sheet_name) //e.g. conjugations_to_be vs EnglishConjugationsToBe
//        loadConjugationsData(LanguageConfig.getConjugationBundleFileName(_selectedConjugation.value))

        viewModelScope.launch {
            billingRepository.isPurchased.collect { purchasedStatus ->
                _isPremiumUser.value = purchasedStatus
                if (DEBUG) {
                    billingRepository.logCurrentStatus()
                }
            }
        }
    }

//    private fun loadCachedSentencesForDot() {
//
//
//
//    }

    private fun loadConjugationsData(bundle_file_name: String?,firestore_sheet_name: String?,) {
        viewModelScope.launch {
       //     val fileName = LanguageConfig.conjugationsFileName

            if (bundle_file_name == null || firestore_sheet_name == null ){
                _uiState.value = ConjugationsUiState.NotAvailable
                return@launch
            }

            _uiState.value = ConjugationsUiState.Loading

            //NOTE: is this fun only bundle or firestore?
            val result = vocabRepository.getFormat0Data(firestore_sheet_name)
           // val result99= vocabRepository.loadBundledFormat0Data(bundle_file_name) //direct from bundle

            result.onSuccess { vocabFile ->

                val allSentences = vocabFile.categories
                    .flatMap { it.words }
                    .flatMap { it.sentences }
                    .map { it.sentence }
                audioCacheManager.recalculateReferenceStats(firestore_sheet_name,allSentences)

                val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
               // val cachedKeys = vocabRepository.getSentenceKeysWithCachedAudio(vocabFile.categories, currentVoiceName)

                _uiState.value = ConjugationsUiState.Success(vocabFile.categories, selectedVoiceName = currentVoiceName, currentSheetName = firestore_sheet_name)
            }.onFailure { error ->
                _uiState.value =
                    ConjugationsUiState.Error(error.localizedMessage ?: "Failed to load file $bundle_file_name")
            }
        }
    }

    fun onConjugationSelected(option: String) {
        if (_selectedConjugation.value != option) {
            _selectedConjugation.value = option
//            val fileName = LanguageConfig.getConjugationBundleFileName(option)
            val bundleName = LanguageConfig.getConjugationBundleFileName(option)
            val sheet_name = LanguageConfig.getConjugationFirestoreSheetName(option)

            loadConjugationsData(bundleName,sheet_name)
        }
    }



    fun saveDataOnExit() {
        // We use appScope to ensure this save operation completes even if the
        // viewModelScope is paused or cancelled as the user navigates away.
        if (false) {
            appScope.launch {
                //TODO: for 1 month feb/march 2026, facebook ads manager campaign. see stats
                if (ttsStatsRepository.isMarchOrApril2026()) {
                    ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.GlobalStats)
                    ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
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
    private fun refreshUI() {
        _uiState.update { currentState ->
            if (currentState is ConjugationsUiState.Success) {
                currentState.copy(lastUpdate = System.currentTimeMillis())
            } else currentState
        }
    }
    fun isHeard(sentence: String): Boolean {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        return historyManager.getPlayCount("Reference", contentID) > 0
    }

    fun playCount(sentence: String): Int {
        val contentID = FirebaseAudioService.generateContentID(sentence)
        return historyManager.getPlayCount("Reference", contentID)
    }
//    fun handleTapObsolete(sentence: String) {
//        val contentID = FirebaseAudioService.generateContentID(sentence)
//        val wasAlreadyHeard = historyManager.isHeard("Reference", contentID)
//
//        // 2. ⚡️ OPTIMISTIC UPDATE (Lightning)
//        // This turns the Red Dot ON immediately.
//        didPlayReferenceSentence(sentence)
//
//        viewModelScope.launch {
//            // 1. Play Audio (Waterfall)
//            when (val currentState = _uiState.value) {
//
//                is ConjugationsUiState.Success -> {
//                    val sheetName = currentState.currentSheetName
//                    val success = audioPlaybackRepository.playTrackAndGetResult(
//                        sentence = sentence,
//                        level = "Reference",
//                        sheetName = sheetName,
//                        isPremiumUser = false // Inject actual status
//                    )
//
//                    // 2. Update Stats on Success
//                    if (!success) {
//                        Timber.w("Playback failed. Rolling back Red Dot.")
//
//                        // Only undo if it wasn't there before this specific tap
//                        if (!wasAlreadyHeard) {
//                            undoPlayReferenceSentence(sentence)
//                        }
//                    }
//                    historyManager.debugPrintAllHistory()
//                }
//                else -> {
//                    println("State is not UiState, skipping audio playback.")
//                }
//            }
//        }
//    }
    fun handleTap(sentence: String) {

        viewModelScope.launch {
            // 1. Play Audio (Waterfall)
            when (val currentState = _uiState.value) {

                is ConjugationsUiState.Success -> {

                    val sheetName = currentState.currentSheetName
                    val status = audioPlaybackRepository.playTrackAndGetStatus(
                        sentence = sentence,
                        level = "Reference",
                        sheetName = sheetName, // Important: Pass this so Graph Stats update!
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
                else -> {
                    println("State is not UiState, skipping audio playback.")
                }
            }
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
            when (val currentState = _uiState.value) {
                is ConjugationsUiState.Success -> {
                    val sheetName = currentState.currentSheetName
                    val currentStats = audioCacheManager.getReferenceStats(sheetName)
                    audioCacheManager.updateReferenceStats(
                        key = sheetName,
                        heard = currentStats.heard + 1,
                        total = currentStats.total
                    )
                }
                else -> {
                    println("State is not UiState, skipping audio playback.")
                }
            }
        }
        refreshUI()
    }
    fun onResume() {
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
        when (val currentState = _uiState.value) {
            is ConjugationsUiState.Success -> {
                val sheetName = currentState.currentSheetName
                val currentStats = audioCacheManager.getReferenceStats(sheetName)
                if (currentStats.heard > 0) {
                    audioCacheManager.updateReferenceStats(
                        key = sheetName,
                        heard = currentStats.heard - 1,
                        total = currentStats.total
                    )
                }
            }
            else -> {
                println("State is not UiState, skipping audio playback.")
            }
        }

        // 3. Update UI (Dot disappears)
        refreshUI()
    }
}