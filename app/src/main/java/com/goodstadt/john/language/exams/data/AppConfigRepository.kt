package com.goodstadt.john.language.exams.data

import android.util.Log
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.models.LlmModelInfo
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

// A sealed class to represent the outcome of the version check
sealed class UpdateState {
    data object NoUpdateNeeded : UpdateState()
    data class OptionalUpdate(val message: String, val url: String) : UpdateState()
    data class ForcedUpdate(val message: String, val url: String) : UpdateState()
}

@Singleton
class AppConfigRepository @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig
) {

    private val defaultModels = listOf(
        LlmModelInfo(
            "gpt-4.1-nano", "GPT-4.1-nano", 0.40f, isDefault = true,
            inputPrice = 0.05F,
            outputPrice = 0.4F
        )
    )

   // val TAG = "AppConfigRepository"
    suspend fun checkAppUpdateStatus(): UpdateState {
        // Fetch the latest values from the server. This is fast because of caching.
        try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            // If fetch fails, we proceed with the last known cached values.
            e.printStackTrace()
        }

        // Get the current version code of the installed app
        val currentVersionCode = BuildConfig.VERSION_CODE

        // Get the version codes from Remote Config
        val minRequiredVersion = remoteConfig.getLong("android_minimum_version_code")
        val recommendedVersion = remoteConfig.getLong("android_recommended_version_code")

        // Get the messages and URL
        val optionalMessage = remoteConfig.getString("update_message_optional")
        val forcedMessage = remoteConfig.getString("update_message_forced")
        val updateUrl = remoteConfig.getString("update_url_android")

        Timber.v("AppConfigRepository comparing min:$minRequiredVersion and rec:$recommendedVersion and $currentVersionCode")

        return when {
           //
            // 1. Check for forced update first (most critical)
            currentVersionCode < minRequiredVersion -> {
                Timber.v("AppConfigRepository.ForcedUpdate()")
                UpdateState.ForcedUpdate(forcedMessage, updateUrl)
            }
            // 2. Then check for optional update
            currentVersionCode < recommendedVersion -> {
                Timber.v("AppConfigRepository.OptionalUpdate()")
                UpdateState.OptionalUpdate(optionalMessage, updateUrl)
            }
            // 3. Otherwise, no update is needed
            else -> {
                Timber.v("AppConfigRepository.NoUpdateNeeded()")
                UpdateState.NoUpdateNeeded
            }
        }
    }
    suspend fun getAvailableOpenAIModels(): List<LlmModelInfo> {
        // Ensure the latest values are fetched and activated
        try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            e.printStackTrace() // Log the error, but proceed with cached/default values
        }

        val jsonString = remoteConfig.getString("llm_models_config")

        return if (jsonString.isNotBlank()) {
            try {
                // Try to parse the JSON string from Remote Config
                return Json.decodeFromString<List<LlmModelInfo>>(jsonString)
            } catch (e: Exception) {
                // If parsing fails (e.g., malformed JSON in the console), return the safe default
                Timber.e("Failed to parse open AI LLM models JSON", e)
                defaultModels
            }
        } else {
            // If the remote value is empty, return the safe default
            defaultModels
        }
    }
    suspend fun getAvailableGeminiModels(): List<LlmModelInfo> {
        // Ensure the latest values are fetched and activated
        try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            e.printStackTrace() // Log the error, but proceed with cached/default values
        }

        val jsonString = remoteConfig.getString("gemini_models_config")

        return if (jsonString.isNotBlank()) {
            try {
                // Try to parse the JSON string from Remote Config
                Json.decodeFromString<List<LlmModelInfo>>(jsonString)
            } catch (e: Exception) {
                // If parsing fails (e.g., malformed JSON in the console), return the safe default
                Timber.e("Failed to parse LLM Gemini models JSON", e)
                defaultModels
            }
        } else {
            // If the remote value is empty, return the safe default
            defaultModels
        }
    }
}