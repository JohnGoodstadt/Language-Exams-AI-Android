package com.goodstadt.john.language.exams.screens.diagnostic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.goodstadt.john.language.exams.ui.theme.orangeLight

@Composable
fun DiagnosticResultView(score: Int, onDismiss: () -> Unit) {
    val readiness = (score * 5) // 5% per correct answer

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Assessment Complete", style = MaterialTheme.typography.headlineMedium)

        // The Progress Ring
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp).padding(24.dp)) {
            CircularProgressIndicator(
                progress = readiness / 100f,
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 12.dp,
                color = if (readiness > 40) Color.Green else orangeLight
            )
            Text("$readiness%", style = MaterialTheme.typography.headlineLarge)
        }

        Text(
            text = "You are $readiness% ready for the B1 Exam.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.height(24.dp))

        // THE IAP HOOK
        Card(colors = CardDefaults.cardColors(containerColor = orangeLight)) {
            Column(Modifier.padding(16.dp)) {
                Text("Target your weak spots!", fontWeight = FontWeight.Bold)
                Text("You missed ${10 - score} critical exam traps. Unlock the full 3,000 word bank to guarantee a pass.")
                Button(onClick = { /* Trigger IAP */ }, modifier = Modifier.fillMaxWidth()) {
                    Text("Unlock Full Mastery")
                }
            }
        }

        TextButton(onClick = onDismiss) {
            Text("Go to Dashboard")
        }
    }
}