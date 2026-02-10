package com.goodstadt.john.language.exams.screens.rateLlmit

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.utils.AnalyticsHelper
import com.goodstadt.john.language.exams.viewmodels.RateLimitSheetViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateLimitPaywall(
    viewModel: RateLimitSheetViewModel = hiltViewModel(),
    limitType: String, // "daily" or "hourly"
    hourlyLimit: Int,
    dailyLimit: Int,
    timeLeftString: String? = null, // Optional "Wait 10 mins" string
    onBuyPremium: () -> Unit,
    onNotNow: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(true) {
        viewModel.incStatForDaily()
    }

    ModalBottomSheet(
        onDismissRequest = {
            AnalyticsHelper.logPaywallResponse(context, "rejected", "limit_$limitType")
            onDismiss()
        },
        sheetState = sheetState
    ) {
        Column(
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

                    LimitRow("Hourly Limit:", "$hourlyLimit plays")
                    LimitRow("Daily Limit:", "$dailyLimit plays")

                    if (timeLeftString != null) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "Next unlock in: $timeLeftString",
                            color = Color(0xFFFF9800), // Orange
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 4. Value Proposition
            Text(
                text = "Unlock UNLIMITED usage for all exams (A1-B2) forever.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 5. BUY BUTTON
            Button(
                onClick = {
                    scope.launch {
                        AnalyticsHelper.logPaywallResponse(context, "accepted", "limit_$limitType")
                        sheetState.hide()
                        onBuyPremium()
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.WorkspacePremium, null)
                Spacer(Modifier.width(8.dp))
                Text("Unlock Premium - $1.99", fontSize = 18.sp)
            }

            // 6. NOT NOW BUTTON
            TextButton(
                onClick = {
                    scope.launch {
                        AnalyticsHelper.logPaywallResponse(context, "rejected", "limit_$limitType")
                        sheetState.hide()
                        onNotNow()
                        onDismiss()
                    }
                }
            ) {
                Text("Wait for reset", color = Color.Gray)
            }
        }
    }
}

@Composable
fun LimitRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}