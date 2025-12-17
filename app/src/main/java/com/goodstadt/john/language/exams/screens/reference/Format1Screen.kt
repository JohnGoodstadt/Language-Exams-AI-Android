package com.goodstadt.john.language.exams.screens.reference


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Transform
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodstadt.john.language.exams.managers.AudioCacheManager
import com.goodstadt.john.language.exams.models.ReferenceCategory
import com.goodstadt.john.language.exams.models.ReferenceSubItem
import com.goodstadt.john.language.exams.screens.StatsSheetEntryPoint
import com.goodstadt.john.language.exams.screens.shared.SideQuestStatsSheet
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.uti.buildSideQuestData
import dagger.hilt.android.EntryPointAccessors

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Format1Screen(
    viewModel: Format1ViewModel = hiltViewModel(),
    // Note: 'data' is removed from params because it comes from ViewModel state now
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // ✅ Ensure red dots are correct when coming back to app
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Bottom Sheet Logic
    var showSideQuestSheet by remember { mutableStateOf(false) }
    val sheetStateSideQuest = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    when (val state = uiState) {
        is Format1UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is Format1UiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
        }
        is Format1UiState.Success -> {
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

                    // 2. itemsIndexed - else can have duplicate key
                    itemsIndexed(
                        items = section.wordsAndSentences,
                        key = { index, item -> "${item.word}_${item.sentence}_$index" }
                    ) { index, item ->

                        // ✅ CHECK HISTORY FOR RED DOT
//                        val contentID = FirebaseAudioService.generateContentID(item.sentence)
                        val isHeard = viewModel.isHeard(item.sentence)
                        val playCount = viewModel.getPlayCount(item.sentence)
                        // Check Playback State (Optional visual cue)
                       // val isPlaying = (state.playbackState is PlaybackState.Playing) &&
                         //       (state.playbackState.id.contains(FirebaseAudioService.generateUnifiedFilename(item.sentence, ""))) // simplified check

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
                                    color = Color.Cyan,//if(isPlaying) Color.Green else Color.Cyan, // Visual feedback
                                    modifier = Modifier.weight(1f)
                                )

                                // ✅ THE RED DOT
                                if (isHeard) {
                                    Text(text = "🔴", fontSize = 12.sp)
                                }
                                if (playCount > 0) {
                                    Text(text = "$playCount", fontSize = 12.sp)
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

            val context = LocalContext.current
            val entryPoint = remember(context) {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    StatsSheetEntryPoint::class.java
                )
            }

            // 3. Side Quest Sheet
            if (showSideQuestSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSideQuestSheet = false },
                    sheetState = sheetStateSideQuest,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    // 1. Get Managers
                    val audioCache = viewModel.getAudioCacheManager()

                    // 2. Collect Latest Stats (Reactive)
                    // This ensures the sheet has data even if it wasn't pre-calculated
                    val referenceCounts by audioCache.referenceHeardCounts.collectAsStateWithLifecycle()

                    // 3. Build the Data Models using the Helper
                    val refData = remember(referenceCounts) {
                        getReferenceData(audioCache, referenceCounts)
                    }

                    // 4. Get Hilt Entry Point for QuizManager
                    val entryPoint = remember(context) {
                        EntryPointAccessors.fromApplication(context.applicationContext, StatsSheetEntryPoint::class.java)
                    }

                    // 3. Build the Data
                    // We use 'remember(referenceCounts)' so it rebuilds whenever the counts change
                    val sideQuestData = remember(referenceCounts) {
                        buildSideQuestData(audioCache)
                    }

                    Box(modifier = Modifier.fillMaxHeight(0.85f)) {
                        // Note: You might need to pass data in here if SideQuestStatsSheet 
                        // doesn't pull everything from Hilt automatically yet.
                        SideQuestStatsSheet(
                            paragraphCount = 0, // Mock or fetch from VM
                            paragraphHeardCount = 0, // Mock or fetch from VM
                            conjugations = sideQuestData.conjugations,
                            adjectives = sideQuestData.adjectives,
                            quickRefs = sideQuestData.quickRefs,
                            quizManager = entryPoint.getQuizManager(),
                            onDismiss = { showSideQuestSheet = false }
                        )
                    }
                }
            }
        }
    }
}
// MARK: - Data Builder Helper
// This constructs the specific UI models needed by SideQuestStatsSheet
// It maps your specific JSON filenames to readable categories.

data class SideQuestData(
    val conjugations: ReferenceCategory,
    val adjectives: ReferenceCategory,
    val quickRefs: List<ReferenceCategory>
)

fun getReferenceData(manager: AudioCacheManager, heardCounts: Map<String, Int>): SideQuestData {

    // Helper to make sub-items
    fun item(title: String, key: String): ReferenceSubItem {
        val heard = heardCounts[key] ?: 0
        val total = manager.getReferenceStats(key).total
        return ReferenceSubItem(title, heard, maxOf(total, 1)) // Avoid div/0 visual issues
    }

    // 1. Conjugations
    val conjugations = ReferenceCategory(
        title = "Conjugations",
        icon = Icons.Default.Transform, // or ArrowTriangleBranch
        description = "Essential verb variations. Understanding these covers 40% of usage.",
        items = listOf(
            item("To Be", "EnglishConjugationsToBe"),
            item("To Have", "EnglishConjugationsToHave"),
            item("To Do", "EnglishConjugationsToDo"),
            item("To Get", "EnglishConjugationsToGet")
        )
    )

    // 2. Adjectives
    val adjectives = ReferenceCategory(
        title = "Adjectives",
        icon = Icons.Default.Palette,
        description = "Descriptive words ordered by complexity.",
        items = listOf(
            item("Basic", "EnglishA1Adjectives"),
            item("Intermediate", "EnglishA2Adjectives"),
            item("Upper", "EnglishB1Adjectives"),
            item("Advanced", "EnglishB2Adjectives")
        )
    )

    // 3. Quick Refs
    val quickRefs = listOf(
        ReferenceCategory(
            title = "Prepositions",
            icon = Icons.Default.SwapVert,
            description = "Tricky connection words.",
            items = listOf(item("Main", "EnglishPrepositions"))
        ),
        ReferenceCategory(
            title = "Sounds the Same",
            icon = Icons.Default.Hearing,
            description = "Homophones (e.g. There, Their, They're).",
            items = listOf(item("Main", "EnglishDefinitionsFormat1"))
        ),
        ReferenceCategory(
            title = "Good vs Well",
            icon = Icons.Default.CheckCircle,
            description = "Common confusion between adjectives and adverbs.",
            items = listOf(item("Main", "EnglishGoodVsWell"))
        )
    )

    return SideQuestData(conjugations, adjectives, quickRefs)
}