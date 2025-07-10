package com.goodstadt.john.language.exams.data.api

import android.util.Base64
import android.util.Log
import com.goodstadt.john.language.exams.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleCloudTTS @Inject constructor() {

    private val TAG = "GoogleCloudTTS"

    // --- Data classes remain the same ---
    @Serializable
    private data class TtsRequest(
        val input: TtsInput,
        val voice: TtsVoice,
        val audioConfig: TtsAudioConfig
    )

    @Serializable
    private data class TtsInput(val text: String)

    @Serializable
    private data class TtsVoice(val languageCode: String, val name: String)

    @Serializable
    private data class TtsAudioConfig(val audioEncoding: String, val speakingRate: Float)

    @Serializable
    private data class TtsResponse(val audioContent: String)


    private val apiKey = BuildConfig.TTS_API_KEY
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getAudioData(
        text: String,
        voiceName: String,
        languageCode: String
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        val urlString = "https://texttospeech.googleapis.com/v1/text:synthesize?key=$apiKey"
        var connection: HttpURLConnection? = null

        try {
            val request = TtsRequest(
                    input = TtsInput(text = text),
                    voice = TtsVoice(languageCode = languageCode, name = voiceName),
                    audioConfig = TtsAudioConfig(
                            audioEncoding = "MP3",
                            speakingRate = 0.9f,
                    )
            )

            // --- FIX 1: Update encodeToString syntax ---
            val requestBody = json.encodeToString<TtsRequest>(request)

            Log.d(TAG, "Sending TTS Request Body: $requestBody")

            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.doOutput = true

            OutputStreamWriter(connection.outputStream).use {
                it.write(requestBody)
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseBody = connection.inputStream.bufferedReader().use { it.readText() }

                // --- FIX 2: Update decodeFromString syntax ---
                val ttsResponse = json.decodeFromString<TtsResponse>(responseBody)

                val audioBytes = Base64.decode(ttsResponse.audioContent, Base64.DEFAULT)
                Result.success(audioBytes)
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                Log.e(TAG, "API Error: $responseCode - $errorBody")
                Result.failure(RuntimeException("API Error: $responseCode - $errorBody"))
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }
}