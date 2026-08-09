package com.goodstadt.john.language.exams.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.goodstadt.john.language.exams.packages.rateLlmit.LimitRow
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.utils.AnalyticsHelper
import com.goodstadt.john.language.exams.viewmodels.RateLimitSheetViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
//import com.johngoodstadt.memorize.language.storage.firebase.fb
//import com.johngoodstadt.memorize.language.storage.firebase.fsUpdateStatsPropertyCount
//import com.johngoodstadt.memorize.language.storage.firebase.fsUpdateUserPropertyCount
//import com.johngoodstadt.memorize.language.ui.theme.orangeLight
//import com.johngoodstadt.memorize.language.utils.RateLimiterManager
//import com.johngoodstadt.memorize.language.utils.StatsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateLimitDailyPaywallBottomSheet (
    viewModel: RateLimitSheetViewModel = hiltViewModel(),
    onBuyPremiumButtonPressed: () -> Unit,
    onCloseSheet: () -> Unit
) {

    val context = LocalContext.current
  //  val rateLimiter = RateLimiterManager.getInstance()
    val uiState by viewModel.uiState.collectAsState()

//    val limitMessage = "Call limits exceeded for the day. Please wait till tomorrow for your next hearing."
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true) // Prevent half-open states
    val priceText by viewModel.priceFlow.collectAsState()

    LaunchedEffect(true) {
        viewModel.incStatForDaily()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
// 2. Use DisposableEffect to manage the observer
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // You can react to specific events here if needed
            if (event == Lifecycle.Event.ON_STOP) { }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            // 3. THIS IS THE KEY:
            // This runs the moment the BottomSheet is removed from the UI.
            // We call the cleanup logic directly in the ViewModel.
            viewModel.flushStats()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ModalBottomSheet in Material 3
    @OptIn(ExperimentalMaterial3Api::class) //
    ModalBottomSheet(
            onDismissRequest = {
                scope.launch {
                    sheetState.hide() // Animate sliding close
                    onCloseSheet() // Remove after animation
                }
            },
            sheetState = sheetState
            // You can customize sheetState, dragHandle, shape, etc.
    ) {
        // Content of the sheet
        Column (
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp), // Safe area
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Icon Header
            Icon(
                imageVector = Icons.Default.LockClock,
                contentDescription = null,
                tint = Color(0xFFFF9800), // Orange Warning
                modifier = Modifier.size(64.dp)
            )

            // 2. Title
            Text(
                text = "Daily Limit Reached",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )


            // 3. Explanation Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("To keep the app free, we limit AI usage:", style = MaterialTheme.typography.labelLarge)

                    LimitRow("Hourly Limit:", "${uiState.hourlyLimit} interactions per hour")
                    LimitRow("Daily Limit:", "${uiState.dailyLimit} interactions per day")

                    Text(
                        text = "Limits exceeded for the day.\nPlease wait till tomorrow for your next hearing.",
                        fontSize = 16.sp,
                        color = orangeLight,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .fillMaxWidth()
                    )

                    Text(
                        text = "All previously heard words are still playable.",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                }
            }


            Spacer(modifier = Modifier.height(8.dp))

            // 5. BUY BUTTON
            Button(
                onClick = {
                    scope.launch {
                        AnalyticsHelper.logPaywallResponse(context, "accepted", "limit_daily")
                        sheetState.hide()
                        viewModel.incGoUnlimited()
                        onBuyPremiumButtonPressed()
                        onCloseSheet() // Remove after animation
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.WorkspacePremium, null)
                Spacer(Modifier.width(8.dp))
                Text("Go Unlimited - ${priceText}", fontSize = 18.sp)
            }

            // 6. NOT NOW BUTTON
            TextButton(
                onClick = {
                    scope.launch {
                        AnalyticsHelper.logPaywallResponse(context, "rejected", "limit_daily")
                        viewModel.incWaitForReset()
                        sheetState.hide()
                        onCloseSheet() // Remove after animation
                    }
                }
            ) {
                Text("Wait for reset", color = Color.Gray)
            }
        }
    }
}
