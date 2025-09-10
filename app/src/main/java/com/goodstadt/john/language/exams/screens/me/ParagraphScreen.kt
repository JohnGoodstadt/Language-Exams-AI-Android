package com.goodstadt.john.language.exams.screens

import android.content.Context
import android.util.Log
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.data.CreditSystemConfig.FREE_TIER_CREDITS

import com.goodstadt.john.language.exams.ui.theme.LanguageExamsAITheme // Replace with your actual theme
import com.goodstadt.john.language.exams.ui.theme.accentColor
import com.goodstadt.john.language.exams.ui.theme.buttonColor
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.viewmodels.MainViewModel
import com.goodstadt.john.language.exams.viewmodels.ParagraphViewModel
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParagraphScreen(
        // We get the ViewModel instance, but we don't use it yet.
    viewModel: ParagraphViewModel = hiltViewModel()//,
  //  mainViewModel: MainViewModel = hiltViewModel()
) {

//    val globalUiState by mainViewModel.uiState.collectAsState()
//    val isPremium = globalUiState.isPremiumUser

    // --- STEP 1: Collect the state from the ViewModel ---
    // The `by` keyword unwraps the State<ParagraphUiState> into a plain ParagraphUiState object.
    // This line creates the subscription. Whenever the ViewModel updates its state,
    // this `uiState` variable will change, triggering a recomposition.
//    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsState()
//    val sheetState = rememberModalBottomSheetState()
    val sheetStateIAP = rememberModalBottomSheetState()
//    val tokenCount by viewModel.totalTokenCount.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    val isPurchased by viewModel.isPurchased.collectAsState(initial = false)
    val productDetails by viewModel.productDetails.collectAsState(initial = null)
    val context = LocalContext.current

    val PREFS_NAME = "AppPrefs"
    val PREFS_KEY = "hasGenerated"



//    LaunchedEffect(Unit) {
//        viewModel.loadCredits()
//    }
    LaunchedEffect(Unit) {

        delay(600) // GPT model might not have been initialised yet
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PREFS_KEY, false)){ //only auto generate once
            viewModel.generateNewParagraph()
            prefs.edit().putBoolean(PREFS_KEY, true).apply()
        }

        Timber.d("waitTimeText:$viewModel.getFormattedWaitTimeLeft()")
    }
    // Use DisposableEffect to tie logic to the composable's lifecycle.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // When the screen is paused (e.g., user navigates to another tab,
            // presses home, or a dialog appears over it), stop the audio.
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.stopPlayback()
//                viewModel.saveDataOnExit()
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
        if (false && BuildConfig.DEBUG) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Calls Used: ${uiState.userCredits.current} / ${uiState.userCredits.total}", // TOKEN_LIMIT needs to be exposed from VM
                        style = MaterialTheme.typography.labelMedium,
                        color = if (uiState.userCredits.current >= viewModel.getTokenLimit()) MaterialTheme.colorScheme.error else Color.Unspecified
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Button(onClick = { viewModel.resetTokensUsed() }) {
                        Text("Reset Tokens")
                    }
                }

            }
        }

        Text(
                text = "Listen to this Sentence",
                style = MaterialTheme.typography.titleLarge
        )

        Text(
                text = "It's made up of words from your vocabulary list. Listen carefully.",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center // Ensures multi-line text is also centered
        )

        Button(onClick = {
             viewModel.generateNewParagraph()
        },
            enabled = !uiState.isLoading,
            colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            contentColor = Color.White
        )) {
            Text("Generate")
        }

        if (DEBUG) {
            CacheProgressBar(
                cachedCount = uiState.userCredits.current,
                totalCount = FREE_TIER_CREDITS,
                displayLowNumber = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 64.dp, vertical = 8.dp)
            )
        }

        Box(
                modifier = Modifier.height(24.dp), // Reserve space equal to the indicator's size
                contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    color = accentColor,
                        modifier = Modifier.size(12.dp), // Make it smaller
                        strokeWidth = 2.dp // Thinner stroke looks better when small
                )
            }
        }

        // --- Divider ---
        HorizontalDivider(modifier = Modifier.padding(top = 2.dp))


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
       // Spacer(modifier = Modifier.weight(1f))
        if (!isPurchased) {
            if (uiState.waitingForCredits) {
                var now by remember { mutableStateOf(Date()) }

                LaunchedEffect(uiState.waitingForCredits) { // runs once when waiting starts
                    while (true) { // keep looping while in wait mode
                        now = Date() // triggers recomposition immediately
                        val remaining = viewModel.secondsRemaining(now)

                        Timber.v("Compose tick: $remaining seconds remaining")

                        if (remaining == 0) {
                            Timber.v("Resetting credits automatically...")
                            viewModel.clearWaitPeriod()
                            break // stop this loop
                        }

                        delay(2000) // wait 2s before next tick
                    }
                }

                if (uiState.showIAPBottomSheet) {
                    ModalBottomSheet(
                        // 5. This callback is triggered when the user dismisses the sheet.
                        onDismissRequest = { viewModel.onBottomSheetDismissed() },
                        sheetState = sheetStateIAP
                    ) {
                        // 6. This is the content that appears INSIDE the sheet.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {

                            Text(
                                text = "Using AI has charges",
                                fontSize = 20.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // Sub-title
                            Text(
                                text = "Therefore we have to limit how many AI calls are made:",
                                fontSize = 16.sp,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            // Body
                            Text(
                                text = "1. Up to ${uiState.hourlyLimit} interactions per hour.",
                                fontSize = 14.sp,
                                textAlign = TextAlign.Start,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .fillMaxWidth()
                                    .align(Alignment.Start)
                            )
                            Text(
                                text = "2. Up to ${uiState.dailyLimit}  interactions per day.",
                                fontSize = 14.sp,
                                textAlign = TextAlign.Start,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .fillMaxWidth()
                                    .align(Alignment.Start)
                            )
                            Text(
                                text = "3. Delay after ${FREE_TIER_CREDITS} paragraphs calls.",
                                fontSize = 14.sp,
                                textAlign = TextAlign.Start,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .fillMaxWidth()
                                    .align(Alignment.Start)
                            )

                            Text(
                                text = "All previously heard words are still playable.",
                                fontSize = 14.sp,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                // This arrangement places equal space around each button, pushing them apart.
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                Button(onClick = {
                                    if (context is androidx.activity.ComponentActivity) {
                                        viewModel.buyPremiumButtonPressed(context)
                                        viewModel.onBottomSheetDismissed()
                                    }
                                })
                                {
                                    productDetails?.let { details ->
                                        details.oneTimePurchaseOfferDetails?.let { offerDetails ->
                                            Text("Go for it: ${offerDetails.formattedPrice}")
                                        }
                                    }
                                }

                                Button(onClick = {
                                    viewModel.onBottomSheetDismissed()
                                }) {
                                    Text("Maybe Later")
                                }
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
                Column {
                    Text(
                        text = "Calling an AI to write the paragraph, just for you, has a cost. So we have to limit it.\nYou can avoid all restrictions, with a contribution, by clicking here:",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .fillMaxWidth()
                    )

                    Icon(
                        imageVector = Icons.Filled.ShoppingCart,
                        contentDescription = "Shopping Cart",
                        tint = orangeLight,
                        modifier = Modifier.clickable { viewModel.buyButtonClicked() }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .fillMaxWidth()
                    )

                    Text(
                        text = "Delay: ${viewModel.formattedCountdown(now)}",
                        fontSize = 12.sp,
                        color = orangeLight,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .fillMaxWidth()
                    )
                    Text(
                        text = "Next refill at:${viewModel.getFormattedCreditRepositoryDate()}",
                        fontSize = 12.sp,
                        color = orangeLight,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .fillMaxWidth()
                    )
                }
            } else {
                if (DEBUG) {
                    Text("Credits left: ${uiState.userCredits.current} (D)")
                }
            }
        }


        Spacer(modifier = Modifier.weight(1f)) //push to bottom

        // --- Bottom Section ---
        Button(onClick = { viewModel.speakSentence() }, colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor,
            contentColor = Color.White
        )) { // Changed from empty lambda
            Text("Play")
        }


        Text(
            text = "AI can make mistakes. Beware of bizarre sentences",
            style = MaterialTheme.typography.bodySmall, // smaller font
            color = Color.Gray,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (DEBUG){
            if (uiState.lastUsedLLMModel.isNotEmpty()){
                Text("${uiState.lastUsedLLMModel} (D)", style = MaterialTheme.typography.labelSmall)
            }
        }
        // Adds some padding at the very bottom of the screen
        Spacer(modifier = Modifier.height(4.dp))

    } //:Column
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