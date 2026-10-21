package com.goodstadt.john.language.exams.packages.Format8

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.data.stats.PageStat
import com.goodstadt.john.language.exams.data.stats.recordPageView
import com.goodstadt.john.language.exams.managers.AccessPolicy
import com.goodstadt.john.language.exams.managers.AudioCacheManager
import com.goodstadt.john.language.exams.managers.ContentArea
import com.goodstadt.john.language.exams.managers.SequencePlayer
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.models.AudioPlaybackStatus
import com.goodstadt.john.language.exams.models.Format8Entry
import com.goodstadt.john.language.exams.models.Format8Example
import com.goodstadt.john.language.exams.models.Format8File
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Audio/entitlement state belongs to the navigation destination; data stays in the reference container VM. */
@HiltViewModel
class Format8PlaybackViewModel @Inject constructor(
    private val audio: AudioPlaybackRepository,
    private val sequence: SequencePlayer,
    private val policy: AccessPolicy,
    private val billing: BillingRepository,
    private val limiter: SimpleRateLimiter,
    private val preferences: UserPreferencesRepository,
    private val stats: TTSStatsRepository,
    private val audioCache: AudioCacheManager
) : ViewModel() {
    enum class Prompt { PREMIUM, DAILY, HOURLY }
    private val owner = "format8:${UUID.randomUUID()}:"
    private var singleJob: Job? = null
    private val _prompt = MutableStateFlow<Prompt?>(null)
    val prompt = _prompt.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    val playingKey = sequence.playingKey
    val isPurchased = billing.isPurchased
    val gapEnabled = preferences.format8SpeakAloudGapFlow.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    var lastSentence: String = ""
        private set

    fun isLocked(rank: Int): Boolean = policy.isRowLocked(ContentArea.REFERENCE, null, rank)
    fun sectionKey(sheetName: String, rank: Int): String = "$owner$sheetName:$rank"
    fun setGapEnabled(enabled: Boolean) { viewModelScope.launch { preferences.setFormat8SpeakAloudGap(enabled) } }
    fun dismissPrompt() { _prompt.value = null }
    fun dismissError() { _error.value = null }
    fun showPremium() { stopPlayback(); _prompt.value = Prompt.PREMIUM }
    fun buyPremium(activity: Activity) { viewModelScope.launch { billing.launchPurchase(activity) } }

    fun onSheetLoaded(file: Format8File, sheetName: String) {
        audioCache.recalculateReferenceStats(sheetName, file.sections.flatMap { it.entries }.flatMap { it.examples }.map { it.german })
    }

    fun playExample(sheetName: String, rank: Int, example: Format8Example) {
        stopPlayback()
        if (isLocked(rank)) { showPremium(); return }
        _error.value = null
        lastSentence = example.german
        // Count the tap + GA4 breadth event; the `.play` (actually heard) follows on playback success below.
        stats.recordReferenceInteraction(sheetName, "sentence")
        singleJob = viewModelScope.launch {
            try {
                val result = audio.playTrackAndGetStatus(example.german, "Reference", sheetName, policy.isPremium)
                ensureActive()
                when (result) {
                    is AudioPlaybackStatus.RateLimited -> showLimit(result.failReason)
                    is AudioPlaybackStatus.Failure -> _error.value = "Unable to play this sentence. Please try again."
                    else -> stats.recordPageView(PageStat.Area.REFERENCE, sheetName, PageStat.Metric.PLAY)  // heard
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _error.value = error.localizedMessage ?: "Unable to play this sentence." }
        }
    }

    /** Sub-tab (adjective group) switch — which group gets used, a within-sheet popularity signal. */
    fun recordSubTabTap(sheetName: String) = stats.recordReferenceInteraction(sheetName, "sub_tab")

    fun toggleSection(sheetName: String, rank: Int, entry: Format8Entry) {
        val key = sectionKey(sheetName, rank)
        if (sequence.playingKey.value == key) { stopPlayback(); return }
        stopPlayback()
        if (isLocked(rank)) { showPremium(); return }
        _error.value = null
        // One section-play interaction (tap + GA4); each sentence then records `.play` as it is heard.
        stats.recordReferenceInteraction(sheetName, "section_play", entry.header)
        val withGap = gapEnabled.value
        sequence.toggle(key = key, sentences = entry.examples.map { it.german }, level = "Reference",
            sheetName = sheetName, isPremiumUser = policy.isPremium,
            gapMillis = if (withGap) Format8Presentation::speakAloudGapMillis else null,
            preflight = {
                if (!policy.isPremium && limiter.doIForbidCall()) {
                    showLimit(limiter.canMakeCallWithResult().failReason ?: SimpleRateLimiter.FailReason.HOURLY)
                    false
                } else true
            },
            onTrackStarted = { sentence ->
                lastSentence = sentence
                stats.recordPageView(PageStat.Area.REFERENCE, sheetName, PageStat.Metric.PLAY)  // heard
            })
    }

    private fun showLimit(reason: SimpleRateLimiter.FailReason) {
        _prompt.value = if (reason == SimpleRateLimiter.FailReason.DAILY) Prompt.DAILY else Prompt.HOURLY
    }

    fun stopPlayback() {
        val hadSingle = singleJob != null
        singleJob?.cancel()
        singleJob = null
        if (sequence.playingKey.value?.startsWith(owner) == true) sequence.stop()
        else if (hadSingle) audio.stopPlayback()
    }

    override fun onCleared() { stopPlayback(); super.onCleared() }
}
