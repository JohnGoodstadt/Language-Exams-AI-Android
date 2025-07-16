// <project-root>/app/src/main/java/com/goodstadt/john/language/exams/screens/shared/HorizontalMenu.kt
package com.goodstadt.john.language.exams.screens.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodstadt.john.language.exams.ui.theme.accentColor
import com.goodstadt.john.language.exams.ui.theme.greyLight2


@Composable
fun MenuItemChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit // <-- Add the onClick parameter
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        // Use your app's "accent" color for the selected state
        selectedContainerColor = Color.Transparent,
        selectedLabelColor = accentColor,
    )

    FilterChip(
//        modifier = Modifier.size(24.dp),
        selected = isSelected,
        onClick = onClick,
        label = { Text(text, fontSize = 20.sp) },
        colors = chipColors,
        border = null // Optional: remove the default border for a cleaner look
    )
}