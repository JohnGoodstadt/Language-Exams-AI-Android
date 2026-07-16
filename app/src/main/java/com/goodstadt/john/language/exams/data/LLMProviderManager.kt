package com.goodstadt.john.language.exams.data

import android.util.Log
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// The enum remains the same
enum class LLMProvider(val key: String) {
    OpenAI("openai"),
    Gemini("gemini"),
    DeepSeek("deepseek")
}

private const val CALL_THRESHOLD = 2 // Matches your iOS DEBUG logic

@Singleton
class LLMProviderManager @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) {

    // Define the sequence of rotation
    private val rotationOrder = listOf(
        LLMProvider.Gemini.key,
        LLMProvider.OpenAI.key,
        LLMProvider.DeepSeek.key
    )

    /**
     * Determines which LLM provider to use, increments the counter,
     * and handles switching providers when the threshold is reached.
     */
    suspend fun getNextProviderAndIncrement(): LLMProvider {
        var currentCount = userPreferencesRepository.llmCallCounterFlow.first()
        var currentProviderString = userPreferencesRepository.llmProviderFlow.first().ifBlank { "gemini" }

        // --- The Core Logic: Circular Rotation ---
        if (currentCount >= CALL_THRESHOLD) {
            // Find where we are in the list
            val currentIndex = rotationOrder.indexOf(currentProviderString)

            // Move to next index (wrap around to 0 if at the end)
            val nextIndex = (if (currentIndex == -1) 0 else currentIndex + 1) % rotationOrder.size

            currentProviderString = rotationOrder[nextIndex]
            currentCount = 0

            Timber.w("LLM Threshold reached. Switching to $currentProviderString.")
        }

        val nextCount = currentCount + 1

        // --- Persist state ---
        userPreferencesRepository.saveLlmCallCounter(nextCount)
        userPreferencesRepository.saveLlmProvider(currentProviderString)

        Timber.d("Using $currentProviderString. Provider call count is now $nextCount.")

        // Map the string back to the Enum
        return mapStringToProvider(currentProviderString)
    }

    /**
     * Gets the current state without incrementing the counter.
     */
    suspend fun getCurrentProviderInfo(): Pair<LLMProvider, Int> {
        val count = userPreferencesRepository.llmCallCounterFlow.first()
        val providerString = userPreferencesRepository.llmProviderFlow.first()
        return Pair(mapStringToProvider(providerString), count)
    }

    /**
     * Resets the manager state for debugging.
     */
    suspend fun reset() {
        userPreferencesRepository.saveLlmCallCounter(0)
        userPreferencesRepository.saveLlmProvider(LLMProvider.Gemini.key)
        Timber.d("LLM State has been reset.")
    }

    private fun mapStringToProvider(key: String): LLMProvider {
        return when (key.lowercase()) {
            "gemini" -> LLMProvider.Gemini
            "deepseek" -> LLMProvider.DeepSeek
            else -> LLMProvider.OpenAI
        }
    }
}