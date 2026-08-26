package com.goodstadt.john.language.exams.packages.Settings

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.config.PremiumOverride
import com.goodstadt.john.language.exams.managers.DebugPremiumOverride
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.Gender
import com.goodstadt.john.language.exams.models.ExamDetails
import com.goodstadt.john.language.exams.models.LanguageCodeDetails
import com.goodstadt.john.language.exams.packages.dailydictionary.DictionaryEntryBrowserScreen
import com.goodstadt.john.language.exams.packages.dailydictionary.DictionaryEntryBrowserViewModel
import com.goodstadt.john.language.exams.screens.RateLimitDailyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyPaywallBottomSheet
import com.goodstadt.john.language.exams.packages.me.PremiumUpgradeSheet
import com.goodstadt.john.language.exams.packages.me.SignInBottomSheet
import com.goodstadt.john.language.exams.screens.shared.HelpInfoSheet
import com.goodstadt.john.language.exams.screens.shared.speakerSelection.VoiceCategoryDropdownHeader
import com.goodstadt.john.language.exams.screens.shared.speakerSelection.VoiceSelectionRow
import com.goodstadt.john.language.exams.ui.theme.accentColor
import com.goodstadt.john.language.exams.ui.theme.buttonColor
import com.goodstadt.john.language.exams.utils.AnalyticsHelper
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel()
) {


    val uiState by viewModel.uiState.collectAsState()
    val sheetContent by viewModel.sheetState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetStateIAP = rememberModalBottomSheetState()
    val isPremiumUser = false//by viewModel.isPremium.collectAsState()

    // val premiumProduct by viewModel.premiumProduct.collectAsState()
    //val isPremiumUser by viewModel.isPremiumUser.collectAsState()
    val isPurchased by viewModel.isPurchased.collectAsState(initial = false)
    val productDetails by viewModel.productDetails.collectAsState(initial = null)
    val billingError by viewModel.billingError.collectAsState(initial = null)
    val context = LocalContext.current

    val showHelpSheet by viewModel.showHelpSheet.collectAsState()
    val helpSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val showCelebration by viewModel.showCelebrationSheet.collectAsStateWithLifecycle()

    var showSignInSheet by remember { mutableStateOf(false) }
    val isDailyRateLimitingSheetVisible by viewModel.showRateDailyLimitSheet.collectAsState()
    val isHourlyRateLimitingSheetVisible by viewModel.showRateHourlyLimitSheet.collectAsState()


    var showDebugSheet by remember { mutableStateOf(false) }
    val vm: DictionaryEntryBrowserViewModel = viewModel()

//    var showTryOutVoicesSheet by remember { mutableStateOf(false) }

//test
    LaunchedEffect(Unit) {
        val isAvailable = viewModel.isGooglePlayServicesAvailable(context)
        if (!isAvailable) {
            // Handle Google Play Services not available
            Timber.e("Google Play Services is NOT available")
        }
    }

    LaunchedEffect(sheetContent, sheetState.isVisible) {
        if (sheetContent != SheetContent.Hidden) {
            sheetState.show()
        } else {
            if (sheetState.isVisible) {
                sheetState.hide()
            }
        }
    }

    // Snackbar host + collector for SettingsUiEvent (e.g. the offline "No internet connection" message).
    // Previously nothing collected viewModel.uiEvent, so every emit() suspended with no collector and the
    // snackbar never showed.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is SettingsUiEvent.ShowSnackbar -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.message,
                        actionLabel = event.actionLabel,
                        // Give actionable snackbars (e.g. offline "Retry") longer so there's time to tap.
                        duration = if (event.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        // The only actionable snackbar is the offline "Retry" for the Upgrade to Exam
                        // Mastery (IAP) sheet - retry opening it (re-checks connectivity).
                        viewModel.onShowBottomSheetClicked()
                    }
                }
            }
        }
    }

    if (sheetContent != SheetContent.Hidden) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideBottomSheet() },
            sheetState = sheetState
        ) {
            // --- CHANGE 1: Adjust padding to add more space at the bottom ---
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 24.dp
                    ), // More bottom padding
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (sheetContent) {
                    SheetContent.ExamSelection -> {
                        // This part remains the same
                        Text(
                            "Choose an Exam Level",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            items(uiState.availableExams, key = { it.json }) { exam ->
                                ExamSelectionRow(
                                    exam = exam,
                                    isSelected = uiState.pendingSelectedExam?.json == exam.json,
                                    onClick = { viewModel.onPendingExamSelect(exam) }
                                )
                            }
                        }
                    }

                    SheetContent.SpeakerSelection -> {
                        // This part remains the same, as the centering logic is moved
                        // into the VoiceCategoryDropdownHeader composable itself.
                        var isFemaleExpanded by remember { mutableStateOf(false) }
                        var isMaleExpanded by remember { mutableStateOf(false) }

                        val pendingSelectedVoice = uiState.pendingSelectedVoice
                        val femaleVoices = uiState.availableVoices.filter { it.gender == Gender.FEMALE }
                        val maleVoices = uiState.availableVoices.filter { it.gender == Gender.MALE }

                        Text(
                            "Choose a Speaker",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- Female Voices Dropdown ---
                        Column {
                            VoiceCategoryDropdownHeader(
                                title = "Female Voices",
                                selectedVoiceName = if (pendingSelectedVoice?.gender == Gender.FEMALE) pendingSelectedVoice.friendlyName else null,
                                isExpanded = isFemaleExpanded,
                                onClick = {
                                    isFemaleExpanded = !isFemaleExpanded; isMaleExpanded = false
                                },
                            )
                            AnimatedVisibility(visible = isFemaleExpanded) {
                                Column(modifier = Modifier.padding(start = 16.dp)) {
                                    femaleVoices.forEach { voice ->
                                        VoiceSelectionRow(
                                            voice = voice,
                                            isSelected = pendingSelectedVoice?.id == voice.id,
                                            onClick = { viewModel.onPendingVoiceSelect(voice) }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        // --- Male Voices Dropdown ---
                        Column {
                            VoiceCategoryDropdownHeader(
                                title = "Male Voices",
                                selectedVoiceName = if (pendingSelectedVoice?.gender == Gender.MALE) pendingSelectedVoice.friendlyName else null,
                                isExpanded = isMaleExpanded,
                                onClick = {
                                    isMaleExpanded = !isMaleExpanded; isFemaleExpanded = false
                                }
                            )
                            AnimatedVisibility(visible = isMaleExpanded) {
                                Column(modifier = Modifier.padding(start = 16.dp)) {
                                    maleVoices.forEach { voice ->
                                        VoiceSelectionRow(
                                            voice = voice,
                                            isSelected = pendingSelectedVoice?.id == voice.id,
                                            onClick = { viewModel.onPendingVoiceSelect(voice) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    SheetContent.LanguageSelection -> {
                        Text(
                            "Choose an English",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            items(uiState.availableLanguages, key = { it.code }) { language ->
                                LanguageSelectionRow(
                                    language = language,
                                    isSelected = uiState.pendingSelectedLanguage?.code == language.code,
                                    onClick = { viewModel.onPendingLanguageSelect(language) }
                                )
                            }
                        }
                    }
                    SheetContent.BothSelection -> {
                        Text(
                            "Choose an English",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "You can choose your accent here or on the 'Me/Settings' Tab",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Light
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            items(uiState.availableLanguages, key = { it.code }) { language ->
                                LanguageSelectionRow(
                                    language = language,
                                    isSelected = uiState.pendingSelectedLanguage?.code == language.code,
                                    onClick = { viewModel.onPendingLanguageSelect(language) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Choose an Exam Level",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "From A1 to B2",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Light
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            items(uiState.availableExams, key = { it.json }) { exam ->
                                ExamSelectionRow(
                                    exam = exam,
                                    isSelected = uiState.pendingSelectedExam?.json == exam.json,
                                    onClick = { viewModel.onPendingExamSelect(exam) }
                                )
                            }
                        }

                    }
                    SheetContent.Hidden -> {}

                }

                Spacer(modifier = Modifier.height(24.dp))
                // --- CHANGE 2: Center the buttons ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    // Change Arrangement.End to Arrangement.CenterHorizontally
                    horizontalArrangement = Arrangement.spacedBy(
                        16.dp,
                        Alignment.CenterHorizontally
                    )
                ) {
                    OutlinedButton(
                        onClick = { viewModel.hideBottomSheet() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { viewModel.saveSelection() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonColor,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Save")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp)) //extra space below buttons
            }
        }
    } //: sheetContent

    if (false) {
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
                        if (context is ComponentActivity) {
                            AnalyticsHelper.logPaywallResponse(context,"accepted", "limit_paragraph")
                            viewModel.buyPremiumButtonPressed(context)
                            viewModel.onBottomSheetDismissed()
                        }
                    })
                    {
                        productDetails?.let { details ->
                            details.oneTimePurchaseOfferDetails?.let { offerDetails ->
                                Text("Unlimited: ${offerDetails.formattedPrice}")
                            }
                        }
                    }

                    Button(onClick = {
                        AnalyticsHelper.logPaywallResponse(context,"rejected", "limit_paragraph")
                        viewModel.IAPCancelled()
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

    // if (uiState.showIAPBottomSheet) {
    if (false) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.onBottomSheetDismissed() },
            sheetState = sheetStateIAP,
            containerColor = MaterialTheme.colorScheme.surface, // Clean background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Visual "Premium" Header
                Icon(
                    imageVector = Icons.Default.Stars, // A "Gold Star" or "Crown" icon
                    contentDescription = null,
                    tint = Color(0xFFFFD700), // Gold Color
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Unlock Full Exam Mastery",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Master the official 3,000+ word bank",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

//                // 2. Value Proposition (Instead of just limits)
//                Column(
//                    verticalArrangement = Arrangement.spacedBy(12.dp),
//                    modifier = Modifier.fillMaxWidth()
//                ) {
//                    BenefitRow("Unlimited AI Pronunciation", "No more ${uiState.hourlyLimit} per hour limits")
//                    BenefitRow("Complete A1-B2 Vocabulary", "All 3,000+ official exam words")
//                    BenefitRow("Lifetime Access", "One-time payment. No subscriptions.")
//                    BenefitRow("Pass Your Exam", "Focus on the words that actually matter")
//                }

                Spacer(modifier = Modifier.height(32.dp))

                // 3. Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Main CTA Button
                    Button(
                        modifier = Modifier.weight(1.5f).height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            if (context is ComponentActivity) {
                                AnalyticsHelper.logPaywallResponse(context, "accepted", "limit_sheet")
                                viewModel.buyPremiumButtonPressed(context)
                                viewModel.onBottomSheetDismissed()
                            }
                        }
                    ) {
                        productDetails?.let { details ->
                            details.oneTimePurchaseOfferDetails?.let { offerDetails ->
                                Text(
                                    "Upgrade: ${offerDetails.formattedPrice}",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }

                    // Secondary Cancel Button
                    OutlinedButton(
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            AnalyticsHelper.logPaywallResponse(context, "rejected", "limit_sheet")
                            viewModel.IAPCancelled()
                            viewModel.onBottomSheetDismissed()
                        }
                    ) {
                        Text("Not Now")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Secure your success in IELTS, TOEFL & Cambridge",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (uiState.showIAPBottomSheet) {
        PremiumUpgradeSheet(
            onDismiss = {
                viewModel.onBottomSheetDismissed()
            }
        )
    }

    if (showHelpSheet) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissHelpSheet() },
            sheetState = helpSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            // 3. Set Height to 3/4
            Box(modifier = Modifier.fillMaxHeight(0.85f)) {
                HelpInfoSheet(
                    onDismiss = { viewModel.dismissHelpSheet() }
                )
            }
        }
    }
//    if (showCelebration) {

// 1. Move the visibility logic inside AnimatedVisibility
//        AnimatedVisibility(
//            visible = showCelebration,
//            enter = slideInVertically(
//                initialOffsetY = { -it } // Start from above the screen (-height)
//            ) + fadeIn(),
//            exit = slideOutVertically(
//                targetOffsetY = { -it } // Slide back up to hidden
//            ) + fadeOut()
//        ) {
//            // 2. Sound and Timer logic
//            LaunchedEffect(Unit) {
//                viewModel.playSuccessSound()
//                delay(5000L)
//                viewModel.onDismissCelebration()
//            }
//
//            // 3. The Layout (Note: BoxScope is provided by the parent or a surrounding Box)
//            Box(modifier = Modifier.fillMaxSize()) {
//                Box(
//                    modifier = Modifier
//                        .align(Alignment.TopCenter)
//                        .zIndex(10f)
//                        .padding(top = 16.dp) // Give it some breathing room from the status bar
//                ) {
//                    AchievementBanner(
//                        isVisible = true, // Always true here because AnimatedVisibility handles the alpha
//                        title = "Grammar Master!",
//                        subtitle = "You've unlocked a new milestone",
//                        onDismiss = { viewModel.onDismissCelebration() }
//                    )
//                }
//            }
//        }
//    }

    if (showSignInSheet) {
        SignInBottomSheet(
            onDismiss = { showSignInSheet = false }//,
//            vm = signInVm // Pass the same VM so state is shared
        )
    }

    if (showDebugSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDebugSheet = false },
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            // Give the sheet a reasonable height so you can see scrolling and typography
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 320.dp, max = 700.dp)
            ) {
                DictionaryEntryBrowserScreen(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = vm
                )
            }

            // Optional: bottom padding so content isn't tight against nav bar
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
    if (isDailyRateLimitingSheetVisible) {
        if (context is ComponentActivity) {
            RateLimitDailyPaywallBottomSheet(
                onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) },
                onCloseSheet = { viewModel.hideDailyRateLimitSheet() }
            )
        }

    }
    if (isHourlyRateLimitingSheetVisible) {
        if (context is ComponentActivity) {
            RateLimitHourlyPaywallBottomSheet(
                onCloseSheet = {
                    viewModel.hideHourlyRateLimitSheet()
                },
                onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) }
            )
        }
    }
    // Main Screen Content (wrapped in a Box so the SnackbarHost can overlay the bottom).
    Box(modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item { SectionHeader("Voice & Exam") }
        if (LanguageConfig.showLanguageSelectionSetting) {
            item {
                SettingsActionItem(
                    icon = Icons.Default.RecordVoiceOver,
                    title = "Change English",
                    currentValue = uiState.currentLanguage,
                    onClick = { viewModel.onSettingClicked(SheetContent.LanguageSelection) }
                )
            }
        }
        item {
            SettingsActionItem(
                icon = Icons.Default.RecordVoiceOver,
                title = "Change Speaker",
                currentValue = uiState.currentFriendlyVoiceName,
                onClick = { viewModel.onSettingClicked(SheetContent.SpeakerSelection) }
            )
        }
        item {
            SettingsActionItem(
                icon = Icons.Default.School,
                title = "Change Exam",
                currentValue = uiState.availableExams.find { it.json == uiState.currentExamName }?.displayName
                    ?: uiState.currentExamName,
                onClick = { viewModel.onSettingClicked(SheetContent.ExamSelection) }
            )
        }
//        item {
//            SettingsActionItem(
//                icon = Icons.Default.School,
//                title = "Change Both",
//                currentValue = "",
//                onClick = { viewModel.onSettingClicked(SheetContent.BothSelection) }
//            )
//        }


        if (isPurchased) {
            productDetails?.let { details ->

                details.oneTimePurchaseOfferDetails?.let { offerDetails ->
                    item {
                        SettingsInfoItem(
                            icon = Icons.Default.Verified,
                            title = "Premium Account Verified",
                            value = "All exam lists and AI limits have been removed forever. Good luck with your studies.",
                            iconTint = Color(0xFF4CAF50)
                        )
                    }

                }
            }
            if (DEBUG) {
                item {
                    SettingsActionItem(
                        icon = Icons.Default.WorkspacePremium, // Use a premium icon
                        title = "Tap to UNDO a Premium user (T  )",

                        currentValue = "", // Display the price
                        onClick = {
                            viewModel.onDebugResetPurchases()
                        }
                    )
                }

            }
        } else {
            item {
                SettingsActionItem(
                    icon = Icons.Default.WorkspacePremium,
                    title = "Upgrade to Exam Mastery",
                    currentValue = "Unlock 3,000+ official words and unlimited AI pronunciation for life. Tap to secure your score.",
                    onClick = {
                        viewModel.onShowBottomSheetClicked()
                    }
                )
            }

        }

        if (viewModel.isUserAnonymous()) {
            item {
                SettingsActionItem(
                    icon = Icons.AutoMirrored.Filled.Login,
                    title = "Sign In",
                    "To enable sync to another device sign in here",
                    onClick = {
                        showSignInSheet = true

                    }
                )
            }
        }//:not logged in

        if (DEBUG){
            item {
                SettingsActionItem(
                    icon = Icons.AutoMirrored.Filled.Login,
                    title = "Sign Out",
                    "Go back to Anonymous (D)",
                    onClick = { viewModel.onSignOutClicked() }
                )
            }
        }


        item { Divider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) }
        item { SectionHeader("About") }
        item {
            SettingsInfoItem(
                icon = Icons.Default.Info,
                title = "Version",
                value = "${uiState.appVersion} -- ${uiState.appVersionCode}"
            )
        }


        if (viewModel.isItMe()) { //JG onSamsung phone

            item {
                SettingsActionItem(
                    icon = Icons.Default.WorkspacePremium,
                    title = "Tap to UNDO a Premium user (It's me)",
                    currentValue = "", // Display the price
                    onClick = {
                        viewModel.onDebugResetPurchases()
                    }
                )
            }

            item {
                SettingsActionItem(
                    icon = Icons.Default.CloudSync,
                    title = "Debug Release",
                    currentValue = "Tap to Log (It's me)",
                    onClick = {
                        if (viewModel.isItMe()) { //JG onSamsung phone
                            viewModel.debugAppValues()
                        }
                    }
                )
            }
            item {
                SettingsActionItem(
                    icon = Icons.Default.Info,
                    title = "Debug App Rate Limiting (It's me)",
                    currentValue =  "Rate Limiting",
                    onClick = {
                        if (viewModel.isItMe()) { //JG onSamsung phone
                            viewModel.debugAppRateLimiting()
                        }
                    }
                )
            }
            item {
                SettingsActionItem(
                    icon = Icons.Default.Info,
                    title = "Debug LLM Credits (It's me)",
                    currentValue =  "LLMCredits",
                    onClick = {
                        if (viewModel.isItMe()) { //JG onSamsung phone
                            viewModel.debugAppLLMCredits()
                        }
                    }
                )

//                SettingsInfoItem(
//                    icon = Icons.Default.Info,
//                    title = "LLM Credits (It's me)",
//                    value =  viewModel.debugAppLLMCredits()
//                )
            }
            item {
//                SettingsInfoItem(
//                    icon = Icons.Default.Info,
//                    title = "Billing (It's me)",
//                    value =  viewModel.debugAppBilling()
//                )
                SettingsActionItem(
                    icon = Icons.Default.Info,
                    title = "Debug App Billing(It's me)",
                    currentValue =  "App Billing",
                    onClick = {
                        if (viewModel.isItMe()) { //JG onSamsung phone
                            viewModel.debugAppBilling()
                        }
                    }
                )
            }

        }

        if (DEBUG) {
            item {
                SettingsActionItem(
                    icon = Icons.Default.CloudSync,
                    title = "IAP",
                    currentValue = "Tap to Log IAP Status (D)",
                    onClick = {
                        if (context is ComponentActivity) {
                            viewModel.onDebugPrintBillingStatus(context)
                        }
                    }
                )
            }
            item {
                SettingsActionItem(
                    icon = Icons.Default.Info,
                    title = "Debug Something",
                    currentValue = "Run the onboarding level check manually (D)",
                    onClick = {
                        navController.navigate("diagnostic_test")
//                            viewModel.callGoogleFunction()
//                        viewModel.resetRateLimits()
//                        viewModel.debugAppRateLimiting()
//                        viewModel.debugVocabQuizRepository()
//                        viewModel.showCelebtation()
//                        showTryOutVoicesSheet = true
//                            viewModel.onDebugCrashlyitcs()
//                            viewModel.userPreferences()
                            //viewModel.debugAppLLMCredits()
//                        viewModel.ShowDictionEntryScreen()
//                        Button(onClick = { showDictionary = true }) {
//                            Text("Open Dictionary Entry")
//                        }
                    }
                )
            }
            item {
                SettingsActionItem(
                    icon = Icons.Default.CloudSync,
                    title = "Upload JSON (D)",
                    currentValue = "Upload bundled quiz JSON to Firestore (D)",
                    onClick = {
                        navController.navigate("upload_json")
                    }
                )
            }
            item {
                // DEBUG: flip the freemium override to FORCE_PREMIUM at runtime, so gated screens unlock
                // live (no app restart). One-way for testing; reading the value keeps this label in sync.
                SettingsActionItem(
                    icon = Icons.Default.Stars,
                    title = "Force Premium (D)",
                    currentValue = "Override: ${DebugPremiumOverride.value} — tap to unlock gated screens (D)",
                    onClick = {
                        DebugPremiumOverride.value = PremiumOverride.FORCE_PREMIUM
                    }
                )
            }
            item {
                SettingsActionItem(
                    icon = Icons.Default.Info,
                    title = "Daily IAP",
                    currentValue = "Try out Daily Rate Limiting Screen (D)",
                    onClick = {
                        viewModel.showDailyRateLimitSheet()
                    }
                )
            }
            item {
                SettingsActionItem(
                    icon = Icons.Default.Info,
                    title = "Hourly IAP",
                    currentValue = "Try out Hourly Rate Limiting Screen (D)",
                    onClick = {
                        viewModel.ShowHourlyRateLimitSheet()
                    }
                )
            }
            item {
                SettingsActionItem(
                    icon = Icons.Default.Info,
                    title = "Print History Stats",
                    currentValue = "(D)",
                    onClick = {
                        viewModel.debugHistory()
                    }
                )
            }
            item {
                SettingsActionItem(
                    icon = Icons.Default.Info,
                    title = "Print XP stats",
                    currentValue = "(D)",
                    onClick = {
                        viewModel.debugXPManager()
                    }
                )
            }
            item {
                SettingsActionItem(
                    icon = Icons.Default.Info,
                    title = "Show Help Screen (D)",
                    currentValue = "(D)",
                    onClick = {
                        viewModel.debugShowHelpScreen()
                    }
                )
            }
            item {
                SettingsActionItem(
                    icon = Icons.Default.Info,
                    title = "Reset Quiz Stats(D)",
                    currentValue = "(D)",
                    onClick = {
                        viewModel.debugResetQuizStats()
                    }
                )
            }
            item {
                SettingsActionItem(
                    icon = Icons.Default.Info,
                    title = "Unhear sentence (D)",
                    currentValue = "(D). Test Section Completions",
                    onClick = {
                        viewModel.UnhearSentence("They worked together to resolve the conflict.","Verbs",3)
                    }
                )
            }

            item {
                SettingsInfoItem(
                    icon = Icons.Default.Info,
                    title = "UID (D)",
                    value = viewModel.firebaseUid()
                )
            }
        } //:DEBUG
    } //: LazyColumn

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    } //: Box

}

@Composable
fun IAPCancelled() {
    TODO("Not yet implemented")
}


// Helper component for the benefit list
@Composable
fun BenefitRowMoved(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF4CAF50), // Success Green
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )


}

@Composable
private fun SettingsActionItem(
    icon: ImageVector,
    title: String,
    currentValue: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = accentColor)
            Text(
                text = currentValue,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsInfoItem(icon: ImageVector, title: String, value: String,iconTint: Color = MaterialTheme.colorScheme.secondary ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ExamSelectionRow(
    exam: ExamDetails,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = exam.displayName,
            modifier = Modifier.weight(1f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center // <-- ADD THIS LINE TO CENTER THE TEXT
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }

}
@Composable
fun LanguageSelectionRow(
    language: LanguageCodeDetails,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = language.name,
            modifier = Modifier.weight(1f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center // <-- ADD THIS LINE TO CENTER THE TEXT
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}



