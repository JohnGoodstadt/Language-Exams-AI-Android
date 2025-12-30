package com.goodstadt.john.language.exams.data.repository

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.goodstadt.john.language.exams.data.RecallState
import com.goodstadt.john.language.exams.data.RecallingItem
import com.goodstadt.john.language.exams.models.Format0Word
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
    private val gson = Gson()
    private val KEY_ITEMS_JSON = stringPreferencesKey("recalling_items_v2")

    private object PreferencesKeys {
        // Stores a Set of Strings (e.g. "Hello", "Cat", "Dog")
        val RECALLED_WORDS = stringSetPreferencesKey("recalled_word_keys")
    }
    /**
     * Exposes the full list of tracked items.
     * ViewModels observe this to filter Today vs Later.
     */
    val allItems: Flow<List<RecallingItem>> = dataStore.data
        .catch {
            emit(emptyPreferences())
        }
        .map { prefs ->
            val jsonString = prefs[KEY_ITEMS_JSON] ?: ""
            if (jsonString.isNotEmpty()) {
                try {
                    val type = object : TypeToken<List<RecallingItem>>() {}.type
                    gson.fromJson(jsonString, type)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
    /**
     * A Hot Flow of the current set of focused words.
     * ViewModels should collect this to update the UI reactively.
     */
//    val recalledWordKeys: Flow<Set<String>> = dataStore.data
//        .catch { exception ->
//            if (exception is IOException) {
//                Log.e(TAG, "Error reading preferences", exception)
//                emit(emptyPreferences())
//            } else {
//                throw exception
//            }
//        }
//        .map { preferences ->
//            preferences[PreferencesKeys.RECALLED_WORDS] ?: emptySet()
//        }

    val recalledWordKeys: Flow<Set<String>> = allItems.map { list ->
        list.map { it.key }.toSet()
    }

    // MARK: - Actions

    suspend fun addWord(word: Format0Word) {
        updateList { currentList ->
            // Only add if not exists
            if (currentList.none { it.key == word.word }) {
                val newItem = RecallingItem(key = word.word) // Defaults to "Now"
                currentList + newItem
            } else {
                currentList
            }
        }
    }

    suspend fun remove(key: String) {
        updateList { currentList ->
            currentList.filter { it.key != key }
        }
    }

    suspend fun removeAll() {
        updateList { emptyList() }
    }

    /**
     * Logic for "I remembered this!" (Button Click: OK)
     * Moves the item to a future date based on Spaced Repetition logic.
     */
    suspend fun recalledOK(key: String) {
        updateList { currentList ->
            currentList.map { item ->
                if (item.key == key) {
                    val oneDay = 86400000L

                    // ✅ FIX: Use 'currentStopNumber' (Int) instead of 'recallState' (Enum)
                    val nextInterval = oneDay * (item.currentStopNumber + 1)

                    // OR if you really meant the Enum ordinal:
                    // val nextInterval = oneDay * (item.recallState.ordinal + 1)

                    item.copy(
                        // Update state to Waiting
                        recallState = RecallState.Waiting,
                        // Increment stop number
                        currentStopNumber = item.currentStopNumber + 1,
                        prevEventTime = System.currentTimeMillis(),
                        nextEventTime = System.currentTimeMillis() + nextInterval
                    )
                } else {
                    item
                }
            }
        }
    }

    // MARK: - Internal Helper
    private suspend fun updateList(transform: (List<RecallingItem>) -> List<RecallingItem>) {
        dataStore.edit { prefs ->
            val jsonString = prefs[KEY_ITEMS_JSON] ?: ""
            val currentList: List<RecallingItem> = if (jsonString.isNotEmpty()) {
                try {
                    val type = object : TypeToken<List<RecallingItem>>() {}.type
                    gson.fromJson(jsonString, type)
                } catch (e: Exception) { emptyList() }
            } else {
                emptyList()
            }

            val newList = transform(currentList)
            prefs[KEY_ITEMS_JSON] = gson.toJson(newList)
        }
    }
// Helper to get raw list synchronously (for logic)
    suspend fun getList(): List<RecallingItem> {
        val prefs = dataStore.data.first()
        val jsonString = prefs[KEY_ITEMS_JSON] ?: ""
        return if (jsonString.isNotEmpty()) {
            gson.fromJson(jsonString, object : TypeToken<List<RecallingItem>>() {}.type)
        } else {
            emptyList()
        }
    }

    suspend fun getItem(key: String): RecallingItem? {
        return getList().find { it.key == key }
    }

    suspend fun addItem(item: RecallingItem) {
        updateList { list ->
            if (list.none { it.key == item.key }) list + item else list
        }
    }

    suspend fun updateItem(item: RecallingItem) {
        updateList { list ->
            list.map { if (it.key == item.key) item else it }
        }
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
//    suspend fun addWord(word: Format0Word) {
//        // Use the word string as the key (ensure it is unique enough for your needs)
//        val key = word.word.trim()
//
//        if (key.isEmpty()) return
//
//        try {
//            dataStore.edit { preferences ->
//                val currentSet = preferences[PreferencesKeys.RECALLED_WORDS] ?: emptySet()
//                if (!currentSet.contains(key)) {
//                    // Create a new set to ensure DataStore detects the change
//                    preferences[PreferencesKeys.RECALLED_WORDS] = currentSet + key
//                    Log.d(TAG, "✅ Focused word: $key")
//                }
//            }
//
//            // Optional: Schedule Spaced Repetition Notification here?
//            // scheduleNotification(word)
//
//        } catch (e: Exception) {
//            Log.e(TAG, "Failed to add word", e)
//        }
//    }

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