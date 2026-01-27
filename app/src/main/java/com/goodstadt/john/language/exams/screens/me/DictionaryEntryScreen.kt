package com.goodstadt.john.language.exams.screens.me

// Jetpack Compose — Dictionary-style entry (Merriam-Webster-ish)
// No Firestore yet; data is hard-coded in the ViewModel.
// Requires Material3.

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// MARK: - Models
/*
data class DictionaryEntry(
    val id: String,
    val headword: String,
    val pronunciation: Pronunciation,
    val partsOfSpeech: List<PartOfSpeechBlock>
)

data class Pronunciation(
    val display: String,  // "ˈən-dər-ˌskȯr"
    val ipaUK: String,
    val ipaUS: String
)

data class PartOfSpeechBlock(
    val ordinal: String,       // "1 of 2"
    val posTitle: String,      // "verb"
    val grammarType: String,   // "transitive verb"
    val headwordLine: String,  // "un·der·score"
    val inflectionsLine: String,
    val synonyms: List<String>,
    val senses: List<Sense>
)

data class Sense(
    val number: Int,
    val definition: String,
    val examples: List<String>
)

// MARK: - ViewModel (hard-coded dummy data)

class DictionaryEntryViewModel {
    var entry by mutableStateOf(underscoreDummy())
        private set

    var showIPA by mutableStateOf(false)
        private set

    // track expanded blocks by ordinal for simplicity
    var expanded by mutableStateOf(setOf<String>())
        private set

    fun toggleIPA() { showIPA = !showIPA }

    fun toggleExpanded(ordinal: String) {
        expanded = if (expanded.contains(ordinal)) expanded - ordinal else expanded + ordinal
    }

    fun isExpanded(ordinal: String): Boolean = expanded.contains(ordinal)
}

private fun underscoreDummy(): DictionaryEntry =
    DictionaryEntry(
        id = "underscore",
        headword = "underscore",
        pronunciation = Pronunciation(
            display = "ˈən-dər-ˌskȯr",
            ipaUK = "/ˌʌndəˈskɔː/",
            ipaUS = "/ˌʌndərˈskɔːr/"
        ),
        partsOfSpeech = listOf(
            PartOfSpeechBlock(
                ordinal = "1 of 2",
                posTitle = "verb",
                grammarType = "transitive verb",
                headwordLine = "un·der·score",
                inflectionsLine = "underscored; underscoring; underscores",
                synonyms = listOf("emphasize", "stress", "highlight", "underline"),
                senses = listOf(
                    Sense(
                        number = 1,
                        definition = "to draw a line under : underline",
                        examples = listOf("Please underscore the key terms on the worksheet.")
                    ),
                    Sense(
                        number = 2,
                        definition = "to make evident : emphasize, stress",
                        examples = listOf(
                            "She arrived early to underscore the importance of the occasion.",
                            "The results underscore the need for consistent practice."
                        )
                    ),
                    Sense(
                        number = 3,
                        definition = "to provide (action on film) with accompanying music",
                        examples = listOf("The composer underscored the final scene with a tense motif.")
                    )
                )
            ),
            PartOfSpeechBlock(
                ordinal = "2 of 2",
                posTitle = "noun",
                grammarType = "countable noun",
                headwordLine = "underscore",
                inflectionsLine = "plural: underscores",
                synonyms = listOf("underline", "score (line)", "background music"),
                senses = listOf(
                    Sense(
                        number = 1,
                        definition = "a line drawn under a word or line especially for emphasis or to indicate intent to italicize",
                        examples = listOf("Add an underscore beneath the title.")
                    ),
                    Sense(
                        number = 2,
                        definition = "music accompanying the action and dialogue of a film",
                        examples = listOf("The film’s underscore subtly reinforced the mood.")
                    )
                )
            )
        )
    )

// MARK: - UI

@Composable
fun DictionaryEntryScreen(
    modifier: Modifier = Modifier,
    viewModel: DictionaryEntryViewModel = remember { DictionaryEntryViewModel() }
) {
    val entry = viewModel.entry
    val showIPA = viewModel.showIPA
    val expanded = viewModel.expanded

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            EntryHeader(
                headword = entry.headword,
                pronunciation = entry.pronunciation,
                showIPA = showIPA,
                onToggleIPA = { viewModel.toggleIPA() }
            )
        }

        items(entry.partsOfSpeech) { pos ->
            PosCard(
                headword = entry.headword,
                pronunciationDisplay = entry.pronunciation.display,
                pos = pos,
                expanded = expanded.contains(pos.ordinal),
                onToggleExpanded = { viewModel.toggleExpanded(pos.ordinal) }
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun EntryHeader(
    headword: String,
    pronunciation: Pronunciation,
    showIPA: Boolean,
    onToggleIPA: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = headword,
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold)
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = pronunciation.display,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onToggleIPA, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                Text(text = if (showIPA) "Hide IPA" else "Show IPA")
            }
        }

        AnimatedVisibility(
            visible = showIPA,
            enter = fadeIn(animationSpec = tween(140)),
            exit = fadeOut(animationSpec = tween(140))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("UK ${pronunciation.ipaUK}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("US ${pronunciation.ipaUS}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = headword,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = pos.ordinal,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onToggleExpanded, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(if (expanded) "Collapse" else "Expand")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = pos.posTitle, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                Text(text = pos.headwordLine, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = pronunciationDisplay, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = pos.inflectionsLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Text("Synonyms", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChipsRow(items = pos.synonyms)

            Text(
                text = pos.grammarType,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = tween(140)),
                exit = fadeOut(animationSpec = tween(140))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Divider()
                    pos.senses.forEachIndexed { idx, sense ->
                        SenseBlock(sense)
                        if (idx != pos.senses.lastIndex) Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    }
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
                text = "${sense.number}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.width(22.dp)
            )
            Text(
                text = ": ${sense.definition}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        sense.examples.forEach { ex ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
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
private fun ChipsRow(items: List<String>) {
    // Lightweight chip row; wraps to multiple lines using FlowRow if available.
    // If you have androidx.compose.foundation:foundation-layout, you can replace this with FlowRow.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Very simple fallback: two chips per row.
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { chip ->
                    AssistChip(
                        onClick = { /* no-op */ },
                        label = { Text(chip) }
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

 */
