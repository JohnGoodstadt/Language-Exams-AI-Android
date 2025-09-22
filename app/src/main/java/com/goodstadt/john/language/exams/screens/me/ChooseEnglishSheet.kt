package com.goodstadt.john.language.exams.screens.me

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.data.Gender
import com.goodstadt.john.language.exams.ui.theme.buttonColor
import com.goodstadt.john.language.exams.viewmodels.ParagraphViewModel
import com.goodstadt.john.language.exams.viewmodels.SheetContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseEnglishSheet(
    viewModel: ChooseEnglishViewModel = hiltViewModel(),
    // A callback that passes the selected variant string back to the caller
    onConfirmSelection: (String) -> Unit
) {

    val uiState by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState()



    ModalBottomSheet(
        onDismissRequest = { viewModel.hideBottomSheet() },
        sheetState = sheetState
    ) {
        // --- CHANGE 1: Adjust padding to add more space at the bottom ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
//                .fillMaxHeight(0.8f)
                .defaultMinSize(minHeight = 1800.dp) // <--
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 24.dp
                ), // More bottom padding
            horizontalAlignment = Alignment.CenterHorizontally
        ) {


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

            Spacer(modifier = Modifier.height(8.dp))

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
//            Spacer(modifier = Modifier.height(12.dp)) //extra space below buttons
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}