package com.goodstadt.john.language.exams.packages.dailydictionary

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statFBCloudHitCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statWOTDBackHitCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statWOTDForwardHitCount
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository.Companion.statWOTDHitCount
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class BrowserUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentEntry: DictionaryEntry? = null,
    val currentDateLabel: String? = null,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isViewingToday: Boolean = true
)

class DictionaryEntryBrowserViewModel(
    app: Application
) : AndroidViewModel(app) {


//    init {
//        ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statWOTDHitCount)
//    }
    private val repo = DailyWordPoolRepository(
        db = FirebaseFirestore.getInstance(),
        context = app.applicationContext
    )

    // existing
    private var entryById: Map<String, DictionaryEntry> = emptyMap()
    private var schedule: List<WotdAssignment> = emptyList()

    private var maxBrowseDaysBack: Int = 7
    private var todayIndex: Int = 0
    private var scheduledIndex: Int = 0

    val uiState = MutableStateFlow(BrowserUiState())

    fun load() {
        uiState.value = uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                // 1) Fetch pool doc
                val pool = withContext(Dispatchers.IO) { repo.fetchPool() }
                val ids = pool.orderedEntryIds
                if (ids.isEmpty()) throw IllegalStateException("Pool is empty")

                // policy
                maxBrowseDaysBack = (pool.uiPolicy?.maxBrowseDaysBack ?: 7).coerceAtLeast(1)

                // 2) Compute today (UTC) and todayIdx in pool
                val todayDoy = dayOfYearUtc()
                val todayIdxInPool = ((todayDoy - 1) % ids.size + ids.size) % ids.size

                // 3) Build last-N-days window ending today (no future)
                val n = maxBrowseDaysBack.coerceAtMost(ids.size)
                val window = buildList {
                    // oldest -> newest
                    for (offset in (n - 1) downTo 0) {
                        val idx = ((todayIdxInPool - offset) % ids.size + ids.size) % ids.size
                        val entryId = ids[idx]
                        val label = makeLabelUtc(dayOfYear = todayDoy - offset)
                        add(
                            WotdAssignment(
                                date = todayDoy - offset,
                                label = label,
                                entryId = entryId
                            )
                        )
                    }
                }

                // 4) Fetch needed entries (cache-first, parallel)
                val entries = withContext(Dispatchers.IO) {
                    window.map { a ->
                        async { repo.fetchEntry(a.entryId) }
                    }.awaitAll()
                }
                entryById = entries.associateBy { it.entryId }

                schedule = window
                todayIndex = window.size - 1
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

    private fun publishState() {
        val currentAssignment = schedule.getOrNull(scheduledIndex)
        val entry = currentAssignment?.let { entryById[it.entryId] }

        uiState.value = uiState.value.copy(
            isLoading = false,
            error = null,
            currentEntry = entry,
            currentDateLabel = currentAssignment?.label,
            canGoBack = scheduledIndex > 0,
            canGoForward = scheduledIndex < todayIndex, // forward locked at today
            isViewingToday = scheduledIndex == todayIndex
        )
    }

    fun goBack() {
        if (scheduledIndex > 0) {
            scheduledIndex--
            publishState()
//            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statWOTDBackHitCount)
        }
    }

    fun goForward() {
        if (scheduledIndex < todayIndex) {
            scheduledIndex++
            publishState()
//            ttsStatsRepository.inc(TTSStatsRepository.fsDOC.GlobalStats, statWOTDForwardHitCount)
        }
    }

    private fun dayOfYearUtc(): Int {
        val nowUtc: ZonedDateTime = ZonedDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
        return nowUtc.dayOfYear
    }

    private fun makeLabelUtc(dayOfYear: Int): String {
        // display only (no year), like "Day 27 • Jan 27"
        val nowUtc = ZonedDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)
        val year = nowUtc.year
        val jan1 = ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        val date = jan1.plusDays((dayOfYear - 1).coerceAtLeast(0).toLong())
        val fmt = DateTimeFormatter.ofPattern("MMM d").withZone(ZoneOffset.UTC)
        return "Day $dayOfYear • ${fmt.format(date)}"
    }
}
