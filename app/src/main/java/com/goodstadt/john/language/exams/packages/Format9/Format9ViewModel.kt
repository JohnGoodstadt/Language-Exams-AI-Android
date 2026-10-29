package com.goodstadt.john.language.exams.packages.Format9

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
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
 * optional SequencePlayer / AudioPlaybackRepository parameters. The sheet's language selects the spoken
 * text; its translationLanguage controls translation display without changing the audio.
 */
@HiltViewModel
class Format9ViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val sequence: SequencePlayer,
    private val policy: AccessPolicy,
    private val billing: BillingRepository,
    private val limiter: SimpleRateLimiter,
    private val stats: TTSStatsRepository,
    private val audioCache: AudioCacheManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    enum class Prompt { DAILY, HOURLY }

    companion object {
        private const val PODCAST_PREFS = "podcast_prefs"
        private const val KEY_TRANSLATION_LANGUAGE = "format9_translation_language"
        private const val KEY_SCENE_PLAY_MODE = "format9_scene_play_mode"
        private const val KEY_SCENE_REPEAT_COUNT = "format9_scene_repeat_count"
        val SCENE_REPEAT_RANGE = 2..20
    }

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
    // The selected-language sentence currently being spoken (for row highlighting). Matches on text.
    private val _currentSpokenText = MutableStateFlow<String?>(null)
    val currentSpokenText = _currentSpokenText.asStateFlow()
    // False = spoken language, true = the user's chosen translation. Audio keeps using the spoken language.
    // Starts false every time this screen opens (the VM is recreated per navigation), so the page always
    // first shows the spoken language.
    private val _showTranslation = MutableStateFlow(false)
    val showTranslation = _showTranslation.asStateFlow()

    // The user's chosen translation language code (de/en/es/zh/pt/hi), persisted globally across podcasts
    // and launches. Blank until they pick one from the chooser.
    private val prefs = context.getSharedPreferences(PODCAST_PREFS, Context.MODE_PRIVATE)
    private val _translationLanguage = MutableStateFlow(prefs.getString(KEY_TRANSLATION_LANGUAGE, "").orEmpty())
    val translationLanguage = _translationLanguage.asStateFlow()
    // What a scene's play button does (chosen in the scene-options sheet), persisted globally. Defaults to
    // playing from that scene to the end of the script until the user picks something else.
    private val _scenePlayMode = MutableStateFlow(
        Format9ScenePlayMode.entries.firstOrNull { it.name == prefs.getString(KEY_SCENE_PLAY_MODE, null) }
            ?: Format9ScenePlayMode.TO_END
    )
    val scenePlayMode = _scenePlayMode.asStateFlow()
    // How many times "repeat the scene" plays it; remembered for next time.
    private val _sceneRepeatCount = MutableStateFlow(prefs.getInt(KEY_SCENE_REPEAT_COUNT, 3).coerceIn(SCENE_REPEAT_RANGE))
    val sceneRepeatCount = _sceneRepeatCount.asStateFlow()

    // Scene the scrubber highlights (playhead): advances as playback crosses scenes, and is set on a scrubber
    // tap for immediate feedback. It's an indicator, not necessarily what's visible on screen.
    private val _focusedSceneIndex = MutableStateFlow(0)
    val focusedSceneIndex = _focusedSceneIndex.asStateFlow()

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
                    audioCache.recalculateReferenceStats(sheetName, orderedLines().map(::spokenText).filter { it.isNotBlank() })
                }
                .onFailure { _error.value = it.message; Timber.e(it, "Format9 load failed for '$name'") }
        }
    }

    // --- Derived helpers ---

    val spokenLanguageCode: String get() = _file.value?.spokenLanguageCode.orEmpty()
    val translationLanguageCode: String get() = _file.value?.translationLanguageCode.orEmpty()

    fun spokenText(line: Format9Line): String = _file.value?.spokenText(line).orEmpty()
    /** Row text in the user's chosen [translationCode] when [showTranslation], else the spoken text. */
    fun displayText(line: Format9Line, translationCode: String, showTranslation: Boolean): String =
        _file.value?.displayText(line, translationCode, showTranslation).orEmpty()

    /** The translation languages this script offers (empty until the sheet loads). */
    val availableTranslationCodes: List<String> get() = _file.value?.availableTranslationLanguageCodes.orEmpty()

    /** True when a usable translation language is chosen for THIS script. */
    fun hasValidTranslationLanguage(): Boolean =
        _translationLanguage.value.isNotBlank() && availableTranslationCodes.contains(_translationLanguage.value)

    /** Default to pre-select the first time: the phone's language if the script has it, else the first. */
    fun defaultTranslationCode(): String {
        val codes = availableTranslationCodes
        val phone = Locale.getDefault().language.lowercase(Locale.ROOT)
        return if (codes.contains(phone)) phone else codes.firstOrNull().orEmpty()
    }

    /** Persist the chosen language globally and start showing the translation in it. */
    fun setTranslationLanguage(code: String) {
        _translationLanguage.value = code
        prefs.edit().putString(KEY_TRANSLATION_LANGUAGE, code).apply()
        _showTranslation.value = true
        stats.recordReferenceInteraction(sheetName, "translate_language")
    }

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
        orderedLines().sumOf { spokenText(it).length }.toDouble() / charsPerSecond
    fun formattedDuration(seconds: Double): String {
        val total = seconds.toInt()
        return "%d:%02d".format(total / 60, total % 60)
    }

    // --- Display ---

    fun toggleTranslation() {
        stats.recordReferenceInteraction(sheetName, "translate")
        _showTranslation.value = !_showTranslation.value
    }
    fun dismissPrompt() { _prompt.value = null }
    fun dismissError() { _playbackError.value = null }
    fun buyPremium(activity: Activity) { viewModelScope.launch { billing.launchPurchase(activity) } }

    // --- Playback ---

    /**
     * A single tapped line plays as a one-item sequence (keyed by the line) so it drives the same playing
     * state as whole-script/scene playback: the floating/top buttons flip to pause, the row highlights for
     * the whole clip, and re-tapping the same line (or any play/pause control) stops it.
     */
    fun playLine(line: Format9Line) {
        stats.recordReferenceInteraction(sheetName, "line", line.id)
        toggleSequence(lineKey(line), listOf(line))
    }

    /** Whole-script Play/Stop (tapping the running key stops). */
    fun togglePlayAll() {
        stats.recordReferenceInteraction(sheetName, "play_all")
        toggleSequence(playAllKey(), orderedLines())
    }

    /**
     * Per-scene Play/Stop, honouring the chosen scene play mode: just the scene, the scene N times in a row,
     * or from the scene to the end of the script. Always under the scene's key, so that scene's button shows
     * pause/stop for the whole run.
     */
    fun toggleScene(scene: Format9Scene) {
        stats.recordReferenceInteraction(sheetName, "scene_play", scene.id)
        toggleSequence(sceneKey(scene), linesForScenePlay(scene))
    }

    private fun linesForScenePlay(scene: Format9Scene): List<Format9Line> {
        val sceneLines = scene.lines.sortedBy { it.sortorder }
        return when (_scenePlayMode.value) {
            Format9ScenePlayMode.SCENE -> sceneLines
            Format9ScenePlayMode.REPEAT -> List(_sceneRepeatCount.value.coerceAtLeast(1)) { sceneLines }.flatten()
            Format9ScenePlayMode.TO_END -> {
                val scenes = orderedScenes()
                val start = scenes.indexOfFirst { it.id == scene.id }
                if (start < 0) sceneLines else scenes.drop(start).flatMap { it.lines.sortedBy { l -> l.sortorder } }
            }
        }
    }

    /** Save the scene-options sheet choice (OK). Nothing plays until a scene's play button is tapped. */
    fun setScenePlayOptions(mode: Format9ScenePlayMode, repeatCount: Int) {
        _scenePlayMode.value = mode
        _sceneRepeatCount.value = repeatCount.coerceIn(SCENE_REPEAT_RANGE)
        prefs.edit()
            .putString(KEY_SCENE_PLAY_MODE, mode.name)
            .putInt(KEY_SCENE_REPEAT_COUNT, _sceneRepeatCount.value)
            .apply()
        stats.recordReferenceInteraction(sheetName, "scene_play_mode", mode.name)
    }

    private fun toggleSequence(key: String, lines: List<Format9Line>) {
        if (sequence.playingKey.value == key && !sequence.isPaused.value) { stopPlayback(); return }
        startSequence(key, lines)
    }

    /**
     * Start [lines] under [key], replacing whatever is playing (no toggle-off). Split out from
     * [toggleSequence] so the scrubber can continue the whole script FROM a scene while it is already the
     * active key (a plain toggle would stop it instead of restarting).
     */
    private fun startSequence(key: String, lines: List<Format9Line>) {
        stopPlayback()
        val playableLines = lines.filter { spokenText(it).isNotBlank() }
        if (playableLines.isEmpty()) return
        _error.value = null
        sequence.toggle(
            key = key,
            sentences = playableLines.map(::spokenText),
            voices = playableLines.map { voiceIdOrNull(it.speakerId) },
            level = "Reference",
            sheetName = sheetName,
            isPremiumUser = policy.isPremium,
            preflight = {
                if (!policy.isPremium && limiter.doIForbidCall()) {
                    showLimit(limiter.canMakeCallWithResult().failReason ?: SimpleRateLimiter.FailReason.HOURLY)
                    false
                } else true
            },
            onTrackStarted = ::handleTrackStarted,
            onCompleted = { _currentSpokenText.value = null }
        )
    }

    /** Shared per-track start handler: moves the row highlight + scrubber playhead, records the play stat. */
    private fun handleTrackStarted(sentence: String) {
        _currentSpokenText.value = sentence
        sceneIndexOfText(sentence)?.let { _focusedSceneIndex.value = it }
        stats.recordPageView(PageStat.Area.REFERENCE, sheetName, PageStat.Metric.PLAY)  // heard
    }

    /** Index of the scene containing a line whose selected spoken text matches [text], or null. */
    private fun sceneIndexOfText(text: String): Int? =
        orderedScenes().indexOfFirst { scene -> scene.lines.any { spokenText(it) == text } }.takeIf { it >= 0 }

    /**
     * Scrubber tap on [sceneIndex]: the screen scrolls there (handled by the UI). If audio is ACTIVELY
     * playing, continue the script from that scene through to the end, under the whole-script key so the
     * top/floating controls stay "playing". If PAUSED, don't make a sound but move the resume point to this
     * scene so the floating play resumes from here. If idle, only the highlight/scroll moves — matching
     * YouTube, where seeking a paused video doesn't start it.
     */
    fun seekToScene(sceneIndex: Int) {
        val scenes = orderedScenes()
        if (sceneIndex !in scenes.indices) return
        _focusedSceneIndex.value = sceneIndex
        stats.recordReferenceInteraction(sheetName, "scrubber", scenes[sceneIndex].id)
        val lines = scenes.drop(sceneIndex).flatMap { it.lines.sortedBy { l -> l.sortorder } }
        if (lines.isEmpty()) return
        when {
            sequence.playingKey.value != null && !sequence.isPaused.value -> startSequence(playAllKey(), lines)
            sequence.isPaused.value -> primePausedSequence(playAllKey(), lines)
        }
    }

    /** Re-arm the paused whole-script run so [SequencePlayer.resume] starts at these lines (no sound now). */
    private fun primePausedSequence(key: String, lines: List<Format9Line>) {
        val playableLines = lines.filter { spokenText(it).isNotBlank() }
        if (playableLines.isEmpty()) return
        _currentSpokenText.value = spokenText(playableLines.first()) // reflect the new resume point
        sequence.prime(
            key = key,
            sentences = playableLines.map(::spokenText),
            voices = playableLines.map { voiceIdOrNull(it.speakerId) },
            level = "Reference",
            sheetName = sheetName,
            isPremiumUser = policy.isPremium,
            onTrackStarted = ::handleTrackStarted,
            onCompleted = { _currentSpokenText.value = null }
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
        _currentSpokenText.value = null
    }

    override fun onCleared() { stopPlayback(); super.onCleared() }
}

/** What a scene header's play button does. Persisted by name. */
enum class Format9ScenePlayMode { SCENE, REPEAT, TO_END }
