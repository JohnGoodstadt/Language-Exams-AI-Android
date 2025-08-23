package com.goodstadt.john.language.exams.data

import android.util.Log
import com.goodstadt.john.language.exams.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerateContentResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiRepository @Inject constructor() {

    /**
     * Generates content using the specified Gemini model.
     * This function creates a new GenerativeModel instance for each call,
     * allowing the model name to be dynamic.
     *
     * @param prompt The text prompt to send to the model.
     * @param modelName The official ID of the Gemini model to use (e.g., "gemini-1.5-flash-latest").
     * @return A [Result] containing the full [GenerateContentResponse] on success,
     *         or an [Exception] on failure.
     */
    suspend fun generateContent(prompt: String, modelName: String): Result<GenerateContentResponse> {
        return try {
            // 1. Create the GenerativeModel instance on-demand with the specified model name.
            val generativeModel = GenerativeModel(
                modelName = modelName,
                apiKey = BuildConfig.GEMINI_API_KEY
            )

            Log.d("GeminiRepository", "Generating content with model: $modelName")

            // 2. Call the API. This is a suspend function.
            val response = generativeModel.generateContent(prompt)

            // 3. Return the full response object on success.
            Result.success(response)

        } catch (e: Exception) {
            // 4. If any part of the process fails, catch the exception and return a Failure result.
            Log.e("GeminiRepository", "Failed to generate content with model: $modelName", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }
    data class GeminiCallCost(
        val inputTokens: Int,
        val outputTokens: Int,
        val totalTokens: Int,
        val gptInputCostUSD: Float,
        val gptOutputCostUSD: Float,
        val totalCostUSD: Float
    )

    // --- 2. The Calculation Function ---
// Kotlin functions are defined with 'fun'.
// We use default parameter values for flexibility, just like in Swift.
    fun calculateGeminiCallCost(
        inputTokens: Int,
        outputTokens: Int,
        // We use Double for precision in calculations. Kotlin is less strict about Float vs. Double.
        inputPricePerMillion: Float,
        outputPricePerMillion: Float
    ): GeminiCallCost {

        if (inputPricePerMillion == 0.0F || outputPricePerMillion == 0.0F) { return GeminiCallCost(inputTokens,outputTokens,  0,0.0F,0.0F, totalCostUSD = 0.0F) }

        // Kotlin's numeric types are more interoperable, so explicit casting isn't always needed.
        // Multiplying an Int by a Double results in a Double.
        val gptInputCost = inputTokens / 1_000_000.0 * inputPricePerMillion
        val gptOutputCost = outputTokens / 1_000_000.0 * outputPricePerMillion

        // Create and return an instance of the GeminiCallCost data class.
        return GeminiCallCost(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            totalTokens = inputTokens + outputTokens,
            gptInputCostUSD = gptInputCost.toFloat(),
            gptOutputCostUSD = gptOutputCost.toFloat(),
            totalCostUSD = gptInputCost.toFloat() + gptOutputCost.toFloat()
        )
    }
}