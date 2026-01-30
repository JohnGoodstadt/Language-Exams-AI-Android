package com.goodstadt.john.language.exams.screens.shared

import android.speech.tts.Voice
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.data.VoiceOption
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.viewmodels.VoiceSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSettingsBottomSheet(
    sentence:String,
    onDismiss: () -> Unit,
    viewModel: VoiceSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Load data when the sheet opens
    LaunchedEffect(Unit) {
        viewModel.loadData()
        viewModel.setLatestSentence(sentence)

    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.60f)
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp) // Padding for navigation bars
        ) {
            Text(
                text = "Try out a voice",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (state.isLoading) {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null) {
                Text(text = state.error ?: "Unknown Error", color = MaterialTheme.colorScheme.error)
            } else {
                // --- Dropdowns ---

                // 1. Female
                if (state.femaleVoices.isNotEmpty()) {
                    VoiceDropdownItem(
                        label = "Female Voices",
                        options = state.femaleVoices,
                        selected = state.selectedFemale,
                        onSelect = {
                            val voiceOption = it
                            viewModel.onDropdownSelectionChange( voiceOption)
                        }
                    )
                }

                // 2. Male
                if (state.maleVoices.isNotEmpty()) {
                    VoiceDropdownItem(
                        label = "Male Voices",
                        options = state.maleVoices,
                        selected = state.selectedMale,
                        onSelect = {
                            val voiceOption = it
                            viewModel.onDropdownSelectionChange( voiceOption)
                        }
                    )
                }

            }

            Spacer(modifier = Modifier.height(32.dp))

//            if (state.existingVoice != null) {
//                Text(
//                    text = "Current Voice: ${state.existingVoice!!.friendlyName}",
//                    style = MaterialTheme.typography.titleMedium,
//                    fontWeight = FontWeight.Bold,
//                    color = MaterialTheme.colorScheme.primary,
//                    modifier = Modifier.padding(bottom = 16.dp)
//                )
//            }
            state.existingVoice?.let { voice ->
                Text(
                    text = "Voice: ${voice.friendlyName}", // No !! needed
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = orangeLight,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }


            // --- Action Buttons ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        viewModel.saveSelection(onComplete = onDismiss)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.existingVoice?.let { voice ->
                    Text(
                        text = "Hit Save to use '${voice.friendlyName}' as the default voice",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Normal,
                        color = orangeLight,
                        modifier = Modifier.padding(bottom = 16.dp,top = 16.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                Text(
                    text = "Go to Settings on the Me tab to choose again",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp, top = 16.dp),
                )
            }

        }
    }
}

// --- Internal Helper Composable for the Dropdown ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceDropdownItem(
    label: String,
    options: List<VoiceOption>, // Ensure your Voice class has a .name field
    selected: String,
    onSelect: (VoiceOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { voice ->
                DropdownMenuItem(
                    text = { Text(voice.friendlyName) },
                    onClick = {
                        onSelect(voice)
                        expanded = false
                    }
                )
            }
        }
    }
}