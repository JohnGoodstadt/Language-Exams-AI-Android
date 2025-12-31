package com.goodstadt.john.language.exams.viewmodels

import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextOverflow
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
fun LifetimeStatsGrid(
    // You can pass the whole XP state or individual values
    // Assuming you pass specific values from MyProgressScreen:
    totalXP: Int, //TODO: can remote
    totalXPString:String,
    badges: Int,
    longestStreak: Int,
    gems: Int,
    userStatus: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
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
            DetailedStatCard(
                title = "Total XP",
                value = totalXPString,
                subTitle = "Experience",
                description = "Cumulative points across all exam levels.",
                icon = Icons.Default.AutoAwesome, // Sparkles
                color = Color(0xFFFFC107), // Amber
                modifier = Modifier.weight(1f)
            )

            // Gems
            DetailedStatCard(
                title = "Gems",
                value = "$gems",
                subTitle = "Currency",
                description = "Use these to buy streak freezes.",
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
            DetailedStatCard(
                title = "Best Streak",
                value = "$longestStreak",
                subTitle = "Days in a row",
                description = "Your all-time consistency record.",
                icon = Icons.Default.LocalFireDepartment,
                color = Color(0xFFFF5722), // Deep Orange
                modifier = Modifier.weight(1f)
            )

            // Status
            DetailedStatCard(
                title = "Status",
                value = userStatus,
                subTitle = "Rank",
                description = "Based on your 7-day activity average.",
                icon = Icons.Default.EmojiEvents, // Crown/Trophy
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


@Composable
fun DetailedStatCard(
    title: String,
    value: String,
    subTitle: String,
    description: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // 1. Header Row: Icon and Value
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )

                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // 2. Title Block
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subTitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 3. Divider
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )

            // 4. Description
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2, // Keeps card heights relatively uniform
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}