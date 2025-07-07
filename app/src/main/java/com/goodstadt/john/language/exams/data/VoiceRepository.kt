package com.goodstadt.john.language.exams.data

import android.content.Context
import com.goodstadt.john.language.exams.models.VoiceInfo
import com.goodstadt.john.language.exams.screens.utils.GenderAsIntSerializer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// We can reuse the VoiceOption data class or create a new one. Let's use it.
// data class VoiceOption(val id: String, val friendlyName: String) is already defined.
/**
 * A simple data class to represent a selectable voice option in the UI.
 */
data class VoiceOption(val id: String, val friendlyName: String, val gender: Gender)

@Serializable(with = GenderAsIntSerializer::class)
enum class Gender {
    FEMALE,
    MALE,
    UNKNOWN
}

@Singleton
class VoiceRepository @Inject constructor(
        // Inject the context to access assets
    @ApplicationContext private val context: Context
) {
    private val jsonParser = Json { ignoreUnknownKeys = true }

    // Cache the loaded voices to avoid re-reading the file
    private var cachedVoices: List<VoiceOption>? = null

    /**
     * Loads the available voices from the flavor-specific 'voices.json' file.
     * The build system ensures the correct file is present in the assets.
     */
    suspend fun getAvailableVoices(): Result<List<VoiceOption>> = withContext(Dispatchers.IO) {
        cachedVoices?.let {
            return@withContext Result.success(it)
        }

        try {
            val jsonString = context.assets.open("voices.json")
                .bufferedReader()
                .use { it.readText() }

//            val voices = jsonParser.decodeFromString<List<VoiceInfo>>(jsonString)
//                .map { VoiceOption(it.id, it.friendlyName) } // Convert to our domain object

            val voices = jsonParser.decodeFromString<List<VoiceInfo>>(jsonString)
                .map { voiceInfo ->
                    // Now you have access to the strongly-typed enum!
                    println("Parsed ${voiceInfo.friendlyName} with gender ${voiceInfo.gender}")
                    VoiceOption(voiceInfo.id, voiceInfo.friendlyName, voiceInfo.gender)
                }

            cachedVoices = voices
            Result.success(voices)
        } catch (e: Exception) {
            // This might happen if a flavor is missing its voices.json file
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Gets the friendly display name for a given voice ID.
     * This now requires loading the list first.
     */
    suspend fun getFriendlyNameForVoice(voiceId: String): String {
        val voicesResult = getAvailableVoices()
        return voicesResult.getOrNull()
            ?.find { it.id == voiceId }
            ?.friendlyName
            ?: voiceId // Fallback to the ID if not found
    }
}