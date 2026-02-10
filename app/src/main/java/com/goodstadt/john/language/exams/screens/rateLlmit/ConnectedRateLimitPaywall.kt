package com.goodstadt.john.language.exams.screens.rateLlmit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.viewmodels.RateLimitSheetViewModel

@Composable
fun ConnectedRateLimitPaywall(
    limitType: String, // "daily" or "hourly"
    onBuyPremium: () -> Unit,
    onNotNow: () -> Unit,
    onDismiss: () -> Unit,
    // ✅ Inject the specific ViewModel for this sheet
    viewModel: RateLimitSheetViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // ✅ LOGGING SIDE EFFECT
    // Runs once when the sheet appears to log your internal stats
    LaunchedEffect(limitType) {
        if (limitType == "daily") {
            viewModel.incStatForDaily()
        } else {
            viewModel.incStatForHourly()
        }
    }

    // Calculate time string
    val timeStr = viewModel.getFormattedTimeLeft(isDaily = (limitType == "daily"))
    val finalTimeStr = if (limitType == "daily") "Tomorrow ($timeStr)" else timeStr

    // Render the UI with data from ViewModel
    RateLimitPaywall(
        limitType = limitType,
        hourlyLimit = uiState.hourlyLimit, // ✅ From VM
        dailyLimit = uiState.dailyLimit,   // ✅ From VM
        timeLeftString = finalTimeStr,
        onBuyPremium = onBuyPremium,
        onNotNow = onNotNow,
        onDismiss = onDismiss
    )
}