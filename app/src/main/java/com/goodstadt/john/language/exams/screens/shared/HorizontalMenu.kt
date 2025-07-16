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
import com.goodstadt.john.language.exams.ui.theme.accentColor
import com.goodstadt.john.language.exams.ui.theme.greyLight2

//@Composable
//fun TabWithHorizontalMenu(menuItems: List<String>) {
//    Column(modifier = Modifier.fillMaxSize()) {
//        LazyRow(
//                modifier = Modifier.fillMaxWidth(),
//                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
//                horizontalArrangement = Arrangement.spacedBy(8.dp)
//        ) {
//            items(menuItems) { item ->
//                MenuItemChip(
//                        text = item,
//                        onClick = { /* Do nothing for now. This can be implemented later. */ })
//            }
//        }
//        // You can add the rest of the tab's content here
//    }
//}

// ...
@OptIn(ExperimentalMaterial3Api::class)
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
        // The default colors will be used for the unselected state,
        // but you could override them here if you wanted.
        // e.g., containerColor = MaterialTheme.colorScheme.surfaceVariant
    )

//    Card(
//            onClick = onClick, // <-- Use the passed-in lambda
//            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
//    ) {
//        Text(
//                text = text,
//                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
//        )
//    }
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(text) },
        colors = chipColors,
        border = null // Optional: remove the default border for a cleaner look
    )
}