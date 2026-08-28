package com.goodstadt.john.language.exams.packages.SavedPractice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.managers.PracticeReminderScheduler
import com.goodstadt.john.language.exams.managers.SavedPracticeManager
import com.goodstadt.john.language.exams.models.SavedSentence
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Backs the "Saved" sub-tab on the Me tab: the sentences the user swiped to Save on the vocab tabs, for
 * focused practice. Level-aware - shows only the current level's saved list, so switching B1<->B2 changes
 * what appears.
 */
@HiltViewModel
class SavedPracticeViewModel @Inject constructor(
    private val savedPracticeManager: SavedPracticeManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val billingRepository: BillingRepository,
    private val practiceReminderScheduler: PracticeReminderScheduler
) : ViewModel() {

    /** Saved sentences for the CURRENT level, newest first. Reactive to both the saved list and the
     *  user's selected level. */
    val saved: StateFlow<List<SavedSentence>> =
        combine(
            savedPracticeManager.savedState,
            userPreferencesRepository.selectedSkillLevelFlow
        ) { all, level ->
            all[level]?.values?.sortedByDescending { it.savedAt } ?: emptyList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Play a saved sentence (cache-first; the audio repo handles caching / rate limiting). Playing any
     * sentence on an entry counts as acknowledging its reminder: the REMIND ME label is cleared and any
     * still-pending notification is cancelled, so the entry drops back into normal (newest-first) order.
     */
    fun play(entry: SavedSentence, sentence: String) {
        if (entry.reminderAt > 0L) {
            savedPracticeManager.clearReminder(entry.level, entry.id)
            entry.word?.word?.let { practiceReminderScheduler.cancel(it) }
        }
        viewModelScope.launch {
            val level = userPreferencesRepository.selectedSkillLevelFlow.first()
            audioPlaybackRepository.playTrackAndGetStatus(
                sentence = sentence,
                level = level,
                isPremiumUser = billingRepository.isPurchased.value
            )
        }
    }

    /** Remove an entry from the saved list and cancel any pending practice reminder for it. */
    fun remove(entry: SavedSentence) {
        savedPracticeManager.remove(entry.level, entry.id)
        entry.word?.word?.let { practiceReminderScheduler.cancel(it) }
    }

    /**
     * DEBUG-only: dump the outstanding practice reminders (across all levels) to logcat, so the scheduled
     * list can be eyeballed when the Saved screen opens. A "due" entry is one whose time has passed (it
     * shows the REMIND ME label); a "pending" entry is still in the future. No-op in release builds.
     */
    fun logOutstandingReminders() {
        if (!BuildConfig.DEBUG) return
        val now = System.currentTimeMillis()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val outstanding = savedPracticeManager.savedState.value.values
            .flatMap { it.values }
            .filter { it.reminderAt > 0L }
            .sortedBy { it.reminderAt }

        if (outstanding.isEmpty()) {
            Timber.tag("PracticeReminder").d("Outstanding reminders: none")
            return
        }
        Timber.tag("PracticeReminder").d("Outstanding reminders (${outstanding.size}):")
        outstanding.forEach { e ->
            val status = if (e.reminderAt <= now) "DUE" else "pending"
            Timber.tag("PracticeReminder")
                .d("  [$status] ${e.level} '${e.word?.word ?: e.id}' -> ${fmt.format(Date(e.reminderAt))}")
        }
    }
}
