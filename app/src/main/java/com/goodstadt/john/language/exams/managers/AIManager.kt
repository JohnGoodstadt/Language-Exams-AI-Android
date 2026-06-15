package com.goodstadt.john.language.exams.managers

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class AIManager {

    // 1. Initialize pointing to the London region
    private val functions = Firebase.functions("europe-west2")

    // Result wrapper to pass data and errors back to ViewModel
    data class AIResponse(
        val text: String? = null,
        val usage: Map<String, Int>? = null,
        val error: AIError? = null
    )

    enum class AIError { BUSY, LIMIT_REACHED, UNAUTHENTICATED, UNKNOWN }

    suspend fun getTeacherParagraphGemini(skillLevelPrompt: String, modelName: String): AIResponse {

        // 2. Auth Check
        if (FirebaseAuth.getInstance().currentUser == null) {
            return AIResponse(error = AIError.UNAUTHENTICATED)
        }

        val data = hashMapOf(
            "systemPrompt" to skillLevelPrompt,
            "model" to modelName
        )

        return try {
            // 3. Call the Cloud Function
            val result = functions
                .getHttpsCallable("callGeminiProxy")
                .call(data)
                .await()

            // 4. Parse Success
            val responseMap = result.data as? Map<*, *>
            val aiText = responseMap?.get("text") as? String
            val usage = responseMap?.get("usage") as? Map<String, Int>

            AIResponse(text = aiText, usage = usage)

        } catch (e: Exception) {
            // 5. Handle Specific Firebase Errors
            if (e is FirebaseFunctionsException) {
                val code = e.code
                val message = e.message

                Timber.e("❌ Function Error: [$code] $message")

                when (code) {
                    FirebaseFunctionsException.Code.UNAVAILABLE -> {
                        // Mapped from Gemini 503 in index.js
                        AIResponse(error = AIError.BUSY)
                    }
                    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> {
                        // Mapped from Gemini 429 in index.js
                        AIResponse(error = AIError.LIMIT_REACHED)
                    }
                    else -> AIResponse(error = AIError.UNKNOWN)
                }
            } else {
                Timber.e(e, "❌ General Network Error")
                AIResponse(error = AIError.UNKNOWN)
            }
        }
    }
    suspend fun getTeacherParagraphOpenAICloudFunction(systemPrompt: String, userPrompt: String, model: String): AIResponse {
        val data = hashMapOf(
            "systemPrompt" to systemPrompt,
            "userPrompt" to userPrompt,
            "model" to model
        )

        return try {
            val result = functions.getHttpsCallable("callOpenAIProxy").call(data).await()
            val res = result.data as Map<*, *>
            AIResponse(
                text = res["text"] as? String,
                usage = res["usage"] as? Map<String, Int>
            )
        } catch (e: Exception) {
            // 5. Handle Specific Firebase Errors
            if (e is FirebaseFunctionsException) {
                val code = e.code
                val message = e.message

                Timber.e("❌ Function Error: [$code] $message")

                when (code) {
                    FirebaseFunctionsException.Code.UNAVAILABLE -> {
                        // Mapped from Gemini 503 in index.js
                        AIResponse(error = AIError.BUSY)
                    }
                    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> {
                        // Mapped from Gemini 429 in index.js
                        AIResponse(error = AIError.LIMIT_REACHED)
                    }
                    else -> AIResponse(error = AIError.UNKNOWN)
                }
            } else {
                Timber.e(e, "❌ General Network Error")
                AIResponse(error = AIError.UNKNOWN)
            }
        }
    }


    /**
     * Android version of AIManager.getTeacherParagraphDeepSeek
     *
     * Returns:
     * - String? (The AI Paragraph)
     * - Map<String, Int>? (The usage stats: inputTokens, outputTokens, totalTokens)
     * - Exception? (The error, if any)
     */
    suspend fun getTeacherParagraphDeepSeek(
        systemPrompt: String,
        userPrompt: String,
        model: String
    ): Triple<String?, Map<String, Int>?, Exception?> {

        // 1. Initialize pointing to your London server
        val functions = Firebase.functions("europe-west2")

        // 2. Prepare the parameters
        val data = hashMapOf(
            "systemPrompt" to systemPrompt,
            "userPrompt" to userPrompt,
            "model" to model
        )

        return try {
            // 3. Call the Cloud Function
            val result = functions
                .getHttpsCallable("callDeepSeekProxy")
                .call(data)
                .await()

            // 4. Parse the result (Cast from Any?)
            val responseMap = result.data as? Map<*, *>
            val aiText = responseMap?.get("text") as? String

            // DeepSeek usage mapping (standardized by our Node.js script)
            val usage = responseMap?.get("usage") as? Map<String, Int>

            // Return: (Text, Stats, Error=null)
            Triple(aiText, usage, null)

        } catch (e: Exception) {
            // 5. Error Handling
            if (e is FirebaseFunctionsException) {
                val code = e.code
                val message = e.message

                // Log exactly like we did in Swift for debugging
                Timber.e("❌ DeepSeek Proxy Error: [$code] $message")

                // Check for specific DeepSeek issues passed through index.js
                if (message?.contains("insufficient_balance") == true) {
                    Timber.e("💸 DEEPSEEK ALERT: Account balance empty!")
                }
            }

            // Return: (null, null, Exception)
            Triple(null, null, e)
        }
    }

}