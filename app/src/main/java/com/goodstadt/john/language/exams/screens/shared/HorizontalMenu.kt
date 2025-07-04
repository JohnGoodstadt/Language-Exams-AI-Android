// <project-root>/app/src/main/java/com/goodstadt/john/language/exams/screens/shared/HorizontalMenu.kt
package com.goodstadt.john.language.exams.screens.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TabWithHorizontalMenu(menuItems: List<String>) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(menuItems) { item ->
                MenuItemChip(text = item)
            }
        }
        // You can add the rest of the tab's content here
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuItemChip(text: String) {
    Card(
            onClick = { /* Handle click */ },
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Text(
                text = text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}