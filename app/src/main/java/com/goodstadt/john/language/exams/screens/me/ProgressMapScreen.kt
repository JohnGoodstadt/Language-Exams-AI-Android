package com.goodstadt.john.language.exams.screens.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.goodstadt.john.language.exams.navigation.MeScreen
import com.goodstadt.john.language.exams.viewmodels.ProgressMapItem
import com.goodstadt.john.language.exams.viewmodels.ProgressMapViewModel
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import kotlin.math.roundToInt

// A placeholder for the gradient progress bar
//@Composable
//fun GradientProgressBar(total: Int, completed: Int, showText: Boolean) {
//    // TODO: Implement this custom progress bar
//    Text("$completed / $total words learned", style = MaterialTheme.typography.bodyMedium)
//}

// Color definitions, translated from Swift
private val completionColors = listOf(
    Color(0xFF601616), // ~5%  (reds)
    Color(0xFF5B1414), // ~15%
    Color(0xFF4A0F0F), // ~25%
    Color(0xFF3F0B0B), // ~35%
    Color(0xFF292C2F), // ~45% (grey/black)
    Color(0xFF092208), // ~55% (greens)
    Color(0xFF0C2C0C), // ~65%
    Color(0xFF144112), // ~75%
    Color(0xFF184915), // ~85%
    Color(0xFF215E1C), // ~95%
    Color(0xFF2B7826)  // 100%
)

private fun getColorForCompletion(item: ProgressMapItem): Color {
    if (item.totalWords == 0) return completionColors[0]
    if (item.completedWords == 0) return Color(0xFF781D1B)
    if (item.completedWords >= item.totalWords) return completionColors[10]

    val percentage = (item.completedWords.toDouble() / item.totalWords.toDouble()) * 100.0
    val bucketIndex = (percentage / 10.0).toInt().coerceIn(0, 9)
    return completionColors[bucketIndex]
}

// Opt-in for the experimental FlowRow
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProgressMapScreen(
    onTileTapped: (categoryTitle: String) -> Unit,
    viewModel: ProgressMapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // --- THIS IS THE FIX ---
    // 2. The LaunchedEffect now uses the current route as its key.
    //    It will re-run whenever the 'activeRoute' changes to "progress".
    LaunchedEffect(Unit) {
        viewModel.loadProgressData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Section
        Column {
            Text("Progress", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                "Track your progress. Red tiles need work, green ones are nearly done. Tap to learn more.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            GradientProgressBar(
                total = uiState.totalWords,
                completed = uiState.completedWords,
                showText = true
            )
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            // Flow Layout Section
            // Note: We use a standard Column with verticalScroll for the overall screen
            // and FlowRow inside it. This is simpler than GeometryReader + ScrollView.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val sortedItems = remember(uiState.progressItems) {
                        uiState.progressItems.sortedByDescending { it.totalWords }
                    }
                    val maxSize = sortedItems.firstOrNull()?.totalWords ?: 1

                    sortedItems.forEach { item ->
                        ProgressTile(
                            item = item,
                            maxSize = maxSize,
                            onClick = {
                                onTileTapped(item.title)
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun ProgressTile(
    item: ProgressMapItem,
    maxSize: Int,
    onClick: () -> Unit
) {
    // Calculate size dynamically
    val ratio = if (maxSize > 0) item.totalWords.toDouble() / maxSize.toDouble() else 0.0
    val width = (80 + (80 * ratio)).dp
    val height = (60 + (60 * ratio)).dp

    Card(
        modifier = Modifier
            .size(width = width, height = height)
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(getColorForCompletion(item))
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            val progressText = if (item.completedWords == 0) {
                "${item.totalWords} to go"
            } else {
                "${item.completedWords} of ${item.totalWords}"
            }
            Text(
                text = progressText,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}


@Composable
fun GradientProgressBar(
    total: Int,
    completed: Int,
    showText: Boolean,
    modifier: Modifier = Modifier
) {
    // Calculate progress as a value between 0.0 and 1.0, ensuring it's within bounds
    val progress = if (total == 0) 0f else (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    // Animate the progress value for a smooth visual effect
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(), // Use a spring animation like the SwiftUI version
        label = "progressAnimation"
    )

    // The main container for the progress bar, equivalent to the SwiftUI GeometryReader + ZStack
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .clip(RoundedCornerShape(10.dp)) // Clip the entire container for rounded corners
    ) {
        // 1. Background bar (the "total" part)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.8f))
        )

        // 2. Progress bar with gradient
        val gradientBrush = Brush.horizontalGradient(
            colors = listOf(Color.Red, Color.Yellow, Color.Green)
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = animatedProgress) // Width is based on the animated progress
                .background(brush = gradientBrush)
        )

        // 3. Text labels, drawn on top of the bars
        if (showText) {
            // Completed Text: Aligned to the end of the *progress* bar
            // We use a Box with the same fractional width to position it correctly
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = animatedProgress)
                    .padding(end = 5.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                // Only show if there's enough space to be readable
                if (animatedProgress > 0.1f) {
                    Text(
                        text = "$completed",
                        color = Color.Black,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Total Text: Aligned to the far right of the *entire* bar
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 5.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "$total",
                    color = Color.Black,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}