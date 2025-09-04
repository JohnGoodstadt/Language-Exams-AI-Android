package com.goodstadt.john.language.exams.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.goodstadt.john.language.exams.config.LanguageConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Use DataStore instead of SharedPreferences for modern, Flow-based preference handling
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Define keys for DataStore
    private object PreferenceKeys {
        val SELECTED_VOICE_NAME = stringPreferencesKey("selected_voice_name")
        val SELECTED_FILE_NAME = stringPreferencesKey("selected_file_name")
        val SELECTED_SKILL_LEVEL = stringPreferencesKey("selected_skill_level")
        val TOTAL_TOKEN_COUNT = intPreferencesKey("total_token_count")
        val LAST_TOKEN_RESET_TIMESTAMP = longPreferencesKey("last_token_reset_timestamp")
        val LLM_CALL_COUNTER = intPreferencesKey("llm_call_counter")
        val LLM_CURRENT_PROVIDER = stringPreferencesKey("llm_current_provider")
        val TTS_CURRENT_CREDITS = intPreferencesKey("tts_current_credits")
        val TTS_TOTAL_CREDITS = intPreferencesKey("tts_total_credits")

    }

    /**
     * A Flow that emits the currently selected file name whenever it changes.
     * It provides the flavor-specific default if no value is set.
     */
    val selectedFileNameFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferenceKeys.SELECTED_FILE_NAME] ?: LanguageConfig.defaultFileName
        }

    /**
     * A Flow that emits the currently selected voice name whenever it changes.
     * It provides the flavor-specific default if no value is set.
     */
    val selectedVoiceNameFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferenceKeys.SELECTED_VOICE_NAME] ?: LanguageConfig.voiceName
        }

    val selectedSkillLevelFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferenceKeys.SELECTED_SKILL_LEVEL] ?: LanguageConfig.defaulSkillLevel
        }

    /**
     * Saves the user's selected file name to DataStore.
     */
    suspend fun saveSelectedFileName(fileName: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.SELECTED_FILE_NAME] = fileName
        }
    }

    /**
     * Saves the user's selected voice name to DataStore.
     */
    suspend fun saveSelectedVoiceName(voiceName: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.SELECTED_VOICE_NAME] = voiceName
        }
    }
    /**
     * Saves the user's selected Skill Level A1,A2,B1 or B2
     */
    suspend fun saveSelectedSkillLevel(skillLevel: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.SELECTED_SKILL_LEVEL] = skillLevel
        }
    }

    // 2. Expose the token count as a Flow
    val totalTokenCountFlow: Flow<Int> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) { emit(emptyPreferences()) } else { throw exception }
        }
        .map { preferences ->
            preferences[PreferenceKeys.TOTAL_TOKEN_COUNT] ?: 0
        }

    // 3. Create a function to increment the token count
    suspend fun incrementTokenCount(count: Int) {
        context.dataStore.edit { preferences ->
            val currentTotal = preferences[PreferenceKeys.TOTAL_TOKEN_COUNT] ?: 0
            preferences[PreferenceKeys.TOTAL_TOKEN_COUNT] = currentTotal + count
            Timber.d("Token count updated to: ${currentTotal + count}")
        }
    }

    // 4. (For future use) A function to reset the count
    suspend fun resetTokenCount() {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.TOTAL_TOKEN_COUNT] = 0
            preferences[PreferenceKeys.LAST_TOKEN_RESET_TIMESTAMP] = System.currentTimeMillis()
        }
    }
    val llmCallCounterFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[PreferenceKeys.LLM_CALL_COUNTER] ?: 0
        }

    val llmProviderFlow: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[PreferenceKeys.LLM_CURRENT_PROVIDER] ?: "openai" // Default to openai
        }

    // --- ADD THESE TWO NEW SAVE FUNCTIONS ---
    suspend fun saveLlmCallCounter(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.LLM_CALL_COUNTER] = count
        }
    }

    suspend fun saveLlmProvider(provider: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.LLM_CURRENT_PROVIDER] = provider
        }
    }
    // --- ADD THESE NEW FLOWS for TTS Credits ---
    val ttsCurrentCreditsFlow: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[PreferenceKeys.TTS_CURRENT_CREDITS] ?: 0 }

    val ttsTotalCreditsFlow: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[PreferenceKeys.TTS_TOTAL_CREDITS] ?: 0 }

    // A combined flow for convenience
    val ttsUserCreditsFlow: Flow<TtsUserCredits> = context.dataStore.data
        .map { preferences ->
            TtsUserCredits(
                current = preferences[PreferenceKeys.TTS_CURRENT_CREDITS] ?: 0,
                total = preferences[PreferenceKeys.TTS_TOTAL_CREDITS] ?: 0
            )
        }

    // --- ADD THESE NEW SAVE FUNCTIONS for TTS Credits ---
    suspend fun saveTtsCurrentCredits(current: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.TTS_CURRENT_CREDITS] = current
        }
    }

    suspend fun saveTtsCredits(current: Int, total: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.TTS_CURRENT_CREDITS] = current
            preferences[PreferenceKeys.TTS_TOTAL_CREDITS] = total
        }
    }
 }
