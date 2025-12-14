package com.goodstadt.john.language.exams.models


/**
 * A simple container for the progress of a specific Reference sheet.
 * Used by AudioCacheManager to return data to the SideQuestStatsSheet.
 */
data class ReferenceStats(
    val heard: Int,
    val total: Int
) {
    // Optional helper for progress bars (0.0 - 1.0)
    val percentage: Float
        get() = if (total > 0) heard.toFloat() / total.toFloat() else 0f
}