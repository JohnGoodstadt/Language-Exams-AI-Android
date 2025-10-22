package com.goodstadt.john.language.exams.data

import android.content.SharedPreferences
import android.util.Log
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.models.LlmModelInfo
import com.goodstadt.john.language.exams.models.TabDefinition
import com.goodstadt.john.language.exams.models.TabsManifest
import com.goodstadt.john.language.exams.utils.logging.TimberFault
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    private val remoteConfig: FirebaseRemoteConfig,
    private val prefs: SharedPreferences
) {

    //A key for storing our local versions map
    private val KEY_LOCAL_SHEET_VERSIONS = "local_sheet_versions_cache"

    private val defaultModels = listOf(
        LlmModelInfo(
            "gpt-4.1-nano", "GPT-4.1-nano", 0.40f, isDefault = true,
            inputPrice = 0.05F,
            outputPrice = 0.4F
        )
    )

    //Reference Tab dynamic sheets
    private val defaultTabs = listOf(
        TabDefinition(id = "quiz", title = "Quiz", type = "fixed_view"),
        TabDefinition(id = "conjugations", title = "Conjugations", type = "fixed_view"),
        TabDefinition(id = "prepositions", title = "Prepositions", type = "fixed_view")
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

        Timber.w("AppConfigRepository comparing min:$minRequiredVersion and rec:$recommendedVersion and $currentVersionCode")



        return when {
           //
            // 1. Check for forced update first (most critical)
            currentVersionCode < minRequiredVersion -> {
                Timber.w("AppConfigRepository.ForcedUpdate()")
                UpdateState.ForcedUpdate(forcedMessage, updateUrl)
            }
            // 2. Then check for optional update
            currentVersionCode < recommendedVersion -> {
                Timber.w("AppConfigRepository.OptionalUpdate()")
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
//                Timber.e("Failed to parse open AI LLM models JSON", e)
                TimberFault.f(
                    message = "Failed to parse open AI LLM models JSON",
                    localizedMessage = e.localizedMessage ?: "null localizedMessage",
                    secondaryText = jsonString,
                    area = "AppConfigRepository.getAvailableOpenAIModels()"
                )
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
                TimberFault.f(
                    message = "Failed to parse LLM Gemini models JSON",
                    localizedMessage = e.localizedMessage ?: "null localizedMessage",
                    secondaryText = jsonString,
                    area = "AppConfigRepository.getAvailableGeminiModels()"
                )
                defaultModels
            }
        } else {
            // If the remote value is empty, return the safe default
            defaultModels
        }
    }
    fun getPrepositionsDataVersion(): Int {
        // Use getLong and convert to Int. This is safer than getDouble.
        return remoteConfig.getLong("prepositions_data_version").toInt()
    }
    /**
     * Gets the locally stored version for a specific sheet.
     * @param sheetName The unique identifier for the sheet (e.g., "EnglishPrepositions").
     * @return The stored version number, or 0 if none is found.
     */
    fun getLocalVersion(sheetName: String): Int {
        val versionsJson = prefs.getString(KEY_LOCAL_SHEET_VERSIONS, "{}") ?: "{}"
        return try {
            val versionsMap = Json.parseToJsonElement(versionsJson).jsonObject
            versionsMap[sheetName]?.jsonPrimitive?.int ?: 0
        } catch (e: Exception) {
            Timber.e(e, "Could not parse local sheet versions JSON")
            0
        }
    }

    /**
     * Updates the locally stored version for a specific sheet.
     * @param sheetName The unique identifier for the sheet.
     * @param newVersion The new version number to store.
     */
    fun updateLocalVersion(sheetName: String, newVersion: Int) {
        val versionsJson = prefs.getString(KEY_LOCAL_SHEET_VERSIONS, "{}") ?: "{}"
        val versionsMap = try {
            Json.parseToJsonElement(versionsJson).jsonObject.toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }

        versionsMap[sheetName] = Json.encodeToJsonElement(newVersion)

        prefs.edit().putString(KEY_LOCAL_SHEET_VERSIONS, Json.encodeToString(versionsMap)).apply()
        Timber.d("Repo: Local version for '$sheetName' updated to v$newVersion.")
    }
    /**
     * Fetches the map of all sheet versions from Remote Config.
     * This function should be suspend to ensure latest values are fetched.
     */
    suspend fun getRemoteSheetVersions(): Map<String, Int> {
        try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch remote config for sheet versions")
        }

        val versionsJson = remoteConfig.getString("sheet_versions")
        return if (versionsJson.isNotBlank()) {
            try {
                Json.decodeFromString<Map<String, Int>>(versionsJson)
            } catch (e: Exception) {
                Timber.e(e, "Could not parse remote sheet versions JSON")
                emptyMap()
            }
        } else {
            emptyMap()
        }
    }

    /**
     * Fetches the configuration for the reference tabs from Remote Config.
     * Returns a default list of fixed tabs if the fetch fails, the config is empty,
     * or the JSON is malformed.
     */
    suspend fun getReferenceTabs(): List<TabDefinition> {
        // Ensure the latest values are fetched and activated, consistent with other functions
        try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch remote config for tabs, will use cached/default values.")
        }

        val jsonString = remoteConfig.getString("reference_view_tabs")

        return if (jsonString.isNotBlank()) {
            try {
                // Try to parse the JSON string from Remote Config
                val manifest = Json.decodeFromString<TabsManifest>(jsonString)
                manifest.tabs
            } catch (e: Exception) {
                // If parsing fails, log the error and return the safe default
                Timber.e(e, "Failed to parse reference_view_tabs JSON")
                // You could add TimberFault here if desired
                defaultTabs
            }
        } else {
            // If the remote value is empty, return the safe default
            defaultTabs
        }
    }
}