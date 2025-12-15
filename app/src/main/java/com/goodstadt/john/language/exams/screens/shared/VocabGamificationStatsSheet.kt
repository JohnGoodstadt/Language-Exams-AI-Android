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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodstadt.john.language.exams.data.QuizHistoryManager
import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.utils.CategoryProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabGamificationStatsSheet(
    grandTotalWords: Int,
    grandTotalMastered: Int,
    categoryProgress: List<CategoryProgress>,
    xpManager: XPManager, // Pass via Hilt EntryPoint if needed
    quizManager: QuizHistoryManager,
    onDismiss: () -> Unit
) {
    // Collect State for Advice Logic
    val xpState by xpManager.state.collectAsState()

    // Calculate total progress percentage
    val globalProgress = if (grandTotalWords > 0)
        grandTotalMastered.toFloat() / grandTotalWords.toFloat()
    else 0f

//    com.goodstadt.john.language.exams.ui.theme.LanguageExamsAITheme {


        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("Exam Progress", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.Gray
                            )
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

                // 1. MISSION CONTROL (Exam Countdown)
                // This Card handles the "Set Goal" vs "Active Countdown" logic
                ExamCountdownCard(
                    xpManager = xpManager,
                    totalWords = grandTotalWords,
                    masteredWords = grandTotalMastered,
                    // Assuming 'currentLevel' is derived from the first category or passed in
                    currentLevelName = categoryProgress.firstOrNull()?.let { "Tab ${it.tabNumber}" }
                        ?: "Exam"
                )

                // 2. THE SMART COACH (Dynamic Advice)
                // Note: We need to calculate targetDailyRate from XPManager if available
                val advice = generateNuancedAdvice(
                    categories = categoryProgress,
                    totalXP = xpState.levels[xpState.currentLevel]?.xp ?: 0,
                    streak = xpState.currentStreak,
                    quizManager = quizManager,
                    targetDailyRate = 10 // Placeholder: derive this from ExamCountdown logic if possible
                )

                SmartCoachCard(
                    title = advice.title,
                    message = advice.message,
                    icon = advice.icon,
                    color = advice.color
                )

                // 3. TOPIC MASTERY LIST
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Topic Mastery",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${(globalProgress * 100).toInt()}% Complete",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            categoryProgress.forEach { category ->
                                TopicProgressRow(category = category)
                            }
                        }
                    }
                }

                // 4. BADGES (Compact Row)
                CompactBadgeRow(xpManager = xpManager)

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
//    }//Theme wrapper for dark mode
}

// MARK: - Smart Advice Logic

data class SmartAdvice(
    val title: String,
    val message: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun generateNuancedAdvice(
    categories: List<CategoryProgress>,
    totalXP: Int,
    streak: Int,
    quizManager: QuizHistoryManager,
    targetDailyRate: Int
): SmartAdvice {

    // 1. ONBOARDING
    if (totalXP < 10) {
        return SmartAdvice("Welcome Aboard!", "Tap any sentence to listen. You get 3 XP for every new sentence.", Icons.Filled.WavingHand, Color(0xFF2196F3)) // Blue
    }
    if (totalXP < 50) {
        return SmartAdvice("First Steps", "You're off to a great start. Explore different topics to see what interests you.", Icons.Filled.DirectionsWalk, Color(0xFF2196F3))
    }

    // 2. THE FINISHER
    val almostDone = categories.find { it.percentage >= 0.85f && !it.isCompleted }
    if (almostDone != null) {
        val remaining = almostDone.total - almostDone.heard
        return SmartAdvice("Finish Strong!", "You are so close to mastering '${almostDone.title}'. Just $remaining sentences left!", Icons.Filled.Flag, Color(0xFFFF9800)) // Orange
    }

    // 3. THE STRUGGLER (Weakness)
    // Note: Assuming quizManager has a synchronous way to check (or passed in as state)
    // For now, simpler logic or assume passed data
    /*
    if (quizManager.hasWeakness()) { ... }
    */

    // 4. THE STREAKER
    if (streak >= 7) {
        return SmartAdvice("On Fire!", "A $streak-day streak is impressive. Consistency is the secret to fluency.", Icons.Filled.LocalFireDepartment, Color(0xFFFF9800))
    }

    // 5. THE DABBLER
    val activeTopics = categories.filter { it.isStarted && !it.isCompleted }
    if (activeTopics.size > 3) {
        val bestFocus = activeTopics.maxByOrNull { it.percentage }
        if (bestFocus != null) {
            return SmartAdvice("Focus Your Efforts", "You have ${activeTopics.size} topics open. Focus on '${bestFocus.title}' to get your next badge.", Icons.Filled.CenterFocusStrong, Color(0xFF9C27B0)) // Purple
        }
    }

    // 6. THE UNTOUCHED
    val untouched = categories.find { !it.isStarted }
    if (untouched != null) {
        return SmartAdvice("Expand Horizons", "You haven't looked at '${untouched.title}' yet. Learn 5 words from it today.", Icons.Filled.Map, Color(0xFF009688)) // Teal
    }

    // 7. DEFAULT
    return SmartAdvice("Keep Going", "You are making steady progress. Daily practice is key to long-term memory.", Icons.Filled.TrendingUp, Color(0xFF4CAF50)) // Green
}

// MARK: - Components

@Composable
fun SmartCoachCard(title: String, message: String, icon: ImageVector, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TopicProgressRow(category: CategoryProgress) {
    val barColor = when {
        category.isCompleted -> Color(0xFF4CAF50) // Green
        category.percentage > 0.8f -> Color(0xFFFF9800) // Orange
        else -> Color(0xFF2196F3) // Blue
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (category.isStarted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))

            if (category.isCompleted) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
            } else {
                Text(
                    "${category.heard} / ${category.total}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Progress Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.LightGray.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(category.percentage)
                    .background(barColor)
            )
        }
    }
}

@Composable
fun CompactBadgeRow(xpManager: XPManager) {
    // You can read earned badges state here
    val xpState by xpManager.state.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Latest Badges", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (xpState.earnedBadges.isEmpty()) {
                Text("Start learning to earn badges!", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            } else {
                // Show last 3 badges
                xpState.earnedBadges.takeLast(3).forEach { badgeName ->
                    Surface(
                        color = Color(0xFFFF9800).copy(alpha = 0.1f), // Orange Tint
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(14.dp))
                            Text(badgeName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

// Minimal placeholder for ExamCountdownCard (You likely have a fuller version)
@Composable
fun ExamCountdownCardObsolete(
    xpManager: XPManager,
    totalWords: Int,
    masteredWords: Int,
    currentLevelName: String
) {
    // If you haven't implemented the Kotlin ExamCountdownCard yet,
    // you can reuse the "SmartCoachCard" style for now with generic text.
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Mission: $currentLevelName", style = MaterialTheme.typography.labelLarge, color = Color(0xFFFF9800))
            Spacer(modifier = Modifier.height(4.dp))

            // Simplified Logic for placeholder
            val remaining = totalWords - masteredWords
            if (remaining <= 0) {
                Text("Level Complete!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            } else {
                Text("$remaining words left", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Keep up the pace to finish on time.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
