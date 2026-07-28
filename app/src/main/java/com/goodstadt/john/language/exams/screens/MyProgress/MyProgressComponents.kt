package com.goodstadt.john.language.exams.screens.MyProgress


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goodstadt.john.language.exams.managers.XPManager
import java.time.LocalDate
import java.util.Locale

import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp

//import java.time.format.TextStyle

@Composable
fun ProfileHeaderView(userLevel: String, streak: Int, gems: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF2196F3), Color(0xFF00BCD4))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = userLevel.take(1),
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }

        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text("Current Focus: $userLevel", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Super User", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) // Dynamic logic needed here

            Spacer(Modifier.height(8.dp))

            // Mini Stats
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabelWithIcon(icon = Icons.Default.LocalFireDepartment, text = "$streak", color = Color(0xFFFF9800))
                LabelWithIcon(icon = Icons.Default.Diamond, text = "$gems", color = Color(0xFF00BCD4))
            }
        }
    }
}

@Composable
fun LabelWithIcon(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}


@Composable
fun ConsistencyHeatmap(xpManager: XPManager) {
    val historyData = remember(xpManager.state) {
        xpManager.getDailyStatsList(daysBack = 14)
    }

    val currentStreak = xpManager.state.value.currentStreak

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = Color(0xFF9C27B0) // Purple
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Consistency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "$currentStreak Day Streak",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF9800) // Orange
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 14-Day Grid (Using Row with weights)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                historyData.forEachIndexed { index, dayStat ->
                    val date = LocalDate.parse(dayStat.dateId)
                    val dayInitial = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())
                    val isActive = dayStat.xpGained > 0
                    val isToday = index == 13

                    DayTick(
                        initial = dayInitial,
                        isActive = isActive,
                        isToday = isToday
                    )
                }
            }
        }
    }
}

@Composable
fun DayTick(initial: String, isActive: Boolean, isToday: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Circle
        Box(
            modifier = Modifier
                .size(18.dp) // Small tick size
                .clip(CircleShape)
                .background(
                    when {
                        isActive -> Color(0xFF9C27B0) // Active Purple
                        isToday -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f) // Today placeholder
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f) // Missed grey
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isActive) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }

        // Label
        Text(
            text = initial,
            fontSize = 9.sp, // Very small text for 14 items
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}



@Composable
fun SectionHeader(title: String, icon: ImageVector, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}




@Composable
fun ActivityChartCard(xpManager: XPManager) {
    // 1. Fetch Data (Last 7 Days)
    // We use 'remember' so it doesn't recalculate on every micro-frame,
    // but relies on recomposition when xpManager state changes (if wired correctly).
    val weeklyData = remember(xpManager.state) {
        xpManager.getDailyStatsList(daysBack = 7)
    }

    // Find max value to scale the bars (prevent div/0)
    val maxXP = weeklyData.maxOfOrNull { it.xpGained }?.coerceAtLeast(10) ?: 10

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Weekly Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("XP / Day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // The Chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp), // Fixed height for the chart area
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyData.forEachIndexed { index, dayStat ->
                    // Calculate Day Label (e.g. "M", "T")
                    // Note: This assumes the list ends with Today
                    val date = LocalDate.parse(dayStat.dateId)
                    val dayLabel = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())

                    BarColumn(
                        xp = dayStat.xpGained,
                        maxXP = maxXP,
                        label = dayLabel,
                        isToday = index == 6 // Last item is today
                    )
                }
            }
        }
    }
}

@Composable
fun BarColumn(xp: Int, maxXP: Int, label: String, isToday: Boolean) {
    // Calculate height percentage (0.0 to 1.0)
    val fillRatio = (xp.toFloat() / maxXP.toFloat()).coerceIn(0f, 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxHeight()
    ) {
        // 1. Bar Container (The "Track")
        // logic: weight(1f) makes this Box take up all available vertical space
        // AFTER the Text and Spacer have claimed their space.
        Box(
            modifier = Modifier
                .weight(1f)
                .width(24.dp), // Keep the width constraint here
            contentAlignment = Alignment.BottomCenter // Grow bar from bottom up
        ) {
            // 2. The Actual Colored Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fillRatio) // Height is now relative to the *Container*, not the whole card
                    .heightIn(min = 4.dp) // Ensure 0 stats still show a tiny nub
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(
                        if (xp > 0) Color(0xFF4CAF50) // Green
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
            )
        }

        // 3. Spacing between Bar and Text
        Spacer(modifier = Modifier.height(8.dp))

        // 4. The Label
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
        )
    }
}