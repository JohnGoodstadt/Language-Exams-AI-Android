package com.goodstadt.john.language.exams.data

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.goodstadt.john.language.exams.utils.PlaybackEvent
import com.goodstadt.john.language.exams.utils.PlaybackEventBus
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * **AudioPlayerService**
 *
 * A low-level infrastructure service responsible for the physical playback of audio media on the Android device.
 * Wraps the Android `MediaPlayer` API to provide a simplified, thread-safe interface for consuming raw audio data.
 *
 * **Key Responsibilities:**
 * - **Media Playback:** Decodes and plays raw MP3 byte arrays received from the Repository layer.
 * - **Lifecycle Management:** Handles resource allocation/deallocation and state transitions (Idle, Playing, Stopped) to prevent memory leaks or overlapping audio.
 * - **Concurrency:** Ensures audio operations do not block the Main Thread, while managing callbacks for completion or errors.
 *
 * **Inputs:**
 * - Receives raw `ByteArray` data (MP3 format) from `ContentRepository` (sourced from Disk, Cloud, or TTS API).
 *
 * **Outputs:**
 * - Returns a `Result` type indicating whether playback started successfully or failed (e.g., corrupt data/codec error).
 * - Triggers completion listeners (if attached) when audio finishes.
 *
 * **Persistence Strategy:**
 * - **Transient:** This service is stateless regarding data storage. It does not save files or user preferences.
 * - It acts purely as a consumer of data provided by upstream repositories.
 */


@Singleton
class AudioPlayerService @Inject constructor(
    private var playbackEventBus: PlaybackEventBus
) {
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Plays raw audio data using MediaPlayer.
     * This is a suspend function that completes when playback is finished or fails.
     */

    // 1. Remove 'suspendCancellableCoroutine'
    // 2. Change return type if you want (or keep Result<Unit>)
    suspend fun playAudio(data: ByteArray): Result<Unit> {
        return try {
            stopPlayback()

            // Write temp file (Blocking I/O, but fast for small TTS files)
            val tempMp3 = File.createTempFile("temp_audio", "mp3")
            tempMp3.deleteOnExit()
            val fos = FileOutputStream(tempMp3)
            fos.write(data)
            fos.close()

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH) // SPEECH is better for TTS
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(tempMp3.absolutePath)

                // ✅ CLEANUP ONLY (No continuation resuming)
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    playbackEventBus.tryEmit(PlaybackEvent.Completed(filenameOrId = tempMp3.name))
                }

                setOnErrorListener { mp, what, extra ->
                    mp?.release()
                    mediaPlayer = null
                    playbackEventBus.tryEmit(PlaybackEvent.Failed(reason = "$what/$extra", filenameOrId = tempMp3.name))
                    true
                }

                prepareAsync()

                setOnPreparedListener {
                    it.start()
                }
            }

            // ✅ RETURN IMMEDIATELY
            // We successfully *started* the process. We don't wait for it to end.
            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    fun stopPlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.reset() //stop warning n log
            it.release()
        }
        mediaPlayer = null
        playbackEventBus.tryEmit(PlaybackEvent.Stopped())
        Timber.d("Playback stopped and resources released.")
    }
}