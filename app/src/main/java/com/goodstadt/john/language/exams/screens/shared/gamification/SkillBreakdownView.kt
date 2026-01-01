package com.goodstadt.john.language.exams.screens.shared.gamification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.managers.XpState

@Composable
fun SkillBreakdownView(
    xpManager: XPManager // Pass the manager to calculate progress
) {
    // Collect state
    val xpState by xpManager.state.collectAsState()

    val orderedLevels = listOf("A1", "A2", "B1", "B2")

    // 1. Filter Data
    val activeLevels = orderedLevels.filter { level ->
        val xp = xpState.levels[level]?.xp ?: 0
        // It is active if we have XP OR if it is the user's current target
        xp > 0 || level == xpState.currentLevel
    }

    val inactiveLevels = orderedLevels.filter { !activeLevels.contains(it) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Skill Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // 2. Active Bars
            if (activeLevels.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    activeLevels.forEach { level ->
                        // Calculate Smart Progress (Brackets)
                        val info = xpManager.getLevelProgress(level)

                        SkillBar(
                            level = level,
                            currentXP = info.currentXPInBracket,
                            totalXP = info.requiredXPForBracket,
                            fraction = info.fraction,
                            color = getSkillBreakdownLevelColor(level),
                            isCurrentFocus = (level == xpState.currentLevel)
                        )
                    }
                }
            }

            // 3. Inactive Summary
            if (inactiveLevels.isNotEmpty()) {
                if (activeLevels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = "Not yet started: ${inactiveLevels.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun SkillBar(
    level: String,
    currentXP: Int,
    totalXP: Int,
    fraction: Float,
    color: Color,
    isCurrentFocus: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "ESOL $level",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                // Visual cue for the current level
                if (isCurrentFocus) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "CURRENT",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = color,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = "$currentXP / $totalXP XP",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // The Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction) // Use the smart fraction
                    .background(color)
            )
        }
    }
}

// Helper to keep colors consistent
private fun getSkillBreakdownLevelColor(level: String): Color {
    return when (level) {
        "A1" -> Color(0xFF4CAF50) // Green
        "A2" -> Color(0xFF2196F3) // Blue
        "B1" -> Color(0xFFFF9800) // Orange
        "B2" -> Color(0xFFF44336) // Red
        else -> Color.Gray
    }
}