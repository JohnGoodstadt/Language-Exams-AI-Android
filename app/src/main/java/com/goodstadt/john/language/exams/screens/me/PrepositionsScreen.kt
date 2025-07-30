package com.goodstadt.john.language.exams.screens.me

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.goodstadt.john.language.exams.viewmodels.PrepositionsUiState
import com.goodstadt.john.language.exams.viewmodels.PrepositionsViewModel

@Composable
fun PrepositionsScreen(viewModel: PrepositionsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    // The when statement handles all the different states from the ViewModel
    when (val state = uiState) {
        is PrepositionsUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is PrepositionsUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: ${state.message}", color = Color.Red)
            }
        }
        is PrepositionsUiState.NotAvailable -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This feature is not available for the current language.")
            }
        }
        is PrepositionsUiState.Success -> {
            // We can reuse the SectionedVocabList composable from ConjugationsScreen!
            SectionedVocabList(
                    categories = state.categories,
                    playbackState = playbackState,
                    googleVoice = state.selectedVoiceName,
                    onRowTapped = { word, sentence ->
                        viewModel.playTrack(word, sentence)
                    }
            )
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {//reliable signal that the user is leaving the screen.
                viewModel.saveDataOnExit()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // This is called when the composable leaves the screen
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}