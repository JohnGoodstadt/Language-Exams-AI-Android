package com.goodstadt.john.language.exams.screens.shared.speakerSelection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goodstadt.john.language.exams.data.Gender
import com.goodstadt.john.language.exams.data.VoiceOption

@Composable
fun SpeakerSelectionSheetContent(
    modifier: Modifier = Modifier,
    // data in
    availableVoices: List<VoiceOption>,
    pendingSelectedVoice: VoiceOption?,
    // actions out
    onVoiceSelected: (VoiceOption) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    // styling
    buttonColor: Color,
    // optional header text override
    title: String = "Choose a Speaker"
) {
    var isFemaleExpanded by remember { mutableStateOf(false) }
    var isMaleExpanded by remember { mutableStateOf(false) }

    val femaleVoices = remember(availableVoices) { availableVoices.filter { it.gender == Gender.FEMALE } }
    val maleVoices = remember(availableVoices) { availableVoices.filter { it.gender == Gender.MALE } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Female Voices Dropdown ---
        Column(modifier = Modifier.fillMaxWidth()) {
            VoiceCategoryDropdownHeader(
                title = "Female Voices",
                selectedVoiceName = if (pendingSelectedVoice?.gender == Gender.FEMALE) pendingSelectedVoice.friendlyName else null,
                isExpanded = isFemaleExpanded,
                onClick = {
                    isFemaleExpanded = !isFemaleExpanded
                    isMaleExpanded = false
                }
            )

            AnimatedVisibility(visible = isFemaleExpanded) {
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    femaleVoices.forEach { voice ->
                        VoiceSelectionRow(
                            voice = voice,
                            isSelected = pendingSelectedVoice?.id == voice.id,
                            onClick = { onVoiceSelected(voice) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        // --- Male Voices Dropdown ---
        Column(modifier = Modifier.fillMaxWidth()) {
            VoiceCategoryDropdownHeader(
                title = "Male Voices",
                selectedVoiceName = if (pendingSelectedVoice?.gender == Gender.MALE) pendingSelectedVoice.friendlyName else null,
                isExpanded = isMaleExpanded,
                onClick = {
                    isMaleExpanded = !isMaleExpanded
                    isFemaleExpanded = false
                }
            )

            AnimatedVisibility(visible = isMaleExpanded) {
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    maleVoices.forEach { voice ->
                        VoiceSelectionRow(
                            voice = voice,
                            isSelected = pendingSelectedVoice?.id == voice.id,
                            onClick = { onVoiceSelected(voice) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Buttons (unchanged behavior)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = Color.White
                )
            ) { Text("Cancel") }

            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = Color.White
                )
            ) { Text("Save") }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
