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

    suspend fun getTeacherParagraph(skillLevelPrompt: String, modelName: String): AIResponse {

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
}