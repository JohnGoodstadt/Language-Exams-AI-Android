package com.goodstadt.john.language.exams.screens.shared.gamification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goodstadt.john.language.exams.data.QuizHistoryManager
// In QuizLevelBreakdownCard.kt

import com.goodstadt.john.language.exams.screens.UsageQuiz.UsageQuizLevelsFilename

// 1. Helper to convert your Enum to the UI Model
fun getDynamicQuizLevels(): List<LevelConfig> {
    return UsageQuizLevelsFilename.entries.map { enumLevel ->
        LevelConfig(
            name = enumLevel.description, // e.g. "Elementary"
            color = getLevelColor(enumLevel.description),
            quizzes = enumLevel.quizzes.map { quizDetail ->
                QuizUiConfig(
                    id = quizDetail.id,
                    title = quizDetail.title // e.g. "Quiz 1 - Simple Tenses"
                )
            }
        )
    }
}

// 2. Helper for Colors (Keep this or move to a Utils file)
fun getLevelColor(levelName: String): Color {
    return when(levelName) {
        "Elementary" -> Color(0xFF4CAF50) // Green
        "Intermediate" -> Color(0xFF2196F3) // Blue
        "Upper" -> Color(0xFFFF9800) // Orange
        "Advanced" -> Color(0xFFF44336) // Red
        else -> Color.Gray
    }
}

// 3. Update the Main List Component
@Composable
fun QuizLevelBreakdownList(quizManager: QuizHistoryManager) {
    // ✅ Use the dynamic list instead of the hardcoded one
    val dynamicLevels = remember { getDynamicQuizLevels() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        dynamicLevels.forEach { config ->
            QuizLevelCard(config = config, quizManager = quizManager)
        }
    }
}
// MARK: - Local Data Models for UI Configuration

data class QuizUiConfig(val id: Int, val title: String)
data class LevelConfig(val name: String, val color: Color, val quizzes: List<QuizUiConfig>)

// Define your Levels and Quizzes here (or map from your existing Enum)
val quizLevels = listOf(
    LevelConfig("Elementary", Color(0xFF4CAF50), (1..5).map { QuizUiConfig(it, "Quiz $it - Basics") }),    // Green
    LevelConfig("Intermediate", Color(0xFF2196F3), (1..5).map { QuizUiConfig(it, "Quiz $it - Grammar") }), // Blue
    LevelConfig("Upper", Color(0xFFFF9800), (1..5).map { QuizUiConfig(it, "Quiz $it - Fluency") }),       // Orange
    LevelConfig("Advanced", Color(0xFFF44336), (1..5).map { QuizUiConfig(it, "Quiz $it - Mastery") })      // Red
)

// MARK: - Main List Component

//@Composable
//fun QuizLevelBreakdownList(quizManager: QuizHistoryManager) {
//    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
//        quizLevels.forEach { config ->
//            QuizLevelCard(config = config, quizManager = quizManager)
//        }
//    }
//}

// MARK: - Collapsible Card Component

@Composable
fun QuizLevelCard(config: LevelConfig, quizManager: QuizHistoryManager) {
    var isExpanded by remember { mutableStateOf(false) }

    // 1. Calculate Summary for the Header
    var totalPerfects = 0
    var totalPlays = 0

    // We iterate just to sum up stats for the badge
    config.quizzes.forEach { quiz ->
        val stats = quizManager.getDetailedStatsForQuiz(config.name, quiz.id)
        totalPerfects += stats.perfects
        totalPlays += stats.attempts
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // --- HEADER (Always Visible) ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Level Icon
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = null,
                    tint = config.color,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Level Title
                Text(
                    text = config.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.weight(1f))

                // Summary Badge (Only if user has played this level)
                if (totalPlays > 0) {
                    Surface(
                        color = config.color.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            if (totalPerfects > 0) {
                                Icon(Icons.Default.Star, null, tint = config.color, modifier = Modifier.size(14.dp))
                                Text(
                                    text = " $totalPerfects",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = config.color,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = "$totalPlays attempts",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Chevron
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // --- EXPANDED LIST ---
            AnimatedVisibility(visible = isExpanded) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    config.quizzes.forEach { quiz ->
                        // Get detailed stats for this specific row
                        val stats = quizManager.getDetailedStatsForQuiz(config.name, quiz.id)

                        QuizRowItem(title = quiz.title, stats = stats)
                    }

                    if (config.quizzes.isEmpty()) {
                        Text(
                            text = "No quizzes available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Individual Row Component

@Composable
fun QuizRowItem(title: String, stats: QuizHistoryManager.DetailedQuizStat) {

    // ✅ LOGIC: Memory Boost Calculation
    // Ready if: Has Perfect score AND Last played > 24 hours ago
    val oneDayMillis = 1000 * 60 * 60 * 24
    val timeSinceLast = System.currentTimeMillis() - stats.lastTimestamp
    val isReadyForBoost = stats.perfects > 0 && timeSinceLast > oneDayMillis

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Side: Title & Tip
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                // Dim text if never played
                color = if (stats.attempts > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ✅ THE TIP UI
            if (isReadyForBoost) {
                Text(
                    text = "Review for Bonus XP!",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50), // Green
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Right Side: Stats
        if (stats.attempts > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {

                // Perfect Count
                if (stats.perfects > 0) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Perfect",
                        tint = Color(0xFFFFC107), // Gold
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${stats.perfects}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 2.dp, end = 8.dp)
                    )
                }

                // Attempt Count
                Icon(
                    imageVector = Icons.Default.PlayCircle, // or Refresh
                    contentDescription = "Attempts",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${stats.attempts}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp)
                )

                // ✅ REFRESH ICON (Visual cue for the Boost)
                if (isReadyForBoost) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Bonus Available",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            // Empty State
            Text(
                text = "-",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
