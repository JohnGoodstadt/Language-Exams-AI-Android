package com.goodstadt.john.language.exams.packages.ReferencePronounsClaude

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.ui.theme.orangeLight

@Composable
fun PronounsClaudeScreen(viewModel: PronounsClaudeViewModel = hiltViewModel()) {
    when (val state = viewModel.uiState.collectAsState().value) {
        is PronounsClaudeUiState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

        is PronounsClaudeUiState.Error ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            }

        is PronounsClaudeUiState.Success ->
            PronounsContent(state.sheet, onPlay = viewModel::play)
    }
}

@Composable
private fun PronounsContent(sheet: Format6File, onPlay: (String) -> Unit) {
    val categories = sheet.categories
    if (categories.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No content.") }
        return
    }

    // Level 1: category (Personal / This-That). Level 2: item (I/me, you, he, …). Reset the item when
    // the category changes; both selections survive rotation.
    var selectedCategoryId by rememberSaveable { mutableStateOf(categories.first().id) }
    val category = categories.firstOrNull { it.id == selectedCategoryId } ?: categories.first()

    val patterns = category.patterns
    var selectedPatternId by rememberSaveable(selectedCategoryId) {
        mutableStateOf(patterns.firstOrNull()?.id ?: "")
    }
    val pattern = patterns.firstOrNull { it.id == selectedPatternId } ?: patterns.firstOrNull()

    Column(Modifier.fillMaxSize()) {

        // Category chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories, key = { it.id }) { c ->
                FilterChip(
                    selected = c.id == category.id,
                    onClick = { selectedCategoryId = c.id },
                    label = { Text(c.label) },
                    colors = FilterChipDefaults.filterChipColors()
                )
            }
        }

        // Item chips for the selected category
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(patterns, key = { it.id }) { p ->
                FilterChip(
                    selected = p.id == pattern?.id,
                    onClick = { selectedPatternId = p.id },
                    label = { Text(p.chip) }
                )
            }
        }

        HorizontalDivider(Modifier.padding(top = 8.dp))

        // Selected pattern detail
        if (pattern != null) {
            PatternDetail(pattern, onPlay)
        }
    }
}

@Composable
private fun PatternDetail(pattern: PronounPattern, onPlay: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(pattern.chip, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (pattern.subtitle.isNotBlank()) {
                    Text(
                        pattern.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // The declension chain: one row per case (Nom / Akk / Dat …), the form green + tappable.
        item { FormChain(pattern.forms) }

        if (pattern.note.isNotBlank()) {
            item {
                Text(
                    "💡  ${pattern.note}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        pattern.sections.forEach { section ->
            item(key = "h-${pattern.id}-${section.header}") { SectionHeader(section) }
            items(section.sentences, key = { "${pattern.id}-${section.header}-${it.text}" }) { s ->
                SentenceRow(s, onPlay)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SectionHeader(section: ExampleSection) {
    // Orange header to match the vocab tabs (1/2/3).
    Text(
        section.header,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = orangeLight,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SentenceRow(sentence: PronounSentence, onPlay: (String) -> Unit) {
    // Tap the row to hear it - no play button, no translation (words are simple), like the reference lists.
    Text(
        PronounsClaudeStyle.highlightGreenWords(sentence.text, sentence.green),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay(sentence.text) }
            .padding(vertical = 6.dp)
    )
}

@Composable
private fun FormChain(forms: List<PronounForm>) {
    // Pure reference (no audio) - the learner hears the forms in the example sentences below.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        forms.forEach { form ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Case tag
                Box(
                    Modifier
                        .width(46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        PronounsClaudeStyle.caseShort(form.case),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    form.text,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = PronounsClaudeStyle.HIGHLIGHT,
                    modifier = Modifier.width(84.dp)
                )
                Text(
                    form.gloss,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

