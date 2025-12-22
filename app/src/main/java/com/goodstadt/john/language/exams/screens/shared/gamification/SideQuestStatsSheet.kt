package com.goodstadt.john.language.exams.screens.shared.gamification

import com.goodstadt.john.language.exams.data.QuizHistoryManager


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goodstadt.john.language.exams.models.ReferenceCategory


sealed class SideQuestNavTarget {
    // We add 'tabId' to know which tab to switch to in the horizontal menu
    data class Reference(val tabId: String, val documentId: String) : SideQuestNavTarget()
    data class MainTab(val tabIndex: Int) : SideQuestNavTarget()
    // ... other targets ...
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SideQuestStatsSheet(
    paragraphCount: Int,
    paragraphHeardCount: Int,
    conjugations: ReferenceCategory?,
    adjectives: ReferenceCategory?,
    quickRefs: List<ReferenceCategory>, // Contains Prepositions, Sounds Same, Good vs Well
    quizManager: QuizHistoryManager,
    onNavigate: (SideQuestNavTarget) -> Unit,
    onDismiss: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Side Quests", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                actions = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

            // 1. INTRO HEADER
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reference Library",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Track your exploration of grammar and bonus materials.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFF9C27B0).copy(alpha = 0.8f) // Purple
                )
            }



            // 3. GRAMMAR DEEP DIVES (Conjugations & Adjectives)
            // Only render if data is provided
            if (conjugations != null || adjectives != null) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "Grammar Deep Dives",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (conjugations != null) {
                        ReferenceGroupCard(category = conjugations, color = Color(0xFF2196F3),onNavigate = onNavigate ) // Blue
                    }

                    if (adjectives != null) {
                        ReferenceGroupCard(category = adjectives, color = Color(0xFF3F51B5),onNavigate = onNavigate) // Indigo
                    }
                }
            }

            // 4. QUICK REFERENCE (Single Items)
            // Expects: Prepositions, Sounds Same, Good vs Well
            if (quickRefs.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Quick Reference",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    quickRefs.forEach { category ->
                        QuickReferenceRow(category = category,onNavigate)
                    }
                }
            }

            // 2. ACTIVE STATS (Creator & Quiz)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // AI Paragraphs (Creation)
                AIWriterCard(count = paragraphCount, heard = paragraphHeardCount,onNavigate)

                // Quiz Mastery (Testing)
                QuizMasteryCard(quizManager = quizManager,onNavigate)

                // ✅ NEW: Detailed Breakdown with Memory Boosts
                Text(
                    text = "Quiz Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                QuizLevelBreakdownList(quizManager = quizManager)
            }

            // 5. FOOTER
            Text(
                text = "Reference items grant Bonus XP but do not affect your Exam readiness calculations.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// MARK: - COMPONENTS

@Composable
fun AIWriterCard(count: Int, heard: Int,onNavigate: (SideQuestNavTarget) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.clickable {
            onNavigate(SideQuestNavTarget.Reference("paragraph", "paragraph"))
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF9C27B0), Color(0xFFE91E63)) // Purple to Pink
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "AI Paragraphs",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {

                            val tabId = "paragraph"
                            val docId = "paragraph"

                            // val c = category

                            // Assuming the Tab ID is the same as Document ID for simple sheets,
                            // or you have a way to map them.
                            onNavigate(SideQuestNavTarget.Reference(tabId = tabId, documentId = docId))
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF009688))
                    ) {
                        Text("Do More", style = MaterialTheme.typography.labelMedium)
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }

                Text(
                    text = "$count",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Generated & practiced ($heard heard)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun QuizMasteryCard(quizManager: QuizHistoryManager,onNavigate: (SideQuestNavTarget) -> Unit) {
    val stats = quizManager.getGlobalStats() // Gets totals from HistoryManager

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.clickable {
            onNavigate(SideQuestNavTarget.Reference("quiz", "quiz"))
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF4CAF50), Color(0xFF8BC34A)) // Green
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Quiz Mastery",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = {

                            val tabId = "quiz"
                            val docId = "quiz"

                            // val c = category

                            // Assuming the Tab ID is the same as Document ID for simple sheets,
                            // or you have a way to map them.
                            onNavigate(SideQuestNavTarget.Reference(tabId = tabId, documentId = docId))
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF009688)),
                    ) {
                        Text("Do More", style = MaterialTheme.typography.labelMedium)
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }


                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("${stats.perfectScores}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Perfects", style = MaterialTheme.typography.labelSmall)
                    }
                    Column {
                        Text("${stats.totalAttempts}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Attempts", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun ReferenceGroupCard(category: ReferenceCategory, color: Color,onNavigate: (SideQuestNavTarget) -> Unit ) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        // Assuming the category items map to a specific document/tab
                        // You need to know which Tab ID corresponds to this category.
                        // If 'category.items' holds the documentId, use the first one.
//                        val tabId = "AdjectivesGroup"//category.items.firstOrNull()?.documentId ?: return@TextButton
//                        val docId = "EnglishA1Adjectives"
                        val firstItem = category.items.firstOrNull()
                        val tabId = category.items.firstOrNull()?.tabId ?: return@TextButton
                        val docId = category.items.firstOrNull()?.documentId ?: return@TextButton

                       // val c = category

                        // Assuming the Tab ID is the same as Document ID for simple sheets,
                        // or you have a way to map them.
                        onNavigate(SideQuestNavTarget.Reference(tabId = tabId, documentId = docId))
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = color)
                ) {
                    Text("Do Another", style = MaterialTheme.typography.labelMedium)
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }

            }

            // Sub-items List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                category.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )

                        // Custom Progress Bar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Track
                            Box(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(color.copy(alpha = 0.2f))
                            ) {
                                // Fill
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(item.coverage)
                                        .background(color)
                                )
                            }

                            Text(
                                text = "${(item.coverage * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(35.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Explainer Text
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun QuickReferenceRow(category: ReferenceCategory,onNavigate: (SideQuestNavTarget) -> Unit){ // ✅ Add Callback)
    // Aggregate data if items are split, or just take the first one
    val totalViewed = category.items.sumOf { it.viewed }
    val totalItems = category.items.sumOf { it.total }
    val coverage = if (totalItems > 0) totalViewed.toFloat() / totalItems else 0f
    val targetItem = category.items.firstOrNull()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = Color(0xFF009688), // Teal
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$totalViewed / $totalItems",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = category.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.padding(start = 32.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        val firstItem = category.items.firstOrNull()
                        val tabId = category.items.firstOrNull()?.tabId ?: return@TextButton
                        val docId = category.items.firstOrNull()?.documentId ?: return@TextButton

                        // val c = category

                        // Assuming the Tab ID is the same as Document ID for simple sheets,
                        // or you have a way to map them.
                        onNavigate(SideQuestNavTarget.Reference(tabId = tabId, documentId = docId))
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF009688)),
                ) {
                    Text("Do More", style = MaterialTheme.typography.labelMedium)
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }


            // Mini Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF009688).copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(coverage)
                        .background(Color(0xFF009688))
                )
            }
        }
    }
}