package com.goodstadt.john.language.exams.screens.reference.shared


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodstadt.john.language.exams.ui.theme.orangeLight

@Composable
fun ScrollableHorizontalLevelPicker(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    // Defines the Orange accent color
    val accentOrange = orangeLight//Color(0xFFFF9800)

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        // Add padding at start/end so first/last items aren't stuck to screen edge
        contentPadding = PaddingValues(horizontal = 16.dp),
        // Space between items
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options) { option ->
            val isSelected = selectedOption == option

            // Determine Colors based on state
            val borderColor = if (isSelected) accentOrange else Color.Gray.copy(alpha = 0.5f)
            val textColor = if (isSelected) accentOrange else Color.White.copy(alpha = 0.9f) // Or MaterialTheme.colorScheme.onSurface
            val backgroundColor = if (isSelected) accentOrange.copy(alpha = 0.1f) else Color.Transparent

            Surface(
                shape = CircleShape, // Lozenge shape
                border = BorderStroke(2.dp, borderColor),
                color = backgroundColor,
                modifier = Modifier
                    .clickable { onOptionSelected(option) }
            ) {
                Text(
                    text = option,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}