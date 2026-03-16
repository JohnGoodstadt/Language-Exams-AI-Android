// In a new file, e.g., viewmodels/RecallViewModel.kt
package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.PlaybackResult
import com.goodstadt.john.language.exams.data.RecallingItem
import com.goodstadt.john.language.exams.data.RecallingItems
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.data.repository.RecallingRepository
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.models.AudioPlaybackStatus
import com.goodstadt.john.language.exams.models.TabDetails
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject

// This class will hold the UI state
data class RecallUiState(
    val todayItems: List<RecallingItem> = emptyList(),
    val laterItems: List<RecallingItem> = emptyList(),
    val wordCounts: Map<String, Int> = emptyMap()
)

@HiltViewModel
class RecallViewModel @Inject constructor(
    private val vocabRepository: ContentRepository, // For playing audio
    private val recallingItemsManager: RecallingItems,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ttsStatsRepository : TTSStatsRepository,
    private val billingRepository: BillingRepository,
    private val recallingRepository: RecallingRepository,
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val rateLimiter: SimpleRateLimiter,
) : ViewModel() {

  //  val recalledItemsFlow = recallingItemsManager.items

    private val _uiState = MutableStateFlow(RecallUiState())
    val uiState = _uiState.asStateFlow()

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    private val _showRateDailyLimitSheet = MutableStateFlow(false)
    val showRateDailyLimitSheet = _showRateDailyLimitSheet.asStateFlow()

    private val _showRateHourlyLimitSheet = MutableStateFlow(false)
    val showRateHourlyLimitSheet = _showRateHourlyLimitSheet.asStateFlow()

    init {
        // Equivalent of .onAppear for the whole screen
        viewModelScope.launch {
            recallingItemsManager.items.collect { allItemsList ->
                // This block runs every time the list of recalled items changes.
                // It replaces the old filterAndSortItems() function.

                val today = Calendar.getInstance()
                val now = System.currentTimeMillis()

                // Partition the NEWLY received list
                val (todayItems, laterItems) = allItemsList.partition { item ->
                    item.nextEventTime <= now || isSameDay(item.nextEventTime, today)
                }

//                val (todayItems, laterItems) = allItemsList.partition { item ->
//                    val nextEventCal = Calendar.getInstance().apply { timeInMillis = item.nextEventTime }
//                    item.nextEventTime < today.timeInMillis ||
//                            (today.get(Calendar.DAY_OF_YEAR) == nextEventCal.get(Calendar.DAY_OF_YEAR) &&
//                                    today.get(Calendar.YEAR) == nextEventCal.get(Calendar.YEAR))
//                }

                // Update the UI state with the newly partitioned and sorted lists
                _uiState.update {
                    it.copy(
                        todayItems = todayItems.sortedBy { i -> i.nextEventTime },
                        laterItems = laterItems.sortedBy { i -> i.nextEventTime }
                    )
                }
            }
        }
       // recallingItemsManager.load("Spanish A1Vocab") moved to earlier

        loadWordCounts()

       requestNotificationPermission()

        viewModelScope.launch {
            billingRepository.isPurchased.collect { purchasedStatus ->
                _isPremiumUser.value = purchasedStatus
                if (DEBUG) {
                    billingRepository.logCurrentStatus()
                }
            }
        }
    }


//    private fun loadAllDataObsolete() {
//        // TODO: Get currentExamJSONName from SharedPreferences/DataStore
//        val currentExamKey = "Spanish A1Vocab"
//        recallingItemsManager.load(currentExamKey)
//        filterAndSortItemsObsolete()
//        loadWordCounts()
//    }

    private fun filterAndSortItemsObsolete() {
        val allItems = recallingItemsManager.items.value
        val today = Calendar.getInstance()

        // Partition the list into two groups based on the isOverdue logic
        val (todayItems, laterItems) = allItems.partition { item ->
            val nextEventCal = Calendar.getInstance().apply { timeInMillis = item.nextEventTime }
            // Logic for "isOverdue"
            item.nextEventTime < today.timeInMillis ||
                    today.get(Calendar.DAY_OF_YEAR) == nextEventCal.get(Calendar.DAY_OF_YEAR) &&
                    today.get(Calendar.YEAR) == nextEventCal.get(Calendar.YEAR)
        }

        _uiState.update {
            it.copy(
                todayItems = todayItems.sortedBy { it.nextEventTime },
                laterItems = laterItems.sortedBy { it.nextEventTime }
            )
        }
    }

    // TODO: Implement logic to get word counts, maybe from another repository
    private fun loadWordCounts() {
        // Placeholder
        _uiState.update { it.copy(wordCounts = mapOf("Can you Answer this?" to 5)) }
    }
//    fun onClearAllClicked() {
//        // This is also correct.
//        viewModelScope.launch {
//            recallingItemsManager.removeAll()
//        }
//    }
//    fun onClearAllClickedObsolete() {
//        recallingItemsManager.removeAll()
//        // Save the now-empty list to storage to make the change permanent
//        // TODO: Use a real exam key
//        recallingItemsManager.save("Spanish A1Vocab")
//    }
//    fun onClearAllClickedO() {
//        viewModelScope.launch {
//            recallingItemsManager.removeAll()
//            // --- 2. THE FIX ---
//            val currentExamKey = userPreferencesRepository.selectedFileNameFlow.first()
//            recallingItemsManager.save(currentExamKey)
//        }
//    }
//    fun onRemoveClickedObsolete(key: String) {
//
//        recallingItemsManager.items.value.forEach { fred ->
//            Timber.d("Word: '${fred.key}', isRecalling")
//
//        }
//
////        val isRecalling = recallingItemsManager.items.contains(key)
////        if (isRecalling){
////            Timber.d("Word: '${key}', isRecalling: $isRecalling")
////        }
//
//        recallingItemsManager.remove(key)
//        // TODO: Save to correct key from SharedPreferences/DataStore
//        recallingItemsManager.save("Spanish A1Vocab")
//        filterAndSortItemsObsolete()
//        // TODO: update app badge and manage notifications
//    }
//    fun onRemoveClicked(key: String) {
//        viewModelScope.launch {
//            recallingItemsManager.remove(key)
//        }
//    }
//    fun onOkClicked(key: String) {
//        viewModelScope.launch {
//            recallingItemsManager.recalledOK(key)
//        }
//    }
    // Actions delegate to Repository
    fun onRemoveClicked(key: String) {
        viewModelScope.launch {
            recallingRepository.remove(key)
        }
    }

    fun onOkClicked(key: String) {
        viewModelScope.launch {
            recallingRepository.recalledOK(key)
        }
    }

    fun onClearAllClicked() {
        viewModelScope.launch {
            recallingRepository.removeAll()
        }
    }
    // Your onClearAllClicked is also simpler now
//    fun onClearAllClicked() {
//        viewModelScope.launch {
//            recallingItemsManager.removeAll()
//        }
//    }
    private fun isSameDay(ms: Long, todayCal: java.util.Calendar): Boolean {
        val itemCal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        return todayCal.get(java.util.Calendar.YEAR) == itemCal.get(java.util.Calendar.YEAR) &&
                todayCal.get(java.util.Calendar.DAY_OF_YEAR) == itemCal.get(java.util.Calendar.DAY_OF_YEAR)
    }
    fun onPlayWord2(word: String) {

        audioPlaybackRepository.stopPlayback()

        viewModelScope.launch {
            //All stats updated in playTrackAndGetStatus()
            val result = audioPlaybackRepository.playTrackAndGetStatus(
                sentence = word,
                level = "Reference",
                isPremiumUser = false
            )

            when (result) {
                is AudioPlaybackStatus.PlayedFromTTSAPI,is AudioPlaybackStatus.PlayedFromLocalCache , is AudioPlaybackStatus.PlayedFromCloudStorage -> {

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

                    if (result.failReason == SimpleRateLimiter.FailReason.DAILY) {
                        _showRateDailyLimitSheet.value = true
                    } else {
                        _showRateHourlyLimitSheet.value = true
                    }
                }

                AudioPlaybackStatus.Failure -> {
                    //TODO: Show Snackbar logic
                    Timber.i("QuizViewModel.handleTap().AudioPlaybackStatus.Failure")
                }
            }
            //TODO: for 1 month feb/march 2026, facebook ads manager campaign. see stats
            if (ttsStatsRepository.isMarchOrApril2026()) {
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.GlobalStats)
                ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
            }

        }

    }
    fun onPlayWord(word: String) {

        if (isPremiumUser.value) {
            Timber.i("onPlayWord() User is a paid user !")
        }else{
            Timber.i("onPlayWord() User is a FREE user")
        }

        viewModelScope.launch {
            val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
            val uniqueSentenceId = generateUniqueSentenceId(word, currentVoiceName)

            val currentLanguageCode =  userPreferencesRepository.selectedLanguageCodeFlow.first()

            val result = vocabRepository.playTextToSpeech(
                text = word,
                uniqueSentenceId = uniqueSentenceId,
                voiceName = currentVoiceName,
                languageCode = currentLanguageCode
            )

            when (result) {
                is PlaybackResult.PlayedFromNetworkAndCached -> {
//                    ttsStatsRepository.updateGlobalTTSStats( word,currentVoiceName)
//                    ttsStatsRepository.updateUserTTSCounts(word.count())
                    ttsStatsRepository.updateTTSStatsWithCosts(word, currentVoiceName)
                    ttsStatsRepository.incWordStats(word)
                }

                is PlaybackResult.PlayedFromLocalCache -> {
                    ttsStatsRepository.updateTTSStatsWithoutCosts()
                    ttsStatsRepository.incWordStats(word)
                }
                is PlaybackResult.Failure -> {}
                PlaybackResult.CacheNotFound -> Timber.e("Cache found to exist but not played")
            }
            // TODO: update word counts
        }
    }

    fun onPlaySentence(word: String, sentence: String) {

        viewModelScope.launch {
            val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
            val uniqueSentenceId = generateUniqueSentenceId(sentence,currentVoiceName)

            val played = vocabRepository.playFromCacheIfFound(uniqueSentenceId)
            if (played){//short cut so user cna play cached sentences with no Internet connection
                ttsStatsRepository.updateTTSStatsWithoutCosts()
                ttsStatsRepository.incWordStats(word)
                return@launch
            }



            val currentLanguageCode =  userPreferencesRepository.selectedLanguageCodeFlow.first()

            val result = vocabRepository.playTextToSpeech(
                text = sentence,
                uniqueSentenceId = uniqueSentenceId,
                voiceName = currentVoiceName,
                languageCode = currentLanguageCode
            )

            when (result) {
                is PlaybackResult.PlayedFromNetworkAndCached -> {
                    ttsStatsRepository.updateGlobalTTSStats( sentence,currentVoiceName)
                    ttsStatsRepository.incUserTTSCounts(sentence.count())
                    ttsStatsRepository.incWordStats(word)
                }
                is PlaybackResult.PlayedFromLocalCache -> {
                    ttsStatsRepository.updateTTSStatsWithoutCosts()
                    ttsStatsRepository.incWordStats(word)
                }
                is PlaybackResult.Failure -> {}
                PlaybackResult.CacheNotFound -> Timber.e("Cache found to exist but not played")
            }
           // _playbackState.value = PlaybackState.Idle
        }
        // TODO: update word counts
    }

    // TODO: Get sentences for a word from VocabRepository
    fun getSentencesForWord(wordKey: String): List<String> {
        // This is a placeholder. You'd fetch this from your vocab data.
        return listOf("This is sentence one.", "This is sentence two.", "This is sentence three.")
    }
    suspend fun fetchTabDetailsForWord(wordKey: String): TabDetails {
        return vocabRepository.findTabDetailsForWord(wordKey)
    }
    suspend fun fetchSentencesForWord(wordKey: String): List<String> {
        // Simply delegate the call to the repository, which contains the business logic.
        return vocabRepository.getSentencesForWord(wordKey)
    }
    fun requestNotificationPermission() {
        // TODO: Implement Android's notification permission request flow for API 33+
    }

    fun onDebugPrintSummaryClicked() {
        val allItems = recallingItemsManager.items.value // Get the current list

        // Use a StringBuilder for efficient string concatenation
        val summary = StringBuilder("\n--- RECALL ITEMS DEBUG SUMMARY ---\n")
        summary.append("Total Items: ${allItems.size}\n")
        summary.append("---------------------------------\n")

        if (allItems.isEmpty()) {
            summary.append("No items to display.\n")
        } else {
            allItems.forEachIndexed { index, item ->
                val nextEventDate = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(item.nextEventTime))

                summary.append("  [${index + 1}] Key: ${item.key}\n")
                summary.append("      State: ${item.recallState}\n")
                summary.append("      Stop: ${item.currentStopNumber} (${item.currentStopCode()})\n")
                summary.append("      Next Due: $nextEventDate\n")
                summary.append("---------------------------------\n")
            }
        }

        // Print the final summary to Logcat with a custom tag
        Timber.e(summary.toString())
    }
}