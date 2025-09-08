package com.goodstadt.john.language.exams.screens.me

//import com.goodstadt.john.language.exams.data.PremiumStatus
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.BuildConfig.DEBUG
import com.goodstadt.john.language.exams.data.Gender
import com.goodstadt.john.language.exams.data.VoiceOption
import com.goodstadt.john.language.exams.models.ExamDetails
import com.goodstadt.john.language.exams.ui.theme.accentColor
import com.goodstadt.john.language.exams.ui.theme.buttonColor
import com.goodstadt.john.language.exams.viewmodels.SettingsViewModel
import com.goodstadt.john.language.exams.viewmodels.SheetContent
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
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
                        val femaleVoices =
                            uiState.availableVoices.filter { it.gender == Gender.FEMALE }
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

    // Main Screen Content
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item { SectionHeader("Voice & Exam") }
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


        if (isPurchased) {
            productDetails?.let { details ->

                details.oneTimePurchaseOfferDetails?.let { offerDetails ->
                    item {
                        SettingsInfoItem(
                            icon = Icons.Default.Verified,
                            title = "You are a Premium user!",
                            value = "Thank you for your support."
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
        }else{
            item {
                SettingsActionItem(
                    icon = Icons.Default.WorkspacePremium,
                    title = "Some restrictions are in place",
                    currentValue = "AI has charges so we limit some tasks. Tap to unlock all exams.",
                    onClick = {
                        viewModel.onShowBottomSheetClicked()
                    }
                )
            }

        }

        item { Divider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) }
        item { SectionHeader("About") }
        item {
            SettingsInfoItem(
                icon = Icons.Default.Info,
                title = "Version",
                value = uiState.appVersion
            )
        }
//        item {
//            SettingsInfoItem(
//                icon = Icons.Default.Info,
//                title = "Hard Coded Version",
//                value = "1.45"
//            )
//        }
//        item {
//            SettingsInfoItem(
//                icon = Icons.Default.Info,
//                title = "Version Code",
//                value = " ${uiState.appVersionCode}"
//            )
//        }
        if (DEBUG) {
            item {
                SettingsActionItem(
                    icon = Icons.Default.CloudSync,
                    title = "Debug IAP",
                    currentValue = "Tap to Log IAP Status",
                    onClick = {
                        if (context is androidx.activity.ComponentActivity) {
                            viewModel.onDebugPrintBillingStatus(context)
                        }
                    }
                )
            }
        }
        if (DEBUG) {
            item {
                SettingsInfoItem(
                    icon = Icons.Default.Info,
                    title = "UID",
                    value = viewModel.firebaseUid()
                )
            }
//            item { SectionHeader("Debug") }
//            item {
//                SettingsInfoItem(
//                    icon = Icons.Default.Info,
//                    title = "Hard coded version",
//                    value = "V1.45"
//                )
//            }
        }
//        item {
//            SettingsActionItem(
//                icon = Icons.Default.Info,
//                title = "TTS Voices",
//                currentValue = "",
//                onClick = { viewModel.downloadAndSaveVoiceList() }
//            )
//        }

    }
}

// Helper Composables (unchanged)
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
private fun SettingsInfoItem(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.secondary
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
private fun ExamSelectionRow(
    exam: ExamDetails,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
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
private fun VoiceSelectionRow(
    voice: VoiceOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = voice.friendlyName,
            modifier = Modifier.weight(1f),
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
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
private fun VoiceCategoryDropdownHeader(
    title: String,
    selectedVoiceName: String?,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val rotationAngle by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- CHANGE 3: Center the text content ---
        Column(
            modifier = Modifier.weight(1f),
            // Add this line to center the text elements inside the column
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = accentColor
            )
            Text(
                text = selectedVoiceName ?: "Tap to select",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selectedVoiceName != null) FontWeight.Bold else FontWeight.Normal
            )
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = "Expand or collapse selection",
            modifier = Modifier.rotate(rotationAngle)
        )
    }
}

// Obsolete composables can be removed if you wish
@Composable
private fun ExpandableVoiceHeaderObsolete(
    voice: VoiceOption,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
}

@Composable
private fun GenderHeaderObsolete(genderName: String) {
}