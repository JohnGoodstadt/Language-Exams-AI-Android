package com.goodstadt.john.language.exams.diagnostic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.Color
import com.goodstadt.john.language.exams.ui.theme.CardBG

@Composable
fun DiagnosticScreen(
    viewModel: DiagnosticViewModel = hiltViewModel(),
    onFinished: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    if (state.showResult) {
        DiagnosticResultView(score = state.score, onDismiss = onFinished)
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            // 1. Header with Skip
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Level Check", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { viewModel.skipTest(); onFinished() }) {
                    Text("Skip")
                }
            }

            // 2. Progress
            val progress = (state.currentQuestionIndex + 1).toFloat() / 10f
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                color = orangeLight
            )

            // 3. Question
            state.questions.getOrNull(state.currentQuestionIndex)?.let { question ->
                Text(
                    text = question.sentence,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(vertical = 24.dp)
                )

                // 4. Options
                question.words.forEach { option ->
                    Button(
                        onClick = { viewModel.onAnswerSelected(option.ok) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CardBG)
                    ) {
                        Text(option.word)
                    }
                }
            }
        }
    }
}