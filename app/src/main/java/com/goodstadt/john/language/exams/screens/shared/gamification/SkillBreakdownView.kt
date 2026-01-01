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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goodstadt.john.language.exams.managers.XpState


@Composable
fun SkillBreakdownView(xpState: XpState) {
    // 1. Define Order
    val orderedLevels = listOf("A1", "A2", "B1", "B2")

    // 2. Filter Data
    // Active = Has XP OR is the one selected in settings (current focus)
    val activeLevels = orderedLevels.filter { level ->
        val xp = xpState.levels[level]?.xp ?: 0
        xp > 0 || level == xpState.currentLevel
    }

    // Inactive = The rest
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

            // 3. Render Active Bars
            if (activeLevels.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    activeLevels.forEach { level ->
                        val xp = xpState.levels[level]?.xp ?: 0
                        SkillBar(
                            level = level,
                            xp = xp,
                            color = getSkillBreakdownLevelColor(level)
                        )
                    }
                }
            }

            // 4. Render Inactive Summary (One Line)
            if (inactiveLevels.isNotEmpty()) {
                // Add a divider if we have both sections
                if (activeLevels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                val listStr = inactiveLevels.joinToString(", ")
                Text(
                    text = "Not yet started: $listStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}


@Composable
fun SkillBar(level: String, xp: Int, color: Color) {
    // Simple calc: max 2000 xp per level visual cap
    val progress = (xp.toFloat() / 2000f).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "ESOL $level",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$xp XP",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

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
                    .fillMaxWidth(progress)
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