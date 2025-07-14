// In a new file, e.g., data/RecallingItems.kt

package com.goodstadt.john.language.exams.data

import android.app.Application
import android.content.Context
import android.util.Log
import com.goodstadt.john.language.exams.models.VocabWord
import com.goodstadt.john.language.exams.utils.timingToDurationMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.goodstadt.john.language.exams.utils.STOPS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// --- Top-level Constants ---
const val RECALL_IT_SAVED_FILENAME = "RecallItSaved"
//private const val timings = "10m,1h,1D,1W,1M,4M"
//private val stops = timings.split(",")

// --- Enums ---

@Serializable // Equivalent to Codable for JSON serialization
enum class RecallState {
    NotStarted,
    Memorising,
    Memorised,
    AnsweredOK,
    AnsweredNotOK,
    Waiting,
    WaitingForAnswer,
    Done,
    Unknown;

    override fun toString(): String {
        return when (this) {
            NotStarted -> "Not Started"
            Memorising -> "Memorising"
            Memorised -> "Memorised"
            AnsweredOK -> "Answered OK"
            AnsweredNotOK -> "Not Answered OK"
            Waiting -> "Waiting"
            WaitingForAnswer -> "WaitingForAnswer"
            Done -> "Done"
            Unknown -> "Unknown"
        }
    }
}

@Serializable // Equivalent to Codable
enum class TimingsState {
    TimingsStateOverdue,
    TimingsStateEarly,
    TimingsStateInWindowEarly,
    TimingsStateInWindowLate,
    TimingsStateDue
}

// --- Data Class (equivalent to your Swift class) ---

@Serializable // Allows this class to be converted to/from JSON
data class RecallingItem(
    val id: String = UUID.randomUUID().toString(),
    val key: String, // unique identifier from client
    val text: String,
    val additionalText: String = "",
    val imageId: String = "",
    // Note: Storing dates as Long (milliseconds since epoch) is the standard,
    // robust way to handle timestamps in Kotlin/Java for serialization.
    val createdDate: Long = System.currentTimeMillis(),
    var learntTime: Long = 0L, // 0L is a good equivalent for distantPast
    var prevEventTime: Long = 0L,
    var nextEventTime: Long = 0L,
    var currentStopNumber: Int = 1,
    var recallState: RecallState = RecallState.NotStarted
) {
    fun currentStopCode(): String {
        val zeroBasedIndex = currentStopNumber - 1
        return STOPS.getOrNull(zeroBasedIndex) ?:STOPS.first()
    }

    fun nextStopTitle(): String {
        // In Kotlin, we can use 'coerceIn' for safety
        val nextIndex = (currentStopNumber).coerceIn(0, STOPS.size - 1)
        return STOPS.getOrNull(nextIndex) ?: STOPS.last()
    }

    fun isLastRecallItem(): Boolean {
        return currentStopNumber >= STOPS.size
    }

    // Note: 'moveFirst' was moved into RecallingItems class as it modifies state
    // based on other parameters. Keeping the data class immutable is preferred.
}

// --- Main Logic Class (equivalent to RecallingItems) ---

// This class will be instantiated as a Singleton by Hilt later
@Singleton
class RecallingItems @Inject constructor (
    private val application: Application,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val appScope: CoroutineScope
) {

    // The list of items. In a ViewModel, this would be a StateFlow.
    private val _items = MutableStateFlow<List<RecallingItem>>(emptyList())
    val items: StateFlow<List<RecallingItem>> = _items.asStateFlow()

    private val context = application.applicationContext
    // --- Public API ---

    fun amIRecalling(key: String): Boolean {
        return _items.value.any { it.key == key }
    }

    fun iHaveMemorisedIt(key: String) {
        _items.value.firstOrNull { it.key == key }?.apply {
            recallState = RecallState.Memorised
            currentStopNumber = 1
            learntTime = System.currentTimeMillis()
            nextEventTime = System.currentTimeMillis()
        }
    }

    fun recalledOKObsolete(key: String) {
        _items.update { currentList ->
            currentList.map { item ->
                if (item.key == key) {
                    // Create a modified copy of the item
                    item.copy(
                        recallState = RecallState.Waiting,
                        prevEventTime = System.currentTimeMillis()
                        // ... other changes
                    )
                } else {
                    item // Return the original item if it's not the one we're changing
                }
            }
        }
    }
// ... inside RecallingItems class ...

    suspend fun recalledOK(key: String) {
        _items.update { currentList ->
            currentList.map { item ->
                // Find the item to update
                if (item.key == key) {
                    // Logic to move to the next stop
                    val nextStopNumber = if (item.currentStopNumber < STOPS.size) {
                        item.currentStopNumber + 1
                    } else {
                        item.currentStopNumber // Stay at the last stop
                    }

                    val nextStopCode = STOPS.getOrNull(nextStopNumber - 1) ?: STOPS.last()
                    val eventTimeMillis = timingToDurationMillis(nextStopCode)

                    // Return a modified copy of the item
                    item.copy(
                        recallState = RecallState.Waiting,
                        currentStopNumber = nextStopNumber,
                        prevEventTime = System.currentTimeMillis(),
                        nextEventTime = System.currentTimeMillis() + eventTimeMillis
                    )
                } else {
                    // Return all other items unchanged
                    item
                }
            }
        }

        // After updating the state, save it.
        appScope.launch { _save() }
    }
    // In data/RecallIt.kt, inside the RecallingItems class



    fun recalledNotOK(key: String) {
        _items.value.firstOrNull { it.key == key }?.apply {
            recallState = RecallState.AnsweredNotOK
        }
    }

    fun add(key: String, text: String, imageId: String, additionalText: String = "") {
        if (!amIRecalling(key)) {
           // val item = RecallingItem(key, text, imageId, additionalText)
            val item = RecallingItem(
                key = key,
                text = text,
                imageId = imageId,
                additionalText = additionalText
            )
            _items.update { currentList -> currentList + item } // Add item to the list
        }
    }

    fun getItem(key: String): RecallingItem? {
        return _items.value.firstOrNull { it.key == key }
    }

    suspend fun remove(key: String) {
        _items.update { currentList -> currentList.filterNot { it.key == key } }
        // Launch the save in the app's scope so it's not cancelled with the ViewModel.
        appScope.launch { _save() }
    }
    suspend fun removeAll() {
        _items.update { emptyList() }
        appScope.launch { _save() }
    }


    // --- Private Helper Methods ---

    private fun moveFirst(item: RecallingItem) {
        item.currentStopNumber = 1
        val eventTimeMillis = timingToDurationMillis(item.currentStopCode())
        item.nextEventTime = System.currentTimeMillis() + eventTimeMillis
    }

    private fun moveNext(item: RecallingItem) {
        if (item.currentStopNumber < STOPS.size) {
            item.currentStopNumber++
        }
        val eventTimeMillis = timingToDurationMillis(item.currentStopCode())
        item.nextEventTime = System.currentTimeMillis() + eventTimeMillis
    }

    // --- Save/Load Logic (equivalent to UserDefaults) ---
    private suspend fun _save() {
        try {
            val storageKey = userPreferencesRepository.selectedFileNameFlow.first()
            val listToSave = _items.value
            val jsonString = Json.encodeToString(listToSave)

            val prefs = context.getSharedPreferences("RecallItPrefs", Context.MODE_PRIVATE)
            prefs.edit().putString(storageKey, jsonString).apply()

            // Add a log to confirm saving
            Log.d("RecallingItems", "Saved ${listToSave.size} items to key '$storageKey'")
//            Log.d("RecallingItems", "SUCCESS: Save to '$storageKey' completed.")
        } catch (e: Exception) {
            Log.e("RecallingItems", "FAILED to save", e)
        }
    }

    fun saveObsolete(storageKey: String = RECALL_IT_SAVED_FILENAME) {
        try {
            // THE FIX: We get the current list from the .value property
            val listToSave = _items.value
            val jsonString = Json.encodeToString(listToSave)

            val prefs = context.getSharedPreferences("RecallItPrefs", Context.MODE_PRIVATE)
            prefs.edit().putString(storageKey, jsonString).apply()

            // Add a log to confirm saving
            Log.d("RecallingItems", "Saved ${listToSave.size} items to key '$storageKey'")

        } catch (e: Exception) {
            Log.e("RecallingItems", "Failed to save items", e)
            e.printStackTrace()
        }
    }


    fun load(storageKey: String = RECALL_IT_SAVED_FILENAME) {
        try {
            val prefs = context.getSharedPreferences("RecallItPrefs", Context.MODE_PRIVATE)
            val jsonString = prefs.getString(storageKey, null)

            _items.value = if (jsonString != null) {
                Json.decodeFromString<List<RecallingItem>>(jsonString)
            } else {
                emptyList()
            }
            // Add a log to confirm loading
            Log.d("RecallingItems", "Loaded ${_items.value.size} items from key '$storageKey'")

        } catch (e: Exception) {
            Log.e("RecallingItems", "Failed to load items, starting fresh.", e)
            _items.value = emptyList() // Use emptyList() for consistency
            e.printStackTrace()
        }
    }

    suspend fun focusOnWord(word: VocabWord) {
        val key = word.word
        if (amIRecalling(key)) return

        val newItem = RecallingItem(
            key = key,
            text = word.translation,
            imageId = "",
            additionalText = word.romanisation
        )
        // Perform both state updates sequentially
        _items.update { it + newItem }
        _items.update { currentList ->
            currentList.map { item ->
                if (item.key == key) {
                    val eventTimeMillis = timingToDurationMillis("10m")
                    item.copy(
                        recallState = RecallState.Waiting,
                        prevEventTime = System.currentTimeMillis(),
                        nextEventTime = System.currentTimeMillis() + eventTimeMillis
                    )
                } else {
                    item
                }
            }
        }
        // Launch the save operation in the background
        appScope.launch { _save() }
    }

}


// --- DEPENDENCIES: These functions need to be implemented ---

/**
 * Parses a string like "10m", "1h", "1D" into a duration in milliseconds.
 * This is the Kotlin equivalent of your 'StringToDateInterval'.
 */
private fun stringToTimeIntervalMillisObsolete(stopCode: String): Long {
    val value = stopCode.dropLast(1).toLongOrNull() ?: return 0
    val unit = stopCode.last().uppercaseChar()

    return when (unit) {
        'M' -> if (stopCode.endsWith("m")) TimeUnit.MINUTES.toMillis(value) else TimeUnit.DAYS.toMillis(value * 30) // Assuming M is month
        'H' -> TimeUnit.HOURS.toMillis(value)
        'D' -> TimeUnit.DAYS.toMillis(value)
        'W' -> TimeUnit.DAYS.toMillis(value * 7)
        else -> 0L
    }
}

// You will also need to provide implementations for these Swift functions
// that were used in your original file.

fun printhires(message: String) { /* TODO: Implement logging */ }
fun String.getAcronyms(): String { /* TODO: Implement acronym logic */ return "?" }
fun secondsToString(seconds: Int): String { /* TODO: Implement time formatting */ return "" }
fun recallingStateString(item: RecallingItem): String { /* TODO: Implement logic */ return "" }