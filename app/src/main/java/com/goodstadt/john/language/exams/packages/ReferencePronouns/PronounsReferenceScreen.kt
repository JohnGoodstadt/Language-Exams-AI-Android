package com.goodstadt.john.language.exams.packages.ReferencePronouns

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PronounsReferenceRoute(
    viewModel: PronounsReferenceViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val speaker = remember { PronounSpeaker(context) }
    val audioConfig = state.sheet?.data?.singleOrNull()?.audioConfig

    LaunchedEffect(audioConfig?.locale, audioConfig?.rate) {
        audioConfig?.let { speaker.configure(it.locale, it.rate.toFloat()) }
    }
    DisposableEffect(speaker) {
        onDispose(speaker::shutdown)
    }

    PronounsReferenceScreen(
        state = state,
        onRetry = viewModel::reload,
        onSelectSection = viewModel::selectSection,
        onSelectPattern = viewModel::selectPattern,
        onSelectDemonstrativeSet = viewModel::selectDemonstrativeSet,
        onSelectFormGroup = viewModel::selectFormGroup,
        onSpeak = speaker::speak,
        onSpeakPattern = { sentences ->
            speaker.speakPattern(
                sentences = sentences,
                pauseMs = audioConfig?.pauseBetweenSentencesMs ?: 450,
            )
        },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PronounsReferenceScreen(
    state: PronounsReferenceUiState,
    onRetry: () -> Unit,
    onSelectSection: (PronounReferenceSection) -> Unit,
    onSelectPattern: (String) -> Unit,
    onSelectDemonstrativeSet: (String) -> Unit,
    onSelectFormGroup: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onSpeakPattern: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(state.sheet?.title ?: "Pronouns") })
        },
    ) { padding ->
        when {
            state.loading -> LoadingContent(Modifier.padding(padding))
            state.errorMessage != null -> ErrorContent(
                message = state.errorMessage,
                onRetry = onRetry,
                modifier = Modifier.padding(padding),
            )
            else -> {
                val content = state.sheet?.data?.singleOrNull() ?: return@Scaffold
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                ) {
                    SectionChooser(
                        selected = state.selectedSection,
                        onSelected = onSelectSection,
                    )
                    when (state.selectedSection) {
                        PronounReferenceSection.PERSONAL -> PersonalPronounsPane(
                            content = content,
                            selectedPatternId = state.selectedPatternId,
                            onSelectPattern = onSelectPattern,
                            onSpeak = onSpeak,
                            onSpeakPattern = onSpeakPattern,
                        )
                        PronounReferenceSection.DEMONSTRATIVES -> DemonstrativesPane(
                            content = content,
                            selectedSetId = state.selectedDemonstrativeSetId,
                            selectedFormGroup = state.selectedFormGroup,
                            onSelectSet = onSelectDemonstrativeSet,
                            onSelectFormGroup = onSelectFormGroup,
                            onSpeak = onSpeak,
                            onSpeakPattern = onSpeakPattern,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionChooser(
    selected: PronounReferenceSection,
    onSelected: (PronounReferenceSection) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selected == PronounReferenceSection.PERSONAL) {
            Button(onClick = { onSelected(PronounReferenceSection.PERSONAL) }) {
                Text("Personal pronouns")
            }
        } else {
            OutlinedButton(onClick = { onSelected(PronounReferenceSection.PERSONAL) }) {
                Text("Personal pronouns")
            }
        }
        if (selected == PronounReferenceSection.DEMONSTRATIVES) {
            Button(onClick = { onSelected(PronounReferenceSection.DEMONSTRATIVES) }) {
                Text("This / that")
            }
        } else {
            OutlinedButton(onClick = { onSelected(PronounReferenceSection.DEMONSTRATIVES) }) {
                Text("This / that")
            }
        }
    }
}

@Composable
private fun PersonalPronounsPane(
    content: PronounsReferenceContent,
    selectedPatternId: String?,
    onSelectPattern: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onSpeakPattern: (List<String>) -> Unit,
) {
    val pattern = content.personalPronouns.firstOrNull { it.id == selectedPatternId }
        ?: content.personalPronouns.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Text(
                text = "Same person, three different grammatical jobs",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(content.personalPronouns, key = { it.id }) { item ->
                    FilterChip(
                        selected = item.id == pattern?.id,
                        onClick = { onSelectPattern(item.id) },
                        label = { Text(item.label) },
                    )
                }
            }
        }
        pattern?.let { selected ->
            item {
                PronounPatternCard(
                    pattern = selected,
                    cases = content.cases,
                    caseOrder = content.screenConfig.caseOrder,
                    onSpeak = onSpeak,
                    onSpeakPattern = onSpeakPattern,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        item {
            Text(
                text = "All personal pronouns",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            PersonalPronounOverview(content)
        }
        items(content.importantNotes) { note ->
            Text(
                text = "• $note",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun PronounPatternCard(
    pattern: PersonalPronounPattern,
    cases: List<PronounCase>,
    caseOrder: List<String>,
    onSpeak: (String) -> Unit,
    onSpeakPattern: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val orderedForms = caseOrder.mapNotNull { caseId ->
        pattern.forms[caseId]?.let { form ->
            (cases.firstOrNull { it.id == caseId } ?: PronounCase(id = caseId)) to form
        }
    }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = pattern.label,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                text = orderedForms.joinToString("  →  ") { it.second.word },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            pattern.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            orderedForms.forEachIndexed { index, (pronounCase, form) ->
                if (index > 0) HorizontalDivider()
                CaseExampleRow(pronounCase, form, onSpeak)
            }
            if (orderedForms.isNotEmpty()) {
                TextButton(
                    onClick = {
                        onSpeakPattern(orderedForms.map { it.second.sentence })
                    },
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 8.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Hear the whole pattern")
                }
            }
        }
    }
}

@Composable
private fun CaseExampleRow(
    pronounCase: PronounCase,
    form: PronounForm,
    onSpeak: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(104.dp)) {
            Text(pronounCase.shortLabel, style = MaterialTheme.typography.labelMedium)
            Text(
                pronounCase.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            HighlightedSentence(form.sentence, form.highlight)
            Text(
                form.supportText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onSpeak(form.sentence) }) {
            Icon(Icons.Default.VolumeUp, contentDescription = "Hear ${form.sentence}")
        }
    }
}

@Composable
private fun PersonalPronounOverview(content: PronounsReferenceContent) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            OverviewRow("Meaning", "Subject", "Direct", "Recipient", header = true)
            HorizontalDivider()
            content.personalPronouns.forEach { pattern ->
                OverviewRow(
                    first = pattern.label,
                    second = pattern.forms["nominative"]?.word.orEmpty(),
                    third = pattern.forms["accusative"]?.word.orEmpty(),
                    fourth = pattern.forms["dative"]?.word.orEmpty(),
                )
            }
        }
    }
}

@Composable
private fun OverviewRow(
    first: String,
    second: String,
    third: String,
    fourth: String,
    header: Boolean = false,
) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall
    val weight = if (header) FontWeight.SemiBold else FontWeight.Normal
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(first, style = style, fontWeight = weight, modifier = Modifier.weight(1.5f))
        Text(second, style = style, fontWeight = weight, modifier = Modifier.weight(1f))
        Text(third, style = style, fontWeight = weight, modifier = Modifier.weight(1f))
        Text(fourth, style = style, fontWeight = weight, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DemonstrativesPane(
    content: PronounsReferenceContent,
    selectedSetId: String?,
    selectedFormGroup: String,
    onSelectSet: (String) -> Unit,
    onSelectFormGroup: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onSpeakPattern: (List<String>) -> Unit,
) {
    val selectedSet = content.demonstratives.sets.firstOrNull { it.id == selectedSetId }
        ?: content.demonstratives.sets.firstOrNull()
    val caseOrder = content.screenConfig.caseOrder
    val examples = caseOrder.mapNotNull { caseId ->
        selectedSet?.examples?.firstOrNull {
            it.formGroup == selectedFormGroup && it.caseId == caseId
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item {
            Text(
                text = content.demonstratives.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                content.demonstratives.sets.forEach { set ->
                    FilterChip(
                        selected = set.id == selectedSet?.id,
                        onClick = { onSelectSet(set.id) },
                        label = { Text(set.label) },
                    )
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(content.demonstratives.formGroupOrder) { formGroup ->
                    FilterChip(
                        selected = formGroup == selectedFormGroup,
                        onClick = { onSelectFormGroup(formGroup) },
                        label = { Text(formGroup.replaceFirstChar(Char::uppercase)) },
                    )
                }
            }
        }
        selectedSet?.let { set ->
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "${set.label} · $selectedFormGroup",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        Text(
                            text = caseOrder.joinToString("  →  ") { caseId ->
                                set.forms[selectedFormGroup]?.get(caseId).orEmpty()
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        examples.forEachIndexed { index, example ->
                            if (index > 0) HorizontalDivider()
                            DemonstrativeExampleRow(
                                pronounCase = content.cases.firstOrNull { it.id == example.caseId },
                                example = example,
                                onSpeak = onSpeak,
                            )
                        }
                        TextButton(
                            onClick = { onSpeakPattern(examples.map { it.sentence }) },
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(horizontal = 8.dp),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Hear the whole pattern")
                        }
                    }
                }
            }
            item {
                set.note?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            item {
                DemonstrativeOverview(
                    set = set,
                    caseOrder = content.screenConfig.caseOrder,
                    formGroupOrder = content.demonstratives.formGroupOrder,
                )
            }
        }
    }
}

@Composable
private fun DemonstrativeExampleRow(
    pronounCase: PronounCase?,
    example: DemonstrativeExample,
    onSpeak: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pronounCase?.label ?: example.caseId,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(92.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            HighlightedSentence(example.sentence, example.highlight)
            Text(
                example.supportText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onSpeak(example.sentence) }) {
            Icon(Icons.Default.VolumeUp, contentDescription = "Hear ${example.sentence}")
        }
    }
}

@Composable
private fun DemonstrativeOverview(
    set: DemonstrativeSet,
    caseOrder: List<String>,
    formGroupOrder: List<String>,
) {
    Text(
        text = "All ${set.label} forms",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            OverviewRow("Form", "Subject", "Direct", "Recipient", header = true)
            HorizontalDivider()
            formGroupOrder.forEach { formGroup ->
                val forms = set.forms[formGroup].orEmpty()
                OverviewRow(
                    first = formGroup.replaceFirstChar(Char::uppercase),
                    second = caseOrder.getOrNull(0)?.let(forms::get).orEmpty(),
                    third = caseOrder.getOrNull(1)?.let(forms::get).orEmpty(),
                    fourth = caseOrder.getOrNull(2)?.let(forms::get).orEmpty(),
                )
            }
        }
    }
}

@Composable
private fun HighlightedSentence(sentence: String, highlight: String) {
    val text = remember(sentence, highlight) {
        buildAnnotatedString {
            val start = sentence.indexOf(highlight)
            if (start < 0 || highlight.isBlank()) {
                append(sentence)
            } else {
                append(sentence.substring(0, start))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(highlight)
                }
                append(sentence.substring(start + highlight.length))
            }
        }
    }
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Loading pronouns…")
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Try again")
        }
    }
}
