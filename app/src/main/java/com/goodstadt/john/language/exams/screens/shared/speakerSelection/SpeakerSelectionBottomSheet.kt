package com.goodstadt.john.language.exams.screens.shared.speakerSelection

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import com.goodstadt.john.language.exams.data.VoiceOption


import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakerSelectionBottomSheet() {
    val vm: SpeakerSelectionViewModel = hiltViewModel()
    val state by vm.uiState.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(state.isSheetVisible) {
        if (!state.isSheetVisible) sheetState.hide()
    }

    if (state.isSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { vm.hide() },
            sheetState = sheetState
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator()
                }
                state.error != null -> {
                    Text(state.error ?: "Error")
                }
                else -> {
                    SpeakerSelectionSheetContent(
                        modifier = Modifier,
                        availableVoices = state.availableVoices,
                        pendingSelectedVoice = state.pendingSelectedVoice,
                        onVoiceSelected = vm::onPendingVoiceSelect,
                        onCancel = vm::hide,
                        onSave = { vm.saveSelection(currentGoogleVoiceNameField = "currentGoogleVoiceName") },
                        buttonColor = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
