package com.goodstadt.john.language.exams.data

import android.content.Context
import com.goodstadt.john.language.exams.R
import com.goodstadt.john.language.exams.data.api.GoogleCloudTTS
import com.goodstadt.john.language.exams.models.VocabFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VocabRepository @Inject constructor(
    @ApplicationContext private val context: Context,
        // --- NEW: Inject the new services ---
    private val googleCloudTts: GoogleCloudTTS,
    private val audioPlayerService: AudioPlayerService
) {
    // Cache the result in memory after the first successful load
    private var cachedVocabFile: VocabFile? = null

    // A lazy json parser instance with lenient configuration
    private val jsonParser = Json {
        ignoreUnknownKeys = true    // Be robust against future changes in the JSON
        coerceInputValues = true    // Use default values if a field is null
        //allowTrailingCommas = true  // Allow trailing commas, common in hand-edited JSON
    }

    /**
     * Loads the vocabulary data from the variant-specific JSON file.
     * This is a suspend function to ensure it runs on a background thread.
     * It uses an in-memory cache to avoid re-reading and re-parsing the file.
     */
    suspend fun getVocabData(): Result<VocabFile> = withContext(Dispatchers.IO) {
        // Return from cache if available
        cachedVocabFile?.let {
            return@withContext Result.success(it)
        }

        // Otherwise, read and parse the file
        try {
            // R.raw.vocab_data will point to the correct file based on the build variant
            val inputStream = context.resources.openRawResource(R.raw.vocab_data)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val vocabFile = jsonParser.decodeFromString<VocabFile>(jsonString)

            // Cache the result and return
            cachedVocabFile = vocabFile
            Result.success(vocabFile)
        } catch (e: Exception) {
            // Log the exception in a real app
            e.printStackTrace()
            Result.failure(e)
        }
    }
    /**
     * Fetches audio data for the given text from the TTS service and plays it.
     * This version includes a file-based caching mechanism.
     */
    suspend fun playTextToSpeech(text: String, uniqueSentenceId: String): Result<Unit> {
        // 1. Define the cache file based on the unique ID.
        // We use the app's private cache directory, which is the correct place for this.
        val cacheDir = context.cacheDir
        val audioCacheFile = File(cacheDir, "$uniqueSentenceId.mp3")

        // 2. Check if the cached file exists.
        if (audioCacheFile.exists()) {
            println("Playing from cache: ${audioCacheFile.name}") // For debugging
            // If it exists, play the audio data from the file.
            return audioPlayerService.playAudio(audioCacheFile.readBytes())
        }

        // 3. If not cached, fetch from the network.
        println("Fetching from network: $text") // For debugging
        val audioResult = googleCloudTts.getAudioData(text)

        return audioResult.fold(
                onSuccess = { audioData ->
                    // 4. On successful fetch, SAVE the data to the cache file.
                    try {
                        audioCacheFile.writeBytes(audioData)
                        println("Saved to cache: ${audioCacheFile.name}") // For debugging
                    } catch (e: Exception) {
                        // Caching failed, but we can still proceed with playback.
                        // Log this error in a real app.
                        e.printStackTrace()
                    }

                    // 5. Play the newly fetched audio data.
                    audioPlayerService.playAudio(audioData)
                },
                onFailure = { exception ->
                    // If network call fails, pass the failure along.
                    Result.failure(exception)
                }
        )
    }
}