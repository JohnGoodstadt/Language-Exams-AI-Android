package com.goodstadt.john.language.exams.screens.reference


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.managers.FirebaseAudioService
import com.goodstadt.john.language.exams.models.HeaderWordsSentencesList
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.viewmodels.Format1BUiState
import com.goodstadt.john.language.exams.viewmodels.Format1BViewModel
import com.goodstadt.john.language.exams.viewmodels.PlaybackState

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Format1Screen(
    viewModel: Format1BViewModel = hiltViewModel(),
    // Note: 'data' is removed from params because it comes from ViewModel state now
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // Bottom Sheet Logic
    var showSideQuestSheet by remember { mutableStateOf(false) }
    val sheetStateSideQuest = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    when (val state = uiState) {
        is Format1BUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is Format1BUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
        }
        is Format1BUiState.Success -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                state.data.forEach { section ->

                    // 1. Sticky Header
                    stickyHeader {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = orangeLight,
                                modifier = Modifier.weight(1f)
                            )
                            // Side Quest Icon
                            IconButton(onClick = { showSideQuestSheet = true }) {
                                Icon(
                                    imageVector = Icons.Filled.WorkspacePremium,
                                    contentDescription = "Stats",
                                    tint = Color(0xFFFF9800)
                                )
                            }
                        }
                        if (section.description.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp)) {
                                Text(text = section.description, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // 2. Items
                    items(
                        items = section.wordsAndSentences,
                        key = { it.word + it.sentence }
                    ) { item ->

                        // ✅ CHECK HISTORY FOR RED DOT
                        val contentID = FirebaseAudioService.generateContentID(item.sentence)
                        val isHeard = state.heardSentenceIDs.contains(contentID)

                        // Check Playback State (Optional visual cue)
                        val isPlaying = (state.playbackState is PlaybackState.Playing) &&
                                (state.playbackState.id.contains(FirebaseAudioService.generateUnifiedFilename(item.sentence, ""))) // simplified check

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clickable {
                                    // ✅ Simple Action
                                    viewModel.handleTap(item.sentence)
                                }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.word,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if(isPlaying) Color.Green else Color.Cyan, // Visual feedback
                                    modifier = Modifier.weight(1f)
                                )

                                // ✅ THE RED DOT
                                if (isHeard) {
                                    Text(text = "🔴", fontSize = 12.sp)
                                }
                            }

                            // ... (Your Sentence Parts UI) ...
                            Text(text = item.sentence, style = MaterialTheme.typography.bodyLarge)

                            if (item.definition.isNotBlank()) {
                                Text(
                                    text = item.definition,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }

            // 3. Side Quest Sheet
            if (showSideQuestSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSideQuestSheet = false },
                    sheetState = sheetStateSideQuest,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Box(modifier = Modifier.fillMaxHeight(0.85f)) {
                        // Note: You might need to pass data in here if SideQuestStatsSheet 
                        // doesn't pull everything from Hilt automatically yet.
                        SideQuestStatsSheet(
                            paragraphCount = 0, // Mock or fetch from VM
                            paragraphHeardCount = 0, // Mock or fetch from VM
                            conjugations = null, // Adjust based on your Sheet params
                            adjectives = null,
                            quickRefs = emptyList(),
                            onDismiss = { showSideQuestSheet = false }
                        )
                    }
                }
            }
        }
    }
}