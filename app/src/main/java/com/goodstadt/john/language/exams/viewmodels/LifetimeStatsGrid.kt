package com.goodstadt.john.language.exams.viewmodels


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goodstadt.john.language.exams.managers.XpState

@Composable
fun LifetimeStatsGrid(xpState: XpState) {

    // 1. Calculate Totals dynamically
    val totalXP = xpState.levels.values.sumOf { it.xp }

    // 2. Determine Status Label (Simple logic based on Total XP)
    // You can replace this with a more complex calc from XPManager if you ported userType()
    val statusLabel = when {
        totalXP > 5000 -> "Super User"
        totalXP > 2000 -> "Heavy User"
        totalXP > 500 -> "Regular"
        else -> "Learner"
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // HEADER
        Text(
            text = "Lifetime Stats",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        // ROW 1
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Total XP
            StatGridItem(
                label = "Total XP",
                value = "$totalXP",
                icon = Icons.Default.AutoAwesome,
                color = Color(0xFFFFC107), // Amber
                modifier = Modifier.weight(1f)
            )

            // Gems
            StatGridItem(
                label = "Gems",
                value = "${xpState.gems}",
                icon = Icons.Default.Diamond,
                color = Color(0xFF00BCD4), // Cyan
                modifier = Modifier.weight(1f)
            )
        }

        // ROW 2
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Best Streak
            StatGridItem(
                label = "Best Streak",
                value = "${xpState.longestStreak}",
                icon = Icons.Default.LocalFireDepartment, // Flame
                color = Color(0xFFFF5722), // Deep Orange
                modifier = Modifier.weight(1f)
            )

            // Status / Rank
            StatGridItem(
                label = "Status",
                value = statusLabel,
                icon = Icons.Default.EmojiEvents, // Trophy
                color = Color(0xFF9C27B0), // Purple
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// MARK: - Subcomponent: The Card
@Composable
fun StatGridItem(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            // Icon Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Value
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall, // Big and bold
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Label
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}