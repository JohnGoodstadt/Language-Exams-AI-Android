package com.goodstadt.john.language.exams.screens.me

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.graphics.Color


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizInfoBottomSheetView(summary: String, explain: String, onCloseSheet: () -> Unit) {

//    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
            onDismissRequest = onCloseSheet,
            sheetState = sheetState,
            // This modifier ensures the content is padded when the keyboard is shown.
            modifier = Modifier.imePadding()
    ){
        Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                    text = "Explanation",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
            )

            Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
            ) {
                Text(
                        text = summary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                )
                if (explain.isNotEmpty()) {
                    Text(
                            text = explain,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                    )
                }
                Spacer(modifier = Modifier.weight(1f)) // Push content to top
//                Row(
//                        modifier = Modifier.fillMaxWidth(),
//                        horizontalArrangement = Arrangement.SpaceEvenly
//                ) {
//                    Button(
//                            onClick = onCloseSheet,
//                            modifier = Modifier.weight(1f)
//                    ) {
//                        Text("Close")
//                    }
//                }
            }
        }
    }

}