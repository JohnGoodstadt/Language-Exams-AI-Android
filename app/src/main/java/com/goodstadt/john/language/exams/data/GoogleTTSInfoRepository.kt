package com.goodstadt.john.language.exams.data


import android.content.Context
import android.util.Log
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.models.VoicesResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleTTSInfoRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // A Ktor client configured to parse JSON automatically
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    /**
     * Fetches the full list of available TTS voices from the Google Cloud API.
     * @return A Result containing the parsed VoicesResponse on success.
     */
    suspend fun fetchVoices(): Result<VoicesResponse> {
        return try {
            val response: VoicesResponse = client.get("https://texttospeech.googleapis.com/v1/voices") {
                // The API key is passed as a URL query parameter
                url {
                    parameters.append("key", BuildConfig.TTS_API_KEY)
                }
            }.body()
            Result.success(response)
        } catch (e: Exception) {
            Timber.e("Failed to fetch voices", e)
            Result.failure(e)
        }
    }

    /**
     * Saves a given string to a text file in the app's external files directory.
     * This location is accessible to the user via a file browser.
     * @param content The string content to save.
     * @param fileName The name of the file to create (e.g., "voices.json").
     * @return A Result containing the absolute path of the saved file on success.
     */
    suspend fun saveContentToFile(content: String, fileName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Get the app-specific directory on external storage (requires no special permissions)
            val dir = context.getExternalFilesDir(null)
            if (dir != null) {
                val file = File(dir, fileName)
                file.writeText(content)
                Timber.i("Successfully saved file to: ${file.absolutePath}")
                Result.success("Saved to: ${file.absolutePath}")
            } else {
                Result.failure(Exception("External storage not available."))
            }
        } catch (e: Exception) {
            Timber.e("Failed to save file", e)
            Result.failure(e)
        }
    }
}