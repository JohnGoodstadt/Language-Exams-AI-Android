// Create a new file in your .../data/ package
package com.goodstadt.john.language.exams.data

import android.util.Log
import com.goodstadt.john.language.exams.BuildConfig.OPENAI_API_KEY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

// The API Key, as requested. In a real app, this should NOT be in source code.

private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"

// Data class to hold the parsed response, matching the Swift LLMResponse
data class LLMResponse(
    val content: String,
    val totalTokens: Int,
    val model: String
)

@Singleton
class OpenAIRepository @Inject constructor() {

    // A single, shared instance of the HTTP client
    private val client = OkHttpClient()

    /**
     * Fetches a sentence completion from the OpenAI API.
     * This is a suspend function, making it safe to call from a coroutine.
     * It returns a result directly or throws an exception on failure.
     */
    suspend fun fetchOpenAIData(
        llmEngine: String,
        systemMessage: String,
        userQuestion: String
    ): LLMResponse {
        // Ensure this network call runs on a background thread
        return withContext(Dispatchers.IO) {
            // 1. Build the JSON request body using org.json
            val jsonBody = JSONObject().apply {
                put("model", llmEngine)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemMessage)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userQuestion)
                    })
                })
            }

            Log.d("OpenAIRepository","model used in call is ${llmEngine}")

            // 2. Create the request
            val body = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(OPENAI_URL)
                .header("Authorization", "Bearer $OPENAI_API_KEY")
                .header("Content-Type", "application/json")
                .post(body)
                .build()

            // 3. Execute the request and process the response
            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                throw Exception("API call failed with code: ${response.code} and message: ${response.message}")
            }

            val responseBody = response.body?.string()
                ?: throw Exception("Received an empty response body from the API.")

            // 4. Parse the JSON response
            val jsonResponse = JSONObject(responseBody)

            val content = jsonResponse.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            val totalTokens = jsonResponse.getJSONObject("usage")
                .getInt("total_tokens")

            val model = jsonResponse.getString("model")

            // 5. Return the structured data class
            LLMResponse(
                    content = content,
                    totalTokens = totalTokens,
                    model = model
            )
        }
    }
}
