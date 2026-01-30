package com.goodstadt.john.language.exams.screens.shared.speakerSelection

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import com.goodstadt.john.language.exams.data.VoiceOption


import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakerSelectionBottomSheet(  onDismiss: () -> Unit ) {
    val vm: SpeakerSelectionViewModel = hiltViewModel()
    val state by vm.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        vm.show() // or vm.ensureVoicesLoaded()
    }
//    LaunchedEffect(state.isSheetVisible) {
//        if (!state.isSheetVisible) sheetState.hide()
//    }

//    if (state.isSheetVisible) {
      ModalBottomSheet(
            onDismissRequest = { vm.hide() },
            sheetState = sheetState
        ) {
          Column(
              modifier = Modifier
                  .fillMaxWidth()
                  .fillMaxHeight(0.92f) // tweak: 0.9–0.95
          ) {
              when {
                  state.isLoading -> {
                      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                          CircularProgressIndicator()
                      }
                  }

                  state.error != null -> {
                      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                          Text(state.error ?: "Error")
                      }
                  }

                  else -> {
                      SpeakerSelectionSheetContent(
                          modifier = Modifier,
                          availableVoices = state.availableVoices,
                          pendingSelectedVoice = state.pendingSelectedVoice,
                          onVoiceSelected = vm::onPendingVoiceSelect,
                          onCancel = onDismiss,//vm::hide,
//                          onSave = { vm.saveSelection(currentGoogleVoiceNameField = "currentGoogleVoiceName") },
                          onSave = {
                              scope.launch {
                                  val ok = vm.saveSelection("currentGoogleVoiceName")
                                  if (ok) {
                                      sheetState.hide()
                                      onDismiss() // sets _showSPEAKERSheet=false
                                  }
                              }
                          },
                          buttonColor = MaterialTheme.colorScheme.primary
                      )
                  }
              }//: when
          }
        }//:BottomSheet
//    }
}
