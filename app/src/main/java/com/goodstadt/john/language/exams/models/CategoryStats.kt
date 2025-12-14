package com.goodstadt.john.language.exams.models

data class CategoryStats(
    val heard: Int,
    val total: Int
) {
    // Helper to get a 0.0 - 1.0 float for ProgressBars
    val percentage: Float
        get() = if (total > 0) heard.toFloat() / total.toFloat() else 0f

    val isCompleted: Boolean
        get() = total > 0 && heard >= total
}