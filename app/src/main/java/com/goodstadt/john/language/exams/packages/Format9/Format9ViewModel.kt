package com.goodstadt.john.language.exams.packages.Format9

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.data.stats.PageStat
import com.goodstadt.john.language.exams.data.stats.recordPageView
import com.goodstadt.john.language.exams.managers.AccessPolicy
import com.goodstadt.john.language.exams.managers.AudioCacheManager
import com.goodstadt.john.language.exams.managers.SequencePlayer
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.models.Format9File
import com.goodstadt.john.language.exams.models.Format9Line
import com.goodstadt.john.language.exams.models.Format9Scene
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * Drives the fileFormat-9 podcast screen: loads the sheet from the bundle, plays a tapped line (in that
 * speaker's voice) through the shared audio waterfall, and plays the whole script / a single scene in order
 * via the shared [SequencePlayer] (which also prefetches ahead). Pause/resume and per-speaker voices use the
 * optional SequencePlayer / AudioPlaybackRepository parameters. English always speaks; the German/English
 * toggle only changes what is displayed.
 */
@HiltViewModel
class Format9ViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val sequence: SequencePlayer,
    private val policy: AccessPolicy,
    private val billing: BillingRepository,
    private val limiter: SimpleRateLimiter,
    private val stats: TTSStatsRepository,
    private val audioCache: AudioCacheManager
) : ViewModel() {

    enum class Prompt { DAILY, HOURLY }

    private val owner = "format9:${UUID.randomUUID()}:"
    private var sheetName: String = ""
    private var speakerVoices: Map<String, String> = emptyMap()
    private var speakerNames: Map<String, String> = emptyMap()

    private val _file = MutableStateFlow<Format9File?>(null)
    val file = _file.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _prompt = MutableStateFlow<Prompt?>(null)
    val prompt = _prompt.asStateFlow()
    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError = _playbackError.asStateFlow()
    // The German sentence currently being spoken (for row highlighting). Matches on text.
    private val _currentGerman = MutableStateFlow<String?>(null)
    val currentGerman = _currentGerman.asStateFlow()
    // Display language: false = learning (German), true = native translation (English). Audio always German.
    private val _showTranslation = MutableStateFlow(false)
    val showTranslation = _showTranslation.asStateFlow()

    val playingKey = sequence.playingKey
    val isPaused = sequence.isPaused
    val isPurchased = billing.isPurchased

    private var loadedName: String? = null

    fun load(name: String) {
        if (name.isBlank() || name == loadedName) return
        loadedName = name
        viewModelScope.launch {
            contentRepository.getFormat9Data(name)
                .onSuccess { f ->
                    sheetName = f.sheetname.ifBlank { name }
                    speakerVoices = f.speakers.associate { it.id to it.voiceId }
                    speakerNames = f.speakers.associate { it.id to it.name }
                    _file.value = f
                    _error.value = null
                    // Warm the "heard" stats for this sheet, like the other reference screens.
                    audioCache.recalculateReferenceStats(sheetName, orderedLines().map { it.german })
                }
                .onFailure { _error.value = it.message; Timber.e(it, "Format9 load failed for '$name'") }
        }
    }

    // --- Derived helpers ---

    fun orderedScenes(): List<Format9Scene> =
        _file.value?.scenes?.sortedBy { it.sortorder }.orEmpty()

    fun orderedLines(): List<Format9Line> =
        orderedScenes().flatMap { it.lines.sortedBy { l -> l.sortorder } }

    fun speakerName(speakerId: String): String = speakerNames[speakerId] ?: speakerId
    fun voiceIdOrNull(speakerId: String): String? = speakerVoices[speakerId]?.takeIf { it.isNotBlank() }

    fun playAllKey(): String = "${owner}all"
    fun sceneKey(scene: Format9Scene): String = "${owner}scene:${scene.id}"
    fun lineKey(line: Format9Line): String = "${owner}line:${line.id}"

    private val charsPerSecond = 15.0
    fun totalEstimatedSeconds(): Double =
        orderedLines().sumOf { it.german.length }.toDouble() / charsPerSecond
    fun formattedDuration(seconds: Double): String {
        val total = seconds.toInt()
        return "%d:%02d".format(total / 60, total % 60)
    }

    // --- Display ---

    fun toggleTranslation() { _showTranslation.value = !_showTranslation.value }
    fun dismissPrompt() { _prompt.value = null }
    fun dismissError() { _playbackError.value = null }
    fun buyPremium(activity: Activity) { viewModelScope.launch { billing.launchPurchase(activity) } }

    // --- Playback ---

    /**
     * A single tapped line plays as a one-item sequence (keyed by the line) so it drives the same playing
     * state as whole-script/scene playback: the floating/top buttons flip to pause, the row highlights for
     * the whole clip, and re-tapping the same line (or any play/pause control) stops it.
     */
    fun playLine(line: Format9Line) = toggleSequence(lineKey(line), listOf(line))

    /** Whole-script Play/Stop (tapping the running key stops). */
    fun togglePlayAll() = toggleSequence(playAllKey(), orderedLines())

    /** Per-scene Play/Stop. */
    fun toggleScene(scene: Format9Scene) =
        toggleSequence(sceneKey(scene), scene.lines.sortedBy { it.sortorder })

    private fun toggleSequence(key: String, lines: List<Format9Line>) {
        if (sequence.playingKey.value == key && !sequence.isPaused.value) { stopPlayback(); return }
        stopPlayback()
        if (lines.isEmpty()) return
        _error.value = null
        sequence.toggle(
            key = key,
            sentences = lines.map { it.german },
            voices = lines.map { voiceIdOrNull(it.speakerId) },
            level = "Reference",
            sheetName = sheetName,
            isPremiumUser = policy.isPremium,
            preflight = {
                if (!policy.isPremium && limiter.doIForbidCall()) {
                    showLimit(limiter.canMakeCallWithResult().failReason ?: SimpleRateLimiter.FailReason.HOURLY)
                    false
                } else true
            },
            onTrackStarted = { sentence ->
                _currentGerman.value = sentence
                stats.recordPageView(PageStat.Area.REFERENCE, sheetName, PageStat.Metric.TAP)
            },
            onCompleted = { _currentGerman.value = null }
        )
    }

    /** Floating control: nothing playing -> play whole script; playing -> pause; paused -> resume. */
    fun playPauseFloating() {
        when {
            sequence.isPaused.value -> sequence.resume()
            sequence.playingKey.value != null -> sequence.pause()
            else -> togglePlayAll()
        }
    }

    private fun showLimit(reason: SimpleRateLimiter.FailReason) {
        _prompt.value = if (reason == SimpleRateLimiter.FailReason.DAILY) Prompt.DAILY else Prompt.HOURLY
    }

    fun stopPlayback() {
        if (sequence.playingKey.value?.startsWith(owner) == true) sequence.stop()
        _currentGerman.value = null
    }

    override fun onCleared() { stopPlayback(); super.onCleared() }
}
