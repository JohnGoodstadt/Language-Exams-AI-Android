package com.goodstadt.john.language.exams.packages.Translate

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.data.repository.TranslateLang

/**
 * A reusable Translate bottom sheet: German <-> English, with a swap button, a translate action near the
 * thumb, tap-to-speak for German output, and (where supported) a microphone button to dictate the source.
 * Self-contained so it can be shown from anywhere: `if (show) TranslateSheet(onDismiss = { show = false })`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateSheet(
    onDismiss: () -> Unit,
    viewModel: TranslateViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val speechAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }

    // System speech dialog -> populate the source box. Uses the recognizer app's own mic handling, so no
    // RECORD_AUDIO permission is needed here.
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) viewModel.onSourceChange(spoken)
        }
    }
    val launchSpeech = {
        val localeTag = if (viewModel.sourceLang == TranslateLang.GERMAN) "de-DE" else "en-US"
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak in ${viewModel.sourceLang.display}")
        }
        runCatching { speechLauncher.launch(intent) }
        Unit
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Translate",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Try out translating from German to English and vice versa.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Direction + swap
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = viewModel.sourceLang.display,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = { viewModel.swapDirection() }) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "Swap languages",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = viewModel.targetLang.display,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Source text + optional mic
            OutlinedTextField(
                value = viewModel.sourceText,
                onValueChange = { viewModel.onSourceChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
                label = { Text("Enter ${viewModel.sourceLang.display}") },
                trailingIcon = if (speechAvailable) {
                    {
                        IconButton(onClick = { launchSpeech() }) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Speak to translate",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else null
            )

            // Translate action (near the thumb)
            Button(
                onClick = { viewModel.translate() },
                enabled = !viewModel.isTranslating && viewModel.sourceText.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (viewModel.isTranslating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Translate to ${viewModel.targetLang.display}")
                }
            }

            viewModel.errorMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Target result. Tapping speaks it when it's German.
            Text(
                text = viewModel.targetLang.display,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = { viewModel.speakTarget() },
                enabled = viewModel.canSpeakTarget
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 72.dp)
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = viewModel.targetText.ifBlank { "Translation will appear here" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (viewModel.targetText.isBlank())
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (viewModel.canSpeakTarget) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Speak translation",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            if (viewModel.canSpeakTarget) {
                Text(
                    text = "Tap the German result to hear it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.size(4.dp))
        }
    }
}
