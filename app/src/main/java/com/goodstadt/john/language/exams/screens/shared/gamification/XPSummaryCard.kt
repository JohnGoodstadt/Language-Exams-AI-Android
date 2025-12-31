package com.goodstadt.john.language.exams.screens.shared.gamification

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goodstadt.john.language.exams.managers.LevelProgressInfo


import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.managers.XpState

@Composable
fun XPSummaryCard(
    xpState: XpState,
    progressInfo: LevelProgressInfo
) {
    val currentLevelName = xpState.currentLevel
    val learnerLevel = xpState.levels[currentLevelName]?.learnerLevel ?: 1

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // 1. Top Section
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular Progress
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progressInfo.fraction },
                        modifier = Modifier.size(70.dp),
                        color = Color(0xFFFFC107), // Amber
                        strokeWidth = 6.dp,
                        trackColor = Color(0xFFFFC107).copy(alpha = 0.2f),
                    )

                    // Crown if Max, Star if normal
                    if (progressInfo.isMaxLevel) {
                        // Use a crown icon if you have one, or Star
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(32.dp))
                    } else {
                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.size(32.dp))
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    if (progressInfo.isMaxLevel) {
                        Text(
                            text = "MAX LEVEL",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFF9800) // Orange
                        )
                        Text(
                            text = "Mastery Achieved",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Shows "60 / 150 XP"
                        Text(
                            text = "${progressInfo.currentXPInBracket} / ${progressInfo.requiredXPForBracket} XP",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "LEVEL $learnerLevel LEARNER",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 2. Footer Section
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))

                val footerText = if (progressInfo.isMaxLevel) {
                    "You have completed the XP path for this exam. Maintain your streak!"
                } else {
                    "Earn 3 XP hearing new sentence. 10 xp for Section completion. Perfect quiz scores gets +10 xp"
                }

                Text(
                    text = footerText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}