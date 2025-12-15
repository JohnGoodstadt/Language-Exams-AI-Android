package com.goodstadt.john.language.exams.screens.shared


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodstadt.john.language.exams.managers.XpState

// We need a mapping for the Badge Types (assuming you ported the Enum)
// If not, here is a local helper to represent them for the UI.
enum class BadgeUiType(val title: String, val icon: ImageVector, val color: Color, val sortOrder: Int) {
    // Easy
    WARM_UP("Warm Up", Icons.Default.LocalFireDepartment, Color(0xFFCD7F32), 10), // Bronze
    FIRST_STEPS("First Steps", Icons.Default.DirectionsWalk, Color(0xFFCD7F32), 11),
    EXPLORER("Grammar Geek", Icons.Default.Map, Color(0xFFCD7F32), 12),

    // Medium
    DEDICATED("Dedicated", Icons.Default.LocalFireDepartment, Color(0xFFC0C0C0), 20), // Silver
    SPRINT("Sprint", Icons.Default.Bolt, Color(0xFFC0C0C0), 21),
    SPONGE("The Sponge", Icons.Default.Psychology, Color(0xFFC0C0C0), 22),
    NIGHT_OWL("Night Owl", Icons.Default.NightsStay, Color(0xFFC0C0C0), 23),
    EARLY_BIRD("Early Bird", Icons.Default.WbSunny, Color(0xFFC0C0C0), 24),

    // Hard
    UNSTOPPABLE("Unstoppable", Icons.Default.LocalFireDepartment, Color(0xFFFFD700), 30), // Gold
    SHARP_EYE("Sharp Eye", Icons.Default.Visibility, Color(0xFFFFD700), 31),
    SNIPER("Sniper", Icons.Default.GpsFixed, Color(0xFFFFD700), 32),
    DICTIONARY("Dictionary", Icons.Default.MenuBook, Color(0xFFFFD700), 33),
    IRON_WILL("Iron Will", Icons.Default.MilitaryTech, Color(0xFFFFD700), 40),
    SUPPORTER("Supporter", Icons.Default.Favorite, Color(0xFFE91E63), 99); // Pink

    companion object {
        fun fromString(raw: String): BadgeUiType? {
            return entries.find { it.title == raw }
        }
    }
}

@Composable
fun BadgeShowcaseSection(xpState: XpState) {
    // 1. Prepare Data
    val allBadges = BadgeUiType.entries
    val earnedSet = xpState.earnedBadges

    // 2. Sort: Earned First, then by Difficulty
    val sortedBadges = remember(earnedSet) {
        allBadges.sortedWith(
            compareByDescending<BadgeUiType> { earnedSet.contains(it.title) } // Earned = true (first)
                .thenBy { it.sortOrder }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Achievements",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${earnedSet.distinct().size}/${allBadges.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Horizontal List
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
        ) {
            items(sortedBadges) { badge ->
                // Count how many times earned (for "Sprint x2")
                val count = earnedSet.count { it == badge.title }
                BadgeItemView(badge = badge, count = count)
            }
        }
    }
}

@Composable
fun BadgeItemView(badge: BadgeUiType, count: Int) {
    val isUnlocked = count > 0
    val opacity = if (isUnlocked) 1f else 0.5f
    val iconColor = if (isUnlocked) badge.color else Color.Gray
    val bgColor = if (isUnlocked) badge.color.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.1f)
    val borderColor = if (isUnlocked) badge.color.copy(alpha = 0.5f) else Color.Transparent

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        // Badge Circle
        Box(
            modifier = Modifier.size(70.dp),
            contentAlignment = Alignment.TopEnd
        ) {
            // Main Circle
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(4.dp) // Make room for the x2 bubble
                    .clip(CircleShape)
                    .background(bgColor)
                    .border(2.dp, borderColor, CircleShape)
                    .clickable(enabled = isUnlocked) {
                        // TODO: Show Detail Sheet
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isUnlocked) badge.icon else Icons.Default.Lock,
                    contentDescription = badge.title,
                    tint = iconColor,
                    modifier = Modifier.size(32.dp).alpha(opacity)
                )
            }

            // "x2" Bubble
            if (count > 1) {
                Box(
                    modifier = Modifier
                        .offset(x = (-4).dp, y = 4.dp) // Position top right
                        .background(Color.Red, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "x$count",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = badge.title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isUnlocked) FontWeight.Bold else FontWeight.Normal,
            color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}