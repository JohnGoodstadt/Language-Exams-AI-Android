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

    // Row pulse (gamification, mirrors iOS): when the quiz sheet closes after a FINISHED run, that quiz's row
    // pulses to draw the eye to its updated stats - 1 blue pulse (finished), 2 green + stats pop (perfect),
    // 3 gold + crown pop + chime (this run completed mastery). "Remove animations" -> a single pulse.
    val quizListState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    var pulsingBaseName by remember { mutableStateOf<String?>(null) }
    var pulseTier by remember { mutableStateOf(RowPulseTier.FINISHED) }
    val pulseAmount = remember { Animatable(0f) }
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
        val animationsOff = Settings.Global.getFloat(
            context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        repeat(if (animationsOff) 1 else pulse.tier.pulses) {
            pulseAmount.animateTo(1f, tween(400))
            pulseAmount.animateTo(0f, tween(400))
            delay(50)
        }
        pulsingBaseName = null
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
        val labelFor: (UsageQuizLevelsFilename) -> String = { if (useFullLabels) it.description else it.shortLabel }
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
                            QuizRowStatsText(rowSummary, small = true, mastered = rowMastered, pulseTier = if (pulsingBaseName == quiz.baseName) pulseTier else null, pulseAmount = pulseAmount.value, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                    if (statsInline && rowSummary != null) {
                        QuizRowStatsText(rowSummary, small = false, mastered = rowMastered, pulseTier = if (pulsingBaseName == quiz.baseName) pulseTier else null, pulseAmount = pulseAmount.value, modifier = Modifier.padding(start = 12.dp, end = 8.dp))
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = LocalExtendedColors.current.buttonBlue
                        )
                    }
                }
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
        modifier = modifier.graphicsLayer {
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
private val RowPulseTier.color: Color
    get() = when (this) {
        RowPulseTier.FINISHED -> Color(0xFF2196F3)
        RowPulseTier.PERFECT -> Color(0xFF4CAF50)
        RowPulseTier.MASTERED -> Color(0xFFFFC107)
    }

/** The mastery chime, unless the phone is on silent/vibrate (MediaPlayer ignores the ringer switch). */
private fun playChimeUnlessSilent(context: Context) {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
    runCatching {
        MediaPlayer.create(context, R.raw.success_chime)?.apply {
            setOnCompletionListener { it.release() }
            start()
        }
    }
}
