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

@Singleton
class AudioPlayerService @Inject constructor() {
    private var mediaPlayer: MediaPlayer? = null

    /**
     * Plays raw audio data using MediaPlayer.
     * This is a suspend function that completes when playback is finished or fails.
     */
    suspend fun playAudio(data: ByteArray): Result<Unit> = suspendCancellableCoroutine { continuation ->
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
    fun stopPlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.stop()
            }
            it.release()
        }
        mediaPlayer = null
        Timber.d("Playback stopped and resources released.")
    }
}