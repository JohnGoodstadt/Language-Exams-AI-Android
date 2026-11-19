package com.goodstadt.john.language.exams.packages.UsageQuiz

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.geometry.Offset
import com.goodstadt.john.language.exams.data.repository.UsageQuizRepository
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.R
import com.goodstadt.john.language.exams.packages.reference.shared.HorizontalLevelPicker
import com.goodstadt.john.language.exams.screens.UsageQuiz.UsageQuizLevelsFilename
import com.goodstadt.john.language.exams.screens.shared.TranslateToggleButton
import com.goodstadt.john.language.exams.ui.theme.LocalExtendedColors

/**
 * TRIAL layout switch for the Reference > Usage Quiz tab.
 * true  = [UsageQuizSplitScreen]: picker screen + quiz in a bottom sheet (more room for questions on small phones).
 * false = the original single-screen [UsageQuizScreen] (unchanged).
 */
object UsageQuizLayout {
    const val USE_SPLIT_SCREEN = true
}

/**
 * Trial alternative to [UsageQuizScreen], split in two:
 *  1. Base screen: level tabs, the selected quiz's learningTitle (+ points), a vertical quiz list, "Start Quiz".
 *  2. Bottom sheet: the current question's summary, then the quiz panel (filters, question, answers,
 *     navigation, Correct/Tries, paging dots).
 * Uses the same [UsageQuizViewModel], so scoring, mastery, history and Focus stats are saved exactly as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageQuizSplitScreen(
    viewModel: UsageQuizViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val questions by viewModel.questions.collectAsState()
    val availableQuizzes by viewModel.availableQuizzes.collectAsState()
    val isRateLimitingSheetVisible by viewModel.showRateLimitSheet.collectAsState()
    val isDailyRateLimitingSheetVisible by viewModel.showRateDailyLimitSheet.collectAsState()
    val isHourlyRateLimitingSheetVisible by viewModel.showRateHourlyLimitSheet.collectAsState()

    val selectedLevel by viewModel.selectedLevel
    val selectedQuiz by viewModel.selectedQuiz
    val currentQuestionIndex by viewModel.currentQuestionIndex
    val userAnswers by viewModel.userAnswers
    val fluency by viewModel.fluency
    val showEnglish by viewModel.showEnglish.collectAsState()

    var showQuizSheet by rememberSaveable { mutableStateOf(false) }
    var showInfoBottomSheet by remember { mutableStateOf(false) }
    var showDashboardSheet by remember { mutableStateOf(false) }
    var isLearningExpanded by rememberSaveable { mutableStateOf(false) }

    // Quiz row history ("Just now · 1x · 8/10"): refresh on stats changes and once a minute so
    // "Just now" ages to "12m" while the screen is open. Tablets show it inline on the right.
    val usageStatsVersion by viewModel.usageStatsVersion.collectAsState()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    val statsInline = LocalConfiguration.current.screenWidthDp >= 600
    // Teaching tip (title to subtitle) shown over the quiz sheet as it opens - see EntryTipBanner.
    var entryTip by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Row pulse (gamification, mirrors iOS): when the quiz sheet closes after a FINISHED run, that quiz's row
    // pulses to draw the eye to its updated stats - 1 blue pulse (finished), 2 green + stats pop (perfect),
    // 3 gold + crown pop + chime (this run completed mastery). "Remove animations" -> a single pulse.
    val quizListState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    var pulsingBaseName by remember { mutableStateOf<String?>(null) }
    var pulseTier by remember { mutableStateOf(RowPulseTier.FINISHED) }
    val pulseAmount = remember { Animatable(0f) }
    // More gamification (mirrors iOS), played with the row pulse when the sheet closes: "New best!" tag,
    // "+N XP" rising from the row, level crown + fireworks, daily-goal ring + banner. Plus explanations:
    // tap the ring (popup) or a row's stats line (guide sheet).
    var xpAtSheetOpen by remember { mutableIntStateOf(0) }
    var newBestBaseName by remember { mutableStateOf<String?>(null) }
    var xpFlyBaseName by remember { mutableStateOf<String?>(null) }
    var xpFlyAmount by remember { mutableIntStateOf(0) }
    val xpFlyProgress = remember { Animatable(0f) }
    var showLevelFireworks by remember { mutableStateOf(false) }
    var showGoalPopup by remember { mutableStateOf(false) }
    var showRowGuide by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(showQuizSheet) {
        if (showQuizSheet) return@LaunchedEffect
        val pulse = viewModel.takePendingRowPulse() ?: return@LaunchedEffect
        delay(350) // let the sheet finish sliding away
        val index = availableQuizzes.indexOfFirst { it.baseName == pulse.baseName }
        if (index >= 0) quizListState.animateScrollToItem(index)
        pulseTier = pulse.tier
        pulseAmount.snapTo(0f)
        pulsingBaseName = pulse.baseName
        delay(250)
        when (pulse.tier) {
            RowPulseTier.FINISHED -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            RowPulseTier.PERFECT -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            RowPulseTier.MASTERED -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                playChimeUnlessSilent(context)
            }
        }
        // "New best!" (stays while this screen is open) and the "+N XP" fly-in, alongside the pulse.
        if (pulse.newBest) newBestBaseName = pulse.baseName
        val xpEarned = viewModel.totalXp() - xpAtSheetOpen
        if (xpEarned > 0) {
            xpFlyAmount = xpEarned
            xpFlyProgress.snapTo(0f)
            xpFlyBaseName = pulse.baseName
            scope.launch { xpFlyProgress.animateTo(1f, tween(1400)) }
        }
        val animationsOff = Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        repeat(if (animationsOff) 1 else pulse.tier.pulses) {
            pulseAmount.animateTo(1f, tween(400))
            pulseAmount.animateTo(0f, tween(400))
            delay(50)
        }
        pulsingBaseName = null
        xpFlyBaseName = null
        // Bigger moments after the row: a whole level mastered (fireworks + banner + chime), otherwise
        // today's goal just reached (banner).
        if (pulse.levelMastered != null) {
            playChimeUnlessSilent(context)
            showLevelFireworks = true
            viewModel.showGamificationBanner("${pulse.levelMastered} mastered! 👑", "You've mastered every quiz in this level.")
        } else if (pulse.dailyGoalDone) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.showGamificationBanner("Daily goal done! 🎯",
                "${UsageQuizRepository.DAILY_GOAL} quizzes today. Great work.")
        }
    }

    // Translate toggle: only offered when the loaded data carries "_en" translations (older cached /
    // Firestore data without them, and the English app, never show the button or translate).
    // Declared here (not inside the Column) so the quiz sheet and info sheet below can use them too.
    val learningData = uiState.format7or10ListRoot?.data?.firstOrNull()
    val hasTranslation = !learningData?.learningTitleEn.isNullOrBlank() ||
        !learningData?.learningPointsEn.isNullOrEmpty() ||
        questions.any { !it.summaryEn.isNullOrBlank() || !it.explainEn.isNullOrBlank() }
    val translating = showEnglish && hasTranslation
    val tr: (String, String?) -> String = { german, english ->
        if (translating && !english.isNullOrBlank()) english else german
    }
    val translateButton: (@Composable () -> Unit)? = if (hasTranslation) {
        { TranslateToggleButton(showEnglish = showEnglish, onClick = { viewModel.toggleShowEnglish() }) }
    } else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalExtendedColors.current.groupedPage)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), // tight: tabs sit close above the learningTitle
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- Level tabs (filter the quiz list below) ---
        val useFullLabels = LocalConfiguration.current.screenWidthDp >= 380
        // Level crown: " 👑" on a level whose every quiz is mastered (re-read when stats change).
        val masteredLevels = remember(usageStatsVersion) {
            UsageQuizLevelsFilename.entries.filter { viewModel.isLevelMastered(it) }.toSet()
        }
        val labelFor: (UsageQuizLevelsFilename) -> String = {
            (if (useFullLabels) it.description else it.shortLabel) + if (it in masteredLevels) " 👑" else ""
        }
        val doneToday = remember(usageStatsVersion, nowMillis) { viewModel.quizzesCompletedToday() }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                HorizontalLevelPicker(
                    options = UsageQuizLevelsFilename.entries.map { labelFor(it) },
                    selectedOption = labelFor(selectedLevel),
                    onOptionSelected = { newLabel ->
                        val level = UsageQuizLevelsFilename.entries.first { labelFor(it) == newLabel }
                        if (level != selectedLevel) {
                            viewModel.onLevelSelected(level)
                            viewModel.loadQuestions()
                        }
                    },
                    filledWhenSelected = true,
                    fontSize = 16.sp
                )
            }
            // Daily goal ring: tap for what it means.
            Box {
                DailyGoalRing(
                    done = doneToday,
                    goal = UsageQuizRepository.DAILY_GOAL,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .clickable { showGoalPopup = true }
                )
                DropdownMenu(expanded = showGoalPopup, onDismissRequest = { showGoalPopup = false }) {
                    DailyGoalExplainer(done = doneToday, goal = UsageQuizRepository.DAILY_GOAL)
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp)) // a little air between the level tabs and the learningTitle

        // --- Fluency label (the stats-dashboard IconButton is removed for now: it took too much height) ---
        if (fluency.label.isNotBlank()) {
            Text(
                text = fluency.label,
                color = fluency.color,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(horizontal = 6.dp)
                    .background(fluency.color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            )
        }

        // --- learningTitle (+ expandable learning points) of the selected quiz ---
        val learningTitle = learningData?.learningTitle?.let { tr(it, learningData?.learningTitleEn) }
            ?: tr(selectedQuiz?.title.orEmpty(), selectedQuiz?.titleEn)
        val learningPoints = if (translating && !learningData?.learningPointsEn.isNullOrEmpty())
            learningData?.learningPointsEn.orEmpty() else learningData?.learningPoints.orEmpty()
        if (learningPoints.isNotEmpty()) {
            UsageQuizLearningPoints(
                learningTitle = learningTitle,
                learningPoints = learningPoints,
                isExpanded = isLearningExpanded,
                onToggle = { isLearningExpanded = !isLearningExpanded },
                trailing = translateButton,
            )
        } else if (learningTitle.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = learningTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                translateButton?.invoke()
            }
        }

        // --- Vertical quiz list (replaces the dropdown) ---
        LazyColumn(
            state = quizListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(LocalExtendedColors.current.groupedPanel),
        ) {
            items(availableQuizzes, key = { it.id }) { quiz ->
                val isSelected = quiz.id == selectedQuiz?.id
                // "20m · 4x · 10/10 10/10 8/10" - re-read when stats change or the minute ticks over.
                val rowSummary = remember(quiz.baseName, usageStatsVersion, nowMillis) {
                    viewModel.quizRowSummary(quiz, nowMillis)
                }
                val rowMastered = remember(quiz.baseName, usageStatsVersion, nowMillis) {
                    viewModel.quizRowMastered(quiz)
                }
                val rowReady = remember(quiz.baseName, usageStatsVersion, nowMillis) {
                    viewModel.quizRowReady(quiz, nowMillis)
                }
                // "New best!" gives way to "↻ ready" (both together don't fit on one line).
                val rowNewBest = newBestBaseName == quiz.baseName && !rowReady
                val onStatsTap = { showRowGuide = true }
                Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.logQuizRowDebug(quiz) // DEBUG builds only: dump this row's stats to logcat
                            if (!isSelected) viewModel.onQuizSelected(quiz)
                        }
                        // Row pulse after a finished quiz's sheet closes (see the LaunchedEffect above).
                        .background(
                            if (pulsingBaseName == quiz.baseName)
                                pulseTier.color.copy(alpha = 0.35f * pulseAmount.value) else Color.Transparent
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Phone: the stats go under the title in small text; tablet: on the right.
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr(quiz.title, quiz.titleEn),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) LocalExtendedColors.current.buttonBlue else MaterialTheme.colorScheme.onSurface
                        )
                        if (!statsInline && rowSummary != null) {
                            QuizRowStatsText(rowSummary, small = true, mastered = rowMastered, ready = rowReady, newBest = rowNewBest, pulseTier = if (pulsingBaseName == quiz.baseName) pulseTier else null, pulseAmount = pulseAmount.value, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                    if (statsInline && rowSummary != null) {
                        QuizRowStatsText(rowSummary, small = false, mastered = rowMastered, ready = rowReady, newBest = rowNewBest, pulseTier = if (pulsingBaseName == quiz.baseName) pulseTier else null, pulseAmount = pulseAmount.value, modifier = Modifier.padding(start = 12.dp, end = 8.dp))
                    }
                    // A clear ⓘ button for the guide, so tapping anywhere else on the row selects the quiz.
                    // Only shown when the row has stats to explain.
                    if (rowSummary != null) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "What the row's symbols mean",
                            tint = LocalExtendedColors.current.buttonBlue,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .clickable { onStatsTap() }
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = LocalExtendedColors.current.buttonBlue
                        )
                    }
                }
                // "+N XP" rising from the row and fading, after the sheet closes.
                if (xpFlyBaseName == quiz.baseName) {
                    val p = xpFlyProgress.value
                    Text(
                        text = "+$xpFlyAmount XP",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9800),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 48.dp)
                            .graphicsLayer {
                                translationY = -70.dp.toPx() * p
                                alpha = 1f - p
                                scaleX = 1f + 0.3f * p; scaleY = scaleX
                            }
                    )
                }
                } // Box
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 0.5.dp,
                    color = LocalExtendedColors.current.mutedGrey
                )
            }
        }

        // --- Start / Continue ---
        val inProgress = userAnswers.isNotEmpty() && userAnswers.size < questions.size
        Button(
            onClick = {
                viewModel.resetForRetakeIfFinished() // finished quiz -> fresh go (Correct/Tries back to 0)
                xpAtSheetOpen = viewModel.totalXp()  // so the XP earned in the sheet can fly in from the row
                // Teaching tip: how mastery works (first time) or "come back tomorrow" (2nd perfect day is today).
                // Shown as our own banner inside the sheet (a system Toast is capped at 2 lines on Android 12+).
                entryTip = viewModel.entryTipForSelectedQuiz()
                showQuizSheet = true
            },
            enabled = selectedQuiz != null || availableQuizzes.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = LocalExtendedColors.current.buttonBlue)
        ) {
            Text(
                text = if (inProgress) "Continue Quiz" else "Start Quiz",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }

    // --- Explanations + level fireworks (gamification) ---
    if (showRowGuide) UsageRowGuideSheet(onDismiss = { showRowGuide = false })
    if (showLevelFireworks) LevelFireworks(onDone = { showLevelFireworks = false })

    // --- The quiz itself, in a near-full-height sheet ---
    if (showQuizSheet) {
        val quizSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showQuizSheet = false },
            sheetState = quizSheetState,
            containerColor = LocalExtendedColors.current.groupedPanel,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Box(modifier = Modifier.fillMaxHeight(0.95f)) {
                UsageQuizSheetPanel(
                    viewModel = viewModel,
                    onShowInfo = { showInfoBottomSheet = true },
                    translating = translating,
                    translateButton = translateButton,
                )
                // Entry teaching tip over the bottom of the quiz: wraps to several lines; 5 s or tap to dismiss.
                EntryTipBanner(
                    tip = entryTip,
                    onDismiss = { entryTip = null },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    UsageQuizBottomSheets(
        viewModel = viewModel,
        context = context,
        isRateLimitingSheetVisible = isRateLimitingSheetVisible,
        isDailyRateLimitingSheetVisible = isDailyRateLimitingSheetVisible,
        isHourlyRateLimitingSheetVisible = isHourlyRateLimitingSheetVisible,
        showInfoBottomSheet = showInfoBottomSheet,
        onCloseInfo = { showInfoBottomSheet = false },
        infoQuestion = questions.getOrNull(currentQuestionIndex),
        showDashboardSheet = showDashboardSheet,
        onCloseDashboard = { showDashboardSheet = false },
        selectedLevelDescription = selectedLevel.description,
        translateInfo = translating,
    )
}

/**
 * The quiz panel from [UsageQuizScreen] (filter chips down to the paging dots), headed by the current
 * question's section summary. Same per-question state handling as the original screen.
 */
@Composable
private fun UsageQuizSheetPanel(
    viewModel: UsageQuizViewModel,
    onShowInfo: () -> Unit,
    translating: Boolean = false,
    translateButton: (@Composable () -> Unit)? = null,
) {
    val correctGreen = LocalExtendedColors.current.textAccentAlternative

    val questions by viewModel.questions.collectAsState()
    val autoAdvance by viewModel.autoAdvance.collectAsState()
    val activeFilters by viewModel.activeFilters.collectAsState()
    val currentQuestionIndex by viewModel.currentQuestionIndex
    val firstAnswers by viewModel.firstAnswers
    val quizStatistics by viewModel.quizStatistics

    var selectedOption by remember { mutableStateOf<String?>(null) }
    var isCurrentAnswerCorrect by remember { mutableStateOf<Boolean?>(null) }
    var displayedSentence by remember { mutableStateOf(AnnotatedString("")) }
    var infoDisabled by remember { mutableStateOf(!viewModel.doIHaveCurrentQuestionInfo()) }

    val displayText = if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
        stringResource(R.string.quiz_hear_choose_best_answer)
    } else {
        stringResource(R.string.quiz_choose_best_answer)
    }

    // Same as UsageQuizScreen: rebuild the question text / restore a previous answer on every move.
    LaunchedEffect(currentQuestionIndex, questions) {
        val question = questions.getOrNull(currentQuestionIndex) ?: return@LaunchedEffect
        val questionText =
            if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
                question.sentence.replace(Regex("(?<!_)_(?!_)"), "___")
            } else ""
        val prevOption = viewModel.selectedOptionFor(question.page)
        val prevCorrect = viewModel.answerCorrectFor(question.page)
        selectedOption = prevOption
        isCurrentAnswerCorrect = prevCorrect
        displayedSentence = if (prevCorrect == true && prevOption != null) {
            when (viewModel.currentFileFormat.value) {
                viewModel.quizFillInTheBlanks -> viewModel.highlightWordInSentence(
                    sentence = question.sentence.replace(Regex("_+"), prevOption),
                    wordToHighlight = prevOption,
                    highlightColor = correctGreen
                )
                viewModel.quizDefinitions -> AnnotatedString(question.title)
                viewModel.quizMultipleChoice -> AnnotatedString(prevOption)
                else -> viewModel.highlightWordInSentence(
                    sentence = prevOption,
                    wordToHighlight = prevOption,
                    highlightColor = correctGreen
                )
            }
        } else {
            AnnotatedString(questionText)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Section summary of the current question
        val currentQuestion = questions.getOrNull(currentQuestionIndex)
        val summary = if (translating && !currentQuestion?.summaryEn.isNullOrBlank())
            currentQuestion?.summaryEn.orEmpty() else currentQuestion?.summary.orEmpty()
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = LocalExtendedColors.current.mutedGrey
            )
        }

        UsageMasteryFilterChips(
            activeFilters = activeFilters,
            onToggle = { viewModel.toggleFilter(it) },
            onSelectAll = { viewModel.selectAllFilters() }
        )

        if (activeFilters.isNotEmpty() && questions.isNotEmpty()) {
            Text(
                text = "Showing ${questions.size} of ${viewModel.totalQuestionCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (questions.isEmpty() && activeFilters.isNotEmpty()) {
            val filterNames = activeFilters.joinToString(", ") { it.name }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "No $filterNames questions to show.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = activeFilters.joinToString("\n") { usageMasteryExplanation(it) },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Select All to see more.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        } else if (questions.isNotEmpty()) {
            // The instruction ("Choose the best answer") on EVERY question, not just the first.
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (questions.isNotEmpty()) {
            val question = questions[currentQuestionIndex.coerceIn(0, questions.lastIndex)]
            UsageQuizQuestionAnswers(
                viewModel = viewModel,
                question = question,
                currentQuestionIndex = currentQuestionIndex,
                selectedOption = selectedOption,
                isCurrentAnswerCorrect = isCurrentAnswerCorrect,
                displayedSentence = displayedSentence,
                onSelectedOptionChange = { selectedOption = it },
                onAnswerCorrectChange = { isCurrentAnswerCorrect = it },
                onDisplayedSentenceChange = { displayedSentence = it },
                onInfoDisabledChange = { infoDisabled = it },
                modifier = Modifier.weight(1f),
            )

            UsageQuizNavigationRow(
                viewModel = viewModel,
                currentQuestionIndex = currentQuestionIndex,
                questionCount = questions.size,
                autoAdvance = autoAdvance,
                infoDisabled = infoDisabled,
                onInfoDisabledChange = { infoDisabled = it },
                onShowInfo = onShowInfo,
                extraButton = translateButton,
            )
        } else if (activeFilters.isEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
        }

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = LocalExtendedColors.current.mutedGrey
        )
        UsageQuizStatisticsRow(
            correct = quizStatistics.correct,
            tries = quizStatistics.tries,
        )
        UsageQuizPagingDots(
            questionCount = questions.size,
            currentQuestionIndex = currentQuestionIndex,
            firstAnswers = firstAnswers,
        )
        Spacer(modifier = Modifier.width(1.dp))
    }
}

/** A quiz row's history summary ("20m · 4x · 10/10 10/10 8/10"): small grey text, digits kept aligned. */
@Composable
private fun QuizRowStatsText(
    summary: String,
    small: Boolean,
    mastered: Boolean,
    ready: Boolean = false,            // "↻ ready" - a perfect run today counts towards the crown
    newBest: Boolean = false,          // "New best!" - the run just finished beat this quiz's best
    onClick: (() -> Unit)? = null,     // tap the stats line -> the row guide
    pulseTier: RowPulseTier? = null,   // non-null while this row is pulsing
    pulseAmount: Float = 0f,           // 0..1 within a pulse
    modifier: Modifier = Modifier,
) {
    // A perfect run makes the stats line (with its new "2p") pop slightly; mastery makes the crown pop.
    val textScale = if (pulseTier == RowPulseTier.PERFECT) 1f + 0.12f * pulseAmount else 1f
    val crownScale = if (pulseTier == RowPulseTier.MASTERED) 1f + 0.8f * pulseAmount else 1f
    val style = (if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
        // "tnum" = tabular digits, so the scores line up row to row (set on the style: Text() has no such param).
        .copy(fontFeatureSettings = "tnum")
    Row(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .graphicsLayer {
                scaleX = textScale; scaleY = textScale
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = summary,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        // Small gold crown after "… · Mastered" (matches the iOS crown.fill).
        if (mastered) {
            Icon(
                imageVector = CrownIcon,
                contentDescription = "Mastered",
                tint = Color(0xFFFFC107),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(if (small) 14.dp else 16.dp)
                    .graphicsLayer { scaleX = crownScale; scaleY = crownScale }
            )
        }
        if (ready) StatsTag("↻ ready", Color(0xFF2196F3), filled = false, style = style)
        if (newBest) StatsTag("New best!", Color(0xFFFF9800), filled = true, style = style)
    }
}

/** Simple filled crown (Material icons have none): three points on a band, with a base bar. */
private val CrownIcon: ImageVector by lazy {
    ImageVector.Builder(name = "Crown", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 7f); lineTo(7.5f, 11.5f); lineTo(12f, 4f); lineTo(16.5f, 11.5f); lineTo(21f, 7f)
            lineTo(19f, 17.5f); lineTo(5f, 17.5f); close()
            moveTo(5f, 19f); lineTo(19f, 19f); lineTo(19f, 21f); lineTo(5f, 21f); close()
        }
    }.build()
}


/** Row-pulse colour per tier: blue (finished), green (perfect), gold (mastered). Mirrors iOS. */
internal val RowPulseTier.color: Color
    get() = when (this) {
        RowPulseTier.FINISHED -> Color(0xFF2196F3)
        RowPulseTier.PERFECT -> Color(0xFF4CAF50)
        RowPulseTier.MASTERED -> Color(0xFFFFC107)
    }

/** The mastery chime, unless the phone is on silent/vibrate (MediaPlayer ignores the ringer switch). */
internal fun playChimeUnlessSilent(context: Context) {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
    runCatching {
        MediaPlayer.create(context, R.raw.success_chime)?.apply {
            setOnCompletionListener { it.release() }
            start()
        }
    }
}


/**
 * Teaching tip banner shown over the bottom of the quiz sheet as it opens (how mastery works / come back
 * tomorrow). Our own card rather than a system Toast, which Android 12+ truncates to two lines. Appears
 * shortly after the sheet, stays 5 s, tap to dismiss. Mirrors the iOS entry-tip banner.
 */
@Composable
internal fun EntryTipBanner(tip: Pair<String, String>?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(tip) {
        if (tip == null) { visible = false; return@LaunchedEffect }
        delay(600)          // let the sheet finish opening
        visible = true
        delay(5_000)
        visible = false
        delay(300)          // exit animation
        onDismiss()
    }
    AnimatedVisibility(
        visible = visible && tip != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        val (title, subtitle) = tip ?: ("" to "")
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 500.dp)
                .fillMaxWidth()
                .clickable { visible = false; onDismiss() }
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


// ---------------- Gamification UI (mirrors iOS UsageQuizView) ----------------

/** Small tag after the stats line: "↻ ready" (outlined) or "New best!" (filled). */
@Composable
internal fun StatsTag(text: String, color: Color, filled: Boolean, style: TextStyle) {
    Text(
        text = text,
        style = style,
        fontWeight = FontWeight.Bold,
        color = if (filled) Color.White else color,
        maxLines = 1,
        modifier = Modifier
            .padding(start = 4.dp)
            .background(if (filled) color else color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}

/** Ring next to the level tabs: today's finished usage quizzes against the daily goal ("1/2"); green ✓ when met. */
@Composable
internal fun DailyGoalRing(done: Int, goal: Int, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(if (goal > 0) (done.toFloat() / goal).coerceAtMost(1f) else 0f, tween(600), label = "goal")
    val met = done >= goal
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    val arc = if (met) Color(0xFF4CAF50) else Color(0xFF2196F3)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(34.dp)
            .semantics { contentDescription = "Daily goal: ${minOf(done, goal)} of $goal quizzes today" }
    ) {
        Canvas(modifier = Modifier.matchParentSize().padding(2.dp)) {
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, useCenter = false, style = stroke)
            drawArc(arc, -90f, 360f * fraction, useCenter = false, style = stroke)
        }
        if (met) Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
        else Text("${minOf(done, goal)}/$goal", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

/** Popup from the daily-goal ring: what the goal is and today's progress. */
@Composable
internal fun DailyGoalExplainer(done: Int, goal: Int, things: String = "Usage quizzes") {
    Column(modifier = Modifier.width(280.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Daily goal 🎯", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Finish $goal different $things today. Any score counts. It starts again at midnight.",
            style = MaterialTheme.typography.bodyMedium)
        Text(
            if (done >= goal) "Done for today ✓" else "Today: ${minOf(done, goal)} of $goal",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (done >= goal) Color(0xFF4CAF50) else Color(0xFF2196F3)
        )
    }
}

/** Guide sheet from tapping a quiz row's stats line: what each symbol on the row means. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsageRowGuideSheet(onDismiss: () -> Unit) {
    val items = listOf(
        Triple("20m · 5x", MaterialTheme.colorScheme.onSurfaceVariant, "Time since your last go, and how many times you've finished this quiz."),
        Triple("10/10 9/10", MaterialTheme.colorScheme.onSurfaceVariant, "Your last 3 scores, newest first (right first time)."),
        Triple("●●○", MaterialTheme.colorScheme.onSurface, "Perfect runs on different days. 3 different days masters the quiz."),
        Triple("👑", Color(0xFFFFC107), "Mastered: perfect runs on 3 different days."),
        Triple("↻ ready", Color(0xFF2196F3), "A perfect run today will count towards the crown."),
        Triple("New best!", Color(0xFFFF9800), "You just beat your best score on this quiz."),
    )
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Your quiz progress", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            items.forEach { (symbol, color, text) ->
                Row(verticalAlignment = Alignment.Top) {
                    Text(symbol, color = color, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("Perfect = every question right on the first tap. Runs with mistakes never undo progress.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Level mastered: a short gold particle burst over the whole screen (dimmed), ~3 s, tap to dismiss.
 * Android has no fireworks effect like iOS's CelebrationOverlay, so this is a simple Canvas burst.
 */
@Composable
internal fun LevelFireworks(onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val particles = remember {
        val rnd = kotlin.random.Random(System.currentTimeMillis())
        List(90) {
            Triple(rnd.nextFloat() * 2f * Math.PI.toFloat(), 0.35f + rnd.nextFloat() * 0.65f,
                listOf(Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFFEB3B), Color.White)[rnd.nextInt(4)])
        }
    }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(2800, easing = LinearOutSlowInEasing))
        onDone()
    }
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().clickable { onDone() }) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val p = progress.value
                val origin = Offset(size.width / 2f, size.height * 0.42f)
                val reach = size.minDimension * 0.55f
                particles.forEach { (angle, speed, color) ->
                    val d = reach * speed * p
                    val pos = Offset(origin.x + kotlin.math.cos(angle) * d,
                        origin.y + kotlin.math.sin(angle) * d + 220f * p * p) // a little gravity
                    drawCircle(color.copy(alpha = (1f - p).coerceIn(0f, 1f)), radius = 6f * (1f - 0.5f * p), center = pos)
                }
                drawCircle(Color(0xFFFFC107).copy(alpha = (0.6f * (1f - p * 3f)).coerceIn(0f, 1f)),
                    radius = 60f + 240f * p, center = origin)
            }
            Text("👑", fontSize = 72.sp, modifier = Modifier.align(Alignment.Center)
                .graphicsLayer { val s = 0.6f + 0.6f * minOf(progress.value * 3f, 1f); scaleX = s; scaleY = s })
        }
    }
}
