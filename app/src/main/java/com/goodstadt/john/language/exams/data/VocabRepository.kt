package com.goodstadt.john.language.exams.data

import android.content.Context
import com.goodstadt.john.language.exams.R
import com.goodstadt.john.language.exams.models.VocabFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VocabRepository @Inject constructor(
    @ApplicationContext private val context: Context
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
}