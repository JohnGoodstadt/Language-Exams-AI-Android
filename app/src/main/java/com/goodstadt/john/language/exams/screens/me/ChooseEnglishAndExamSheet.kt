package com.goodstadt.john.language.exams.screens.me

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
import com.goodstadt.john.language.exams.ui.theme.buttonColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChooseEnglishAndExamSheet(
    viewModel: ChooseEnglishViewModel = hiltViewModel(),
    onClose: () -> Unit
) {

    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 8.dp,
                end = 8.dp,
                top = 8.dp,
                bottom = 8.dp
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
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(uiState.availableLanguages, key = { it.code }) { language ->
                LanguageSelectionRow(
                    language = language,
                    isSelected = uiState.pendingSelectedLanguage?.code == language.code,
                    onClick = { viewModel.onPendingLanguageSelect(language) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
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
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(uiState.availableExams, key = { it.json }) { exam ->
                ExamSelectionRow(
                    exam = exam,
                    isSelected = uiState.pendingSelectedExam?.json == exam.json,
                    onClick = { viewModel.onPendingExamSelect(exam) }
                )
            }
        }
        /*
        5. CLAUDE Two LazyColumn without height constraints — layout crash
File: ChooseEnglishAndExamSheet.kt lines 49, 70
Two LazyColumn composables are stacked inside a Column without Modifier.weight() or fixed height. Both try to take infinite height, which causes layout issues — only one list will be scrollable or visible.

Fix: Add Modifier.weight(1f) to each LazyColumn, e.g.:

LazyColumn(modifier = Modifier.weight(1f)) { ... }
         */
        Spacer(modifier = Modifier.height(8.dp))
//        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            // Change Arrangement.End to Arrangement.CenterHorizontally
            horizontalArrangement = Arrangement.spacedBy(
                16.dp,
                Alignment.CenterHorizontally
            )
        ) {
            OutlinedButton(
                onClick = { onClose() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = Color.White
                )
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    viewModel.saveSelection()
                    onClose()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = Color.White
                )
            ) {
                Text("Save")
            }
        }
    }
//    }
}