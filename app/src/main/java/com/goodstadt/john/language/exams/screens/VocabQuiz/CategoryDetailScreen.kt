package com.goodstadt.john.language.exams.screens.VocabQuiz

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Using the same color palette as the Dashboard
private val DarkBG = Color(0xFF121212)
private val CardBG = Color(0xFF1C1C1E)
private val AccentOrange = Color(0xFFFF9500)
private val SuccessGreen = Color(0xFF4CAF50)

data class QuizSet(
    val id: String,         // e.g., "education_set_1"
    val setTitle: String,   // e.g., "Set 1 of 3"
    val wordPreview: String,// e.g., "Ambitious, Reliable, Sustainable..."
    val bestScore: Int,     // e.g., 8
    val totalQuestions: Int = 10,
    val isCompleted: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailScreen(
    categoryTitle: String,
    quizSets: List<QuizSet>,
    onBackClick: () -> Unit,
    onStartSet: (QuizSet) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(categoryTitle, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBG)
            )
        },
        containerColor = DarkBG
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. STATS SUMMARY
            item {
                CategorySummaryCard(quizSets)
            }

            item {
                Text(
                    "Available Question Sets",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }

            // 2. LIST OF SETS (Replacing the Dropdown)
            items(quizSets) { quizSet ->
                QuizSetRow(
                    quizSet = quizSet,
                    onClick = { onStartSet(quizSet) }
                )
            }
        }
    }
}

@Composable
fun CategorySummaryCard(sets: List<QuizSet>) {
    val totalMastered = sets.sumOf { it.bestScore }
    val totalPossible = sets.size * 10

    Card(
        colors = CardDefaults.cardColors(containerColor = CardBG),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Category Mastery", color = Color.Gray, fontSize = 12.sp)
                Text("$totalMastered / $totalPossible Words", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            // Simple percentage circle
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { totalMastered.toFloat() / totalPossible.toFloat() },
                    color = AccentOrange,
                    trackColor = Color.DarkGray,
                    strokeWidth = 6.dp,
                    modifier = Modifier.size(50.dp)
                )
            }
        }
    }
}

@Composable
fun QuizSetRow(quizSet: QuizSet, onClick: () -> Unit) {
    val isPerfect = quizSet.bestScore == quizSet.totalQuestions

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBG)
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // SET NUMBER ICON
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(if (isPerfect) SuccessGreen else Color.DarkGray, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isPerfect) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    text = quizSet.setTitle.filter { it.isDigit() }.take(1),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // TEXT CONTENT
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = quizSet.setTitle,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = quizSet.wordPreview,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                maxLines = 1
            )
        }

        // SCORE DISPLAY
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${quizSet.bestScore}/${quizSet.totalQuestions}",
                color = if (isPerfect) SuccessGreen else Color.White,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = AccentOrange,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}