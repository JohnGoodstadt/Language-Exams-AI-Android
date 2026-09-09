package com.goodstadt.john.language.exams.managers

import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.data.repository.FirebaseAudioService
import com.goodstadt.john.language.exams.models.AudioPlaybackStatus
import com.goodstadt.john.language.exams.utils.PlaybackEvent
import com.goodstadt.john.language.exams.utils.PlaybackEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays a list of sentences in sequence, back-to-back, reusable from any screen (vocab tabs now; Reference
 * lists later). Isolated from UI/ViewModels so there's ONE implementation of the pipeline:
 *
 *  - each track is resolved to the local cache via the normal waterfall (disk -> Cloud Storage -> Google
 *    TTS), then played;
 *  - while one track plays, the NEXT is resolved in the background, so after the first the rest run
 *    gaplessly from cache (rolling one-ahead avoids a burst of simultaneous TTS);
 *  - each track is played through the normal single-sentence path, so history/red-dots/XP and play stats
 *    are recorded exactly as a tap would - and cached tracks never re-hit TTS;
 *  - sequencing waits for the audio layer's playback-finished event between tracks (per-track safety cap).
 *
 * App-wide singleton: only one sequence plays at a time. Owns its own scope (independent of any ViewModel);
 * callers stop it when their screen leaves or the app backgrounds.
 */
@Singleton
class SequencePlayer @Inject constructor(
    private val contentRepository: ContentRepository,
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playbackEventBus: PlaybackEventBus
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val trackMaxMillis = 30_000L // safety cap per track if no completion event arrives

    private val _playingKey = MutableStateFlow<String?>(null)
    /** The key of the list currently playing, or null. UI shows Pause for this key, Play elsewhere. */
    val playingKey = _playingKey.asStateFlow()

    /**
     * Toggle sequential playback for [key]. Re-calling with the same key stops it; a different key stops the
     * first and starts anew. [level] is the history bucket for each play. [preflight] runs ONLY when starting
     * (not when stopping) - return false to abort before playback begins (e.g. the caller failed a rate-limit
     * check and showed a paywall). Rate limiting is deliberately NOT applied per track, so once started the
     * list plays uninterrupted; per-track play stats are still recorded by the play call.
     *
     * [onTrackStarted] fires just before each track plays (e.g. remember the last-played sentence).
     * [onTrackPlayed] fires after each track plays *successfully* - use it for the same post-play bookkeeping
     * a single-tap does (advance the spaced-repetition dot, update stats, refresh the row), so a section
     * play updates the row dots exactly as tapping each row would.
     * [onCompleted] fires ONCE if the whole list played through to the end - NOT when the run is stopped,
     * cancelled, or aborted by a failure. Use it for end-of-sequence work (e.g. the section-complete banner).
     */
    fun toggle(
        key: String,
        sentences: List<String>,
        level: String,
        preflight: () -> Boolean = { true },
        onTrackStarted: (String) -> Unit = {},
        onTrackPlayed: (String) -> Unit = {},
        onCompleted: () -> Unit = {}
    ) {
        if (_playingKey.value == key) { // re-tap = stop (no preflight on stop)
            stop()
            return
        }
        stop() // stop any other list first
        if (sentences.isEmpty()) return
        if (!preflight()) return // caller's gate (e.g. rate limit) - only on start

        _playingKey.value = key
        job = scope.launch {
            try {
                runPipeline(sentences, level, onTrackStarted, onTrackPlayed, onCompleted) // `this` is the launch's scope; prefetches are its children
            } catch (e: Exception) {
                Timber.e(e, "SequencePlayer: playback failed for '$key'")
            } finally {
                audioPlaybackRepository.stopPlayback()
                _playingKey.value = null
            }
        }
    }

    /** Stop any in-progress sequence (re-tap, screen left, app backgrounded). */
    fun stop() {
        job?.cancel()
        job = null
        audioPlaybackRepository.stopPlayback()
        _playingKey.value = null
    }

    // Extension on the launch's own scope so every async prefetch is a child of the job and is
    // cancelled together with it when stop() calls job.cancel().
    private suspend fun CoroutineScope.runPipeline(
        sentences: List<String>,
        level: String,
        onTrackStarted: (String) -> Unit,
        onTrackPlayed: (String) -> Unit,
        onCompleted: () -> Unit
    ) {
        val voiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
        val languageCode = userPreferencesRepository.selectedLanguageCodeFlow.first()

        fun idOf(s: String) = FirebaseAudioService.generateUnifiedFilename(s, voiceName)
        fun resolve(s: String): Deferred<Boolean> =
            async { contentRepository.ensureAudioCached(s, idOf(s), voiceName, languageCode) }

        // Pipeline: resolve track 0, then for each track resolve the NEXT while this one plays.
        // playedAll stays true only if we never break early (cancel / rate-limit / failure).
        var playedAll = true
        var currentReady: Deferred<Boolean> = resolve(sentences[0])
        for (i in sentences.indices) {
            if (!isActive) { playedAll = false; break }
            currentReady.await() // this track is now cached (or resolution failed)

            val nextReady: Deferred<Boolean>? =
                if (i < sentences.lastIndex) resolve(sentences[i + 1]) else null

            if (!isActive) { playedAll = false; break }
            onTrackStarted(sentences[i]) // let the caller record the last-played sentence, etc.
            // Cached tracks hit the local-cache tier instantly and still record history/XP/play stats.
            val status = audioPlaybackRepository.playTrackAndGetStatus(
                sentence = sentences[i],
                level = level,
                useRateLimiting = false
            )
            if (status is AudioPlaybackStatus.RateLimited || status is AudioPlaybackStatus.Failure) {
                playedAll = false; break
            }
            onTrackPlayed(sentences[i]) // played OK -> caller advances the row's dot / stats, same as a tap

            // Wait for this track to finish before starting the next.
            withTimeoutOrNull(trackMaxMillis) {
                playbackEventBus.events.first {
                    it is PlaybackEvent.Completed || it is PlaybackEvent.Failed
                }
            }

            currentReady = nextReady ?: break // last track -> natural end (playedAll stays true)
        }
        if (playedAll && isActive) onCompleted() // whole list finished normally
    }
}
