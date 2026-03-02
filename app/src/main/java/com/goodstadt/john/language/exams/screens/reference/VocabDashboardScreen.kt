package com.goodstadt.john.language.exams.screens.reference

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.models.CategoryMasteryStats
import com.goodstadt.john.language.exams.models.DashboardUiState
import com.goodstadt.john.language.exams.screens.SectionQuizContainer
import com.goodstadt.john.language.exams.viewmodels.VocabDashboardViewModel
import com.goodstadt.john.language.exams.viewmodels.VocabQuizViewModel
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
            ModalBottomSheet(
                onDismissRequest = {
                    viewModel.closeSmartReview()
                },
                sheetState = smartReviewSheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                // Call the container we created in Step 2
                SmartReviewContainer()
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
            Row(
                modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val label = if (state.wordsDueCount == 1) "word" else "words"
                    Text(
                        "${state.wordsDueCount}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "$label due for review",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        state.wordsDueList.joinToString(",").take(30),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Normal,
                            // Explicitly set a smaller size if bodySmall is still too big
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Button(
                    onClick = onStartReview,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Review Now")
                }
//                if (BuildConfig.DEBUG) {
                if (false) {
                    Button(
                        onClick = {
                            onDebugClick()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Reset (D)")
                    }
                }
            }
        }

        // 2. Breakdown Title
        Text(
            "Progress",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

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

@Composable
fun SmartReviewContainer(
    viewModel: VocabQuizViewModel = hiltViewModel()
) {
    // 1. Trigger the specific logic for Smart Review
    LaunchedEffect(Unit) {
        viewModel.loadSmartReviewQuiz()
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