package com.goodstadt.john.language.exams.packages.Speeches

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.screens.RateLimitDailyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyPaywallBottomSheet
import com.goodstadt.john.language.exams.ui.theme.LocalExtendedColors
import com.goodstadt.john.language.exams.utils.findActivity
import kotlinx.coroutines.launch

private const val MIN_BOX_DP = 120f
private const val MAX_BOX_DP = 700f

/**
 * "Speeches" sub-tab on the Me tab: the learner prepares a title and text to rehearse (e.g. a reply to
 * "Sprechen Sie Deutsch?"), switches language with the translate button and plays the spoken language.
 * Ready-made answers follow the learner's own. Port of the iOS PreparedSpeechesScreen.
 */
@Composable
fun SpeechesScreen(viewModel: SpeechesViewModel = hiltViewModel()) {
    val speeches by viewModel.speeches.collectAsState()
    val examples by viewModel.examples.collectAsState()
    val prompt by viewModel.prompt.collectAsState()
    val activity = LocalContext.current.findActivity()
    val accent = LocalExtendedColors.current.screenAccent
    var pendingDelete by remember { mutableStateOf<PreparedSpeech?>(null) }

    // Leaving the page stops playback (going to the background keeps it playing), as on iOS.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { viewModel.stopPlayback() } }

    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "Prepare what you want to say, then rehearse it until it feels natural.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = viewModel::add) {
                    Icon(Icons.Default.AddCircle, "Add speech", tint = accent, modifier = Modifier.size(36.dp))
                }
            }
        }
        items(speeches, key = { it.id }) { speech ->
            SpeechCard(speech, viewModel, isBuiltIn = false, onDelete = { pendingDelete = speech })
        }
        if (examples.isNotEmpty()) {
            item {
                Text("Ready-made answers", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = LocalExtendedColors.current.sectionHeader,
                    modifier = Modifier.padding(top = 8.dp))
            }
            items(examples, key = { it.id }) { example ->
                SpeechCard(example, viewModel, isBuiltIn = true, onDelete = {})
            }
        }
    }

    pendingDelete?.let { speech ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this speech?") },
            text = { Text(if (speech.title.isNotBlank()) "\"${speech.title}\" will be removed." else "This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(speech.id); pendingDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }

    when (prompt) {
        SpeechesViewModel.Prompt.DAILY -> RateLimitDailyPaywallBottomSheet(
            onBuyPremiumButtonPressed = { activity?.let(viewModel::buyPremium) }, onCloseSheet = viewModel::dismissPrompt)
        SpeechesViewModel.Prompt.HOURLY -> RateLimitHourlyPaywallBottomSheet(
            onBuyPremiumButtonPressed = { activity?.let(viewModel::buyPremium) }, onCloseSheet = viewModel::dismissPrompt)
        null -> Unit
    }
}

/**
 * One title + body pair. The right-hand column swaps language, locks and plays; the body box has a drag
 * handle at its bottom right, like a resizable text box on the web. Built-in answers have no delete or lock.
 */
@Composable
private fun SpeechCard(
    speech: PreparedSpeech,
    viewModel: SpeechesViewModel,
    isBuiltIn: Boolean,
    onDelete: () -> Unit
) {
    val accent = LocalExtendedColors.current.screenAccent
    val fieldBg = MaterialTheme.colorScheme.onSurface.copy(alpha = if (speech.isLocked) 0.05f else 0.09f)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current

    var showingSpoken by rememberSaveable(speech.id) { mutableStateOf(speech.spokenText.isNotEmpty()) }
    var isTranslating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var bodyFocused by remember { mutableStateOf(false) }

    val playingKey by viewModel.playingKey.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val isPlayingThis = playingKey == viewModel.playKey(speech.id) && !isPaused

    val shownLang = if (showingSpoken) viewModel.spokenLang else viewModel.nativeLang
    val shownText = if (showingSpoken) speech.spokenText else speech.nativeText
    val canPlay = showingSpoken && speech.spokenText.isNotBlank()
    val title = (if (!showingSpoken && isBuiltIn) viewModel.nativeTitle(speech.id) else null) ?: speech.title

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Title row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(fieldBg)) {
                if (speech.isLocked) {
                    Text(
                        title.ifBlank { "Untitled" },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (title.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    )
                } else {
                    TextField(
                        value = speech.title,
                        onValueChange = { v -> viewModel.update(speech.id) { it.copy(title = v) } },
                        placeholder = { Text("Title, e.g. My Intro") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleMedium,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = clearFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            if (isBuiltIn) {
                Spacer(Modifier.width(48.dp))
            } else {
                IconButton(onClick = onDelete, enabled = !speech.isLocked, modifier = Modifier.width(48.dp)) {
                    Icon(Icons.Outlined.Delete, if (speech.isLocked) "Unlock to delete" else "Delete speech",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (speech.isLocked) 0.3f else 1f))
                }
            }
        }

        Row(verticalAlignment = Alignment.Top) {
            // Body box with drag-to-resize handle
            Box(
                Modifier.weight(1f).height(speech.boxHeight.dp)
                    .clip(RoundedCornerShape(10.dp)).background(fieldBg)
            ) {
                if (speech.isLocked) {
                    // Read-only but still scrollable and selectable.
                    SelectionContainer(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Text(
                            shownText.ifEmpty { "Nothing written in ${shownLang.display} yet" },
                            color = if (shownText.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 26.dp)
                        )
                    }
                } else {
                    TextField(
                        value = shownText,
                        onValueChange = { v ->
                            viewModel.update(speech.id) { if (showingSpoken) it.copy(spokenText = v) else it.copy(nativeText = v) }
                        },
                        placeholder = { Text("Write what you want to say in ${shownLang.display}") },
                        enabled = !isTranslating,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        // An English keyboard would "correct" German words.
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            autoCorrectEnabled = !showingSpoken
                        ),
                        colors = clearFieldColors(),
                        modifier = Modifier.fillMaxSize().padding(bottom = 14.dp)
                            .onFocusChanged { bodyFocused = it.isFocused }
                    )
                }
                if (isTranslating) CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp))
                // Drag handle: bottom right
                Icon(
                    Icons.Default.DragHandle, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(28.dp).rotate(-45f)
                        .semantics { contentDescription = "Resize text box" }
                        .pointerInput(speech.id) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                val dy = with(density) { drag.y.toDp().value }
                                viewModel.update(speech.id) {
                                    it.copy(boxHeight = (it.boxHeight + dy).coerceIn(MIN_BOX_DP, MAX_BOX_DP))
                                }
                            }
                        }
                )
            }

            // Right-hand column: translate (+ language), lock, play, Done
            Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    enabled = !isTranslating,
                    onClick = {
                        focusManager.clearFocus()
                        errorMessage = null
                        isTranslating = true
                        val fromSpoken = showingSpoken
                        scope.launch {
                            if (viewModel.prepareSwitch(speech.id, fromSpoken)) showingSpoken = !fromSpoken
                            else errorMessage = "Couldn't translate. Check your connection and try again."
                            isTranslating = false
                        }
                    }
                ) { Icon(Icons.Default.Translate, "Showing ${shownLang.display}. Switch language", tint = accent) }
                Text(shownLang.code.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!isBuiltIn) {
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        viewModel.update(speech.id) { it.copy(isLocked = !it.isLocked) }
                    }) {
                        Icon(
                            if (speech.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                            if (speech.isLocked) "Locked. Unlock to edit" else "Unlocked. Lock to stop editing",
                            tint = if (speech.isLocked) accent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(enabled = canPlay, onClick = { focusManager.clearFocus(); viewModel.playOrPause(speech) }) {
                    Icon(
                        if (isPlayingThis) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                        if (isPlayingThis) "Pause" else "Play",
                        tint = if (canPlay) accent else Color.Gray.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    )
                }
                if (bodyFocused) {
                    TextButton(onClick = { focusManager.clearFocus() }) {
                        Text("Done", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }

        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun clearFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent
)
