package com.goodstadt.john.language.exams.screens.shared.speakerSelection

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goodstadt.john.language.exams.ui.theme.accentColor


@Composable
fun VoiceCategoryDropdownHeader(
    title: String,
    selectedVoiceName: String?,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val rotationAngle by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- CHANGE 3: Center the text content ---
        Column(
            modifier = Modifier.weight(1f),
            // Add this line to center the text elements inside the column
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = accentColor
            )
            Text(
                text = selectedVoiceName ?: "Tap to Hear",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selectedVoiceName != null) FontWeight.Bold else FontWeight.Normal
            )
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "Expand or collapse selection",
            modifier = Modifier.rotate(rotationAngle)
        )
    }
}
