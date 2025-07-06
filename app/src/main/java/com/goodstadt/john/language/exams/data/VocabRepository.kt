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
    private val vocabCache = mutableMapOf<String, VocabFile>()

    // A lazy json parser instance with lenient configuration
    private val jsonParser = Json {
        ignoreUnknownKeys = true    // Be robust against future changes in the JSON
        coerceInputValues = true    // Use default values if a field is null
        //allowTrailingCommas = true  // Allow trailing commas, common in hand-edited JSON
    }

    /**
     * Loads the vocabulary data from the specified JSON file.
     * This version is dynamic and includes a more robust cache.
     * @param fileName The name of the resource file to load (without the .json extension).
     */
    suspend fun getVocabData(fileName: String): Result<VocabFile> = withContext(Dispatchers.IO) {
        // Return from cache if available for this specific file
        vocabCache[fileName]?.let {
            println("Returning '$fileName' from cache.") // For debugging
            return@withContext Result.success(it)
        }

        try {
            // Dynamically get the resource ID from the filename string
            val resourceId = context.resources.getIdentifier(
                    fileName,
                    "raw",
                    context.packageName
            )

            // Check if the resource was found
            if (resourceId == 0) {
                return@withContext Result.failure(Exception("Resource file not found: $fileName.json"))
            }

            println("Loading '$fileName' from resources.") // For debugging
            val inputStream = context.resources.openRawResource(resourceId)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val vocabFile = jsonParser.decodeFromString<VocabFile>(jsonString)

            // Cache the result using the filename as the key
            vocabCache[fileName] = vocabFile
            Result.success(vocabFile)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    /**
     * Fetches audio data for the given text from the TTS service and plays it.
     * This version includes a file-based caching mechanism.
     */
    suspend fun playTextToSpeech(
        text: String,
        uniqueSentenceId: String,
        voiceName: String, // <-- New parameter
        languageCode: String // <-- New parameter
    ): Result<Unit> {
        // 1. Define the cache file based on the unique ID.
        // We use the app's private cache directory, which is the correct place for this.
        val cacheDir = context.cacheDir
        val audioCacheFile = File(cacheDir, "$uniqueSentenceId.mp3")

        // 2. Check if the cached file exists.
        if (audioCacheFile.exists()) {
            println("Playing from cache: Yippee!") // For debugging
            // If it exists, play the audio data from the file.
            return audioPlayerService.playAudio(audioCacheFile.readBytes())
        }

        // 3. If not cached, fetch from the network.
        println("Fetching from network: $text") // For debugging
        val audioResult = googleCloudTts.getAudioData(text, voiceName, languageCode)

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