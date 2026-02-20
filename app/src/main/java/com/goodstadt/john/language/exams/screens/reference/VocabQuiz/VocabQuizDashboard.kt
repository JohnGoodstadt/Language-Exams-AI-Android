package com.goodstadt.john.language.exams.screens.reference.VocabQuiz

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Force Dark Mode Colors ---
private val DarkBG = Color(0xFF121212)
private val CardBG = Color(0xFF1C1C1E)
private val AccentOrange = Color(0xFFFF9500) // Your brand color
private val SuccessGreen = Color(0xFF4CAF50)

sealed class QuizRoute(val route: String) {
    object Dashboard : QuizRoute("dashboard")
    object Detail : QuizRoute("detail/{categoryId}") {
        fun createRoute(categoryId: String) = "detail/$categoryId"
    }
}

data class QuizCategory(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val totalWords: Int,
    val masteredCount: Int,
    val isPriority: Boolean = false // Used for the "Weak Words" card
)

@Composable
fun VocabQuizDashboard(
    weakWordCount: Int,
    categories: List<QuizCategory>,
    onCategoryClick: (QuizCategory) -> Unit,
    onWeakWordsClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBG
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. HEADER: Total Progress
            item(span = { GridItemSpan(2) }) {
                DashboardHeader(mastered = 120, total = 444)
            }

            // 2. PRIORITY: Weak Words Review
            if (weakWordCount > 0) {
                item(span = { GridItemSpan(2) }) {
                    WeakWordsHeroCard(count = weakWordCount, onClick = onWeakWordsClick)
                }
            }

            // 3. TITLE: Browse Categories
            item(span = { GridItemSpan(2) }) {
                Text(
                    text = "Categories",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // 4. GRID: The 16 Groupings
            items(categories) { category ->
                CategoryGridCard(category = category, onClick = { onCategoryClick(category) })
            }
        }
    }
}

@Composable
fun DashboardHeader(mastered: Int, total: Int) {
    val progress = mastered.toFloat() / total.toFloat()
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("Your B1 Progress", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$mastered",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "/$total words",
                fontSize = 16.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = SuccessGreen,
            trackColor = Color.DarkGray,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
fun WeakWordsHeroCard(count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = AccentOrange),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Smart Review", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.Black)
                Text("Practice $count weak words", fontSize = 14.sp, color = Color.Black.copy(alpha = 0.7f))
            }
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}

@Composable
fun CategoryGridCard(category: QuizCategory, onClick: () -> Unit) {
    val progress = category.masteredCount.toFloat() / category.totalWords.toFloat()

    Card(
        modifier = Modifier.height(140.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = CardBG),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = AccentOrange,
                modifier = Modifier.size(28.dp)
            )

            Column {
                Text(
                    text = category.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.White,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${category.masteredCount}/${category.totalWords}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.weight(1f))
                    // Small completion tick if 100%
                    if (progress >= 1f) {
                        Icon(Icons.Default.CheckCircle, null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}