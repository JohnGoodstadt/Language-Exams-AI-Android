package com.goodstadt.john.language.exams.screens.reference

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodstadt.john.language.exams.data.repository.FirebaseAudioService
import com.goodstadt.john.language.exams.managers.AudioCacheManager
import com.goodstadt.john.language.exams.models.Format2Level
import com.goodstadt.john.language.exams.screens.StatsSheetEntryPoint
import com.goodstadt.john.language.exams.screens.shared.gamification.SideQuestStatsSheet
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.uti.buildSideQuestData
import com.goodstadt.john.language.exams.viewmodels.GroupedFormat2ViewModel
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupedFormat2Screen(
    viewModel: GroupedFormat2ViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navViewModel: NavigationViewModel = hiltViewModel(context as ComponentActivity)

    // Rate Limit State
    val isRateLimitingSheetVisible by viewModel.showRateLimitSheet.collectAsStateWithLifecycle()
    val isDailyRateLimitingSheetVisible by viewModel.showRateDailyLimitSheet.collectAsStateWithLifecycle()
    val isHourlyRateLimitingSheetVisible by viewModel.showRateHourlyLimitSheet.collectAsStateWithLifecycle()

    // Side Quest Sheet
    var showSideQuestSheet by remember { mutableStateOf(false) }
    val sheetStateSideQuest = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(modifier = Modifier.fillMaxSize()) {

        // 1. SUB-TAB PICKER (Reuse your logic for small screens if needed)
        if (uiState.subTabs.isNotEmpty()) {
            val configuration = LocalConfiguration.current
            val isSmallScreen = configuration.screenWidthDp < 380

        /*    val options = remember(uiState.subTabs, isSmallScreen) {
                uiState.subTabs.map { if (isSmallScreen) getShortTabTitle(it.title) else it.title }
            }*/
            val options = remember(uiState.subTabs, isSmallScreen) {
                uiState.subTabs.map { getShortTabTitle(it.title) }
            }
            val rawSelectedTitle = uiState.selectedSubTab?.title ?: ""
//            val selectedOption = if (isSmallScreen) getShortTabTitle(rawSelectedTitle) else rawSelectedTitle
            val selectedOption = getShortTabTitle(rawSelectedTitle)

            HorizontalLevelPicker(
                options = options,
                selectedOption = selectedOption,
                onOptionSelected = { selectedDisplayTitle ->
                    val newTab = uiState.subTabs.firstOrNull { tab ->
                        val display = getShortTabTitle(tab.title)
                        display == selectedDisplayTitle
                    }
                    if (newTab != null) viewModel.onSubTabSelected(newTab)
                }
            )
        }

        // 2. CONTENT AREA
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (uiState.error != null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: ${uiState.error}", color = Color.Red)
            }
        } else {
            // Render the Format 2 Content
            uiState.currentFormat2File?.let { file ->


                // ✅ USE THE STATELESS COMPONENT
                Format2Content(
                    title = file.title,
                    description = file.description,
                    levels = file.data,

                    // Logic Delegates
                    isHeard = { sentence ->
//                        val id = FirebaseAudioService.generateContentID(sentence)
                        viewModel.isHeard(sentence)
//                        uiState.heardSentenceIDs.contains(id)
                    },
                    getPlayCount = { 0 }, // Optional if you want to implement count logic
                    onPlayTrack = { sentence -> viewModel.handleSentenceTap(sentence) },
                    onShowSideQuestSheet = { showSideQuestSheet = true }
                )
            }
        }
    }

    // --- SHEETS ---

    // Rate Limits
    if (isRateLimitingSheetVisible) RateLimitOKReasonsBottomSheet { viewModel.hideRateOKLimitSheet() }
    if (isDailyRateLimitingSheetVisible) { /* ... RateLimitDailyReasons ... */ }
    if (isHourlyRateLimitingSheetVisible) { /* ... RateLimitHourlyReasons ... */ }

    // Side Quest Sheet
    if (showSideQuestSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSideQuestSheet = false },
            sheetState = sheetStateSideQuest
        ) {
            val acm = viewModel.getAudioCacheManager()
            val referenceCounts by acm.referenceHeardCounts.collectAsStateWithLifecycle()
            val sideQuestData = remember(referenceCounts) { buildSideQuestData(acm) }
            //val refData = remember(referenceCounts) { getReferenceData(acm, referenceCounts) } // if you use this helper

            val entryPoint = remember(context) { EntryPointAccessors.fromApplication(context.applicationContext, StatsSheetEntryPoint::class.java) }

            Box(modifier = Modifier.fillMaxHeight(0.85f)) {
                SideQuestStatsSheet(
                    paragraphCount = viewModel.getAIParagraphCount(),
                    paragraphHeardCount = viewModel.getAIParagraphHeardCount(),
                    conjugations = sideQuestData.conjugations,
                    adjectives = sideQuestData.adjectives,
                    quickRefs = sideQuestData.quickRefs,
                    quizManager = entryPoint.getQuizManager(),
                    xpManager = entryPoint.getXPManager(),
                    onNavigate = { target ->
                        showSideQuestSheet = false
                        navViewModel.requestNavigation(target)
                    },
                    onDismiss = { showSideQuestSheet = false }
                )
            }
        }
    }
}

// Helper (Copy from GroupedSheetScreen if not shared)
private fun getShortTabTitle(original: String): String {
    return when (original) {
        "Good vs Well" -> "Good/Well"
        "Say vs Tell" -> "Say/Tell"
        "Speak vs Talk" -> "Speak/Talk"
        "Hear vs Listen" -> "Hear/Listen"

        else -> original
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Format2Content(
    title: String,
    description: String,
    levels: List<Format2Level>,
    // Callbacks for logic
    isHeard: (String) -> Boolean,
    getPlayCount: (String) -> Int,
    onPlayTrack: (String) -> Unit,
    onShowSideQuestSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // --- 1. Top-Level Title and Description ---
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = orangeLight
                    )
                    Spacer(modifier = Modifier.weight(1f))

                    // Side Quest Icon
                    IconButton(onClick = onShowSideQuestSheet) {
                        Icon(
                            imageVector = Icons.Filled.WorkspacePremium,
                            contentDescription = "Stats",
                            tint = Color(0xFFFF9800)
                        )
                    }
                }
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
            }
        }

        // --- 2. Loop through each 'level' ---
        levels.forEach { level ->
            stickyHeader {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (level.description.isNotBlank()) {
                        Text(
                            text = level.description,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }

            items(
                items = level.wordsAndSentences,
                key = { entry -> "${entry.word}-${entry.definition}" }
            ) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    entry.sentences.forEach { item ->

                        // Use callbacks
                        val isSentenceHeard = isHeard(item.sentence)
                        val count = getPlayCount(item.sentence)

                        Format2Row(
                            word = entry.word,
                            sentence = item.sentence,
                            isHeard = isSentenceHeard,
                            playCount = count,
                            onTapped = {
                                onPlayTrack(item.sentence)
                            },
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
            }
        }
    }
}