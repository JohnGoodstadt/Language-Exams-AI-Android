package com.goodstadt.john.language.exams.screens.reference

//package com.yourpackage.ui.reference.generic // Or your preferred package

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.goodstadt.john.language.exams.screens.RateLimitDailyReasonsBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyReasonsBottomSheet
import com.goodstadt.john.language.exams.screens.reference.shared.SectionedVocabList
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet

// ... other necessary imports

// 1. RENAMED: PrepositionsScreen -> ReferenceGenericScreen
// 2. MODIFIED: The ViewModel type is now our new generic one
@Composable
fun ReferenceGenericScreen(viewModel: ReferenceGenericViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isRateLimitingSheetVisible by viewModel.showRateLimitSheet.collectAsState()
    val isDailyRateLimitingSheetVisible by viewModel.showRateDailyLimitSheet.collectAsState()
    val isHourlyRateLimitingSheetVisible by viewModel.showRateHourlyLimitSheet.collectAsState()

    // 3. MODIFIED: The when statement now checks for ReferenceGenericUiState types
    when (val state = uiState) {
        is ReferenceGenericUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is ReferenceGenericUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: ${state.message}", color = Color.Red)
            }
        }
        is ReferenceGenericUiState.NotAvailable -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This feature is not available for the current language.")
            }
        }
        is ReferenceGenericUiState.Success -> {
            // The existing SectionedVocabList works perfectly, as it just needs a list of categories
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
//        RateLimitHourlyReasonsBottomSheet(onCloseSheet = { viewModel.hideHourlyRateLimitSheet() })
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