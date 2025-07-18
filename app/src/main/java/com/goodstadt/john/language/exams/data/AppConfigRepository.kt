package com.goodstadt.john.language.exams.data

import com.goodstadt.john.language.exams.BuildConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.tasks.await
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

    val TAG = "AppConfigRepository"
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

        println("AppConfigRepository comparing min:$minRequiredVersion and rec:$recommendedVersion and $currentVersionCode")

        return when {
           //
            // 1. Check for forced update first (most critical)
            currentVersionCode < minRequiredVersion -> {
                println("AppConfigRepository.ForcedUpdate()")
                UpdateState.ForcedUpdate(forcedMessage, updateUrl)
            }
            // 2. Then check for optional update
            currentVersionCode < recommendedVersion -> {
                println("AppConfigRepository.OptionalUpdate()")
                UpdateState.OptionalUpdate(optionalMessage, updateUrl)
            }
            // 3. Otherwise, no update is needed
            else -> {
                println("AppConfigRepository.NoUpdateNeeded()")
                UpdateState.NoUpdateNeeded
            }
        }
    }
}