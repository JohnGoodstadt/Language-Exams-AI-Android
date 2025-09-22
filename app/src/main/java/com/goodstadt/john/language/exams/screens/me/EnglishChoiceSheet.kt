package com.goodstadt.john.language.exams.screens.me

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnglishChoiceSheet(
    // A callback that passes the selected variant string back to the caller
    onConfirmSelection: (String) -> Unit
) {
    val options = listOf("British", "American", "Australian")
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf(options[0]) }

    Column(
        modifier = Modifier.padding(16.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Choose Your English", style = MaterialTheme.typography.headlineSmall)
        Text("Please select the English variant you would like to learn.")

        Spacer(modifier = Modifier.height(8.dp))

        // The Dropdown Menu
        ExposedDropdownMenuBox(
            expanded = isDropdownExpanded,
            onExpandedChange = { isDropdownExpanded = it }
        ) {
            TextField(
                value = selectedOption,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = isDropdownExpanded,
                onDismissRequest = { isDropdownExpanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            selectedOption = option
                            isDropdownExpanded = false
                        }
                    )
                }
            }
        }

        // The confirmation button
        Button(
            onClick = { onConfirmSelection(selectedOption) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Choose this")
        }
    }
}