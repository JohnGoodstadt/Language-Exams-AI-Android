package com.goodstadt.john.language.exams.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.goodstadt.john.language.exams.models.Format0Word
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecallingRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private val TAG = "RecallingRepository"

    private object PreferencesKeys {
        // Stores a Set of Strings (e.g. "Hello", "Cat", "Dog")
        val RECALLED_WORDS = stringSetPreferencesKey("recalled_word_keys")
    }

    // MARK: - Read Data

    /**
     * A Hot Flow of the current set of focused words.
     * ViewModels should collect this to update the UI reactively.
     */
    val recalledWordKeys: Flow<Set<String>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Error reading preferences", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.RECALLED_WORDS] ?: emptySet()
        }

    /**
     * One-shot fetch (Suspend function).
     * Useful for initial state setup in ViewModel init blocks.
     */
    suspend fun getAllRecalledKeys(): Set<String> {
        return try {
            val preferences = dataStore.data.first()
            preferences[PreferencesKeys.RECALLED_WORDS] ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    // MARK: - Write Data

    /**
     * Adds a word to the "Focus" list.
     */
    suspend fun addWord(word: Format0Word) {
        // Use the word string as the key (ensure it is unique enough for your needs)
        val key = word.word.trim()

        if (key.isEmpty()) return

        try {
            dataStore.edit { preferences ->
                val currentSet = preferences[PreferencesKeys.RECALLED_WORDS] ?: emptySet()
                if (!currentSet.contains(key)) {
                    // Create a new set to ensure DataStore detects the change
                    preferences[PreferencesKeys.RECALLED_WORDS] = currentSet + key
                    Log.d(TAG, "✅ Focused word: $key")
                }
            }

            // Optional: Schedule Spaced Repetition Notification here?
            // scheduleNotification(word)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to add word", e)
        }
    }

    /**
     * Removes a word from the "Focus" list.
     */
    suspend fun removeWord(word: Format0Word) {
        val key = word.word.trim()

        try {
            dataStore.edit { preferences ->
                val currentSet = preferences[PreferencesKeys.RECALLED_WORDS] ?: emptySet()
                if (currentSet.contains(key)) {
                    preferences[PreferencesKeys.RECALLED_WORDS] = currentSet - key
                    Log.d(TAG, "❌ Removed focus: $key")
                }
            }

            // Optional: Cancel Notification
            // cancelNotification(word)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove word", e)
        }
    }

    // MARK: - Helpers

    suspend fun isRecalled(word: String): Boolean {
        // Helper if you need to check a single word outside of a flow
        val keys = getAllRecalledKeys()
        return keys.contains(word)
    }

    suspend fun clearAll() {
        dataStore.edit { it.remove(PreferencesKeys.RECALLED_WORDS) }
    }
}