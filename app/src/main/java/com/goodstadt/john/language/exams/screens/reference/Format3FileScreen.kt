package com.goodstadt.john.language.exams.screens.reference


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goodstadt.john.language.exams.models.Format3Category
import com.goodstadt.john.language.exams.models.Format3File
import com.goodstadt.john.language.exams.models.Format3Sentence
import timber.log.Timber


// 1. Define Strict Colors (Hardcoded to ensure Dark Mode look)
private val DarkBackground = Color(0xFF121212)      // Almost Black
private val DarkCardBackground = Color(0xFF2C2C2E)  // Dark Grey
private val DarkTextPrimary = Color.White           // White
private val DarkTextSecondary = Color(0xFFB0B0B0)   // Light Grey

@Composable
fun Format3FileScreen(
    file: Format3File,
    onSentenceTapped: (String) -> Unit,
    vm: Format3FileViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()

    LaunchedEffect(file) {
        vm.setFile(file)
    }

    // 2. USE SURFACE INSTEAD OF BOX
    // Surface paints the background color reliably across the whole screen area.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground,
        contentColor = DarkTextPrimary // Sets default text color to White
    ) {
        when {
            state.isLoading -> LoadingView()

            state.errorMessage != null -> ErrorView(
                message = state.errorMessage ?: "Load failed",
                onRetry = { Timber.i("Retry clicked") }
            )

            state.file != null -> Format3ContentView(
                file = state.file!!,
                sentencesFor = { vm.sentencesFor(it) },
                onSentenceTapped = onSentenceTapped
            )

            else -> EmptyView(onLoad = { Timber.i("Load clicked") })
        }
    }
}

@Composable
private fun Format3ContentView(
    file: Format3File,
    sentencesFor: (Format3Category) -> List<Format3Sentence>,
    onSentenceTapped: (String) -> Unit
) {
    // 3. Explicitly background the LazyColumn just in case
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = DarkCardBackground,
                    contentColor = DarkTextPrimary // Forces text inside to be White
                ),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(file.title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(file.subtitle, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Text(file.description, style = MaterialTheme.typography.bodyMedium, color = DarkTextSecondary)
                }
            }
        }

        val categoriesSorted = file.categories.sortedBy { it.sortOrder }

        items(categoriesSorted, key = { it.sortOrder }) { category ->
            CategoryCard(
                category = category,
                sentences = sentencesFor(category),
                onSentenceTapped = onSentenceTapped
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CategoryCard(
    category: Format3Category,
    sentences: List<Format3Sentence>,
    onSentenceTapped: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = DarkCardBackground,
            contentColor = DarkTextPrimary // Forces text inside to be White
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(category.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(category.description, style = MaterialTheme.typography.bodyMedium, color = DarkTextSecondary)
            Spacer(Modifier.height(12.dp))

            sentences.forEach { s ->
                Text(
                    text = s.sentence,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .clickable { onSentenceTapped(s.sentence) }
                )
            }
        }
    }
}

// --- Helper Views (Updated to match Dark Theme) ---

@Composable
private fun LoadingView() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = DarkTextPrimary)
        Spacer(Modifier.height(12.dp))
        Text("Loading…", color = DarkTextSecondary)
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Couldn’t load content", style = MaterialTheme.typography.titleMedium, color = DarkTextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(message, color = DarkTextSecondary)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun EmptyView(onLoad: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No content loaded", style = MaterialTheme.typography.titleMedium, color = DarkTextPrimary)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onLoad) { Text("Load") }
    }
}