package com.goodstadt.john.language.exams.screens.reference

import androidx.activity.ComponentActivity
import com.goodstadt.john.language.exams.screens.StatsSheetEntryPoint
import com.goodstadt.john.language.exams.screens.shared.gamification.SideQuestStatsSheet
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet


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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.uti.buildSideQuestData
import dagger.hilt.android.EntryPointAccessors

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReferenceGenericScreenNew(
    viewModel: ReferenceGenericViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRateLimitSheetVisible by viewModel.showRateLimitSheet.collectAsStateWithLifecycle()

    // Bottom Sheet State
    var showSideQuestSheet by remember { mutableStateOf(false) }
    val sheetStateSideQuest = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val navViewModel: NavigationViewModel = hiltViewModel(LocalContext.current as ComponentActivity)

    when (val state = uiState) {
        is GenericVocabUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is GenericVocabUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
        }

        is GenericVocabUiState.Success -> {
            Box(modifier = modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    state.categories.forEach { category ->

                        // 1. Sticky Header
                        stickyHeader {
                            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = category.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = orangeLight,
                                        modifier = Modifier.weight(1f)
                                    )
                                    // Stats Icon
                                    if (category == state.categories[0]){
                                        IconButton(onClick = { showSideQuestSheet = true }) {
                                            Icon(
                                                imageVector = Icons.Filled.WorkspacePremium,
                                                contentDescription = "Stats",
                                                tint = Color(0xFFFF9800)
                                            )
                                        }
                                    }

                                }
                                HorizontalDivider()
                            }
                        }

                        // 2. Items
                        items(category.words) { wordEntry ->
                            val sentence = wordEntry.sentences.firstOrNull()?.sentence ?: ""

                            // ✅ Red Dot Logic
                            val isHeard = viewModel.isHeard(sentence)

                            // Visual Playback
//                            val isPlaying = (state.playbackState is PlaybackState.Playing) &&
//                                    (state.playbackState.id.contains(FirebaseAudioService.generateUnifiedFilename(sentence, "")))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                    .clickable {
                                        // ✅ Action
                                        viewModel.handleTap(sentence)
                                    },
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = wordEntry.word,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Cyan,//if(isPlaying) Color.Green else Color.Cyan,
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (isHeard) {
                                        Text(text = "🔴", fontSize = 12.sp)
                                    }
                                }

                                if (sentence.isNotEmpty()) {
                                    Text(
                                        text = sentence,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }

                                if (wordEntry.definition.isNotEmpty()) {
                                    Text(
                                        text = wordEntry.definition,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }

                // --- Sheets ---
                if (isRateLimitSheetVisible) {
                    RateLimitOKReasonsBottomSheet { viewModel.hideRateLimitSheet() }
                }

                if (showSideQuestSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { showSideQuestSheet = false },
                        sheetState = sheetStateSideQuest,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        val acm = viewModel.getAudioCacheManager()

                        // Collect latest stats
                        val referenceCounts by acm.referenceHeardCounts.collectAsStateWithLifecycle()
                        //val refData = remember(referenceCounts) { getReferenceData(acm, referenceCounts) }

                        val manifest = viewModel.getCachedManifest()
                        val sideQuestData = remember(referenceCounts) {
                            buildSideQuestData(acm,manifest)
                        }

                        val entryPoint = remember(key1 = context) {
                            EntryPointAccessors.fromApplication(context.applicationContext, StatsSheetEntryPoint::class.java)
                        }

                        viewModel.incSideQuestStat()

                        Box(modifier = Modifier.fillMaxHeight(0.85f)) {
                            SideQuestStatsSheet(
                                paragraphCount = viewModel.getAIParagraphCount(),
                                paragraphHeardCount = viewModel.getAIParagraphHeardCount(),
                                conjugations = sideQuestData.conjugations,
                                adjectives = sideQuestData.adjectives,
                                pairs = sideQuestData.pairs,
                                quickRefs = sideQuestData.quickRefs,
                                quizManager = entryPoint.getQuizManager(),
                                xpManager = entryPoint.getXPManager(),
                                onNavigate = { target ->
                                    showSideQuestSheet = false // Close sheet first
                                    navViewModel.requestNavigation(target) // Send signal to Parent
                                },
                                onDismiss = { showSideQuestSheet = false }
                            )
                        }
                    }
                }
            }
        }
    }
}