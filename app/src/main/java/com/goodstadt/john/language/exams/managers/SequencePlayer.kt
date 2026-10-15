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

    /** Existing vocab callers retain their defaults. Pause stops; next Play restarts the section. */
    fun toggle(
        key: String, sentences: List<String>, level: String,
        preflight: () -> Boolean = { true },
        onTrackStarted: (String) -> Unit = {},
        onTrackPlayed: (String) -> Unit = {},
        onCompleted: () -> Unit = {},
        sheetName: String = "",
        isPremiumUser: Boolean = false,
        gapMillis: ((String) -> Long)? = null
    ) {
        if (_playingKey.value == key) { stop(); return }
        stop()
        if (sentences.isEmpty() || !preflight()) return
        val run = ++runVersion
        _playingKey.value = key
        job = scope.launch {
            try {
                runPipeline(sentences, level, sheetName, isPremiumUser, gapMillis, onTrackStarted, onTrackPlayed, onCompleted)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Timber.e(error, "SequencePlayer: playback failed for '$key'") }
            finally {
                // Old cancelled jobs must never stop or clear a newer sequence.
                if (runVersion == run) {
                    audioPlaybackRepository.stopPlayback()
                    _playingKey.value = null
                    job = null
                }
            }
        }
    }

    fun stop() {
        ++runVersion
        job?.cancel()
        job = null
        audioPlaybackRepository.stopPlayback()
        _playingKey.value = null
    }

    private suspend fun CoroutineScope.runPipeline(
        sentences: List<String>, level: String, sheetName: String, isPremiumUser: Boolean,
        gapMillis: ((String) -> Long)?, onTrackStarted: (String) -> Unit,
        onTrackPlayed: (String) -> Unit, onCompleted: () -> Unit
    ) {
        val voice = userPreferencesRepository.selectedVoiceNameFlow.first()
        val language = userPreferencesRepository.selectedLanguageCodeFlow.first()
        fun resolve(sentence: String): Deferred<Boolean> = async {
            contentRepository.ensureAudioCached(sentence, FirebaseAudioService.generateUnifiedFilename(sentence, voice), voice, language)
        }
        var ready = resolve(sentences.first())
        var playedAll = true
        for (index in sentences.indices) {
            ensureActive()
            if (!ready.await()) { playedAll = false; break }
            val next = if (index < sentences.lastIndex) resolve(sentences[index + 1]) else null
            ensureActive()
            onTrackStarted(sentences[index])
            // Subscribe before starting even a very short cached clip.
            val finished = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(trackMaxMillis) {
                    playbackEventBus.events.first { it is PlaybackEvent.Completed || it is PlaybackEvent.Failed }
                }
            }
            try {
                val status = audioPlaybackRepository.playTrackAndGetStatus(
                    sentence = sentences[index], level = level, sheetName = sheetName,
                    isPremiumUser = isPremiumUser, useRateLimiting = false)
                ensureActive()
                if (status is AudioPlaybackStatus.Failure || status is AudioPlaybackStatus.RateLimited) {
                    next?.cancel(); playedAll = false; break
                }
                onTrackPlayed(sentences[index])
                if (finished.await() !is PlaybackEvent.Completed) {
                    next?.cancel(); playedAll = false; break
                }
            } finally { finished.cancel() }
            if (next == null) break
            gapMillis?.let { delay(it(sentences[index]).coerceAtLeast(0L)) }
            ready = next
        }
        if (playedAll && isActive) onCompleted()
    }
}
