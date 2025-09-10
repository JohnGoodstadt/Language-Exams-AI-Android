// In a new file, e.g., viewmodels/RecallViewModel.kt
package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.BillingRepository
import com.goodstadt.john.language.exams.data.PlaybackResult
import com.goodstadt.john.language.exams.data.RecallingItem
import com.goodstadt.john.language.exams.data.RecallingItems
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
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
    private val vocabRepository: VocabRepository, // For playing audio
    private val recallingItemsManager: RecallingItems,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ttsStatsRepository : TTSStatsRepository,
    private val billingRepository: BillingRepository
) : ViewModel() {

  //  val recalledItemsFlow = recallingItemsManager.items

    private val _uiState = MutableStateFlow(RecallUiState())
    val uiState = _uiState.asStateFlow()

    private val _isPremiumUser = MutableStateFlow(false)
    val isPremiumUser = _isPremiumUser.asStateFlow()

    init {
        // Equivalent of .onAppear for the whole screen
        viewModelScope.launch {
            recallingItemsManager.items.collect { allItemsList ->
                // This block runs every time the list of recalled items changes.
                // It replaces the old filterAndSortItems() function.

                val today = Calendar.getInstance()

                // Partition the NEWLY received list
                val (todayItems, laterItems) = allItemsList.partition { item ->
                    val nextEventCal = Calendar.getInstance().apply { timeInMillis = item.nextEventTime }
                    item.nextEventTime < today.timeInMillis ||
                            (today.get(Calendar.DAY_OF_YEAR) == nextEventCal.get(Calendar.DAY_OF_YEAR) &&
                                    today.get(Calendar.YEAR) == nextEventCal.get(Calendar.YEAR))
                }

                // Update the UI state with the newly partitioned and sorted lists
                _uiState.update {
                    it.copy(
                        todayItems = todayItems.sortedBy { it.nextEventTime },
                        laterItems = laterItems.sortedBy { it.nextEventTime }
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
    fun onClearAllClicked() {
        // This is also correct.
        viewModelScope.launch {
            recallingItemsManager.removeAll()
        }
    }
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
    fun onRemoveClicked(key: String) {
        viewModelScope.launch {
            recallingItemsManager.remove(key)
        }
    }
    fun onOkClicked(key: String) {
        viewModelScope.launch {
            recallingItemsManager.recalledOK(key)
        }
    }

    // Your onClearAllClicked is also simpler now
//    fun onClearAllClicked() {
//        viewModelScope.launch {
//            recallingItemsManager.removeAll()
//        }
//    }
    fun onPlayWord(word: String) {

        if (isPremiumUser.value) {
            Timber.i("onPlayWord() User is a paid user !")
        }else{
            Timber.i("onPlayWord() User is a FREE user")
        }

        viewModelScope.launch {
            val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
            val uniqueSentenceId = generateUniqueSentenceId(word, currentVoiceName)

            val currentLanguageCode = LanguageConfig.languageCode

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

                is PlaybackResult.PlayedFromCache -> {
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

        if (isPremiumUser.value) {
            Timber.i("onPlaySentence() User is a paid user !")
        }else{
            Timber.i("onPlaySentence() User is a FREE user")
        }

        viewModelScope.launch {
            val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
            val uniqueSentenceId = generateUniqueSentenceId(sentence,currentVoiceName)

            val currentLanguageCode = LanguageConfig.languageCode

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
                is PlaybackResult.PlayedFromCache -> {
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