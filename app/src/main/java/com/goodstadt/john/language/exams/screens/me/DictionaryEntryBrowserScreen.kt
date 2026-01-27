package com.goodstadt.john.language.exams.screens.me

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
import com.goodstadt.john.language.exams.models.DictionaryEntry
import com.goodstadt.john.language.exams.models.PartOfSpeechBlock
import com.goodstadt.john.language.exams.models.Sense
import com.goodstadt.john.language.exams.viewmodels.DictionaryEntryBrowserViewModel

@Composable
fun DictionaryEntryBrowserScreen(
    modifier: Modifier = Modifier,
    viewModel: DictionaryEntryBrowserViewModel
) {
    val state by viewModel.uiState

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    when {
        state.isLoading -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state.error != null -> {
            ErrorPane(
                modifier = modifier.fillMaxSize(),
                message = state.error ?: "Error",
                onRetry = { viewModel.load() }
            )
        }
        state.currentEntry == null -> {
            ErrorPane(
                modifier = modifier.fillMaxSize(),
                message = "No scheduled entry. Ensure today/yesterday/day-before exist in scheduledAssignments.",
                onRetry = { viewModel.load() }
            )
        }
        else -> {
            DictionaryEntryDetail(
                modifier = modifier.fillMaxSize(),
                entry = state.currentEntry!!,
                dateString = state.currentDate,
                isViewingToday = state.isViewingToday,
                canBack = state.canBack,
                canForward = state.canForward,
                onBack = { viewModel.goBack() },
                onForward = { viewModel.goForward() }
            )
        }
    }
}

@Composable
private fun ErrorPane(
    modifier: Modifier = Modifier,
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Failed to load bundle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun DictionaryEntryDetail(
    modifier: Modifier = Modifier,
    entry: DictionaryEntry,
    dateString: String?,
    isViewingToday: Boolean,
    canBack: Boolean,
    canForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit
) {
    var showIpa by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TopControlRow(
                dateString = dateString,
                isViewingToday = isViewingToday,
                canBack = canBack,
                canForward = canForward,
                onBack = onBack,
                onForward = onForward
            )
        }

        item {
            Header(
                entry = entry,
                showIpa = showIpa,
                onToggleIpa = { showIpa = !showIpa },
                isViewingToday = isViewingToday
            )
        }

        items(entry.partsOfSpeech) { pos ->
            PosCard(
                headword = entry.headword,
                pronunciationDisplay = entry.pronunciation.display,
                pos = pos,
                expanded = expanded.contains(pos.key()),
                onToggleExpanded = {
                    expanded = if (expanded.contains(pos.key())) expanded - pos.key() else expanded + pos.key()
                }
            )
        }

        item { Spacer(Modifier.height(18.dp)) }
    }
}

@Composable
private fun TopControlRow(
    dateString: String?,
    isViewingToday: Boolean,
    canBack: Boolean,
    canForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(onClick = onBack, enabled = canBack) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Back")
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(onClick = onForward, enabled = canForward) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Forward")
        }

        Spacer(Modifier.weight(1f))

        if (dateString != null) {
            val label = if (isViewingToday) "Today • $dateString" else dateString
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Header(
    entry: DictionaryEntry,
    showIpa: Boolean,
    onToggleIpa: () -> Unit,
    isViewingToday: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = entry.headword,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(10.dp))
            if (isViewingToday) {
                AssistChip(onClick = { }, label = { Text("TODAY") }, enabled = false)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = entry.pronunciation.display,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = onToggleIpa,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(if (showIpa) "Hide IPA" else "Show IPA")
            }
        }

        if (showIpa) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                entry.pronunciation.ipaUK?.let {
                    Text("UK $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                entry.pronunciation.ipaUS?.let {
                    Text("US $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Divider()
    }
}

@Composable
private fun PosCard(
    headword: String,
    pronunciationDisplay: String,
    pos: PartOfSpeechBlock,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(headword, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(pos.ordinal, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = onToggleExpanded,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(if (expanded) "Collapse" else "Expand")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(pos.pos, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                pos.headwordLine?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(pronunciationDisplay, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                pos.inflectionsLine?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            if (pos.synonyms.isNotEmpty()) {
                Text("Synonyms", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SynonymChips(pos.synonyms)
            }

            pos.grammarType?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (expanded) {
                Divider()
                pos.senses.forEachIndexed { idx, sense ->
                    SenseBlock(sense)
                    if (idx != pos.senses.lastIndex) Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                }
            }
        }
    }
}

@Composable
private fun SenseBlock(sense: Sense) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = "${sense.senseNumber}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(22.dp)
            )
            Text(
                text = ": ${sense.definition}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        sense.examples.forEach { ex ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(22.dp)
                )
                Text(
                    text = ex,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SynonymChips(items: List<String>) {
    // Dependency-light wrap: 2 chips per row. Replace with FlowRow if you have it.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { chip ->
                    AssistChip(onClick = { /* no-op */ }, label = { Text(chip) })
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun PartOfSpeechBlock.key(): String =
    "${ordinal}|${pos}|${grammarType ?: ""}"
