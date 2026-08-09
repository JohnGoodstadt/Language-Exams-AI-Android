package com.goodstadt.john.language.exams.packages.reference

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import capitalizeFirstLetter
import com.goodstadt.john.language.exams.packages.dailydictionary.DictionaryEntry
import com.goodstadt.john.language.exams.packages.dailydictionary.PartOfSpeechBlock
import com.goodstadt.john.language.exams.ui.theme.orangeLight


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
//            Text(
//                    text = "Explanation",
//                    style = MaterialTheme.typography.titleLarge,
//                    modifier = Modifier.padding(16.dp)
//            )

            Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
            ) {
                Text(
                        text = summary.capitalizeFirstLetter(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                )
                if (explain.isNotEmpty()) {
//                if (explain.headword.isNotBlank()) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordQuizInfoBottomSheetViewOriginal(summary: String, explain: DictionaryEntry?, onCloseSheet: () -> Unit) {

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
//            Text(
//                text = "Explanation",
//                style = MaterialTheme.typography.titleLarge,
//                modifier = Modifier.padding(16.dp)
//            )

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
                //if (explain.isNotEmpty()) {
                if (explain != null ) {
                    Text(
                        text = explain.headword,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.weight(1f)) // Push content to top
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordQuizInfoBottomSheetView(
    summary: String,
    explain: DictionaryEntry?,
    onCloseSheet: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onCloseSheet,
        sheetState = sheetState,
        modifier = Modifier.imePadding()
    ) {
        // One scroll for the whole bottom sheet content (avoid nested scrolling).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.Start
        ) {
//            Text(
//                text = "Explanation",
//                style = MaterialTheme.typography.titleLarge,
//                modifier = Modifier.padding(bottom = 12.dp)
//            )

            Text(
                text = summary.capitalizeFirstLetter(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            )

            explain?.let { entry ->
                DictionaryEntryCardInSheet(
                    entry = entry,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun DictionaryEntryCardInSheet(
    entry: DictionaryEntry,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Headword
            Text(
                text = entry.headword.ifBlank { entry.entryId },
                style = MaterialTheme.typography.headlineMedium,
                color = orangeLight,
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
    if (block.ordinal.isNotBlank()) {
        Text(
            text = block.ordinal,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
    }

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