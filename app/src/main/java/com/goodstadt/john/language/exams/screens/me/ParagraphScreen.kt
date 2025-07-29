package com.goodstadt.john.language.exams.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.goodstadt.john.language.exams.BuildConfig
//import com.goodstadt.john.language.exams.managers.TokenOptionsDialog
import com.goodstadt.john.language.exams.managers.TokenTopUpOption

import com.goodstadt.john.language.exams.ui.theme.LanguageExamsAITheme // Replace with your actual theme
import com.goodstadt.john.language.exams.ui.theme.accentColor
import com.goodstadt.john.language.exams.ui.theme.buttonColor
import com.goodstadt.john.language.exams.viewmodels.ParagraphViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParagraphScreen(
        // We get the ViewModel instance, but we don't use it yet.
    viewModel: ParagraphViewModel = hiltViewModel()
) {
    // --- STEP 1: Collect the state from the ViewModel ---
    // The `by` keyword unwraps the State<ParagraphUiState> into a plain ParagraphUiState object.
    // This line creates the subscription. Whenever the ViewModel updates its state,
    // this `uiState` variable will change, triggering a recomposition.
//    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsState()
   // val tokenCount by viewModel.totalTokenCount.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current

    val showDialog by viewModel.showTokenDialog.collectAsState()
    val canWait by viewModel.canWait.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(600) // GPT model might not have been chosen yet
        viewModel.generateNewParagraph()
    }
    // Use DisposableEffect to tie logic to the composable's lifecycle.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // When the screen is paused (e.g., user navigates to another tab,
            // presses home, or a dialog appears over it), stop the audio.
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.stopPlayback()
            }
        }

        // Add the observer to the screen's lifecyclegoogle
        lifecycleOwner.lifecycle.addObserver(observer)

        // This is called when the composable is removed from the screen (disposed).
        // It's the perfect place to clean up the observer.
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(showDialog) {
        if (!showDialog && sheetState.isVisible) {
            sheetState.hide()
        }
    }

    if (showDialog) {
        ModalBottomSheet(
            onDismissRequest = { /* prevent user from dismissing */ },
            sheetState = sheetState
        ) {
            TokenOptionsBottomSheet(
                canWait = canWait,
                onOptionSelected = {
                    viewModel.onTokenTopUpSelected(it)
                    // DO NOT hide the sheet here — let ViewModel trigger hide via state
                },
                onResetClicked = {
                    viewModel.resetTokenBalanceForDebug()
                }
            )
        }
    }


    Column(
            // Fills the whole screen
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            // Centers all children horizontally
            horizontalAlignment = Alignment.CenterHorizontally,
            // Adds consistent spacing between elements
            verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (BuildConfig.DEBUG) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
//                Text(
//                    text = "Debug Controls",
//                    style = MaterialTheme.typography.bodySmall,
//                    fontWeight = FontWeight.Normal
//                )
//                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("${uiState.currentLlmModel?.title}")
                    Button(onClick = { viewModel.cycleLlmEngine() }) {
                        Text("Change Model")
                    }
                }
                val tokenBalance by viewModel.tokenBalance.collectAsState()
                val tokenLimit = viewModel.tokenLimit
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Tokens Left: $tokenBalance / $tokenLimit",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (tokenBalance < tokenLimit) MaterialTheme.colorScheme.error else Color.Unspecified

                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { viewModel.resetTokensUsed() }) {
                        Text("Reset Tokens")
                    }
                }

            }
        }
        // --- Top Section ---
        Text(
                text = "Listen to this Sentence",
                style = MaterialTheme.typography.titleLarge
        )

        Text(
                text = "It's made up of words from other pages. Listen carefully.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center // Ensures multi-line text is also centered
        )

        Button(onClick = {
             viewModel.generateNewParagraph()
        }, colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            contentColor = Color.White
        )) {
            Text("Generate")
        }

        Box(
                modifier = Modifier.height(24.dp), // Reserve space equal to the indicator's size
                contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = accentColor,
                        modifier = Modifier.size(24.dp), // Make it smaller
                        strokeWidth = 2.dp // Thinner stroke looks better when small
                )
            }
        }

        // --- Divider ---
        HorizontalDivider(modifier = Modifier.padding(top = 10.dp))


        // --- Middle Section ---
        AttributedHighlightedText(
                paragraph = uiState.generatedSentence,
                highlightedWords = uiState.highlightedWords,
                // You can also add modifiers here if needed, e.g.,
                modifier = Modifier.clickable { viewModel.speakSentence() }
        )

        Text(
                text = uiState.translation,
                style = MaterialTheme.typography.bodyMedium, // Smaller for the subtitle/translation
                color = MaterialTheme.colorScheme.onSurfaceVariant, // A more subtle color
                textAlign = TextAlign.Center
        )
        // Show an error message if one exists in the state
        uiState.error?.let {
            Text(
                    text = it,
//                    text = "Error: API Call failed. Please try again.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
            )
        }
        // This Spacer pushes the "Play" button towards the bottom
        Spacer(modifier = Modifier.weight(1f))

        // --- Bottom Section ---
        Button(onClick = { viewModel.speakSentence() }, colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            contentColor = Color.White
        )) { // Changed from empty lambda
            Text("Play")
        }

        // Adds some padding at the very bottom of the screen
        Spacer(modifier = Modifier.height(16.dp))
    }
}


// A @Preview function allows you to see your Composable in the design view of Android Studio.
@Preview(showBackground = true)
@Composable
fun ParagraphScreenPreview() {
    // You must wrap your preview in a Theme to see it correctly.
    // Replace YourAppTheme with the actual name of your app's theme.
    LanguageExamsAITheme {
        ParagraphScreen()
    }
}

@Composable
fun TokenOptionsBottomSheet(
    canWait: Boolean,
    onOptionSelected: (TokenTopUpOption) -> Unit,
    onResetClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text("You're out of tokens.", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))

        if (canWait) {
            Button(
                onClick = { onOptionSelected(TokenTopUpOption.FREE) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Wait 1 Hour for Free Top-Up")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = { onOptionSelected(TokenTopUpOption.BUY_099) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Buy More – £0.99")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { onOptionSelected(TokenTopUpOption.BUY_199) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Buy More – £1.99")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onResetClicked() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
        ) {
            Text("DEBUG: Reset Tokens")
        }
    }
}
