package com.goodstadt.john.language.exams.managers

import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.data.repository.FirebaseAudioService
import com.goodstadt.john.language.exams.models.AudioPlaybackStatus
import com.goodstadt.john.language.exams.utils.PlaybackEvent
import com.goodstadt.john.language.exams.utils.PlaybackEventBus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Shared ordered playback with rolling audio prefetch, cancellable gaps, and per-run ownership. */
@Singleton
class SequencePlayer @Inject constructor(
    private val contentRepository: ContentRepository,
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playbackEventBus: PlaybackEventBus
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var runVersion = 0L
    private val trackMaxMillis = 30_000L
    private val _playingKey = MutableStateFlow<String?>(null)
    val playingKey = _playingKey.asStateFlow()
    // True while a sequence is held by pause(); resume() continues from the current track.
    private val _isPaused = MutableStateFlow(false)
    val isPaused = _isPaused.asStateFlow()

    /** Retained so pause()/resume() can continue the same run from [currentIndex]. */
    private data class RunParams(
        val sentences: List<String>,
        val voices: List<String?>?,      // optional per-track voice; null entry -> the user's voice
        val level: String,
        val sheetName: String,
        val isPremiumUser: Boolean,
        val gapMillis: ((String) -> Long)?,
        val onTrackStarted: (String) -> Unit,
        val onTrackPlayed: (String) -> Unit,
        val onCompleted: () -> Unit
    )
    private var params: RunParams? = null
    private var currentIndex = 0

    /**
     * Existing vocab/reference callers retain their defaults (single voice from prefs, no pause). Tapping
     * the control that owns the running key stops it. [voices] (aligned to [sentences]) gives a per-track
     * voice for multi-speaker scripts; a null entry falls back to the user's selected voice.
     */
    fun toggle(
        key: String, sentences: List<String>, level: String,
        preflight: () -> Boolean = { true },
        onTrackStarted: (String) -> Unit = {},
        onTrackPlayed: (String) -> Unit = {},
        onCompleted: () -> Unit = {},
        sheetName: String = "",
        isPremiumUser: Boolean = false,
        gapMillis: ((String) -> Long)? = null,
        voices: List<String?>? = null
    ) {
        if (_playingKey.value == key && !_isPaused.value) { stop(); return }
        stop()
        if (sentences.isEmpty() || !preflight()) return
        params = RunParams(sentences, voices, level, sheetName, isPremiumUser, gapMillis,
            onTrackStarted, onTrackPlayed, onCompleted)
        _playingKey.value = key
        _isPaused.value = false
        startFrom(0)
    }

    /**
     * Arm a sequence in the PAUSED state without playing anything, positioned at [startIndex]. A later
     * [resume] begins from there. Used to move a paused run's resume point (e.g. a scrubber seek on a paused
     * script) silently; parameters mirror [toggle] minus the preflight (no playback starts here).
     */
    fun prime(
        key: String, sentences: List<String>, level: String,
        onTrackStarted: (String) -> Unit = {},
        onTrackPlayed: (String) -> Unit = {},
        onCompleted: () -> Unit = {},
        sheetName: String = "",
        isPremiumUser: Boolean = false,
        gapMillis: ((String) -> Long)? = null,
        voices: List<String?>? = null,
        startIndex: Int = 0
    ) {
        stop()
        if (sentences.isEmpty()) return
        params = RunParams(sentences, voices, level, sheetName, isPremiumUser, gapMillis,
            onTrackStarted, onTrackPlayed, onCompleted)
        _playingKey.value = key
        _isPaused.value = true
        currentIndex = startIndex.coerceIn(0, sentences.lastIndex)
    }

    /** Suspend the active run, keeping its key + position. The in-flight track is cut. */
    fun pause() {
        if (_playingKey.value == null || _isPaused.value) return
        _isPaused.value = true
        ++runVersion            // invalidate the current run so its `finally` won't clear state
        job?.cancel()
        job = null
        audioPlaybackRepository.stopPlayback()
    }

    /** Continue a paused run, restarting the current (possibly half-played) track from its start. */
    fun resume() {
        if (_playingKey.value == null || !_isPaused.value) return
        _isPaused.value = false
        startFrom(currentIndex)
    }

    fun stop() {
        ++runVersion
        job?.cancel()
        job = null
        audioPlaybackRepository.stopPlayback()
        _playingKey.value = null
        _isPaused.value = false
        params = null
        currentIndex = 0
    }

    private fun startFrom(startIndex: Int) {
        val p = params ?: return
        val run = ++runVersion
        currentIndex = startIndex
        job = scope.launch {
            try {
                runPipeline(p, startIndex)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Timber.e(error, "SequencePlayer: playback failed") }
            finally {
                // Old/paused runs must never stop or clear a newer (or deliberately held) sequence.
                if (runVersion == run && !_isPaused.value) {
                    audioPlaybackRepository.stopPlayback()
                    _playingKey.value = null
                    params = null
                    currentIndex = 0
                    job = null
                }
            }
        }
    }

    private suspend fun CoroutineScope.runPipeline(p: RunParams, startIndex: Int) {
        val defaultVoice = userPreferencesRepository.selectedVoiceNameFlow.first()
        val language = userPreferencesRepository.selectedLanguageCodeFlow.first()
        fun voiceAt(i: Int): String = p.voices?.getOrNull(i)?.takeIf { it.isNotBlank() } ?: defaultVoice
        fun resolve(i: Int): Deferred<Boolean> = async {
            val v = voiceAt(i)
            contentRepository.ensureAudioCached(
                p.sentences[i], FirebaseAudioService.generateUnifiedFilename(p.sentences[i], v), v, language)
        }
        var ready = resolve(startIndex)
        var playedAll = true
        var index = startIndex
        while (index < p.sentences.size) {
            ensureActive()
            if (!ready.await()) { playedAll = false; break }
            val next = if (index < p.sentences.lastIndex) resolve(index + 1) else null
            ensureActive()
            currentIndex = index
            p.onTrackStarted(p.sentences[index])
            // Subscribe before starting even a very short cached clip.
            val finished = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(trackMaxMillis) {
                    playbackEventBus.events.first { it is PlaybackEvent.Completed || it is PlaybackEvent.Failed }
                }
            }
            try {
                val status = audioPlaybackRepository.playTrackAndGetStatus(
                    sentence = p.sentences[index], level = p.level, sheetName = p.sheetName,
                    isPremiumUser = p.isPremiumUser, useRateLimiting = false, voiceOverride = voiceAt(index))
                ensureActive()
                if (status is AudioPlaybackStatus.Failure || status is AudioPlaybackStatus.RateLimited) {
                    next?.cancel(); playedAll = false; break
                }
                p.onTrackPlayed(p.sentences[index])
                if (finished.await() !is PlaybackEvent.Completed) {
                    next?.cancel(); playedAll = false; break
                }
            } finally { finished.cancel() }
            if (next == null) break
            p.gapMillis?.let { delay(it(p.sentences[index]).coerceAtLeast(0L)) }
            ready = next
            index++
        }
        if (playedAll && isActive) p.onCompleted()
    }
}
