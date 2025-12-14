package com.goodstadt.john.language.exams.models

import com.google.gson.annotations.SerializedName

/**
 * Represents the history for a specific Level (e.g. "A1", "Reference").
 * This matches the Firestore Document structure exactly.
 */
data class HistoryData(
    // Unix Timestamp (in seconds) of the last local write.
    // Used to determine if Cloud or Local is newer during sync.
    @SerializedName("last_updated")
    var lastUpdated: Long = 0,

    // A map of Sentence Hashes (CID_...) to Play Counts.
    // Example: "CID_Hello_a1b2..." : 5
    @SerializedName("data")
    var items: MutableMap<String, Int> = mutableMapOf()
)
