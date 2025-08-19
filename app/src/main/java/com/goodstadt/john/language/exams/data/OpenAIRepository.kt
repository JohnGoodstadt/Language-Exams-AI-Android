// Create a new file in your .../data/ package
package com.goodstadt.john.language.exams.data

import android.util.Log
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.BuildConfig.OPENAI_API_KEY


import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject


import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.utils.EmptyContent.headers
import io.ktor.http.ContentType
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders
import io.ktor.http.append
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.*




import io.ktor.client.request.*
import io.ktor.http.*
 // The specific JSON serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// The API Key, as requested. In a real app, this should NOT be in source code.

private const val OPENAI_URL = "https://api.openai.com/v1/chat/completions"

// Data class to hold the parsed response, matching the Swift LLMResponse
//data class LLMResponse(
//    val content: String,
//    val totalTokens: Int,
//    val model: String
//)

 //--- A NEW DATA HOLDER for a cleaner return type ---
data class LlmResponse(
    val content: String,
    val totalTokensUsed: Int,
    val promptTokens: Int,
    val completionTokens: Int
)

@Serializable
data class OpenAIResponse(
    val choices: List<Choice>,
    val usage: UsageData // <-- ADD THIS
)

@Serializable
data class Choice(
    val message: Message
)

@Serializable
data class Message(
    val role: String,
    val content: String
)

// --- ADD THIS NEW DATA CLASS ---
@Serializable
data class UsageData(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)



@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<RequestMessage>
    // You can add other parameters here like 'temperature', 'max_tokens', etc.
)

@Serializable
data class RequestMessage(
    val role: String, // "system" or "user"
    val content: String
)

@Singleton
class OpenAIRepository @Inject constructor() {

    // A single, shared instance of the HTTP client
    private val client = OkHttpClient()
    private val client99 = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true // This is important for robustness
            })
        }
    }

    /**
     * Fetches a sentence completion from the OpenAI API.
     * This is a suspend function, making it safe to call from a coroutine.
     * It returns a result directly or throws an exception on failure.
     */

    suspend fun fetchOpenAIData(
        llmEngine: String,
        systemMessage: String,
        userQuestion: String
    ): LlmResponse { // <-- Change the return type
        // ... your existing Ktor client and request setup logic ...

        val requestBody = OpenAIRequest(
            model = llmEngine,
            messages = listOf(
                RequestMessage(role = "system", content = systemMessage),
                RequestMessage(role = "user", content = userQuestion)
            )
        )

        val response: OpenAIResponse = client99.post(OPENAI_URL) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $OPENAI_API_KEY")
                append(HttpHeaders.ContentType, Json)
            }


            // b) Set the request body. Ktor will automatically serialize it to JSON.
            setBody(requestBody)
        }.body()
//        Log.e("OpenAIRepository","$response")

        // Extract the content and the token count
        val content = response.choices.firstOrNull()?.message?.content ?: ""
        val tokensUsed = response.usage.totalTokens
        val completionTokens = response.usage.completionTokens
        val promptTokens = response.usage.promptTokens

        // Return the new, richer data object
        return LlmResponse(
            content = content,
            totalTokensUsed = tokensUsed,
            promptTokens = promptTokens,
            completionTokens = completionTokens
        )
    }
}
