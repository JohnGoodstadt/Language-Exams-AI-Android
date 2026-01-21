package com.goodstadt.john.language.exams.data

import android.content.SharedPreferences
import android.util.Log
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.models.AppUIManifest
import com.goodstadt.john.language.exams.models.LlmModelInfo
import com.goodstadt.john.language.exams.models.TabDefinition
import com.goodstadt.john.language.exams.models.TabsManifest
import com.goodstadt.john.language.exams.utils.logging.TimberFault
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
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
    private val prefs: SharedPreferences,
    private val jsonParser: Json
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
     fun checkAppUpdateStatus(): UpdateState {
        // Fetch the latest values from the server. This is fast because of caching.
//        try {
//            remoteConfig.fetchAndActivate().await()
//        } catch (e: Exception) {
//            // If fetch fails, we proceed with the last known cached values.
//            e.printStackTrace()
//            Timber.wtf(e.localizedMessage)
//        }

        // Get the current version code of the installed app
        val currentVersionCode = BuildConfig.VERSION_CODE

        // Get the version codes from Remote Config
        val minRequiredVersion = remoteConfig.getLong("android_minimum_version_code") //e.g. 73
        val recommendedVersion = remoteConfig.getLong("android_recommended_version_code") //e.g. 78

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
    suspend fun getRemoteSheetVersionsOriginal(): Map<String, Int> {
        try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch remote config for sheet versions")
        }

        val versionsJson = remoteConfig.getString("sheet_versions")
        return if (versionsJson.isNotBlank()) {
            try {
                //this works
                //val fred = Json.decodeFromString<Map<String, Int>>(versionsJson)
                //Timber.i("$fred")
                Json.decodeFromString<Map<String, Int>>(versionsJson)
            } catch (e: Exception) {
                Timber.e(e, "Could not parse remote sheet versions JSON (1)")
                emptyMap()
            }
        } else {
            Timber.e("Could not parse remote sheet versions JSON (2)")
            emptyMap()
        }
    }
    suspend fun getRemoteSheetVersions(): Map<String, Int> {
        // 1. Fetch from network (We keep this for Prod, but it doesn't hurt in Debug)
        try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch remote config for sheet versions")
        }

        // 2. DECIDE: Real JSON or Fake JSON?
        val versionsJson = if (BuildConfig.DEBUG) {
            Timber.w("⚠️ DEV MODE: Using Local Sheet Versions Override")
            // Paste your Full JSON here (Existing sheets + New Spanish ones)
            """
        {
            "EnglishPrepositions": 5,
            "EnglishA1Adjectives": 5,
            "EnglishA2Adjectives": 5,
            "EnglishB1Adjectives": 5,
            "EnglishB2Adjectives": 5,
            "EnglishA1Vocab": 8,
            "EnglishA2Vocab": 11,
            "EnglishB1Vocab": 8,
            "EnglishB2Vocab": 8,
            "EnglishDefinitionsFormat1": 4,
            "EnglishGoodVsWell": 5,
            "EnglishSayVsTell": 1,
            "EnglishSpeakVsTalk": 1,
            "EnglishHearVsListen": 1,
            "EnglishBorrowVsLend": 1,
            "EnglishBringVsTake": 1,
            "EnglishLookVsSee": 1,
            "SpanishReferenceSheet1": 1,
            "SpanishReferenceSheet2": 1,
            "SpanishReferenceSheet3": 1,
            "SpanishReferenceSheet4": 1
        }
        """.trimIndent()
        } else {
            // Production: Get from Firebase
            remoteConfig.getString("sheet_versions")
        }

        // 3. Decode whatever string we got (Local or Remote)
        return if (versionsJson.isNotBlank()) {
            try {
                Json.decodeFromString<Map<String, Int>>(versionsJson)
            } catch (e: Exception) {
                Timber.e(e, "Could not parse remote sheet versions JSON")
                emptyMap()
            }
        } else {
            Timber.e("Could not parse remote sheet versions JSON (Empty)")
            emptyMap()
        }
    }
    /**
     * Fetches and parses the entire UI manifest from Remote Config.
     * This function is the single source of truth for UI structure.
     * It handles fetching, caching (via Remote Config SDK), and fallback to defaults.
     *
     * @return The parsed AppUIManifest, or a default/empty manifest on failure.
     */
    fun getAppUiManifestOriginal(): AppUIManifest {
        val crashlytics = FirebaseCrashlytics.getInstance()

        // Get the single manifest JSON string from Remote Config
        val manifestJsonString = remoteConfig.getString("app_ui_manifest")


        return if (manifestJsonString.isNotBlank()) {
            try {
                // Attempt to parse the JSON string from the server or cache
                jsonParser.decodeFromString<AppUIManifest>(manifestJsonString)
            } catch (e: Exception) {

                // If parsing the remote/cached JSON fails, log it and fall back to the bundled default.
                Timber.e(e, "CRITICAL: Failed to parse 'app_ui_manifest' from remote. Falling back to default.")

                // 3. Record the non-fatal exception.
                // This sends the full exception object, including its stack trace, to Firebase.
                crashlytics.recordException(e)

                parseDefaultManifest()


            }
        } else {
            val error = Exception("Data load failed completely for app_ui_manifest")
            crashlytics.recordException(error)

            // If the remote string is empty, fall back to the default immediately.
            Timber.w("Remote 'app_ui_manifest' is blank. Falling back to default.")
            parseDefaultManifest()
        }
    }
    fun getAppUiManifest(): AppUIManifest {
        val crashlytics = FirebaseCrashlytics.getInstance()

        // 1. Determine which JSON string to use
        val manifestJsonString = if (BuildConfig.DEBUG) {
            Timber.w("⚠️ DEV MODE: Using Local Manifest Override")
            // Hardcoded JSON for testing
            """
        {
            "sheetRegistry": {
                "quiz": {
                    "title": "Quiz.",
                    "sheetDataType": "fixed",
                    "screenType": "FixedScreen"
                },
                "conjugations": {
                    "title": "Conjugations.",
                    "sheetDataType": "fixed",
                    "screenType": "FixedScreen"
                },
                "EnglishPrepositions": {
                    "title": "Prepositions.",
                    "sheetDataType": "VocabFile",
                    "screenType": "VocabScreen",
                    "firestoreDocumentId": "EnglishPrepositions"
                },
                "AdjectivesGroup": {
                    "title": "Adjectives.",
                    "screenType": "GroupedVocabScreen",
                    "sheetDataType": "VocabFile",
                    "subTabs": [
                        { "title": "Basic", "firestoreDocumentId": "EnglishA1Adjectives", "sheetDataType": "VocabFile" },
                        { "title": "Intermediate", "firestoreDocumentId": "EnglishA2Adjectives", "sheetDataType": "VocabFile" },
                        { "title": "Upper", "firestoreDocumentId": "EnglishB1Adjectives", "sheetDataType": "VocabFile" },
                        { "title": "Advanced", "firestoreDocumentId": "EnglishB2Adjectives", "sheetDataType": "VocabFile" }
                    ]
                },
                "EnglishDefinitionsFormat1": {
                    "title": "Sounds the Same",
                    "sheetDataType": "Format1",
                    "screenType": "Format1Screen",
                    "firestoreDocumentId": "EnglishDefinitionsFormat1"
                },
               "PairsGroup": {
      "title": "Word Pairs",
      "screenType": "GroupedFormat2Screen",
      "sheetDataType": "Format2",
      "subTabs": [
        {
          "title": "Good vs Well",
          "firestoreDocumentId": "EnglishGoodVsWell",
          "sheetDataType": "Format2",
          "screenType": "Format2Screen"
        },
        {
          "title": "Say vs Tell",
          "firestoreDocumentId": "EnglishSayVsTell",
          "sheetDataType": "Format2",
          "screenType": "Format2Screen"
        },
        {
          "title": "Speak vs Talk",
          "firestoreDocumentId": "EnglishSpeakVsTalk",
          "sheetDataType": "Format2",
          "screenType": "Format2Screen"
        },
        {
          "title": "Hear vs Listen",
          "firestoreDocumentId": "EnglishHearVsListen",
          "sheetDataType": "Format2",
          "screenType": "Format2Screen"
        },
        {
          "title": "Borrow vs Lend",
          "firestoreDocumentId": "EnglishBorrowVsLend",
          "sheetDataType": "Format2",
          "screenType": "Format2Screen"
        },
        {
          "title": "Bring Vs Take",
          "firestoreDocumentId": "EnglishBringVsTake",
          "sheetDataType": "Format2",
          "screenType": "Format2Screen"
        },
        {
          "title": "Look vs See",
          "firestoreDocumentId": "EnglishLookVsSee",
          "sheetDataType": "Format2",
          "screenType": "Format2Screen"
        }
      ]
    },
                "SpanishLanguage": {
                    "title": "Spanish",
                    "screenType": "GroupedFormat3Screen",
                    "sheetDataType": "Format3",
                    "requiredLocale": "es",
                    "subTabs": [
                        { "title": "Vowels", "firestoreDocumentId": "SpanishReferenceSheet1", "sheetDataType": "Format3" },
                        { "title": "Word Stress", "firestoreDocumentId": "SpanishReferenceSheet2", "sheetDataType": "Format3" },
                        { "title": "Schwa", "firestoreDocumentId": "SpanishReferenceSheet3", "sheetDataType": "Format3" },
                        { "title": "Final", "firestoreDocumentId": "SpanishReferenceSheet4", "sheetDataType": "Format3" }
                    ]
                }
            },
            "layouts": {
                "referenceTab": {
                    "order": [
                        "quiz",
                        "conjugations",
                        "EnglishPrepositions",
                        "SpanishLanguage",
                        "AdjectivesGroup",
                        "EnglishDefinitionsFormat1",
                        "PairsGroup"
                        
                    ]
                },
                "meTab": {
                    "order": [
                        "focusing",
                        "settings",
                        "vocabulary",
                        "progress",
                        "paragraph"
                    ]
                }
            }
        }
        """.trimIndent()
        } else {
            // PRODUCTION: Fetch from Remote Config
            remoteConfig.getString("app_ui_manifest")
        }

        // 2. Proceed with Parsing (The rest of your code stays exactly the same)
        return if (manifestJsonString.isNotBlank()) {
            try {
                // Attempt to parse the JSON string from the server (or our debug string)
                jsonParser.decodeFromString<AppUIManifest>(manifestJsonString)
            } catch (e: Exception) {
                // If parsing fails, log it and fall back to the bundled default.
                Timber.e(e, "CRITICAL: Failed to parse 'app_ui_manifest'. Falling back to default.")
                crashlytics.recordException(e)
                parseDefaultManifest()
            }
        } else {
            val error = Exception("Data load failed completely for app_ui_manifest")
            crashlytics.recordException(error)
            Timber.w("Remote 'app_ui_manifest' is blank. Falling back to default.")
            parseDefaultManifest()
        }
    }
    /**
     * A private helper to parse the default manifest from the defaults map.
     * This is the ultimate safety net.
     */
    private fun parseDefaultManifest(): AppUIManifest {
        val defaultJsonString = remoteConfig.getString("app_ui_manifest") // Gets the default value
        return if (defaultJsonString.isNotBlank()) {
            try {
                jsonParser.decodeFromString<AppUIManifest>(defaultJsonString)
            } catch (e: Exception) {
                Timber.e(e, "FATAL: Could not parse BUNDLED default 'app_ui_manifest'. Check your defaults file.")
                AppUIManifest() // Return a completely empty manifest as a last resort
            }
        } else {
            Timber.e("FATAL: BUNDLED default 'app_ui_manifest' is missing or blank.")
            AppUIManifest()
        }
    }
    // In AppConfigRepository or MainActivity
    fun debugRemoteConfig() {
        val config = Firebase.remoteConfig

        config.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val updated = task.result
                Timber.d("Config params updated: $updated")

                // Check what the server actually sent
                val json = config.getString("app_ui_manifest")
                val info = config.info

                Timber.d("Fetch status: ${info.lastFetchStatus}")
                Timber.d("Content Source: ${info.lastFetchStatus}") // Should be 'REMOTE'
                Timber.d("JSON starts with: ${json.take(50)}") // Check if it has PairsGroup

            } else {
                Timber.e("Config fetch failed")
            }
        }
    }
    /**
     * Fetches the configuration for the reference tabs from Remote Config.
     * Returns a default list of fixed tabs if the fetch fails, the config is empty,
     * or the JSON is malformed.
     */
    /*
    Not now needed

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
       */

}