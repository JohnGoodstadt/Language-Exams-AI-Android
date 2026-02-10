package com.goodstadt.john.language.exams.screens.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goodstadt.john.language.exams.ui.theme.accentColor

@Composable
fun CacheProgressBar(
    cachedCount: Int,
    totalCount: Int,
    displayIfZero:Boolean = false,
    displayLowNumber:Boolean = true,
    modifier: Modifier = Modifier
) {
    // This 'if' check replaces SwiftUI's .opacity() modifier.
    // The entire composable will not be part of the UI if the count is zero.
    if (!displayIfZero || cachedCount > 0) {
        // Calculate progress as a float between 0.0 and 1.0
        val progress = if (totalCount > 0) {
            cachedCount.toFloat() / totalCount.toFloat()
        } else {
            0f // Avoid division by zero
        }

        Row(
            modifier = modifier.height(30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (displayLowNumber && cachedCount > 0) {
                Text(
                    text = "$cachedCount",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            // Use a weight modifier to make the progress bar fill the available space
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp) ,
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant


            )
            Text(
                text = "$totalCount",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
