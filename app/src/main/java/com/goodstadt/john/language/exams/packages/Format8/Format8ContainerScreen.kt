package com.goodstadt.john.language.exams.packages.Format8

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodstadt.john.language.exams.managers.DebugPremiumOverride
import com.goodstadt.john.language.exams.models.Format7or10Section
import com.goodstadt.john.language.exams.models.Format8Entry
import com.goodstadt.john.language.exams.models.Format8File
import com.goodstadt.john.language.exams.models.SubTabDefinition
import com.goodstadt.john.language.exams.packages.ReferenceQuiz.ReferenceQuizScreen
import com.goodstadt.john.language.exams.packages.me.PremiumUpgradeSheet
import com.goodstadt.john.language.exams.screens.RateLimitDailyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.shared.MenuItemChip
import com.goodstadt.john.language.exams.ui.theme.LocalExtendedColors
import com.goodstadt.john.language.exams.ui.theme.LocalHighlightUnderline
import com.goodstadt.john.language.exams.utils.findActivity
import com.goodstadt.john.language.exams.packages.Translate.TranslateSheet
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Format8ContainerScreen(
    subTabs: List<SubTabDefinition>,
    selectedSheetName: String?,
    file: Format8File?,
    isLoading: Boolean,
    error: String?,
    onSubTabSelected: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    playback: Format8PlaybackViewModel = hiltViewModel()
) {
    val playingKey by playback.playingKey.collectAsStateWithLifecycle()
    val purchased by playback.isPurchased.collectAsStateWithLifecycle()
    val gapEnabled by playback.gapEnabled.collectAsStateWithLifecycle()
    val prompt by playback.prompt.collectAsStateWithLifecycle()
    val playbackError by playback.error.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val activity = LocalContext.current.findActivity()
    var showTranslate by rememberSaveable(selectedSheetName) { mutableStateOf(false) }
    var showQuiz by rememberSaveable(selectedSheetName) { mutableStateOf(false) }
    var quiz by remember(selectedSheetName) { mutableStateOf<List<Format7or10Section>>(emptyList()) }
    val lockedRanks = remember(file, purchased, DebugPremiumOverride.value) {
        (0 until (file?.sections?.sumOf { it.entries.size } ?: 0)).filter { playback.isLocked(it) }.toSet()
    }
    val quizFile = remember(file, lockedRanks) {
        var rank = 0
        file?.copy(sections = file.sections.sortedBy { it.sortorder }.map { section ->
            section.copy(entries = section.entries.filter { rank++ !in lockedRanks })
        })
    }
    val canQuiz = remember(quizFile) { quizFile != null && Format8QuizGenerator.generate(quizFile, 1).isNotEmpty() }

    DisposableEffect(owner, selectedSheetName) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) playback.stopPlayback()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); playback.stopPlayback() }
    }
    LaunchedEffect(file, selectedSheetName) {
        if (file != null && selectedSheetName != null) playback.onSheetLoaded(file, selectedSheetName)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (subTabs.size > 1) {
            LazyRow(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                items(subTabs, key = { it.firestoreDocumentId }) { tab ->
                    MenuItemChip(text = tab.title, isSelected = tab.firestoreDocumentId == selectedSheetName,
                        onClick = { playback.stopPlayback(); playback.recordSubTabTap(tab.firestoreDocumentId); onSubTabSelected(tab.firestoreDocumentId) })
                }
            }
            HorizontalDivider()
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            when {
                isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
                file != null && selectedSheetName != null -> key(selectedSheetName) {
                    Format8Content(file, selectedSheetName, lockedRanks, playingKey, gapEnabled, playback,
                        modifier = Modifier.widthIn(max = 840.dp).fillMaxSize())
                    FloatingActionButton(
                        onClick = { if (canQuiz && quizFile != null) {
                            playback.stopPlayback(); quiz = Format8QuizGenerator.generate(quizFile); showQuiz = true
                        } },
                        shape = CircleShape, containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = if (canQuiz) LocalExtendedColors.current.screenAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                    ) { Icon(Icons.Default.SportsEsports, if (canQuiz) "Quiz" else "Quiz unavailable for this sheet") }
                    FloatingActionButton(
                        onClick = { playback.stopPlayback(); showTranslate = true },
                        shape = CircleShape, containerColor = MaterialTheme.colorScheme.surface, contentColor = LocalExtendedColors.current.screenAccent,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                    ) { Icon(Icons.Default.Translate, "Translate") }
                }
            }
        }
    }
    when (prompt) {
        Format8PlaybackViewModel.Prompt.PREMIUM -> PremiumUpgradeSheet(onDismiss = playback::dismissPrompt)
        Format8PlaybackViewModel.Prompt.DAILY -> RateLimitDailyPaywallBottomSheet(
            onBuyPremiumButtonPressed = { activity?.let(playback::buyPremium) }, onCloseSheet = playback::dismissPrompt)
        Format8PlaybackViewModel.Prompt.HOURLY -> RateLimitHourlyPaywallBottomSheet(
            onBuyPremiumButtonPressed = { activity?.let(playback::buyPremium) }, onCloseSheet = playback::dismissPrompt)
        null -> Unit
    }
    if (playbackError != null) AlertDialog(onDismissRequest = playback::dismissError,
        title = { Text("Playback") }, text = { Text(playbackError!!) },
        confirmButton = { TextButton(onClick = playback::dismissError) { Text("OK") } })
    if (showTranslate) TranslateSheet(onDismiss = { showTranslate = false }, initialText = playback.lastSentence)
    if (showQuiz && file != null) ModalBottomSheet(onDismissRequest = { showQuiz = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Box(Modifier.fillMaxHeight(0.92f)) {
            ReferenceQuizScreen(category = subTabs.firstOrNull { it.firestoreDocumentId == selectedSheetName }?.title ?: file.title,
                level = "", prebuilt = quiz, prebuiltAreaId = "Pronouns/$selectedSheetName")
        }
    }
}

@Composable
private fun Format8Content(
    file: Format8File, sheetName: String, lockedRanks: Set<Int>, playingKey: String?, gapEnabled: Boolean,
    playback: Format8PlaybackViewModel, modifier: Modifier = Modifier
) {
    val sections = remember(file) { file.sections.sortedBy { it.sortorder } }
    LazyColumn(modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "intro") {
            Surface(shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(file.title, style = MaterialTheme.typography.titleLarge, color = LocalExtendedColors.current.screenAccent, fontWeight = FontWeight.Bold)
                    if (file.description.isNotEmpty()) Text(file.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (file.intro.isNotEmpty()) Text(remember(file) { Format8Presentation.introduction(file) })
                }
            }
        }
        var baseRank = 0
        sections.forEachIndexed { sectionIndex, section ->
            item(key = "section:$sectionIndex") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(section.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (section.rule.isNotEmpty() || section.explanation.isNotEmpty() || sectionIndex == 0) Surface(shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (section.rule.isNotEmpty()) Text(section.rule, fontWeight = FontWeight.SemiBold)
                            if (section.explanation.isNotEmpty()) Text(section.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (sectionIndex == 0) Row(verticalAlignment = Alignment.CenterVertically) {
                                IconToggleButton(checked = gapEnabled, onCheckedChange = playback::setGapEnabled) {
                                    Icon(
                                        imageVector = Icons.Default.RecordVoiceOver,
                                        contentDescription = "Pause to repeat aloud",
                                        modifier = Modifier.size(20.dp),
                                        tint = if (gapEnabled) LocalExtendedColors.current.screenAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text("Pause to repeat aloud", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            section.entries.forEachIndexed { entryIndex, entry ->
                val rank = baseRank + entryIndex
                item(key = "entry:$rank") {
                    Format8EntrySection(entry, rank in lockedRanks,
                        playing = playingKey == playback.sectionKey(sheetName, rank),
                        onToggle = { playback.toggleSection(sheetName, rank, entry) },
                        onPremium = playback::showPremium,
                        onPlay = { playback.playExample(sheetName, rank, it) })
                }
            }
            baseRank += section.entries.size
        }
    }
}

@Composable
private fun Format8EntrySection(
    entry: Format8Entry, locked: Boolean, playing: Boolean, onToggle: () -> Unit,
    onPremium: () -> Unit, onPlay: (com.goodstadt.john.language.exams.models.Format8Example) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(entry.header, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = LocalExtendedColors.current.screenAccent, fontWeight = FontWeight.Bold)
            if (!locked && entry.examples.isNotEmpty()) IconButton(onClick = onToggle) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (playing) "Pause ${entry.header}" else "Play all examples in ${entry.header}", tint = LocalExtendedColors.current.screenAccent)
            }
        }
        if (!locked) {
            if (entry.pattern.isNotEmpty()) Text(entry.pattern, color = Format8Presentation.highlightColor, style = MaterialTheme.typography.bodyMedium)
            if (entry.explanation.isNotEmpty()) Text(entry.explanation, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)) {
            if (locked) Row(Modifier.fillMaxWidth().clickable(onClick = onPremium).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, "Locked", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp)); Text("Premium", color = LocalExtendedColors.current.screenAccent)
            } else entry.examples.forEachIndexed { index, example ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Play German sentence", onClick = { onPlay(example) })
                    .padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val underlineHighlight = LocalHighlightUnderline.current
                    Text(remember(example.german, example.key, underlineHighlight) {
                        Format8Presentation.highlight(Format8Presentation.displaySentence(example.german), listOf(example.key), underlineHighlight)
                    }, Modifier.weight(1f).alignByBaseline(), style = MaterialTheme.typography.bodyLarge)
                    Text(remember(example.english) {
                        Format8Presentation.displaySentence(Format8Presentation.compactEnglish(example.english), Locale.ENGLISH)
                    }, Modifier.weight(1f).alignByBaseline(), textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
