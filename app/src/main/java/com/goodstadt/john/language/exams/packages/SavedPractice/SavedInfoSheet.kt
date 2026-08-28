package com.goodstadt.john.language.exams.packages.SavedPractice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goodstadt.john.language.exams.models.SaveReminder

/**
 * Content for the bottom sheet shown right after a word is Saved. Explains what just happened (the word is
 * saved and appears on the "Saved" screen on the Me tab) and offers Apple-Mail-style reminder choices.
 *
 * UI + plumbing only for now: [onReminderSelected] fires the chosen [SaveReminder] but the caller does
 * nothing with it yet (future: persist against the saved word + schedule a local notification).
 */
@Composable
fun SavedInfoSheetContent(
    wordText: String,
    onReminderSelected: (SaveReminder) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Saved",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Text(
            text = "“$wordText” has been added to your Saved list. Find it on the “Saved” " +
                "screen on the Me tab to practise it later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        Text(
            text = "Remind me to practise",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        // One full-width button per reminder choice (Apple-Mail style). Empty behaviour for now.
        SaveReminder.entries.forEach { option ->
            OutlinedButton(
                onClick = {
                    onReminderSelected(option)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = option.label)
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}
