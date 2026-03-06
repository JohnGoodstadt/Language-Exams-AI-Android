package com.goodstadt.john.language.exams.screens.reference

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
//import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.models.UsageLevelSummary
import com.goodstadt.john.language.exams.models.UsageMastery
import com.goodstadt.john.language.exams.models.UsageQuizOverviewItem
import com.goodstadt.john.language.exams.viewmodels.UsageDashboardViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageDashboardScreen(
    viewModel: UsageDashboardViewModel = hiltViewModel(),
    onStartQuiz: (Int) -> Unit // Pass back Quiz ID to start
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Grammar Progress") })
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (val state = uiState) {
                is UsageDashboardViewModel.UiState.Loading -> {
//                    CircularProgressIndicator(modifier = Modifier.align(LineHeightStyle.Alignment.Center))
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is UsageDashboardViewModel.UiState.ColdStart -> {
                    UsageColdStartView(
                        levelName = state.levelName,
                        onStartFirst = { onStartQuiz(1) } // Start Quiz 1
                    )
                }
                is UsageDashboardViewModel.UiState.Active -> {
                    UsageActiveView(
                        summary = state.summary,
                        onStartQuiz = onStartQuiz
                    )
                }
            }
        }
    }
}

// MARK: - Cold Start View
@Composable
fun UsageColdStartView(levelName: String, onStartFirst: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.School,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("Start $levelName Grammar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Test your knowledge of syntax and usage. Complete quizzes to earn stars and track your mastery of specific rules.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
        )
        Button(onClick = onStartFirst) {
            Text("Start Quiz 1")
        }
    }
}

// MARK: - Active View
@Composable
fun UsageActiveView(summary: UsageLevelSummary, onStartQuiz: (Int) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header Stats
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    StatColumn("${summary.completedQuizzes}/${summary.totalQuizzes}", "Completed")
                    StatColumn("${summary.totalStars}", "Stars Earned")
                }
            }
        }

        // 2. Next Up Logic
        val nextUp = summary.items.firstOrNull { !it.isStarted || it.bestScore < 6 }
        if (nextUp != null) {
            item {
                Text("Next Up", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                UsageQuizRow(item = nextUp, isNextUp = true, onClick = { onStartQuiz(nextUp.id) })
            }
        }

        // 3. All Quizzes
        item {
            Text("All Quizzes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        }

        items(summary.items) { item ->
            UsageQuizRow(item = item, isNextUp = false, onClick = { onStartQuiz(item.id) })
        }
    }
}

@Composable
fun UsageQuizRow(item: UsageQuizOverviewItem, isNextUp: Boolean, onClick: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isNextUp) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.clickable {
            if (item.isStarted) expanded = !expanded else onClick()
        }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status Icon
                val icon = if (item.isPerfect) Icons.Default.CheckCircle else if (item.isStarted) Icons.Default.TrendingUp else Icons.Default.Circle
                val tint = if (item.isPerfect) Color(0xFF4CAF50) else if (item.isStarted) Color(0xFFFF9800) else Color.Gray

                Icon(icon, null, tint = tint)

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (item.isStarted) {
                        Text("Best: ${item.bestScore}/10", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (item.isStarted) {
                    // Star Rating
                    Row {
                        repeat(3) { i ->
                            // Simple logic: 5=1, 8=2, 10=3
                            val starActive = (i == 0 && item.bestScore >= 5) || (i == 1 && item.bestScore >= 8) || (i == 2 && item.bestScore == 10)
                            Icon(
                                Icons.Default.Star,
                                null,
                                modifier = Modifier.size(16.dp),
                                tint = if (starActive) Color(0xFFFFC107) else Color.Gray.copy(alpha = 0.3f)
                            )
                        }
                    }
                } else if (isNextUp) {
                    Button(onClick = onClick, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Text("Start")
                    }
                }
            }

            // Expanded Granular Details
            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Question Breakdown", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))

                // The "Heatmap Bar" (10 Blocks)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Iterate fixed 1..10
                    (1..10).forEach { i ->
                        val mastery = item.questionMastery[i] ?: UsageMastery.NotStarted
                        val color = when(mastery) {
                            UsageMastery.Fluent -> Color(0xFF4CAF50) // Green
                            UsageMastery.Learning -> Color(0xFFFF9800) // Orange
                            UsageMastery.Struggling -> Color(0xFFF44336) // Red
                            UsageMastery.NotStarted -> Color.Gray.copy(alpha = 0.2f)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f) // Square
                                .padding(2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color)
                        )
                    }
                }

                // Play Button
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Text("Retake Quiz")
                }
            }
        }
    }
}

@Composable
fun StatColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}