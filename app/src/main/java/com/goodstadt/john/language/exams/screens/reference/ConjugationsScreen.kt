package com.goodstadt.john.language.exams.screens.reference

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.screens.RateLimitDailyReasonsBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyReasonsBottomSheet
import com.goodstadt.john.language.exams.screens.reference.shared.SectionedVocabList
import com.goodstadt.john.language.exams.viewmodels.ConjugationsUiState
import com.goodstadt.john.language.exams.viewmodels.ConjugationsViewModel
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet

//import com.goodstadt.john.language.exams.viewmodels.PlaybackState

@Composable
fun ConjugationsScreen(viewModel: ConjugationsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val selectedConjugation by viewModel.selectedConjugation.collectAsState()

    val isRateLimitingSheetVisible by viewModel.showRateLimitSheet.collectAsState()
    val isDailyRateLimitingSheetVisible by viewModel.showRateDailyLimitSheet.collectAsState()
    val isHourlyRateLimitingSheetVisible by viewModel.showRateHourlyLimitSheet.collectAsState()

    when (val state = uiState) {
        is ConjugationsUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ConjugationsUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: ${state.message}", color = Color.Red)
            }
        }
        is ConjugationsUiState.NotAvailable -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This feature is not available for the current language.")
            }
        }
        is ConjugationsUiState.Success -> {

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally) {

                HorizontalLevelPicker(
                    options = LanguageConfig.conjugationOptions,
                    selectedOption = selectedConjugation,
                    onOptionSelected = viewModel::onConjugationSelected
                )

                SectionedVocabList(
                    categories = state.categories,
                    playbackState = playbackState,
                    googleVoice = state.selectedVoiceName,
                    cachedAudioWordKeys = state.cachedAudioWordKeys,
                    onRowTapped = { word, sentence ->
                        viewModel.playTrack(word, sentence)
                    }
                )
            }
        }
    }
    if (isRateLimitingSheetVisible){
        RateLimitOKReasonsBottomSheet(onCloseSheet = { viewModel.hideRateOKLimitSheet() })
    }
    if (isDailyRateLimitingSheetVisible){
//        RateLimitDailyReasonsBottomSheet (onCloseSheet = { viewModel.hideDailyRateLimitSheet() })
        if (context is androidx.activity.ComponentActivity) {
            RateLimitDailyReasonsBottomSheet(
                onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) },
                onCloseSheet = { viewModel.hideDailyRateLimitSheet() }
            )
        }

    }
    if (isHourlyRateLimitingSheetVisible){
        if (context is androidx.activity.ComponentActivity) {
            RateLimitHourlyReasonsBottomSheet(
                onCloseSheet = { viewModel.hideHourlyRateLimitSheet() },
                onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) }
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

