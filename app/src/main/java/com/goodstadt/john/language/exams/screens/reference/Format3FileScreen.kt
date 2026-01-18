package com.goodstadt.john.language.exams.screens.reference


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goodstadt.john.language.exams.models.Format3Category
import com.goodstadt.john.language.exams.models.Format3File
import com.goodstadt.john.language.exams.models.Format3Sentence
import timber.log.Timber

@Composable
fun Format3FileScreen(
    assetPath: String,
    onSentenceTapped: (String) -> Unit,
    vm: Format3FileViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by vm.uiState.collectAsState()

    LaunchedEffect(assetPath) {
        if (state.file == null && state.errorMessage == null && !state.isLoading) {
            vm.loadFromAssets(context, assetPath)
        }
    }

    when {
        state.isLoading -> LoadingView()

        state.errorMessage != null -> ErrorView(
            message = state.errorMessage ?: "Load failed",
            onRetry = { Timber.i("Error")}
        )

        state.file != null -> Format3ContentView(
            file = state.file!!,
            sentencesFor = { vm.sentencesFor(it) },
            onSentenceTapped = onSentenceTapped
        )

        else -> EmptyView(onLoad = { Timber.i("Error")})
    }
}

@Composable
private fun LoadingView() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Loading…", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Couldn’t load content", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun EmptyView(onLoad: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("No content loaded", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onLoad) { Text("Load") }
    }
}

@Composable
private fun Format3ContentView(
    file: Format3File,
    sentencesFor: (Format3Category) -> List<Format3Sentence>,
    onSentenceTapped: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(file.title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(file.subtitle, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Text(file.description, style = MaterialTheme.typography.bodyMedium)
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
    Card(colors = CardDefaults.cardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(category.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(category.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))

            // Sentence list
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
