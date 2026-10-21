package com.goodstadt.john.language.exams.packages.Format9

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.goodstadt.john.language.exams.models.Format9Line
import com.goodstadt.john.language.exams.models.Format9Scene
import com.goodstadt.john.language.exams.screens.RateLimitDailyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyPaywallBottomSheet
import com.goodstadt.john.language.exams.ui.theme.Orange
import com.goodstadt.john.language.exams.utils.findActivity
import kotlinx.coroutines.launch

private val SpeakerTeal = Color(0xFF00897B)

/**
 * Podcasts (fileFormat-9): a multi-speaker script. Tap a line to hear it (in that speaker's voice); the
 * top "Play whole script" and each scene's play button run the lines in order; the floating button
 * bottom-right play/pauses whatever is running; the floating translate button bottom-left flips the whole
 * script between German and English (audio always German).
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val currentGerman by viewModel.currentGerman.collectAsStateWithLifecycle()
    val showTranslation by viewModel.showTranslation.collectAsStateWithLifecycle()
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
                                Text(f.title, style = MaterialTheme.typography.titleLarge, color = Orange, fontWeight = FontWeight.Bold)
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
                                        contentDescription = null, tint = Orange, modifier = Modifier.size(32.dp))
                                    Text(if (playingKey != null) "Stop" else "Play whole script",
                                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                }
                                // Scene scrubber (YouTube-style): one notch per scene. Tap a notch to jump to
                                // that scene; if audio is playing it continues from there to the end, otherwise
                                // it just scrolls there.
                                if (scenes.size > 1) {
                                    SceneScrubber(
                                        sceneCount = scenes.size,
                                        focusedIndex = focusedSceneIndex,
                                        onSeek = { i ->
                                            viewModel.seekToScene(i)
                                            scope.launch { listState.animateScrollToItem(sceneHeaderItemIndex(scenes, i)) }
                                        }
                                    )
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
                                line = line,
                                speakerName = viewModel.speakerName(line.speakerId),
                                showTranslation = showTranslation,
                                isPlaying = currentGerman == line.german,
                                onClick = { viewModel.playLine(line) }
                            )
                        }
                    }
                }

                // Floating translate (bottom-left) + play/pause (bottom-right).
                FloatingActionButton(
                    onClick = { viewModel.toggleTranslation() },
                    shape = CircleShape, containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = if (showTranslation) Orange else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
                ) { Icon(Icons.Default.Translate, if (showTranslation) "Showing English" else "Show English") }

                val floatingPlaying = playingKey != null && !isPaused
                FloatingActionButton(
                    onClick = { viewModel.playPauseFloating() },
                    shape = CircleShape, containerColor = MaterialTheme.colorScheme.surface, contentColor = Orange,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                ) { Icon(if (floatingPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (floatingPlaying) "Pause" else "Play") }
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
}

@Composable
private fun SceneHeader(scene: Format9Scene, activePlaying: Boolean, onToggle: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(scene.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                color = Orange, fontWeight = FontWeight.Bold)
            IconButton(onClick = onToggle) {
                Icon(if (activePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (activePlaying) "Stop ${scene.title}" else "Play scene ${scene.title}", tint = Orange)
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
    line: Format9Line,
    speakerName: String,
    showTranslation: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val displayText = if (showTranslation) line.english.ifBlank { line.german } else line.german
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = "Play German line", onClick = onClick)
            .background(if (isPlaying) Orange.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(speakerName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = SpeakerTeal)
        // Highlight the playing line with COLOUR only (not weight): a heavier weight is wider and would
        // reflow a long paragraph's line count when it starts/stops. Matches the iOS fix.
        Text(displayText, style = MaterialTheme.typography.bodyLarge,
            color = if (isPlaying) Orange else MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * A thin YouTube-style bar: one notch per scene with small gaps, laid out edge-to-edge. The current scene
 * (playhead) is solid orange, the rest faint. Each notch sits in a taller invisible column so it's
 * comfortably tappable despite the thin visual.
 */
@Composable
private fun SceneScrubber(
    sceneCount: Int,
    focusedIndex: Int,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for (i in 0 until sceneCount) {
            Box(
                Modifier.weight(1f).height(24.dp).clickable { onSeek(i) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (i == focusedIndex) Orange else Orange.copy(alpha = 0.25f))
                )
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
