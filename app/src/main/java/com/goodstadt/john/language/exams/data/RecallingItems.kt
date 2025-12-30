// In a new file, e.g., data/RecallingItems.kt

package com.goodstadt.john.language.exams.data

import android.app.Application
import android.content.Context
import androidx.annotation.Keep
import com.goodstadt.john.language.exams.data.repository.RecallingRepository
import com.goodstadt.john.language.exams.models.Format0Word
import com.goodstadt.john.language.exams.utils.timingToDurationMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import com.goodstadt.john.language.exams.utils.STOPS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton



// --- Constants for Spaced Repetition ---
// Used by RecallingItem to calculate labels
private const val TIMINGS_STR = "10m,1h,1D,1W,1M,4M"
val STOPS: List<String> = TIMINGS_STR.split(",")


@Keep
@Serializable
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
            WaitingForAnswer -> "Waiting For Answer"
            Done -> "Done"
            Unknown -> "Unknown"
        }
    }
}

// --- Data Class ---

@Keep
@Serializable
data class RecallingItem(
    val id: String = UUID.randomUUID().toString(),

    // The unique identifier (usually the word itself, e.g., "Hello")
    val key: String,

    // The translation or main text to display
    val text: String = "",

    // Extra info (e.g. Romanisation/Pinyin)
    val additionalText: String = "",

    val imageId: String = "",

    // Timestamps (Milliseconds)
    val createdDate: Long = System.currentTimeMillis(),
    var learntTime: Long = 0L,
    var prevEventTime: Long = 0L,
    var nextEventTime: Long = 0L,

    // Spaced Repetition State
    var currentStopNumber: Int = 1,
    var recallState: RecallState = RecallState.NotStarted
) {
    // MARK: - Logic Helpers

    fun currentStopCode(): String {
        val zeroBasedIndex = currentStopNumber - 1
        return STOPS.getOrNull(zeroBasedIndex) ?: STOPS.first()
    }

    fun nextStopTitle(): String {
        // Look ahead to the next stop index
        val nextIndex = (currentStopNumber).coerceIn(0, STOPS.size - 1)
        return STOPS.getOrNull(nextIndex) ?: STOPS.last()
    }

    fun isLastRecallItem(): Boolean {
        return currentStopNumber >= STOPS.size
    }
}



@Singleton
class RecallingItems @Inject constructor(
    private val repository: RecallingRepository,
    // We use a predefined IO scope for background logic if not called from a suspend function
    private val appScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    // ✅ READ: Expose the Repository flow directly, converted to a StateFlow for instant access
    val items: StateFlow<List<RecallingItem>> = repository.allItems
        .stateIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    // MARK: - Checkers

    fun amIRecalling(key: String): Boolean {
        // synchronous check against the latest cached value from StateFlow
        return items.value.any { it.key == key }
    }

    fun getItem(key: String): RecallingItem? {
        return items.value.firstOrNull { it.key == key }
    }

    // MARK: - Actions

    /**
     * Adds a new word to the focus list.
     * Sets the initial state and next event time.
     */
    fun add(key: String, text: String, imageId: String, additionalText: String = "") {
        // Prevent duplicates
        if (amIRecalling(key)) return

        val eventTimeMillis = timingToDurationMillis("10m") // First stop

        val newItem = RecallingItem(
            key = key,
            text = text,
            imageId = imageId,
            additionalText = additionalText,
            recallState = RecallState.Waiting,
            createdDate = System.currentTimeMillis(),
            prevEventTime = System.currentTimeMillis(),
            nextEventTime = System.currentTimeMillis() + eventTimeMillis,
            currentStopNumber = 1
        )

        appScope.launch {
            repository.addItem(newItem)
        }
    }

    fun remove(key: String) {
        appScope.launch {
            repository.remove(key)
        }
    }

    fun removeAll() {
        appScope.launch {
            repository.removeAll()
        }
    }

    // MARK: - Spaced Repetition Logic

    /**
     * User answered correctly. Move to next stop (10m -> 1h -> 1d...).
     */
    fun recalledOK(key: String) {
        appScope.launch {
            // 1. Get latest version from Repo to ensure thread safety
            val item = repository.getItem(key) ?: return@launch

            // 2. Calculate Next Stop
            val nextStopNumber = if (item.currentStopNumber < STOPS.size) {
                item.currentStopNumber + 1
            } else {
                item.currentStopNumber // Cap at max
            }

            // 3. Calculate Time
            // (index is 0-based, stopNumber is 1-based)
            val stopCode = STOPS.getOrNull(nextStopNumber - 1) ?: STOPS.last()
            val delayMillis = timingToDurationMillis(stopCode)

            // 4. Create Updated Object
            val updatedItem = item.copy(
                recallState = RecallState.Waiting,
                currentStopNumber = nextStopNumber,
                prevEventTime = System.currentTimeMillis(),
                nextEventTime = System.currentTimeMillis() + delayMillis
            )

            // 5. Save
            repository.updateItem(updatedItem)
        }
    }

    /**
     * User answered incorrectly.
     * Logic: Stay at current level? Or reset to 1?
     * Implementing "Mark as AnsweredNotOK" for now based on your enum.
     */
    fun recalledNotOK(key: String) {
        appScope.launch {
            val item = repository.getItem(key) ?: return@launch

            // Logic: You might want to reset stop number to 1 here?
            val updatedItem = item.copy(
                recallState = RecallState.AnsweredNotOK,
                // Optional: Reset timer?
                // nextEventTime = System.currentTimeMillis() + timingToDurationMillis("10m")
            )

            repository.updateItem(updatedItem)
        }
    }

    /**
     * Instant Memorization (Skip all steps)
     */
    fun iHaveMemorisedIt(key: String) {
        appScope.launch {
            val item = repository.getItem(key) ?: return@launch

            val updatedItem = item.copy(
                recallState = RecallState.Memorised,
                currentStopNumber = STOPS.size,
                learntTime = System.currentTimeMillis(),
                nextEventTime = Long.MAX_VALUE // Never show again
            )

            repository.updateItem(updatedItem)
        }
    }

    // MARK: - Helpers

    private fun timingToDurationMillis(code: String): Long {
        val unit = code.last()
        val value = code.dropLast(1).toLongOrNull() ?: 1L

        return when (unit) {
            'm' -> value * 60 * 1000L
            'h' -> value * 60 * 60 * 1000L
            'D' -> value * 24 * 60 * 60 * 1000L
            'W' -> value * 7 * 24 * 60 * 60 * 1000L
            'M' -> value * 30 * 24 * 60 * 60 * 1000L
            else -> 10 * 60 * 1000L // Default 10m
        }
    }

}

// You will also need to provide implementations for these Swift functions
// that were used in your original file.

//fun printhires(message: String) { /* TODO: Implement logging */ }
//fun String.getAcronyms(): String { /* TODO: Implement acronym logic */ return "?" }
//fun secondsToString(seconds: Int): String { /* TODO: Implement time formatting */ return "" }
//fun recallingStateString(item: RecallingItem): String { /* TODO: Implement logic */ return "" }