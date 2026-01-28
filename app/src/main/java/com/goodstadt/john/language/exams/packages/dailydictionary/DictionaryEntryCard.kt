package com.goodstadt.john.language.exams.packages.dailydictionary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goodstadt.john.language.exams.ui.theme.Orange

@Composable
fun DictionaryEntryCard(
    entry: DictionaryEntry,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Headword
            Text(
                text = entry.headword.ifBlank { entry.entryId },
                style = MaterialTheme.typography.headlineMedium,
                color = Orange,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(4.dp))

            // Pronunciation + labels
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.pronunciation.display.isNotBlank()) {
                    Text(
                        text = entry.pronunciation.display,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (entry.labels.isNotEmpty()) {
                    Text(
                        text = entry.labels.joinToString(" • "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            entry.partsOfSpeech.forEachIndexed { idx, block ->
                PartOfSpeechBlockView(block = block)

                if (idx != entry.partsOfSpeech.lastIndex) {
                    Spacer(Modifier.height(14.dp))
                    Divider()
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun PartOfSpeechBlockView(block: PartOfSpeechBlock) {
    // e.g. "1 of 2"
    if (block.ordinal.isNotBlank()) {
        Text(
            text = block.ordinal,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
    }

    // "verb" + "transitive verb"
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = block.pos,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        block.grammarType?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    block.inflectionsLine?.takeIf { it.isNotBlank() }?.let {
        Spacer(Modifier.height(6.dp))
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (block.synonyms.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Synonyms: " + block.synonyms.joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(Modifier.height(10.dp))

    block.senses.forEach { s ->
        Text(
            text = "${s.senseNumber}. ${s.definition}",
            style = MaterialTheme.typography.bodyLarge
        )

        if (s.examples.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            s.examples.forEach { ex ->
                Text(
                    text = "• $ex",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(10.dp))
    }
}
