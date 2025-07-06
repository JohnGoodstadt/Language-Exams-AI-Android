package com.goodstadt.john.language.exams.data

import android.content.Context
import android.content.SharedPreferences
import com.goodstadt.john.language.exams.config.LanguageConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Retrieves the saved voice name. If no voice name is saved, it returns
     * the default voice name for the current build flavor.
     */
    fun getSelectedVoiceName(): String {
        return prefs.getString(KEY_VOICE_NAME, null) ?: LanguageConfig.voiceName
    }

    /**
     * Saves the user's selected voice name to SharedPreferences.
     */
    suspend fun saveSelectedVoiceName(voiceName: String) {
        prefs.edit().putString(KEY_VOICE_NAME, voiceName).apply()
    }
    // --- NEW: Level/File Name Logic ---

    /**
     * Retrieves the saved file name. If no file name is saved, it returns
     * the default file name for the current build flavor.
     */
    fun getSelectedFileName(): String {
        return prefs.getString(KEY_FILE_NAME, null) ?: LanguageConfig.defaultFileName
    }

    /**
     * Saves the user's selected file name to SharedPreferences.
     */
    suspend fun saveSelectedFileName(fileName: String) {
        prefs.edit().putString(KEY_FILE_NAME, fileName).apply()
    }
    // --- End of New Logic ---

    companion object {
        private const val PREFS_NAME = "user_settings"
        private const val KEY_VOICE_NAME = "selected_voice_name"
        private const val KEY_FILE_NAME = "selected_file_name"
    }
}