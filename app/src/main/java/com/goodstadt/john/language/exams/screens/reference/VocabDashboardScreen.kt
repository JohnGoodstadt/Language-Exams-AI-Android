package com.goodstadt.john.language.exams.screens.reference

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.models.CategoryMasteryStats
import com.goodstadt.john.language.exams.models.DashboardUiState
import com.goodstadt.john.language.exams.viewmodels.VocabDashboardViewModel

@Composable
fun VocabDashboardScreen(
    viewModel: VocabDashboardViewModel = hiltViewModel(),
    onNavigateToQuiz: (String?) -> Unit // Pass category title, or null for "Mixed Review"
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else if (uiState.isColdStart) {
        // STATE A: Cold Start
        VocabQuizOnboardingView(onStartSuggestion = onNavigateToQuiz)
    } else {
        // STATE B: Active Dashboard
        VocabQuizActiveView(
            state = uiState,
            onStartReview = { onNavigateToQuiz(null) } // Null means "Review All"
        )
    }
}

@Composable
fun VocabQuizActiveView(state: DashboardUiState, onStartReview: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Due Today Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${state.wordsDueCount}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "words due for review",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Button(
                    onClick = onStartReview,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Review Now")
                }
            }
        }

        // 2. Breakdown Title
        Text("Category Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        // 3. Category List
        state.categoryStats.forEach { stat ->
            CategoryMasteryRow(stat)
        }
    }
}

@Composable
fun CategoryMasteryRow(stat: CategoryMasteryStats) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stat.categoryTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)

            if (stat.masteredCount > 0) {
                Text(
                    "${stat.masteredCount}/${stat.totalWords} Mastered",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50)
                )
            }
        }

        // Stacked Progress Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Mastered (Green)
            if (stat.masteredFraction > 0) {
                Box(Modifier.fillMaxHeight().weight(stat.masteredFraction).background(Color(0xFF4CAF50)))
            }
            // Learning (Orange)
            if (stat.learningFraction > 0) {
                Box(Modifier.fillMaxHeight().weight(stat.learningFraction).background(Color(0xFFFF9800)))
            }
            // Remaining (Transparent/Grey)
            val remaining = 1f - (stat.masteredFraction + stat.learningFraction)
            if (remaining > 0) {
                Box(Modifier.fillMaxHeight().weight(remaining))
            }
        }
    }
}

@Composable
fun VocabQuizOnboardingView(onStartSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Psychology,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "Master Vocabulary",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "We track how well you know every word. Take quizzes to build your mastery level.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Spacer(Modifier.height(24.dp))

        Text("Try a topic to start:", style = MaterialTheme.typography.labelLarge)

        Spacer(Modifier.height(12.dp))

        // Suggestions
        SuggestionButton("Personal Information") { onStartSuggestion("Personal Information") }
        SuggestionButton("Education") { onStartSuggestion("Education") }
    }
}

@Composable
fun SuggestionButton(title: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(title)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(16.dp))
    }
}