package com.goodstadt.john.language.exams.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.screens.rateLlmit.LimitRow
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.utils.AnalyticsHelper
import com.goodstadt.john.language.exams.utils.formatTimeInterval
import com.goodstadt.john.language.exams.viewmodels.RateLimitSheetViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateLimitHourlyPaywallBottomSheet(
    viewModel: RateLimitSheetViewModel = hiltViewModel(),
    onBuyPremiumButtonPressed: () -> Unit,
    onCloseSheet: () -> Unit
) {

    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var limitMessage by remember { mutableStateOf("Call limits exceeded for this hour. Please wait till later for your next hearing.") }

    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true) // Prevent half-open states

    LaunchedEffect(true) {
      //  limitMessage = "So far -- Hourly calls:${currentHourlyCount}, Daily calls:$currentDailyCount "
        limitMessage = "Limits exceeded for this hour. Please wait for your next hearing."
        viewModel.currentHourlyTimeLeftToWait().let { hours ->
            val TIME = hours?.let { formatTimeInterval(it.toDouble()) }
            limitMessage = "$TIME"//"Call limits exceeded for this hour. Please wait $TIME for your next hearing."

            viewModel.incStatForHourly()

        }

    }

    // ModalBottomSheet in Material 3
    @OptIn(ExperimentalMaterial3Api::class) //
    ModalBottomSheet(
            onDismissRequest = {
                coroutineScope.launch {
                    sheetState.hide() // Animate sliding close
                    onCloseSheet() // Remove after animation
                }
            },
            sheetState = sheetState
            // You can customize sheetState, dragHandle, shape, etc.
    ) {
        // Content of the sheet
        Column  (
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
                text = "You're on a roll! 🚀",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text("You've used all your free AI credits for now.", style = MaterialTheme.typography.labelLarge)

            LimitRow("Hourly Credits:", "${uiState.hourlyLimit} interactions per hour")
            LimitRow("Daily Credits:", "${uiState.dailyLimit} interactions per day")


            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = "Credits refill in: $limitMessage",
                color = Color(0xFFFF9800), // Orange
                fontWeight = FontWeight.Bold
            )


            Spacer(modifier = Modifier.height(8.dp))
// 4. Value Proposition
            Text(
                text = "Learn as much as you want, whenever you want.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )





            // Close button or something if you want
            Button(
                    onClick = {
                        coroutineScope.launch {
                            sheetState.hide() // Slide out animation
                            AnalyticsHelper.logPaywallResponse(context,"accepted", "limit_hourly")
                            onBuyPremiumButtonPressed()
                            onCloseSheet()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth(),
//                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ){
                Icon(Icons.Default.WorkspacePremium, null)
                Spacer(Modifier.width(8.dp))
                Text("Go Unlimited - ${viewModel.formattedPrice()}", fontSize = 18.sp)

            }
            // 6. NOT NOW BUTTON
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        AnalyticsHelper.logPaywallResponse(context, "rejected", "limit_hourly")
                        sheetState.hide()
                        onCloseSheet()
                    }
                },
//                modifier = Modifier.fillMaxWidth(), // Match width of Buy button
                shape = RoundedCornerShape(12.dp),  // Match shape
                // ✅ SLIGHT BORDER: Use outline color with low opacity
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                // Gray text to show it's secondary
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("I'll wait for refill")
            }

            Text(
                text = "All previously heard words are still playable.",
                fontSize = 12.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
