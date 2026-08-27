package com.goodstadt.john.language.exams.models

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

/**
 * Represents the history for a specific Level (e.g. "A1", "Reference").
 * This matches the Firestore Document structure exactly.
 */
@Keep
data class HistoryData(
    // Unix Timestamp (in seconds) of the last local write.
    // Used to determine if Cloud or Local is newer during sync.
    @SerializedName("last_updated")
    var lastUpdated: Long = 0,

    // A map of Sentence Hashes (CID_...) to Play Counts.
    // Example: "CID_Hello_a1b2..." : 5
    @SerializedName("data")
    var items: MutableMap<String, Int> = mutableMapOf(),

    // LOCAL-ONLY (not synced to Firestore): spaced-repetition progress per sentence, driving the
    // red -> amber -> green dot. One entry per HEARD sentence (not per tap). Left out of the Firestore
    // flush payload on purpose; cloud sync of this is a possible future extension.
    @SerializedName("spaced")
    var spaced: MutableMap<String, SpacedPlay> = mutableMapOf()
)

/**
 * Spaced-repetition record for a single sentence. [count] is capped at 3 (1=red, 2=amber, 3=green) and
 * only advances when at least the configured gap has passed since [lastAt], so the colour reflects plays
 * spread over time (a forgetting curve), not raw taps.
 */
@Keep
data class SpacedPlay(
    @SerializedName("c") var count: Int = 0,   // 0..3 -> dot colour
    @SerializedName("t") var lastAt: Long = 0  // epoch SECONDS of the last counted play
)
