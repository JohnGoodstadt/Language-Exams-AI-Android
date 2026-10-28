package com.goodstadt.john.language.exams.packages.Format9

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import java.util.Locale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodstadt.john.language.exams.models.Format9Scene
import com.goodstadt.john.language.exams.screens.RateLimitDailyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyPaywallBottomSheet
import com.goodstadt.john.language.exams.ui.theme.LocalExtendedColors
import com.goodstadt.john.language.exams.utils.findActivity
import kotlinx.coroutines.launch


/**
 * Podcasts (fileFormat-9): a multi-speaker script. Tap a line to hear it (in that speaker's voice); the
 * top "Play whole script" and each scene's play button run the lines in order; the floating button
 * bottom-right play/pauses whatever is running; the floating translate button bottom-left flips the whole
 * script between the sheet language and its translation (audio keeps the sheet language).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Format9ContainerScreen(
    documentId: String,
    modifier: Modifier = Modifier,
    viewModel: Format9ViewModel = hiltViewModel()
) {
    LaunchedEffect(documentId) { viewModel.load(documentId) }
    val file by viewModel.file.collectAsStateWithLifecycle()
    val loadError by viewModel.error.collectAsStateWithLifecycle()
    val playingKey by viewModel.playingKey.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val currentSpokenText by viewModel.currentSpokenText.collectAsStateWithLifecycle()
    val showTranslation by viewModel.showTranslation.collectAsStateWithLifecycle()
    val translationLanguage by viewModel.translationLanguage.collectAsStateWithLifecycle()
    var showLanguageChooser by remember { mutableStateOf(false) }
    val focusedSceneIndex by viewModel.focusedSceneIndex.collectAsStateWithLifecycle()
    val prompt by viewModel.prompt.collectAsStateWithLifecycle()
    val playbackError by viewModel.playbackError.collectAsStateWithLifecycle()
    val ownerLifecycle = LocalLifecycleOwner.current
    val activity = LocalContext.current.findActivity()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    DisposableEffect(ownerLifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) viewModel.stopPlayback()
        }
        ownerLifecycle.lifecycle.addObserver(observer)
        onDispose { ownerLifecycle.lifecycle.removeObserver(observer); viewModel.stopPlayback() }
    }

    fun activelyPlaying(key: String) = playingKey == key && !isPaused

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when {
            loadError != null -> Column(
                Modifier.align(Alignment.Center).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) { Text("Could not load this podcast.\n$loadError", color = MaterialTheme.colorScheme.error) }

            file == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))

            else -> {
                val f = file!!
                val scenes = viewModel.orderedScenes()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item(key = "intro") {
                        Surface(shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(f.title, style = MaterialTheme.typography.titleLarge, color = LocalExtendedColors.current.screenAccent, fontWeight = FontWeight.Bold)
                                if (f.description.isNotEmpty()) {
                                    Text(f.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Listening time: ${viewModel.formattedDuration(viewModel.totalEstimatedSeconds())}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                // Reflects ANY playback (line / scene / whole-script): shows pause + "Stop"
                                // while something is playing, so a single-line tap flips it too.
                                val anyPlaying = playingKey != null && !isPaused
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .clickable { if (playingKey != null) viewModel.stopPlayback() else viewModel.togglePlayAll() }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(if (anyPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null, tint = LocalExtendedColors.current.screenAccent, modifier = Modifier.size(32.dp))
                                    Text(if (playingKey != null) "Stop" else "Play whole script",
                                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    scenes.forEachIndexed { index, scene ->
                        item(key = "scene:${scene.id}:$index") {
                            SceneHeader(
                                scene = scene,
                                activePlaying = activelyPlaying(viewModel.sceneKey(scene)),
                                onToggle = { viewModel.toggleScene(scene) }
                            )
                        }
                        items(scene.lines.sortedBy { it.sortorder }, key = { it.id }) { line ->
                            PodcastLineRow(
                                displayText = viewModel.displayText(line, translationLanguage, showTranslation),
                                speakerName = viewModel.speakerName(line.speakerId),
                                isPlaying = currentSpokenText == viewModel.spokenText(line),
                                onClick = { viewModel.playLine(line) }
                            )
                        }
                    }
                }

                // Floating translate (bottom-left) + play/pause (bottom-right). A FAB-styled Surface (not a
                // FloatingActionButton) so tap AND long-press work via combinedClickable: tap toggles the
                // translation (or opens the chooser the first time / when the stored language isn't in this
                // script); long-press re-opens the chooser to change language.
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .size(56.dp)
                        .combinedClickable(
                            onClick = {
                                if (viewModel.hasValidTranslationLanguage()) viewModel.toggleTranslation()
                                else showLanguageChooser = true
                            },
                            onLongClick = { showLanguageChooser = true }
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Translate,
                            contentDescription = if (showTranslation) "Showing translation" else "Show translation",
                            tint = if (showTranslation) LocalExtendedColors.current.statusOrange else MaterialTheme.colorScheme.onSurfaceVariant // orange like the other floating buttons
                        )
                    }
                }

                val floatingPlaying = playingKey != null && !isPaused
                FloatingActionButton(
                    onClick = { viewModel.playPauseFloating() },
                    shape = CircleShape, containerColor = MaterialTheme.colorScheme.surface, contentColor = LocalExtendedColors.current.statusOrange, // matches Format6/Conjugations FABs
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                ) { Icon(if (floatingPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (floatingPlaying) "Pause" else "Play") }

                // Floating scene scrubber (YouTube-style) sitting BETWEEN the translate and play/pause FABs.
                // Tap a notch to jump to that scene; playing continues from there to the end, paused moves the
                // resume point, idle just scrolls.
                if (scenes.size > 1) {
                    SceneScrubber(
                        sceneCount = scenes.size,
                        focusedIndex = focusedSceneIndex,
                        onSeek = { i ->
                            viewModel.seekToScene(i)
                            scope.launch { listState.animateScrollToItem(sceneHeaderItemIndex(scenes, i)) }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp, start = 84.dp, end = 84.dp)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }

    when (prompt) {
        Format9ViewModel.Prompt.DAILY -> RateLimitDailyPaywallBottomSheet(
            onBuyPremiumButtonPressed = { activity?.let(viewModel::buyPremium) }, onCloseSheet = viewModel::dismissPrompt)
        Format9ViewModel.Prompt.HOURLY -> RateLimitHourlyPaywallBottomSheet(
            onBuyPremiumButtonPressed = { activity?.let(viewModel::buyPremium) }, onCloseSheet = viewModel::dismissPrompt)
        null -> Unit
    }
    if (playbackError != null) AlertDialog(
        onDismissRequest = viewModel::dismissError,
        title = { Text("Playback") }, text = { Text(playbackError!!) },
        confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } })

    if (showLanguageChooser) {
        TranslationLanguageChooserSheet(
            codes = viewModel.availableTranslationCodes,
            initialSelection = translationLanguage.ifBlank { viewModel.defaultTranslationCode() },
            onChoose = { code -> viewModel.setTranslationLanguage(code); showLanguageChooser = false },
            onDismiss = { showLanguageChooser = false }
        )
    }
}

@Composable
private fun SceneHeader(scene: Format9Scene, activePlaying: Boolean, onToggle: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(scene.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                color = LocalExtendedColors.current.screenAccent, fontWeight = FontWeight.Bold)
            IconButton(onClick = onToggle) {
                Icon(if (activePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (activePlaying) "Stop ${scene.title}" else "Play scene ${scene.title}", tint = LocalExtendedColors.current.screenAccent)
            }
        }
        val placeTime = listOf(scene.place, scene.timeOfDay).filter { it.isNotBlank() }.joinToString("  ·  ")
        if (placeTime.isNotEmpty()) {
            Text(placeTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (scene.summary.isNotEmpty()) {
            Text(scene.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PodcastLineRow(
    displayText: String,
    speakerName: String,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = "Play line", onClick = onClick)
            .background(if (isPlaying) LocalExtendedColors.current.screenAccent.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(speakerName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = LocalExtendedColors.current.teal)
        // Highlight the playing line with COLOUR only (not weight): a heavier weight is wider and would
        // reflow a long paragraph's line count when it starts/stops. Matches the iOS fix.
        Text(displayText, style = MaterialTheme.typography.bodyLarge,
            color = if (isPlaying) LocalExtendedColors.current.screenAccent else MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Floating YouTube-style scrubber shown between the two FABs: a rounded capsule of notches, one per scene
 * with small gaps. The current scene (playhead) is solid orange, the rest faint. Each notch sits in a taller
 * invisible column so it's comfortably tappable despite the thin visual.
 */
@Composable
private fun SceneScrubber(
    sceneCount: Int,
    focusedIndex: Int,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until sceneCount) {
                Box(
                    Modifier.weight(1f).height(40.dp).clickable { onSeek(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (i == focusedIndex) LocalExtendedColors.current.screenAccent else LocalExtendedColors.current.screenAccent.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}

/**
 * Flat LazyColumn item index of scene [sceneIndex]'s header. Layout is: the intro item (0), then for each
 * scene a header item plus one item per line. Used to scroll a tapped scrubber notch into view.
 */
private fun sceneHeaderItemIndex(scenes: List<Format9Scene>, sceneIndex: Int): Int {
    var idx = 1 // intro item
    for (i in 0 until sceneIndex) idx += 1 + scenes[i].lines.size
    return idx
}

/**
 * First-run bottom sheet that lets the user pick which of the script's available languages to show as the
 * translation. Standard Material 3 controls: a ModalBottomSheet with RadioButton rows (full language name
 * + "See the script in <X>") and a "Choose this one" button. Opened on the first tap of the translate
 * button, and reachable later via a long-press on it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslationLanguageChooserSheet(
    codes: List<String>,
    initialSelection: String,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember {
        mutableStateOf(if (codes.contains(initialSelection)) initialSelection else codes.firstOrNull().orEmpty())
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp)) {
            Text(
                "Choose which language for translation",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "The translate button will show the script in this language. You can change it later by long-pressing the translate button.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            codes.forEach { code ->
                val name = languageDisplayName(code)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(selected = code == selected, role = Role.RadioButton, onClick = { selected = code })
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = code == selected, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "See the script in $name",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (code != codes.last()) HorizontalDivider()
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onChoose(selected) },
                enabled = selected.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Choose this one") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Device-localised full name for a two-letter code ("es" -> "Spanish"); falls back to the code. */
private fun languageDisplayName(code: String): String =
    Locale(code).getDisplayLanguage(Locale.getDefault())
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        .ifBlank { code.uppercase(Locale.ROOT) }
