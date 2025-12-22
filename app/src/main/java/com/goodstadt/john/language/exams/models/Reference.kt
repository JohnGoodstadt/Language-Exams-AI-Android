package com.goodstadt.john.language.exams.models

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Transform
import androidx.compose.ui.graphics.vector.ImageVector
import com.goodstadt.john.language.exams.managers.AudioCacheManager

data class ReferenceSubItem(
    val title: String,
    val viewed: Int,
    val total: Int,
    val documentId: String,
    val tabId: String
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
