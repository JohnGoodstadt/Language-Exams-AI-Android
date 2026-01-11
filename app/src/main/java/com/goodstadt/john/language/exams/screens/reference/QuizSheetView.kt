package com.goodstadt.john.language.exams.screens.reference

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.models.TestMyselfSections
import com.goodstadt.john.language.exams.ui.theme.Orange
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.viewmodels.QuizSheetViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizSheetView(
    questions: List<TestMyselfSections>,
    title: String,
    onDismiss: () -> Unit,
    viewModel: QuizSheetViewModel = hiltViewModel()
) {
    // Initialize VM with data
    LaunchedEffect(questions) {
        viewModel.setup(questions, title)
    }

    val uiState by viewModel.uiState.collectAsState()

    if (uiState.questions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val currentQuestion = uiState.questions[uiState.currentIndex]

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(uiState.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
                actions = {
                    // Close Button
                    // (Optional if using BottomSheet drag handle)
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Progress Text
            Text(
                "Question ${uiState.currentIndex + 1} of ${uiState.questions.size}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // 2. Question Text (The Sentence)
            // For Homophone quiz, sentence is empty string in data, but logic might vary.
            // If empty, we show "Select the correct sentence".
            Text(
                text = if (currentQuestion.sentence.isEmpty()) currentQuestion.summary else currentQuestion.sentence,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 24.dp)
            )

            // 3. Options List
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                currentQuestion.words.forEach { option ->

                    // Determine State
                    val isSelected = uiState.userAnswers[uiState.currentIndex] == option
                    val isCorrect = option.ok

                    // Determine Colors
                    // If selected & correct -> Green
                    // If selected & wrong -> Red
                    // Otherwise -> Default Theme Colors
                    val radioColor = when {
                        isSelected && isCorrect -> Color.Green
                        isSelected && !isCorrect -> Color.Red
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    // Main Row Container
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.onAnswerSelected(option) } // Tap anywhere to select
                            .padding(vertical = 8.dp)
                    ) {
                        // Left Side: Speaker + Text
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f) // Take up all space except Radio Button
                        ) {
                            // Speaker Icon
                            // Using standard icon for portability, replace with painterResource(R.drawable.ic_speaker) if available
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Play Audio",
                                //tint = MaterialTheme.colorScheme.primary,
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable {
                                        viewModel.playAudio(option.word)
                                    }
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            // Option Text
                            Text(
                                text = option.word,
                                style = MaterialTheme.typography.bodyLarge,
                                color = orangeLight,
                                modifier = Modifier.fillMaxWidth() // Wraps if multi-line
                            )
                        }

                        // Right Side: Radio Button
                        RadioButton(
                            selected = isSelected,
                            onClick = { viewModel.onAnswerSelected(option) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = radioColor,
                                unselectedColor = if (isSelected && !isCorrect) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    // Optional: Thin divider between rows
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 4. Info Button (If explanation exists)
            if (currentQuestion.explain.isNotEmpty()) {
                IconButton(onClick = { viewModel.toggleInfo() }) {
                    Icon(Icons.Default.Info, contentDescription = "Info", tint = Orange)
                }
            }

            // 5. Navigation Buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.prevQuestion() },
                    enabled = uiState.currentIndex > 0
                ) {
                    Icon(Icons.Default.ArrowBack, null)
                }


                Row( horizontalArrangement = Arrangement.Center, // ✅ Correct
                    verticalAlignment = Alignment.CenterVertically // Optional: keeps text aligned
                ) {
                    Text("Correct: ${uiState.scores.size}", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(16.dp))
                    Text("Tries: ${uiState.totalTries}", color = if(uiState.totalTries <= uiState.scores.size) Color(0xFF4CAF50) else Color.Red)
                }

                IconButton(
                    onClick = { viewModel.nextQuestion() },
                    enabled = uiState.currentIndex < uiState.questions.size - 1
                ) {
                    Icon(Icons.Default.ArrowForward, null)
                }
            }

            // 6. Dot Indicators
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                repeat(uiState.questions.size) { index ->
                    val color = when {
                        index == uiState.currentIndex -> MaterialTheme.colorScheme.primary
                        uiState.scores[index] == true -> Color(0xFF4CAF50) // Green
                        else -> Color.LightGray
                    }
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }

        // Info Bottom Sheet
        if (uiState.showInfoSheet) {
            ModalBottomSheet(onDismissRequest = { viewModel.toggleInfo() }) {
                Column(modifier = Modifier.padding(24.dp)) {
                    //Text("Explanation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    //Spacer(Modifier.height(16.dp))
                    Text(currentQuestion.explain, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}