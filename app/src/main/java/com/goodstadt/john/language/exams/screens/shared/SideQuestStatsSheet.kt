package com.goodstadt.john.language.exams.screens.shared


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodstadt.john.language.exams.data.QuizHistoryManager
import com.goodstadt.john.language.exams.models.ReferenceCategory
import com.goodstadt.john.language.exams.models.ReferenceSubItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SideQuestStatsSheet(
    paragraphCount: Int,
    paragraphHeardCount: Int,
    conjugations: ReferenceCategory?,
    adjectives: ReferenceCategory?,
    quickRefs: List<ReferenceCategory>,
    quizManager: QuizHistoryManager, // Passed in from ViewModel
    onDismiss: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Side Quests", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. INTRO HEADER
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reference Library",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Track your exploration of grammar and bonus materials.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Filled.MenuBook, // Or similar book icon
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFF9C27B0).copy(alpha = 0.8f) // Purple
                )
            }

            // 2. ACTIVE SIDE QUESTS (AI + Quizzes)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // AI Paragraphs
                AIWriterCard(count = paragraphCount, heard = paragraphHeardCount)

                // Quiz Stats
                // Note: QuizMasteryCard needs to be self-contained or passed stats.
                // Assuming we pass the manager or extract stats here.
                QuizMasteryCard(quizManager = quizManager)
            }

            // 3. DEEP DIVES (Grouped Stats)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Grammar Deep Dives",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (conjugations != null) {
                    ReferenceGroupCard(category = conjugations, color = Color(0xFF2196F3)) // Blue
                }

                if (adjectives != null) {
                    ReferenceGroupCard(category = adjectives, color = Color(0xFF3F51B5)) // Indigo
                }
            }

            // 4. QUICK REFERENCE (Single Items)
            if (quickRefs.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Quick Reference",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    quickRefs.forEach { category ->
                        QuickReferenceRow(category = category)
                    }
                }
            }

            // 5. FOOTER
            Text(
                text = "Reference items grant Bonus XP but do not affect your Exam readiness calculations.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// MARK: - Components

@Composable
fun AIWriterCard(count: Int, heard: Int) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF9C27B0), Color(0xFFE91E63)) // Purple to Pink
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column {
                Text(
                    text = "AI Paragraphs",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Generated & practiced ($heard heard)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ReferenceGroupCard(category: ReferenceCategory, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Sub-items List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                category.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )

                        // Custom Progress Bar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(color.copy(alpha = 0.1f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(item.coverage)
                                        .background(color.copy(alpha = 0.7f))
                                )
                            }

                            Text(
                                text = "${(item.coverage * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(30.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Explainer Text
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun QuickReferenceRow(category: ReferenceCategory) {
    // Aggregate data if multiple items exist (though logic assumes 1 for QuickRefs)
    val totalViewed = category.items.sumOf { it.viewed }
    val totalItems = category.items.sumOf { it.total }
    val coverage = if (totalItems > 0) totalViewed.toFloat() / totalItems else 0f

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = Color(0xFF009688), // Teal
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$totalViewed / $totalItems",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = category.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.padding(start = 32.dp)
            )

            // Mini Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF009688).copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(coverage)
                        .background(Color(0xFF009688))
                )
            }
        }
    }
}

// Minimal implementation of QuizMasteryCard for this sheet
// (Ideally this should be in its own file if complex)
@Composable
fun QuizMasteryCard(quizManager: QuizHistoryManager) {
    // Note: You would normally read stats from the manager here.
    // For now, using placeholder or exposing a method in QuizManager to get GlobalStats.
    val stats = quizManager.getGlobalStats() // Assuming this method exists in Kotlin manager

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF4CAF50), Color(0xFF8BC34A)) // Green to Light Green
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column {
                Text("Quiz Mastery", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text("${stats.perfectScores}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Perfects", style = MaterialTheme.typography.labelSmall)
                    }
                    Column {
                        Text("${stats.totalAttempts}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Attempts", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}