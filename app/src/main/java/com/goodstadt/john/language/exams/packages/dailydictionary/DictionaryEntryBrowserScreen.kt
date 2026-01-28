package com.goodstadt.john.language.exams.packages.dailydictionary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel


@Composable
fun DictionaryEntryBrowserScreen(
    modifier: Modifier = Modifier,
    viewModel: DictionaryEntryBrowserViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.load()
    }

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

        state.currentEntry == null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No entry available.")
            }
        }

        else -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                TopControlRow(
                    canBack = state.canGoBack,
                    canForward = state.canGoForward,
                    isViewingToday = state.isViewingToday,
                    onBack = viewModel::goBack,
                    onForward = viewModel::goForward
                )

                Spacer(Modifier.height(10.dp))

                state.currentDateLabel?.let { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                }

                DictionaryEntryCard(
                    entry = state.currentEntry!!,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TopControlRow(
    canBack: Boolean,
    canForward: Boolean,
    isViewingToday: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onBack,
            enabled = canBack
        ) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Back") }

        Spacer(Modifier.weight(1f))

        if (isViewingToday) {
            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text(
                    "TODAY",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // keep layout stable
            Spacer(Modifier.height(1.dp))
        }

        Spacer(Modifier.weight(1f))

        FilledTonalIconButton(
            onClick = onForward,
            enabled = canForward
        ) { Icon(Icons.Filled.ChevronRight, contentDescription = "Forward") }
    }
}
