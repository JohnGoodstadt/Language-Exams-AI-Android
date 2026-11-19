package com.goodstadt.john.language.exams.packages.VocabQuiz

import com.goodstadt.john.language.exams.screens.shared.LaunchSpinner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import com.goodstadt.john.language.exams.ui.theme.LocalExtendedColors
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goodstadt.john.language.exams.models.CategoryMasteryStats
import com.goodstadt.john.language.exams.models.DashboardUiState
import com.goodstadt.john.language.exams.packages.CategoryTab.SectionQuizContainer
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.goodstadt.john.language.exams.data.repository.VocabQuizRepository
import com.goodstadt.john.language.exams.packages.UsageQuiz.RowPulseTier
import com.goodstadt.john.language.exams.packages.UsageQuiz.color
import com.goodstadt.john.language.exams.packages.UsageQuiz.StatsTag
import com.goodstadt.john.language.exams.packages.UsageQuiz.DailyGoalRing
import com.goodstadt.john.language.exams.packages.UsageQuiz.DailyGoalExplainer
import com.goodstadt.john.language.exams.packages.UsageQuiz.LevelFireworks
import com.goodstadt.john.language.exams.packages.UsageQuiz.playChimeUnlessSilent
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabDashboardScreen(
    viewModel: VocabDashboardViewModel = hiltViewModel(),
    onNavigateToQuiz: (String?) -> Unit // Pass category title, or null for "Mixed Review"
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentQuizTitle by viewModel.currentQuizTitle.collectAsState()
    val quizSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val showSmartReview by viewModel.showSmartReviewSheet.collectAsState()
    val smartReviewSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Gamification (mirrors iOS): when a section's quiz closes after a FINISHED go, its row pulses (1 blue /
    // 2 green / 3 gold + crown + chime), "New best!" and "+N XP" show on it, then a level-mastered
    // (fireworks) or daily-goal banner. The section is snapshotted as the quiz opens and compared after
    // the sheet closes (Android records the go when the sheet closes, hence the short wait).
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val fx = remember { SectionRowFx() }
    var snapshot by remember { mutableStateOf<VocabDashboardViewModel.SectionSnapshot?>(null) }
    LaunchedEffect(currentQuizTitle) {
        val title = currentQuizTitle
        if (title != null) { snapshot = viewModel.snapshotSection(title); return@LaunchedEffect }
        val snap = snapshot ?: return@LaunchedEffect
        snapshot = null
        delay(600) // let the sheet close and its go be recorded
        val out = viewModel.outcomeSince(snap) ?: return@LaunchedEffect
        fx.play(out, context, haptics, viewModel)
    }
    // "All caught up! ✓": Review Now cleared every due word.
    var dueBeforeReview by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(showSmartReview) {
        if (showSmartReview) { dueBeforeReview = viewModel.uiState.value.wordsDueCount; return@LaunchedEffect }
        val before = dueBeforeReview ?: return@LaunchedEffect
        dueBeforeReview = null
        delay(700) // the dashboard reloads after the review saves
        if (before > 0 && viewModel.uiState.value.wordsDueCount == 0) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.showBanner("All caught up! ✓", "No words left to review today.")
        }
    }
    if (fx.showFireworks) LevelFireworks(onDone = { fx.showFireworks = false })

    if (uiState.isLoading) {
        Box(
            Modifier.fillMaxSize(), contentAlignment = Alignment.Center
        ) { LaunchSpinner() }
    } else if (uiState.isColdStart) {
        // STATE A: Cold Start — suggestion buttons open the quiz sheet directly
        VocabQuizOnboardingView(categories = uiState.categoryStats, onStartSuggestion = { title ->
            viewModel.openQuizForCategory(title)
        })
    } else {
        // STATE B: Active Dashboard
        VocabQuizActiveView(
            onStartReview = {
                viewModel.openSmartReview()
            },
            state = uiState,
            onCategoryClick = { title -> viewModel.openQuizForCategory(title) },
            onDebugClick = { viewModel.debugResetVocabProgress() },
            viewModel = viewModel,
            fx = fx
        )
    }

    // Bottom sheets live outside the if/else so they work from both onboarding and active states
    if (currentQuizTitle != null) {
        var isQuizDirty by remember { mutableStateOf(false) }
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.closeQuizSheet(isDirty = isQuizDirty)
            },
            sheetState = quizSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SectionQuizContainer(categoryTitle = currentQuizTitle!!,
                onInteraction = { dirty -> isQuizDirty = dirty })
        }
    }
    if (showSmartReview) {
        var isSessionDirty by remember { mutableStateOf(false) }
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.closeSmartReview(isDirty = isSessionDirty)
            },
            sheetState = smartReviewSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SmartReviewContainer(
                onInteraction = { dirty ->
                    isSessionDirty = dirty
                }
            )
        }
    }
}

@Composable
fun VocabQuizActiveView(
    state: DashboardUiState,
    onStartReview: () -> Unit,
    onCategoryClick: (String) -> Unit,
    onDebugClick: () -> Unit,
    viewModel: VocabDashboardViewModel? = null,   // gamification data (null in previews)
    fx: SectionRowFx = remember { SectionRowFx() }
) {
    val buttonBlue = LocalExtendedColors.current.buttonBlue
    // Re-read time-based bits ("12m", "↻ ready", "N due") once a minute.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); nowMillis = System.currentTimeMillis() } }
    var showRowGuide by remember { mutableStateOf(false) }
    var showGoalPopup by remember { mutableStateOf(false) }
    if (showRowGuide) VocabRowGuideSheet(onDismiss = { showRowGuide = false })
    // The category panel gets a MIN height reaching the bottom of the screen (from where it starts),
    // so it stretches when the list is short but grows and scrolls when it is long. (A weight(1f)
    // panel inside heightIn(min) capped it at screen height and broke scrolling.)
    val density = LocalDensity.current
    var panelTop by remember { mutableStateOf(0.dp) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val screenHeight = maxHeight
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // Narrower side margin so the blue card is wider; other content adds 8dp back.
            .padding(horizontal = 8.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Due Today Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = buttonBlue,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Outer Column to stack the top section and the bottom list
            Column(modifier = Modifier.padding(20.dp)) {

                // Top Section: Number + "Review Now" Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Daily study streak (the app-wide XP streak).
                        val streak = remember(state) { viewModel?.currentStreak() ?: 0 }
                        if (streak > 0) {
                            Text(
                                "🔥 $streak-day streak",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        val label = if (state.wordsDueCount == 1) "word" else "words"
                        val yet = if (state.wordsDueCount == 0) "yet." else ""
                        Text(
                            "${state.wordsDueCount}",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "$label due for review $yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = onStartReview,
                        enabled = state.wordsDueCount != 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = buttonBlue,
                            disabledContainerColor = Color.White.copy(alpha = 0.5f),
                            disabledContentColor = buttonBlue.copy(alpha = 0.5f)
                        )
                    ) {
                        Text("Review Now")
                    }
                }

                // Bottom Section: The sorted list of words
                Spacer(modifier = Modifier.height(12.dp)) // Add some breathing room
                if (state.wordsDueCount != 0) {
                    Text(
                        text = "${state.wordsDueList
                            .sortedBy { it.length }
                            .joinToString(", ").take(55)}...",
                        // We can now remove .take(60) if you want to show more!
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp
                        ),
                        color = Color.White
                    )
                }else{
                    Text(
                        text = "Tap on a section below to start quizzing yourself",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }

            }
        }

        // 2. Breakdown Title + Info Button
        var showScoringInfo by remember { mutableStateOf(false) }

        // Heading + its subtitle grouped, so the outer column's 20dp spacing doesn't split them.
        Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Progress",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                // Level crown: every section in this level mastered.
                val levelDone = remember(state) { viewModel?.isLevelMastered() ?: false }
                if (levelDone) {
                    Text("  ${state.level} 👑", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                }
            }
            Spacer(Modifier.weight(1f))
            // Vocab daily goal ring (separate from the Usage Quiz's) - tap for what it means.
            if (viewModel != null) {
                val doneToday = remember(state, nowMillis) { viewModel.sectionsCompletedToday() }
                Box {
                    DailyGoalRing(done = doneToday, goal = VocabQuizRepository.DAILY_GOAL,
                        modifier = Modifier.clickable { showGoalPopup = true })
                    DropdownMenu(expanded = showGoalPopup, onDismissRequest = { showGoalPopup = false }) {
                        DailyGoalExplainer(done = doneToday, goal = VocabQuizRepository.DAILY_GOAL, things = "vocab sections")
                    }
                }
            }
            IconButton(onClick = { showScoringInfo = true }) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "How scoring works",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // What "Mastered" means (same wording as iOS).
        Text(
            "Mastered: every word right first time, on 3 different days. A go with mistakes never loses the days you have already earned.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        } //: Column - heading + subtitle

        if (showScoringInfo) {
            ScoringInfoBottomSheet(onDismiss = { showScoringInfo = false })
        }

        // 3. Category List, on a grouped panel (white on light, black on dark) down to the bottom
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .onGloballyPositioned { panelTop = with(density) { it.positionInParent().y.toDp() } }
                // screen - top padding - panel's offset - bottom padding
                .heightIn(min = (screenHeight - 16.dp - panelTop - 16.dp).coerceAtLeast(0.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(LocalExtendedColors.current.groupedPanel)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            state.categoryStats.forEach { stat ->
                CategoryMasteryRow(
                    stat,
                    onClick = { onCategoryClick(stat.categoryTitle) },
                    viewModel = viewModel,
                    nowMillis = nowMillis,
                    stateKey = state,
                    fx = fx,
                    onStatsTap = { showRowGuide = true }
                )
            }
        }
//        if (BuildConfig.DEBUG) {
        if (false) {
            Column {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            Timber.w("debugResetVocabProgress")
                            // viewModel.debugResetVocabProgress()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Clear All Data (D)")
                    }
                }
            }

        }
    }
    }
}

/**
 * One section on the dashboard, laid out like the Usage Quiz rows (mirrors iOS):
 *   1. play icon, title, word count (or "12/19 words learned")
 *   2. stats line "20m · 5x · ●●○ · 8/8 7/8 8/8" (or "… · 3 on 3 days · Mastered 👑") + "↻ ready" / "N due" /
 *      "New best!" - tap it for the guide
 *   3. the existing progress line ("1 of 3 days · last go 6 of 8" + what's next), kept for comparison.
 * Pulses after its quiz closed, with "+N XP" rising from it. Tapping the row opens that section's quiz.
 */
@Composable
fun CategoryMasteryRow(
    stat: CategoryMasteryStats,
    onClick: () -> Unit,
    viewModel: VocabDashboardViewModel? = null,
    nowMillis: Long = System.currentTimeMillis(),
    stateKey: Any? = null,                // re-read the gamification bits when the dashboard reloads
    fx: SectionRowFx = remember { SectionRowFx() },
    onStatsTap: () -> Unit = {},
) {
    val progress = stat.dayProgress // per-category "N of 3 days"; null until the quiz is played
    val pulsing = fx.pulsingTitle == stat.categoryTitle
    val pulseAmount = if (pulsing) fx.amount.value else 0f
    Box(modifier = Modifier.fillMaxWidth()) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(LocalExtendedColors.current.cardBackground)
            // Row pulse after its quiz closed.
            .background(if (pulsing) fx.tier.color.copy(alpha = 0.35f * pulseAmount) else Color.Transparent)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        // 1. Header: play icon, title, count / words learned
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = "Start quiz",
                tint = LocalExtendedColors.current.buttonBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                stat.categoryTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            // Words learned (each right first time on 3 different days) - "learned", so "Mastered" stays
            // for the whole section.
            Text(
                if (stat.masteredCount > 0) "${stat.masteredCount}/${stat.totalWords} words learned" else "${stat.totalWords} Words",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 2. Stats line (Usage-Quiz style)
        if (viewModel != null) {
            val summary = remember(stat, stateKey, nowMillis) { viewModel.sectionSummary(stat, nowMillis) }
            if (summary != null) {
                val mastered = remember(stat, stateKey) { viewModel.isSectionMastered(stat) }
                val ready = remember(stat, stateKey, nowMillis) { viewModel.isSectionReady(stat, nowMillis) }
                val due = remember(stat, stateKey, nowMillis) { viewModel.sectionDueCount(stat, nowMillis) }
                val style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")
                val textScale = if (pulsing && fx.tier == RowPulseTier.PERFECT) 1f + 0.12f * pulseAmount else 1f
                val crownScale = if (pulsing && fx.tier == RowPulseTier.MASTERED) 1f + 0.8f * pulseAmount else 1f
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onStatsTap() } // tap the stats line for the guide (title opens the quiz)
                        .graphicsLayer {
                            scaleX = textScale; scaleY = textScale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                ) {
                    Text(summary, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (mastered) {
                        Text("👑", style = style, modifier = Modifier
                            .padding(start = 4.dp)
                            .graphicsLayer { scaleX = crownScale; scaleY = crownScale })
                    }
                    if (ready) StatsTag("↻ ready", Color(0xFF2196F3), filled = false, style = style)
                    if (due > 0 && !mastered) StatsTag("$due due", Color(0xFF9C27B0), filled = false, style = style)
                    // "New best!" gives way to "↻ ready" (both together don't fit on one line).
                    if (fx.newBestTitle == stat.categoryTitle && !ready)
                        StatsTag("New best!", Color(0xFFFF9800), filled = true, style = style)
                }
            }
        }

        // 3. Progress line: "N of 3 days" (or the last go's score) + what's next
        if (progress != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    progress.progressLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    progress.nextStep,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (progress.isMastered) Color(0xFF4CAF50) else LocalExtendedColors.current.buttonBlue,
                    maxLines = 1
                )
            }
        }
    }
    // "+N XP" rising from the row and fading, after its quiz closed.
    if (fx.xpTitle == stat.categoryTitle) {
        val p = fx.xpProgress.value
        Text(
            "+${fx.xpAmount} XP",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF9800),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 40.dp)
                .graphicsLayer {
                    translationY = -60.dp.toPx() * p
                    alpha = 1f - p
                    scaleX = 1f + 0.3f * p; scaleY = scaleX
                }
        )
    }
    } // Box
}

/**
 * Animation state for the section rows (row pulse, New best, XP fly-in, level fireworks). [play] runs the
 * whole sequence for one finished go, then the level-mastered or daily-goal moment. Mirrors iOS.
 */
class SectionRowFx {
    var pulsingTitle by mutableStateOf<String?>(null)
    var tier by mutableStateOf(RowPulseTier.FINISHED)
    val amount = Animatable(0f)
    var newBestTitle by mutableStateOf<String?>(null)
    var xpTitle by mutableStateOf<String?>(null)
    var xpAmount by mutableIntStateOf(0)
    val xpProgress = Animatable(0f)
    var showFireworks by mutableStateOf(false)

    suspend fun play(
        out: VocabDashboardViewModel.SectionOutcome,
        context: android.content.Context,
        haptics: HapticFeedback,
        viewModel: VocabDashboardViewModel,
    ) = coroutineScope {
        tier = out.tier
        amount.snapTo(0f)
        pulsingTitle = out.title
        delay(250)
        when (out.tier) {
            RowPulseTier.FINISHED -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            RowPulseTier.PERFECT -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            RowPulseTier.MASTERED -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                playChimeUnlessSilent(context)
            }
        }
        if (out.newBest) newBestTitle = out.title
        if (out.xpEarned > 0) {
            xpAmount = out.xpEarned
            xpProgress.snapTo(0f)
            xpTitle = out.title
            launch { xpProgress.animateTo(1f, tween(1400)) }
        }
        val animationsOff = Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        repeat(if (animationsOff) 1 else out.tier.pulses) {
            amount.animateTo(1f, tween(400))
            amount.animateTo(0f, tween(400))
            delay(50)
        }
        pulsingTitle = null
        xpTitle = null
        if (out.levelMastered) {
            playChimeUnlessSilent(context)
            showFireworks = true
            viewModel.showBanner("${viewModel.uiState.value.level} vocab mastered! 👑",
                "You've mastered every section in this level.")
        } else if (out.dailyGoalDone) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.showBanner("Daily goal done! 🎯", "${VocabQuizRepository.DAILY_GOAL} vocab sections today. Great work.")
        }
    }
}

/** Guide sheet from tapping a section row's stats line: what each symbol means. Mirrors iOS. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabRowGuideSheet(onDismiss: () -> Unit) {
    val items = listOf(
        Triple("20m · 5x", MaterialTheme.colorScheme.onSurfaceVariant, "Time since your last go, and how many times you've finished this section."),
        Triple("8/8 7/8", MaterialTheme.colorScheme.onSurfaceVariant, "Your last 3 scores, newest first (words right first time)."),
        Triple("●●○", MaterialTheme.colorScheme.onSurface, "Perfect goes on different days. 3 different days masters the section."),
        Triple("👑", Color(0xFFFFC107), "Mastered: every word right first time, on 3 different days."),
        Triple("↻ ready", Color(0xFF2196F3), "A perfect go today will count towards the crown."),
        Triple("5 due", Color(0xFF9C27B0), "Words in this section that are due for review now."),
        Triple("New best!", Color(0xFFFF9800), "You just beat your best score on this section."),
        Triple("12/19 learned", MaterialTheme.colorScheme.onSurfaceVariant, "Words you've got right first time on 3 different days."),
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Your section progress", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            items.forEach { (symbol, color, text) ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(symbol, color = color, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("Perfect = every word right on the first tap. Goes with mistakes never undo progress.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun VocabQuizOnboardingView(
    categories: List<CategoryMasteryStats>,
    onStartSuggestion: (String) -> Unit
) {
    // Same stretch-to-bottom (but still scrollable) panel as VocabQuizActiveView.
    val density = LocalDensity.current
    var panelTop by remember { mutableStateOf(0.dp) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val screenHeight = maxHeight
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Psychology,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "Master Vocabulary",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "We track how well you know every word. Take quizzes to build your mastery level.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Spacer(Modifier.height(24.dp))

        Text("Tap a category to start quizzing", style = MaterialTheme.typography.titleSmall)

        Spacer(Modifier.height(12.dp))

        // Every category that has a quiz at the current level (like iOS), on a grouped panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { panelTop = with(density) { it.positionInParent().y.toDp() } }
                .heightIn(min = (screenHeight - 24.dp - panelTop - 24.dp).coerceAtLeast(0.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(LocalExtendedColors.current.groupedPanel)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            categories.forEach { stat ->
                CategoryMasteryRow(stat, onClick = { onStartSuggestion(stat.categoryTitle) })
            }
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoringInfoBottomSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "How Scoring Works",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // How Each Answer Is Marked
            Text(
                "How Each Answer Is Marked",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            ScoringRow(emoji = "\u2B50", title = "Flawless", description = "You picked the right answer on your first try.")
            ScoringRow(emoji = "\uD83D\uDCA1", title = "Assisted", description = "You got it right, but used the hint button.")
            ScoringRow(emoji = "\uD83D\uDD04", title = "Stumbled", description = "You picked a wrong answer first, then got it right.")
            ScoringRow(emoji = "\u274C", title = "Failed", description = "You gave up or skipped the question.")

            HorizontalDivider()

            // Your Progress Levels
            Text(
                "Your Progress Levels",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            LevelRow(emoji = "\uD83C\uDD95", title = "New", description = "You haven't been quizzed on this word yet.", schedule = "Appears in your next quiz")
            LevelRow(emoji = "\uD83D\uDD34", title = "Struggling", description = "You couldn't get this one right \u2014 don't worry, it happens!", schedule = "Comes back in 10 minutes")
            LevelRow(emoji = "\uD83D\uDFE0", title = "Learning", description = "You got there in the end, but needed a few tries.", schedule = "Comes back in 6 hours")
            LevelRow(emoji = "\uD83D\uDFE1", title = "Review", description = "You got it right \u2014 nice work! Now let's make sure it sticks.", schedule = "Comes back tomorrow morning")
            LevelRow(emoji = "\uD83D\uDFE2", title = "Mastered", description = "You got it right first time on 3 different days \u2014 you really know this one!", schedule = "Done! Won't come back unless you reset")

            HorizontalDivider()

            // Tip
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp)) {
                    Text("\uD83D\uDCA1 ", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Don't cram! If a word isn't due yet, answering it again won't advance your progress. Spacing out your practice helps you remember for longer.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ScoringRow(emoji: String, title: String, description: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LevelRow(emoji: String, title: String, description: String, schedule: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(schedule, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun SmartReviewContainer(
    viewModel: VocabSectionQuizViewModel = hiltViewModel(),
    onInteraction: (Boolean) -> Unit
) {
    // 1. Trigger the specific logic for Smart Review
    LaunchedEffect(Unit) {
        viewModel.loadSmartReviewQuiz()
    }

    val isDirty by viewModel.isDirty.collectAsStateWithLifecycle()
    LaunchedEffect(isDirty) {
        onInteraction(isDirty)
    }

    // 2. Render the Quiz Screen
    // We use a Box with fixed height to ensure the BottomSheet behaves correctly
    Box(modifier = Modifier.fillMaxHeight(0.95f)) {
        VocabQuizScreen(
            viewModel = viewModel,
            autoLoad = false // Important: Don't load default B1/A1 quiz
        )
    }
}
