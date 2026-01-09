package com.goodstadt.john.language.exams.data

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
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
class AudioPlayerService @Inject constructor() {
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Plays raw audio data using MediaPlayer.
     * This is a suspend function that completes when playback is finished or fails.
     */
    suspend fun playAudioObsolete(data: ByteArray): Result<Unit> = suspendCancellableCoroutine { continuation ->
        try {
            stopPlayback()
            // MediaPlayer can't play from a byte array directly.
            // We write it to a temporary file.
            val tempMp3 = File.createTempFile("temp_audio", "mp3")
            tempMp3.deleteOnExit()
            val fos = FileOutputStream(tempMp3)
            fos.write(data)
            fos.close()

            mediaPlayer?.release() // Release any previous instance
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                )
                setDataSource(tempMp3.absolutePath)

                // --- THE CORRECTED LOGIC IS HERE ---
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    // Check if the coroutine is still active before resuming
                    if (continuation.isActive) {
                        continuation.resume(Result.success(Unit))
                    }
                }
                setOnErrorListener { mp, _, _ ->
                    mp?.release()
                    mediaPlayer = null
                    // Check if the coroutine is still active before resuming
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(RuntimeException("MediaPlayer error")))
                    }
                    true
                }

                // Handle cancellation of the coroutine
                continuation.invokeOnCancellation {
                    this@apply.release()
                    mediaPlayer = null
                }

                prepareAsync() // Asynchronously prepare the player
                setOnPreparedListener {
                    it.start()
                }
            }
        } catch (e: Exception) {
            // If an exception happens during setup, resume with failure
            if (continuation.isActive) {
                continuation.resume(Result.failure(e))
            }
        }
    }
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
                    // Do NOT try to resume anything here. The function has already returned.
                }

                setOnErrorListener { mp, _, _ ->
                    mp?.release()
                    mediaPlayer = null
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
        Timber.d("Playback stopped and resources released.")
    }
}