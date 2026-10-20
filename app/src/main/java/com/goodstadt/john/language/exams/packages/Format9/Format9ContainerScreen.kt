package com.goodstadt.john.language.exams.packages.Format9

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * STEP 1 placeholder for the Podcasts (fileFormat-9) reference screen. It only confirms that the new JSON
 * reads from the bundle into [Format9File]; the real script UI (speaker rows, tap-to-play, whole-script /
 * per-scene play-pause, etc.) arrives in step 2.
 */
@Composable
fun Format9ContainerScreen(
    documentId: String,
    viewModel: Format9ViewModel = hiltViewModel()
) {
    LaunchedEffect(documentId) { viewModel.load(documentId) }
    val file by viewModel.file.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        val currentFile = file
        val message = when {
            error != null -> "❌ Could not read Format9 '$documentId': $error"
            currentFile == null -> "Loading podcast '$documentId'…"
            else -> "✅ Format9 '${currentFile.title}' — " +
                "${currentFile.scenes.size} scenes, " +
                "${currentFile.scenes.sumOf { it.lines.size }} lines, " +
                "${currentFile.speakers.size} speakers\n(Screen detail coming in step 2)"
        }
        Text(text = message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
    }
}
