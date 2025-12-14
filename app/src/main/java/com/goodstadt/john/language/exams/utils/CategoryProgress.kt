package com.goodstadt.john.language.exams.utils


data class CategoryProgress(
    val title: String,
    val total: Int,
    val heard: Int,
    val tabNumber: Int,
    val sortOrder: Int
) {
    // Computed Properties for UI Logic

    val percentage: Float
        get() = if (total > 0) heard.toFloat() / total.toFloat() else 0f

    val isStarted: Boolean
        get() = heard > 0

    val isCompleted: Boolean
        get() = heard >= total && total > 0
}