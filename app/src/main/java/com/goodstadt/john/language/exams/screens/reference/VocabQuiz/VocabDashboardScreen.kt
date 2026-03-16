package com.goodstadt.john.language.exams.screens.reference.VocabQuiz

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodstadt.john.language.exams.models.CategoryMasteryStats
import com.goodstadt.john.language.exams.models.DashboardUiState
import com.goodstadt.john.language.exams.screens.SectionQuizContainer
import com.goodstadt.john.language.exams.viewmodels.VocabDashboardViewModel
import com.goodstadt.john.language.exams.viewmodels.VocabSectionQuizViewModel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabDashboardScreen(
    viewModel: VocabDashboardViewModel = hiltViewModel(),
    onNavigateToQuiz: (String?) -> Unit // Pass category title, or null for "Mixed Review"
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentQuizTitle by viewModel.currentQuizTitle.collectAsState()
    val quizSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val showSmartReview by viewModel.showSmartReviewSheet.collectAsState()
    val smartReviewSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (uiState.isLoading) {
        Box(
            Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
    } else if (uiState.isColdStart) {
        // STATE A: Cold Start
        VocabQuizOnboardingView(onStartSuggestion = onNavigateToQuiz)
    } else {
        // STATE B: Active Dashboard
        VocabQuizActiveView(
            onStartReview = {
                viewModel.openSmartReview()
            },
            state = uiState,
            onCategoryClick = { title -> viewModel.openQuizForCategory(title) },
            onDebugClick = { viewModel.debugResetVocabProgress() }
        )

        if (currentQuizTitle != null) {
            var isQuizDirty by remember { mutableStateOf(false) }
            ModalBottomSheet(
                onDismissRequest = {
                    viewModel.closeQuizSheet(isDirty = isQuizDirty)
                },
                sheetState = quizSheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                // Wrapper to initialize the specific quiz
                // We reuse the exact same container from CategoryTabScreen
                SectionQuizContainer(categoryTitle = currentQuizTitle!!,  // ✅ Update local state when user answers inside
                    onInteraction = { dirty -> isQuizDirty = dirty })
            }
        }
        if (showSmartReview) {
            var isSessionDirty by remember { mutableStateOf(false) }
            ModalBottomSheet(
                onDismissRequest = {
                    viewModel.closeSmartReview(isDirty = isSessionDirty)
                },
                sheetState = smartReviewSheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                // Call the container we created in Step 2
                SmartReviewContainer(
                    onInteraction = { dirty ->
                        isSessionDirty = dirty
                    }
                )
            }
        }


    }
}

@Composable
fun VocabQuizActiveView(
    state: DashboardUiState,
    onStartReview: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onDebugClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Due Today Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Outer Column to stack the top section and the bottom list
            Column(modifier = Modifier.padding(20.dp)) {

                // Top Section: Number + "Review Now" Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val label = if (state.wordsDueCount == 1) "word" else "words"
                        val yet = if (state.wordsDueCount == 0) "yet." else ""
                        Text(
                            "${state.wordsDueCount}",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "$label due for review $yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Button(
                        onClick = onStartReview,
                        enabled = state.wordsDueCount != 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            // Optional: You can also explicitly set disabled colors if you want
                            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                    ) {
                        Text("Review Now")
                    }
                }

                // Bottom Section: The sorted list of words
                Spacer(modifier = Modifier.height(12.dp)) // Add some breathing room
                if (state.wordsDueCount != 0) {
                    Text(
                        text = "${state.wordsDueList
                            .sortedBy { it.length }
                            .joinToString(", ").take(55)}...",
                        // We can now remove .take(60) if you want to show more!
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }else{
                    Text(
                        text = "Tap on a section below to start quizzing yourself",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

            }
        }

        // 2. Breakdown Title + Info Button
        var showScoringInfo by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Progress",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { showScoringInfo = true }) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "How scoring works",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (showScoringInfo) {
            ScoringInfoBottomSheet(onDismiss = { showScoringInfo = false })
        }

        // 3. Category List
        state.categoryStats.forEach { stat ->
            CategoryMasteryRow(stat, onClick = {
                onCategoryClick(stat.categoryTitle)
            })
        }
//        if (BuildConfig.DEBUG) {
        if (false) {
            Column {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            Timber.w("debugResetVocabProgress")
                            // viewModel.debugResetVocabProgress()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Clear All Data (D)")
                    }
                }
            }

        }
    }
}

@Composable
fun CategoryMasteryRow(
    stat: CategoryMasteryStats, onClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() } // ✅ Make clickable
            .padding(vertical = 4.dp) // Add padding for touch target
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stat.categoryTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (stat.masteredCount > 0) {
                Text(
                    "${stat.masteredCount}/${stat.totalWords} Mastered",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50)
                )
            }
        }

        // Stacked Progress Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Mastered (Green)
            if (stat.masteredFraction > 0) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .weight(stat.masteredFraction)
                        .background(Color(0xFF4CAF50))
                )
            }
            // Learning (Orange)
            if (stat.learningFraction > 0) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .weight(stat.learningFraction)
                        .background(Color(0xFFFF9800))
                )
            }
            // Remaining (Transparent/Grey)
            val remaining = 1f - (stat.masteredFraction + stat.learningFraction)
            if (remaining > 0) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .weight(remaining)
                )
            }
        }
    }
}

@Composable
fun VocabQuizOnboardingView(onStartSuggestion: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Psychology,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "Master Vocabulary",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "We track how well you know every word. Take quizzes to build your mastery level.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Spacer(Modifier.height(24.dp))

        Text("Try a topic to start:", style = MaterialTheme.typography.labelLarge)

        Spacer(Modifier.height(12.dp))

        // Suggestions
        SuggestionButton("Personal Information") { onStartSuggestion("Personal Information") }
        SuggestionButton("Education") { onStartSuggestion("Education") }
    }
}

@Composable
fun SuggestionButton(title: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(title)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoringInfoBottomSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "How Scoring Works",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // How Each Answer Is Marked
            Text(
                "How Each Answer Is Marked",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            ScoringRow(emoji = "\u2B50", title = "Flawless", description = "You picked the right answer on your first try.")
            ScoringRow(emoji = "\uD83D\uDCA1", title = "Assisted", description = "You got it right, but used the hint button.")
            ScoringRow(emoji = "\uD83D\uDD04", title = "Stumbled", description = "You picked a wrong answer first, then got it right.")
            ScoringRow(emoji = "\u274C", title = "Failed", description = "You gave up or skipped the question.")

            HorizontalDivider()

            // Your Progress Levels
            Text(
                "Your Progress Levels",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            LevelRow(emoji = "\uD83C\uDD95", title = "New", description = "You haven't been quizzed on this word yet.", schedule = "Appears in your next quiz")
            LevelRow(emoji = "\uD83D\uDD34", title = "Struggling", description = "You couldn't get this one right \u2014 don't worry, it happens!", schedule = "Comes back in 10 minutes")
            LevelRow(emoji = "\uD83D\uDFE0", title = "Learning", description = "You got there in the end, but needed a few tries.", schedule = "Comes back in 6 hours")
            LevelRow(emoji = "\uD83D\uDFE1", title = "Review", description = "You got it right \u2014 nice work! Now let's make sure it sticks.", schedule = "Comes back tomorrow morning")
            LevelRow(emoji = "\uD83D\uDFE2", title = "Mastered", description = "You got it right 3 times in a row \u2014 you really know this one!", schedule = "Done! Won't come back unless you reset")

            HorizontalDivider()

            // Tip
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Text("\uD83D\uDCA1 ", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Don't cram! If a word isn't due yet, answering it again won't advance your progress. Spacing out your practice helps you remember for longer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ScoringRow(emoji: String, title: String, description: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LevelRow(emoji: String, title: String, description: String, schedule: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(schedule, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SmartReviewContainer(
    viewModel: VocabSectionQuizViewModel = hiltViewModel(),
    onInteraction: (Boolean) -> Unit
) {
    // 1. Trigger the specific logic for Smart Review
    LaunchedEffect(Unit) {
        viewModel.loadSmartReviewQuiz()
    }

    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    LaunchedEffect(isDirty) {
        onInteraction(isDirty)
    }

    // 2. Render the Quiz Screen
    // We use a Box with fixed height to ensure the BottomSheet behaves correctly
    Box(modifier = Modifier.fillMaxHeight(0.95f)) {
        VocabQuizScreen(
            viewModel = viewModel,
            autoLoad = false // Important: Don't load default B1/A1 quiz
        )
    }
}