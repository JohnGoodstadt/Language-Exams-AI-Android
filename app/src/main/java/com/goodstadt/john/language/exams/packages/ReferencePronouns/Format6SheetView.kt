package com.goodstadt.john.language.exams.packages.ReferencePronouns

import com.goodstadt.john.language.exams.screens.shared.LaunchSpinner
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.packages.ReferenceQuiz.ReferenceQuizScreen
import com.goodstadt.john.language.exams.packages.Translate.TranslateSheet
import com.goodstadt.john.language.exams.ui.theme.LocalExtendedColors
import com.goodstadt.john.language.exams.screens.shared.buttonStyleChipColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Format6SheetView(viewModel: Format6ViewModel = hiltViewModel()) {
    when (val state = viewModel.uiState.collectAsState().value) {
        is Format6UiState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LaunchSpinner() }

        is Format6UiState.Error ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            }

        is Format6UiState.Success -> {
            val categories = state.sheet.categories
            val isUsageReferenceSheet = viewModel.isUsageReferenceSheet
            // Level-1 (category) selection is lifted here so the floating "Q" knows which chip to quiz.
            var selectedCategoryId by rememberSaveable { mutableStateOf(categories.firstOrNull()?.id ?: "") }
            val selectedCategory = categories.firstOrNull { it.id == selectedCategoryId } ?: categories.firstOrNull()

            var showTranslateSheet by rememberSaveable { mutableStateOf(false) }
            var showEnglish by rememberSaveable(state.sheet.sheetname) { mutableStateOf(false) }
            var showQuizSheet by rememberSaveable { mutableStateOf(false) }
            var showUpgradeSheet by rememberSaveable { mutableStateOf(false) }
            val quizSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            Box(Modifier.fillMaxSize()) {
                PronounsContent(
                    sheet = state.sheet,
                    selectedCategoryId = selectedCategoryId,
                    onCategorySelected = { selectedCategoryId = it },
                    onPlay = viewModel::play,
                    showEnglish = isUsageReferenceSheet && showEnglish,
                    isCategoryLocked = { index -> viewModel.isCategoryLocked(index) },
                    onLockedTapped = { showUpgradeSheet = true },
                    onChipTapped = { viewModel.recordReferenceTap() }
                )

                if (categories.isNotEmpty()) {
                    // Floating Quiz ("Q") button, bottom-LEFT: quizzes just the selected chip. Only shown
                    // for sheets that ship a quiz (pronouns); teaching sheets like Prepositions have none.
                    if (viewModel.hasQuiz) {
                        FloatingActionButton(
                            onClick = { showQuizSheet = true },
                            shape = CircleShape,
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = LocalExtendedColors.current.statusOrange,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                        ) {
                            Icon(imageVector = Icons.Filled.SportsEsports, contentDescription = "Quiz")
                        }
                    }

                    // Floating Translate ("T") button, bottom-RIGHT - mirrors the vocab tabs' "T".
                    FloatingActionButton(
                        onClick = {
                            if (isUsageReferenceSheet) showEnglish = !showEnglish
                            else showTranslateSheet = true
                        },
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = if (isUsageReferenceSheet && showEnglish)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else LocalExtendedColors.current.statusOrange,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Icon(imageVector = Icons.Filled.Translate, contentDescription = if (showEnglish) "Show German" else "Show English")
                    }
                }
            }

            // Freemium content lock: shown when the user taps a locked category chip.
            if (showUpgradeSheet) {
                com.goodstadt.john.language.exams.packages.me.PremiumUpgradeSheet(
                    onDismiss = { showUpgradeSheet = false }
                )
            }

            // Translate the last sentence played on this screen (blank if none yet).
            if (showTranslateSheet && !isUsageReferenceSheet) {
                TranslateSheet(
                    onDismiss = { showTranslateSheet = false },
                    initialText = viewModel.getLatestSentence()
                )
            }

            // Fill-in-the-blank quiz for the selected category, in a bottom sheet (like the Focus screen).
            if (showQuizSheet && selectedCategory != null) {
                ModalBottomSheet(
                    onDismissRequest = { showQuizSheet = false },
                    sheetState = quizSheetState,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Box(Modifier.fillMaxHeight(0.92f)) {
                        // Reference teaching content isn't level-specific — blank level hides the "· B1"
                        // suffix. Prepositions uses its authored quiz loader; pronouns uses its own
                        // (generator-backed) loader; both are filtered to the selected category chip.
                        if (viewModel.isPrepositionsSheet) {
                            ReferenceQuizScreen(
                                category = selectedCategory.label,
                                level = "",
                                prepositionsQuizSheet = viewModel.quizSheetName,
                                prepositionsCategoryFilter = selectedCategory.label
                            )
                        } else {
                            ReferenceQuizScreen(
                                category = selectedCategory.label,
                                level = "",
                                pronounsQuizSheet = viewModel.quizSheetName,
                                pronounsCategoryFilter = selectedCategory.label
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PronounsContent(
    sheet: Format6File,
    selectedCategoryId: String,
    onCategorySelected: (String) -> Unit,
    onPlay: (String) -> Unit,
    showEnglish: Boolean = false,
    isCategoryLocked: (Int) -> Boolean = { false },
    onLockedTapped: () -> Unit = {},
    onChipTapped: () -> Unit = {}
) {
    val categories = sheet.categories
    if (categories.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No content.") }
        return
    }

    // Level 1: category (Personal / This-That) - selection lifted to the caller. Level 2: item (I/me,
    // you, he, …). Reset the item when the category changes; the item selection survives rotation.
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
            // Freemium gate: the first two category chips are free; the rest are locked teasers that open
            // the paywall instead of selecting (mirrors iOS Format6SheetView).
            itemsIndexed(categories, key = { _, c -> c.id }) { index, c ->
                val locked = isCategoryLocked(index)
                FilterChip(
                    selected = !locked && c.id == category.id,
                    onClick = { if (locked) onLockedTapped() else { onChipTapped(); onCategorySelected(c.id) } },
                    label = { Text(c.displayLabel(showEnglish)) },
                    leadingIcon = if (locked) {
                        { Icon(Icons.Filled.Lock, contentDescription = "Locked", modifier = Modifier.size(16.dp)) }
                    } else null,
                    colors = buttonStyleChipColors()
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
                    onClick = { onChipTapped(); selectedPatternId = p.id },
                    label = { Text(p.displayChip(showEnglish)) },
                    colors = buttonStyleChipColors()
                )
            }
        }

        HorizontalDivider(Modifier.padding(top = 8.dp))

        // Selected pattern detail
        if (pattern != null) {
            PatternDetail(pattern, onPlay, showEnglish)
        }
    }
}

@Composable
private fun PatternDetail(pattern: PronounPattern, onPlay: (String) -> Unit, showEnglish: Boolean) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(pattern.displayChip(showEnglish), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val subtitle = if (showEnglish) pattern.subtitleEn?.takeIf { it.isNotBlank() } ?: pattern.subtitle else pattern.subtitle
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
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
                SentenceRow(s, onPlay, showEnglish)
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
        color = LocalExtendedColors.current.sectionHeader,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun SentenceRow(sentence: PronounSentence, onPlay: (String) -> Unit, showEnglish: Boolean) {
    // Display authored English when requested, but always play the original German sentence.
    val visibleText = if (showEnglish && !sentence.en.isNullOrBlank())
        AnnotatedString(sentence.en.orEmpty())
    else Format6Style.highlightGreenWords(sentence.text, sentence.green)
    Text(
        visibleText,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay(sentence.text) }
            .padding(vertical = 6.dp)
    )
}

private fun PronounCategory.displayLabel(showEnglish: Boolean): String =
    if (showEnglish) labelEn?.takeIf { it.isNotBlank() } ?: label else label

private fun PronounPattern.displayChip(showEnglish: Boolean): String =
    if (showEnglish) chipEn?.takeIf { it.isNotBlank() } ?: chip else chip

@Composable
private fun FormChain(forms: List<PronounForm>) {
    // Pure reference (no audio) - the learner hears the forms in the example sentences below.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LocalExtendedColors.current.groupedPanel) // white / black panel on the grouped page
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
                        Format6Style.caseShort(form.case),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    form.text,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = LocalExtendedColors.current.teal,
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
