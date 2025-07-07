package com.goodstadt.john.language.exams.screens.me

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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

    // This LaunchedEffect now handles both showing and hiding cleanly.
    LaunchedEffect(sheetContent, sheetState.isVisible) {
        if (sheetContent != SheetContent.Hidden) {
            sheetState.show()
        } else {
            if (sheetState.isVisible) {
                sheetState.hide()
            }
        }
    }

    // --- The Bottom Sheet Composable ---
    if (sheetContent != SheetContent.Hidden) {
        ModalBottomSheet(
                onDismissRequest = { viewModel.hideBottomSheet() },
                sheetState = sheetState
        ) {
            Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (sheetContent) {
                    SheetContent.ExamSelection -> {
                        // Exam selection UI remains unchanged
                        Text("Choose an Exam Level", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        // ... (rest of ExamSelection UI is the same)
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

                    // --- START OF MODIFICATION FOR SPEAKER SELECTION ---
                    SheetContent.SpeakerSelection -> {
                        var isExpanded by remember { mutableStateOf(false) }
                        val selectedVoice = uiState.pendingSelectedVoice

                        Text("Choose a Speaker", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Show the current selection as an expandable header
                        if (selectedVoice != null) {
                            ExpandableVoiceHeader(
                                    voice = selectedVoice,
                                    isExpanded = isExpanded,
                                    onClick = { isExpanded = !isExpanded }
                            )
                        }

                        // The animated, collapsible list of other voices
                        AnimatedVisibility(visible = isExpanded) {
                            // Filter out the selected voice and group the rest by gender
                            val otherVoicesByGender = uiState.availableVoices
                                .filter { it.id != selectedVoice?.id }
                                .groupBy { it.gender }

                            Divider(modifier = Modifier.padding(vertical = 8.dp))

                            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                                otherVoicesByGender.forEach { (gender, voicesInGroup) ->
                                    // Sticky header for each gender
                                    item {
                                        GenderHeader(gender.name.replaceFirstChar { it.titlecase() })
                                    }
                                    // List of voices for that gender
                                    items(voicesInGroup, key = { it.id }) { voice ->
                                        VoiceSelectionRow(
                                                voice = voice,
                                                isSelected = false, // Not needed, selection is at the top
                                                onClick = {
                                                    viewModel.onPendingVoiceSelect(voice)
                                                    isExpanded = false // Collapse after selection
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // --- END OF MODIFICATION ---

                    SheetContent.Hidden -> {}
                }

                // This section for Save/Cancel buttons remains unchanged
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End)
                ) {
                    OutlinedButton(onClick = { viewModel.hideBottomSheet() }) {
                        Text("Cancel")
                    }
                    Button(onClick = { viewModel.saveSelection() }) {
                        Text("Save")
                    }
                }
            }
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
                    // Use displayName from the full ExamDetails object for a better UI
                    currentValue = uiState.availableExams.find { it.json == uiState.currentExamName }?.displayName ?: uiState.currentExamName,
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

// The helper composables below remain unchanged. Their `onClick` and `isSelected`
// props are controlled by the parent, so they work perfectly with the new logic.

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
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
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
@Composable
private fun ExpandableVoiceHeader(
    voice: VoiceOption,
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
        // Voice name and gender in parentheses
        Text(
                text = "${voice.friendlyName} (${voice.gender.name.lowercase().replaceFirstChar { it.titlecase() }})",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
        )
        // The rotating arrow icon
        Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Expand or collapse voice selection",
                modifier = Modifier.rotate(rotationAngle),
                tint = MaterialTheme.colorScheme.primary
        )
    }
}

// --- NEW COMPOSABLE 2: A simple header for gender groups ---
@Composable
private fun GenderHeader(genderName: String) {
    Text(
            text = genderName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp)
    )
}