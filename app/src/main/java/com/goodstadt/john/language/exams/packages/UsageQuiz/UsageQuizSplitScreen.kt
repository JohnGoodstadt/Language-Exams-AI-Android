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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.R
import com.goodstadt.john.language.exams.packages.reference.shared.HorizontalLevelPicker
import com.goodstadt.john.language.exams.screens.UsageQuiz.UsageQuizLevelsFilename
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

    var showQuizSheet by rememberSaveable { mutableStateOf(false) }
    var showInfoBottomSheet by remember { mutableStateOf(false) }
    var showDashboardSheet by remember { mutableStateOf(false) }
    var isLearningExpanded by rememberSaveable { mutableStateOf(false) }

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
        val learningData = uiState.format7or10ListRoot?.data?.firstOrNull()
        val learningTitle = learningData?.learningTitle ?: selectedQuiz?.title.orEmpty()
        val learningPoints = learningData?.learningPoints.orEmpty()
        if (learningPoints.isNotEmpty()) {
            UsageQuizLearningPoints(
                learningTitle = learningTitle,
                learningPoints = learningPoints,
                isExpanded = isLearningExpanded,
                onToggle = { isLearningExpanded = !isLearningExpanded },
            )
        } else if (learningTitle.isNotBlank()) {
            Text(
                text = learningTitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp)
            )
        }

        // --- Vertical quiz list (replaces the dropdown) ---
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(LocalExtendedColors.current.groupedPanel),
        ) {
            items(availableQuizzes, key = { it.id }) { quiz ->
                val isSelected = quiz.id == selectedQuiz?.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (!isSelected) viewModel.onQuizSelected(quiz) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = quiz.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) LocalExtendedColors.current.buttonBlue else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
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
            onClick = { showQuizSheet = true },
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
        val summary = questions.getOrNull(currentQuestionIndex)?.summary.orEmpty()
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
        } else if (currentQuestionIndex == 0 && questions.isNotEmpty()) {
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
