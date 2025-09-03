package com.goodstadt.john.language.exams.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.managers.RateLimiterManager
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.utils.formatTimeInterval
import com.goodstadt.john.language.exams.viewmodels.RateLimitSheetViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateLimitHourlyReasonsBottomSheet(
    viewModel: RateLimitSheetViewModel = hiltViewModel(),
    onCloseSheet: () -> Unit
) {

    val rateLimiter = RateLimiterManager.getInstance()

    var limitMessage by remember { mutableStateOf("Call limits exceeded for this hour. Please wait till later for your next hearing.") }

    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true) // Prevent half-open states

    LaunchedEffect(true) {
      //  limitMessage = "So far -- Hourly calls:${currentHourlyCount}, Daily calls:$currentDailyCount "
        limitMessage = "Call limits exceeded for this hour. Please wait for your next hearing."
        rateLimiter.currentHourlyTimeLeftToWait?.let { hours ->
            val TIME = formatTimeInterval(hours.toDouble())
            limitMessage = "Call limits exceeded for this hour. Please wait $TIME for your next hearing."

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
        Column {
            // Title
            Text(
                    text = "Using AI has charges",
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
            )

            // Sub-title
            Text(
                    text = "Therefore we have to limit how many AI calls are made:",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Body
            Text(
                    text = "1. Up to ${rateLimiter.currentHourlyLimit} interactions per hour.",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Text(
                    text = "2. Up to ${rateLimiter.currentDailyLimit} interactions per day.",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Text(
                    text = "All previously heard words are still playable.",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // The limit message (in yellow, for example)
            Text(
                    text = limitMessage,
                    fontSize = 16.sp,
                    color = orangeLight,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Close button or something if you want
            Button(
                    onClick = {
                        coroutineScope.launch {
                            sheetState.hide() // Slide out animation
                            onCloseSheet() // Remove after animation
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
            ){
                Text("Lift All Restrictions", modifier = Modifier
                    .padding(horizontal = 64.dp, vertical = 4.dp))
            }
            Text(
                text = "All exam lists A1,A2,B1,B2 for all time",
                fontSize = 12.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth()
            )
        }
    }
}
