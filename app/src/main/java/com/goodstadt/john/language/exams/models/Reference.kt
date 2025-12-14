package com.goodstadt.john.language.exams.models

import androidx.compose.ui.graphics.vector.ImageVector

data class ReferenceSubItem(
    val title: String,
    val viewed: Int,
    val total: Int
) {
    val coverage: Float
        get() = if (total > 0) viewed.toFloat() / total.toFloat() else 0f
}

data class ReferenceCategory(
    val title: String,
    val icon: ImageVector, // Or ResId if you prefer
    val description: String,
    val items: List<ReferenceSubItem>
)