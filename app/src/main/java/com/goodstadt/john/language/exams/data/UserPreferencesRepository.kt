package com.goodstadt.john.language.exams.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.goodstadt.john.language.exams.config.LanguageConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
}