package com.goodstadt.john.language.exams.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.models.CallCost
//import com.goodstadt.john.language.exams.data.CallCost
import com.goodstadt.john.language.exams.viewmodels.GeminiViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiExampleScreen(
    viewModel: GeminiViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Google Gemini API") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --- GENERATE BUTTON ---
            Button(
                onClick = { viewModel.generateContent() },
                enabled = !uiState.isLoading, // Disable when loading
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Ask Gemini")
                }
            }

            // --- RESULT DISPLAY AREA ---
            Column(
                modifier = Modifier
                    .weight(1f) // Takes up remaining space
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(uiState.resultText)
            }

            // --- ERROR MESSAGE DISPLAY ---
            uiState.errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // --- COST DETAILS CARD ---
//            uiState.lastCallCost?.let { cost ->
//                CostDetailsCard(cost = cost)
//            }
        }
    }
}


@Composable
fun CostDetailsCard(cost: CallCost, modifier: Modifier = Modifier) {
    val currencyFormatter = remember {
        NumberFormat.getCurrencyInstance(Locale.US).apply {
            maximumFractionDigits = 6
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Last Call Cost Analysis", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Input Tokens:", style = MaterialTheme.typography.bodySmall)
                Text("${cost.inputTokens}", style = MaterialTheme.typography.bodySmall)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Output Tokens:", style = MaterialTheme.typography.bodySmall)
                Text("${cost.outputTokens}", style = MaterialTheme.typography.bodySmall)
            }
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total Estimated Cost:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(currencyFormatter.format(cost.totalCostUSD), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}