package com.goodstadt.john.language.exams.packages.CategoryTab

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.goodstadt.john.language.exams.ui.theme.LocalExtendedColors

/**
 * "Learn, then quiz" nudges (mirrors iOS V5_learn_then_quiz):
 *  1. The first time the learner leaves a readiness-audit summary for the app, tab 1 opens with a one-time
 *     guide: listen to a whole section, then quiz yourself on it.
 *  2. Every time all the sentences in a section have been heard, a sheet offers that section's quiz
 *     (the same as tapping the section's Quiz button).
 */
object LearnThenQuiz {
    private const val PREFS = "learn_then_quiz"
    private const val KEY_SEEN_GUIDE = "hasSeenVocabStartGuide"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasSeenStartGuide(context: Context): Boolean = prefs(context).getBoolean(KEY_SEEN_GUIDE, false)

    fun setSeenStartGuide(context: Context, seen: Boolean) {
        prefs(context).edit().putBoolean(KEY_SEEN_GUIDE, seen).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabStartGuideSheet(firstSectionTitle: String?, onDone: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDone,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("How to learn here", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Every section works the same way: listen first, then quiz yourself.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            GuideStep(
                number = 1, icon = Icons.Filled.VolumeUp, title = "Listen to a whole section",
                text = (firstSectionTitle?.let { "Start with \"$it\". " } ?: "") +
                        "Tap each word to hear its sentence, or tap ▶ on the section header to hear them all."
            )
            GuideStep(
                number = 2, icon = Icons.Filled.SportsEsports, title = "Quiz yourself on it",
                text = "When you've heard every sentence, we'll offer the section's quiz. You can also tap Quiz on the section header any time."
            )
            GuideStep(
                number = 3, icon = Icons.AutoMirrored.Filled.ArrowForward, title = "Move on to the next section",
                text = "Keep going section by section. Your progress and quiz results build up as you go."
            )
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LocalExtendedColors.current.buttonBlue, contentColor = Color.White)
            ) { Text("Let's start", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun GuideStep(number: Int, icon: ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = LocalExtendedColors.current.buttonBlue, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("$number. $title", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionQuizPromptSheet(sectionTitle: String, onQuiz: () -> Unit, onLater: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onLater,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Filled.Verified, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(48.dp))
            Text(
                "You've heard all of \"$sectionTitle\"",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
            )
            Text(
                "Now quiz yourself on it. Testing yourself straight after listening is the quickest way to make the words stick.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Button(
                onClick = onQuiz,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LocalExtendedColors.current.buttonBlue, contentColor = Color.White)
            ) {
                Icon(Icons.Filled.SportsEsports, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Quiz me on $sectionTitle", fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            TextButton(onClick = onLater) {
                Text("Later", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
