package com.goodstadt.john.language.exams.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.models.DailyDictionaryBundle
import com.goodstadt.john.language.exams.models.DailyDictionaryBundleLoader
import com.goodstadt.john.language.exams.models.DictionaryEntry
import com.goodstadt.john.language.exams.models.WotdAssignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneOffset

data class BrowserUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val currentEntry: DictionaryEntry? = null,
    val currentDate: String? = null,
    val isViewingToday: Boolean = true,
    val canBack: Boolean = false,
    val canForward: Boolean = false,
    val maxBrowseDaysBack: Int = 3
)

class DictionaryEntryBrowserViewModel(app: Application) : AndroidViewModel(app) {

    private var bundle: DailyDictionaryBundle? = null
    private var schedule: List<WotdAssignment> = emptyList()
    private var entryById: Map<String, DictionaryEntry> = emptyMap()

    private var scheduledIndex: Int = 0
    private var todayIndex: Int = 0
    private var maxBrowseDaysBack: Int = 3

    var uiState = androidx.compose.runtime.mutableStateOf(BrowserUiState())
        private set

    fun load() {
        uiState.value = uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val b = withContext(Dispatchers.IO) {
                    DailyDictionaryBundleLoader.loadFromAssets(getApplication())
                }
                bundle = b
                entryById = b.entries.associateBy { it.entryId }
                schedule = b.wotd.scheduledAssignments.sortedBy { it.date }

                maxBrowseDaysBack = (b.wotd.uiPolicy?.maxBrowseDaysBack ?: 3).coerceAtLeast(1)

                todayIndex = computeTodayIndex(schedule)
                scheduledIndex = todayIndex

                publishState()
            } catch (e: Exception) {
                uiState.value = BrowserUiState(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun goBack() {
        if (!canGoBack()) return
        scheduledIndex -= 1
        publishState()
    }

    fun goForward() {
        if (!canGoForward()) return
        scheduledIndex += 1
        publishState()
    }

    private fun publishState() {
        val cur = schedule.getOrNull(scheduledIndex)
        val entry = cur?.entryId?.let { entryById[it] }

        uiState.value = BrowserUiState(
            isLoading = false,
            error = null,
            currentEntry = entry,
            currentDate = cur?.date,
            isViewingToday = (scheduledIndex == todayIndex),
            canBack = canGoBack(),
            canForward = canGoForward(),
            maxBrowseDaysBack = maxBrowseDaysBack
        )
    }

    private fun canGoBack(): Boolean {
        val minIndex = (todayIndex - (maxBrowseDaysBack - 1)).coerceAtLeast(0)
        return scheduledIndex > minIndex
    }

    // Forward locked at "today"
    private fun canGoForward(): Boolean {
        return scheduledIndex < todayIndex
    }

    private fun computeTodayIndex(schedule: List<WotdAssignment>): Int {
        if (schedule.isEmpty()) return 0
        val today = todayStringUtc()

        // Latest date <= today
        val idx = schedule.indexOfLast { it.date <= today }
        return if (idx >= 0) idx else 0
    }

    private fun todayStringUtc(): String {
        // UTC date string YYYY-MM-DD
        val today = LocalDate.now(ZoneOffset.UTC)
        return today.toString()
    }
}
