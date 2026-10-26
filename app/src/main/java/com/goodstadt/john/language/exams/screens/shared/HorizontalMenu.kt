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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodstadt.john.language.exams.ui.theme.LocalExtendedColors
import com.goodstadt.john.language.exams.ui.theme.LocalIsDarkTheme


@Composable
fun MenuItemChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit, // <-- Add the onClick parameter
    // Unselected label colour. Defaults to onSurface (near-black, the previous look); pass
    // onSurfaceVariant for a greyer unselected state so the selected accent stands out more.
    unselectedLabelColor: Color = MaterialTheme.colorScheme.onSurface,
    // Light mode only: show the selected chip as a filled button (button blue, white text)
    // instead of the underline. Used where the chips act as buttons (e.g. Format8 sub-tabs).
    filledWhenSelected: Boolean = false,
) {
    // Light mode uses the iOS style: every option is button blue and the selected one is underlined.
    // Dark mode keeps the original style (accent-coloured selection, plain unselected labels).
    val isLight = !LocalIsDarkTheme.current
    val buttonBlue = LocalExtendedColors.current.buttonBlue
    val filled = isLight && filledWhenSelected
    val chipColors = FilterChipDefaults.filterChipColors(
        // Use your app's "accent" color for the selected state
        selectedContainerColor = if (filled) buttonBlue else Color.Transparent,
        selectedLabelColor = when {
            filled -> Color.White
            isLight -> buttonBlue
            else -> LocalExtendedColors.current.screenAccent
        },
        labelColor = if (isLight) buttonBlue else unselectedLabelColor,
    )

    FilterChip(
//        modifier = Modifier.size(24.dp),
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text,
                fontSize = 20.sp,
                textDecoration = if (isLight && !filled && isSelected) TextDecoration.Underline else null
            )
        },
        colors = chipColors,
        border = null // Optional: remove the default border for a cleaner look
    )
}