package com.goodstadt.john.language.exams.data

import android.util.Log
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// The enum remains the same
enum class LLMProvider {
    OpenAI,
    Gemini
}

private const val CALL_THRESHOLD = 3//20

@Singleton
class LLMProviderManager @Inject constructor(
    // It depends only on your existing repository
    private val userPreferencesRepository: UserPreferencesRepository
) {

    /**
     * Determines which LLM provider to use, increments the counter,
     * and handles switching providers when the threshold is reached.
     */
    suspend fun getNextProviderAndIncrement(): LLMProvider {
        // 1. Get the current state from the repository's flows.
        //    .first() gets the most recent value.
        var currentCount = userPreferencesRepository.llmCallCounterFlow.first()
        var currentProviderString = userPreferencesRepository.llmProviderFlow.first()

        // --- The Core Logic (remains the same) ---
        if (currentCount >= CALL_THRESHOLD) {
            currentProviderString = if (currentProviderString == "openai") "gemini" else "openai"
            currentCount = 0
            Timber.w("Call threshold reached. Switching to $currentProviderString.")
        }

        val nextCount = currentCount + 1

        // --- 2. Persist the new state via repository functions ---
        userPreferencesRepository.saveLlmCallCounter(nextCount)
        userPreferencesRepository.saveLlmProvider(currentProviderString)

        Timber.d("Using $currentProviderString. Call count is now $nextCount.")

        return if (currentProviderString == "gemini") LLMProvider.Gemini else LLMProvider.OpenAI
    }

    /**
     * Gets the current state without incrementing the counter.
     */
    suspend fun getCurrentProviderInfo(): Pair<LLMProvider, Int> {
        val count = userPreferencesRepository.llmCallCounterFlow.first()
        val providerString = userPreferencesRepository.llmProviderFlow.first()
        val provider = if (providerString == "gemini") LLMProvider.Gemini else LLMProvider.OpenAI
        return Pair(provider, count)
    }

    /**
     * Resets the manager state for debugging.
     */
    suspend fun reset() {
        userPreferencesRepository.saveLlmCallCounter(0)
        userPreferencesRepository.saveLlmProvider("openai") // Reset to default
        Timber.d("State has been reset.")
    }
}