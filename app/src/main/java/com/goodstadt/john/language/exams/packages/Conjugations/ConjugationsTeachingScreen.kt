package com.goodstadt.john.language.exams.packages.Conjugations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.models.Format7or10Section
import com.goodstadt.john.language.exams.packages.ReferencePronouns.Format6File
import com.goodstadt.john.language.exams.packages.ReferencePronouns.PronounCategory
import com.goodstadt.john.language.exams.packages.ReferencePronouns.PronounForm
import com.goodstadt.john.language.exams.packages.ReferencePronouns.PronounSentence
import com.goodstadt.john.language.exams.packages.ReferencePronouns.PronounsClaudeStyle
import com.goodstadt.john.language.exams.packages.ReferenceQuiz.ReferenceQuizScreen
import com.goodstadt.john.language.exams.packages.Translate.TranslateSheet
import com.goodstadt.john.language.exams.packages.reference.shared.HorizontalLevelPicker

/**
 * The NEW conjugation reference screen: a fileFormat-6, pattern-focused view (sibling of the Pronouns /
 * Prepositions "Claude" screens). Top = verb picker (sein / haben / …). One row of chips = tense
 * (Präsens, Präteritum, …); tapping a tense shows the six-person paradigm ("How it works") with the
 * example sentences listed directly beneath it, all in one scrolling column. Two floating buttons like
 * the pronoun screen: "Q" (bottom-left) opens a fill-in-the-blank quiz for the selected tense, generated
 * fresh each open; "T" (bottom-right) opens Translate. Switched in via [USE_NEW_CONJUGATIONS_TEACHING]
 * in [ConjugationsScreen] so it can be compared with the old flat list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConjugationsTeachingScreen(viewModel: ConjugationsTeachingViewModel = hiltViewModel()) {
    val selectedVerb by viewModel.selectedVerb.collectAsState()
    var showTranslateSheet by rememberSaveable { mutableStateOf(false) }

    // Quiz bottom sheet (reuses the shared ReferenceQuizScreen, fed a runtime-generated question set).
    var showQuizSheet by rememberSaveable { mutableStateOf(false) }
    var quizSections by remember { mutableStateOf<List<Format7or10Section>>(emptyList()) }
    var quizTitle by remember { mutableStateOf("") }
    var quizAreaId by remember { mutableStateOf("") }
    val quizSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Selected tense chip, lifted here so the "Q" button knows which tense to quiz. Reset on verb change.
    var selectedCategoryId by rememberSaveable(selectedVerb) { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {

        // The horizontal verb picker (sein / haben / …). Translate lives in the floating button only.
        HorizontalLevelPicker(
            options = viewModel.verbOptions,
            selectedOption = selectedVerb,
            onOptionSelected = viewModel::onVerbSelected
        )

        Box(Modifier.fillMaxSize()) {
            when (val state = viewModel.uiState.collectAsState().value) {
                is ConjugationsTeachingUiState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                is ConjugationsTeachingUiState.Error ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                    }

                is ConjugationsTeachingUiState.Success -> {
                    val categories = state.sheet.categories
                    // Default the selection to the first tense once the sheet is loaded / verb changed.
                    if (selectedCategoryId.isBlank() || categories.none { it.id == selectedCategoryId }) {
                        selectedCategoryId = categories.firstOrNull()?.id ?: ""
                    }

                    ConjugationsTeachingContent(
                        sheet = state.sheet,
                        selectedCategoryId = selectedCategoryId,
                        onCategorySelected = { selectedCategoryId = it },
                        onPlay = viewModel::play
                    )

                    // Only offer a quiz for a tense whose form actually varies enough across persons to
                    // make a real question (hides it for English's identical-form tenses — see isQuizzable).
                    val selectedCat = categories.firstOrNull { it.id == selectedCategoryId }
                    if (selectedCat != null && ConjugationsQuizGenerator.isQuizzable(selectedCat)) {
                        // Floating Quiz ("Q") button, bottom-LEFT — quizzes just the selected tense chip
                        // with a freshly generated fill-in-the-blank set, like the pronoun screen.
                        FloatingActionButton(
                            onClick = {
                                val generated = ConjugationsQuizGenerator.generate(selectedCat, count = 10)
                                if (generated.isNotEmpty()) {
                                    quizSections = generated
                                    quizTitle = selectedCat.label
                                    quizAreaId = "Conjugations/$selectedVerb/${selectedCat.id}"
                                    showQuizSheet = true
                                }
                            },
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = Color(0xFFFF9800),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.SportsEsports, contentDescription = "Quiz")
                        }
                    }
                }
            }

            // The single Translate ("T") button, bottom-RIGHT — mirrors the vocab tabs' "T".
            FloatingActionButton(
                onClick = { showTranslateSheet = true },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Color(0xFFFF9800),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Icon(imageVector = Icons.Filled.Translate, contentDescription = "Translate")
            }
        }
    }

    // Translate the last sentence played on this screen (blank if none yet).
    if (showTranslateSheet) {
        TranslateSheet(
            onDismiss = { showTranslateSheet = false },
            initialText = viewModel.getLatestSentence()
        )
    }

    // Fill-in-the-blank quiz for the selected tense, in a bottom sheet (same screen the pronoun /
    // preposition quizzes use), fed the runtime-generated questions via `prebuilt`.
    if (showQuizSheet && quizSections.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showQuizSheet = false },
            sheetState = quizSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Box(Modifier.fillMaxHeight(0.92f)) {
                ReferenceQuizScreen(
                    category = quizTitle,
                    level = "",
                    prebuilt = quizSections,
                    prebuiltAreaId = quizAreaId,
                    prebuiltFileFormat = 7
                )
            }
        }
    }
}

@Composable
private fun ConjugationsTeachingContent(
    sheet: Format6File,
    selectedCategoryId: String,
    onCategorySelected: (String) -> Unit,
    onPlay: (String) -> Unit
) {
    val categories = sheet.categories
    if (categories.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No content.") }
        return
    }

    val category = categories.firstOrNull { it.id == selectedCategoryId } ?: categories.first()

    Column(Modifier.fillMaxSize()) {

        // Tense chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories, key = { it.id }) { c ->
                FilterChip(
                    selected = c.id == category.id,
                    onClick = { onCategorySelected(c.id) },
                    label = { Text(c.label) },
                    colors = FilterChipDefaults.filterChipColors()
                )
            }
        }

        HorizontalDivider(Modifier.padding(top = 4.dp))

        CategoryDetail(category, onPlay)
    }
}

/**
 * For the selected tense: the paradigm table (all six persons), any grammar tip, then the example
 * sentences listed straight underneath — no sub-headers. The whole thing scrolls when it overflows.
 */
@Composable
private fun CategoryDetail(category: PronounCategory, onPlay: (String) -> Unit) {
    // Merge the "How it works" pattern (forms + note) and the "Examples" pattern (sentences) into one view.
    val forms = category.patterns.flatMap { it.forms }
    val notes = category.patterns.mapNotNull { it.note.takeIf { n -> n.isNotBlank() } }
    val sentences = category.patterns.flatMap { it.sections }.flatMap { it.sentences }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (forms.isNotEmpty()) {
            item { FormChain(forms, onPlay) }
        }

        notes.forEach { note ->
            item {
                Text(
                    "💡  $note",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Example sentences directly below the paradigm — no header.
        items(sentences, key = { "${category.id}-${it.text}" }) { s ->
            SentenceRow(s, onPlay)
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SentenceRow(sentence: PronounSentence, onPlay: (String) -> Unit) {
    // Tap the row to hear it — same interaction as the reference lists.
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
private fun FormChain(forms: List<PronounForm>, onPlay: (String) -> Unit) {
    // The paradigm table. Each row is tappable so the learner can hear the isolated form too.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        forms.forEach { form ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlay("${form.case} ${form.text}") }
            ) {
                // Person tag (ich / du / er / wir / ihr / sie, or I / you / he / we / they)
                Box(
                    Modifier
                        .width(46.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        form.case,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    form.text,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = PronounsClaudeStyle.HIGHLIGHT,
                    modifier = Modifier.width(160.dp)
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
