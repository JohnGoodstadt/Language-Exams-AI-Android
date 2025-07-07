package com.goodstadt.john.language.exams.screens.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // <-- Make sure this is imported
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.data.VoiceOption
import com.goodstadt.john.language.exams.models.ExamDetails
import com.goodstadt.john.language.exams.viewmodels.SettingsViewModel
import com.goodstadt.john.language.exams.viewmodels.SheetContent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val sheetContent by viewModel.sheetState.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(sheetContent) {
        if (sheetContent != SheetContent.Hidden) {
            sheetState.show()
        } else {
            sheetState.hide()
        }
    }

    // --- The Bottom Sheet Composable ---
    if (sheetContent != SheetContent.Hidden) {
        ModalBottomSheet(
                onDismissRequest = { viewModel.hideBottomSheet() },
                sheetState = sheetState
        ) {
            // --- NEW, SIMPLIFIED STRUCTURE ---
            // The main `when` block is now inside the ModalBottomSheet content lambda.
            // This ensures it has the latest state when the sheet is composed.
            Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (sheetContent) {
                    SheetContent.ExamSelection -> {
                        Text("Choose an Exam Level", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        // --- ADD THIS IF/ELSE BLOCK FOR DEBUGGING ---
                        if (uiState.availableExams.isEmpty()) {
                            Text(
                                    "No exams found for this language. Check ViewModel logs.",
                                    color = Color.Red,
                                    modifier = Modifier.padding(vertical = 24.dp)
                            )
                        } else {
                            LazyColumn {
                                items(uiState.availableExams, key = { it.json }) { exam ->
                                    ExamSelectionRow(
                                            exam = exam,
                                            isSelected = uiState.currentExamName == exam.json,
                                            onClick = {
                                                viewModel.onExamSelected(exam)
                                            }
                                    )
                                }
                            }
                        }
                        // --- END OF DEBUGGING BLOCK ---
                    }
                    SheetContent.SpeakerSelection -> {
                        Text("Choose a Speaker", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        LazyColumn {
                            items(uiState.availableVoices, key = { it.id }) { voice ->
                                VoiceSelectionRow(
                                        voice = voice,
                                        isSelected = uiState.currentFriendlyVoiceName == voice.friendlyName,
                                        onClick = { viewModel.onVoiceSelected(voice) }
                                )
                            }
                        }
                    }
                    SheetContent.Hidden -> {}
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            viewModel.hideBottomSheet()
                        }
                    }
                }) {
                    Text("Close")
                }
            }
            // --- END OF NEW STRUCTURE ---
        }
    }

    // The Main Screen Content (LazyColumn) remains unchanged.
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
                    currentValue = uiState.currentExamName,
                    onClick = { viewModel.onSettingClicked(SheetContent.ExamSelection) }
            )
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
    }
}

// The helper composables below remain unchanged

@Composable
private fun SectionHeader(title: String) {
    Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
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
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = currentValue, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsInfoItem(icon: ImageVector, title: String, value: String) {
    Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = MaterialTheme.colorScheme.secondary)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = exam.displayName, modifier = Modifier.weight(1f), fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
        if (isSelected) {
            Icon(imageVector = Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
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