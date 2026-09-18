package com.goodstadt.john.language.exams.packages.ReadinessAudit

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.R
import com.goodstadt.john.language.exams.packages.UsageQuiz.QuizQuestion
import com.goodstadt.john.language.exams.packages.UsageQuiz.dotColor
import com.goodstadt.john.language.exams.packages.reference.QuizInfoBottomSheetView
import com.goodstadt.john.language.exams.packages.reference.UsageDashboardScreen
import com.goodstadt.john.language.exams.packages.reference.shared.HorizontalLevelPicker
import com.goodstadt.john.language.exams.screens.RateLimitDailyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyPaywallBottomSheet
import com.goodstadt.john.language.exams.storage.UiEvent
import com.goodstadt.john.language.exams.ui.theme.blueBright2
import com.goodstadt.john.language.exams.ui.theme.buttonColor
import com.goodstadt.john.language.exams.ui.theme.greyLight2
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.utils.HeightClass
import com.goodstadt.john.language.exams.utils.rememberHeightClass
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet
import timber.log.Timber


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadinessAuditScreen(
    viewModel: ReadinessAuditViewModel = hiltViewModel(),
    initialVersion: Int = 1,
    initialLevel: ReadinessAuditLevels? = null, // Open straight onto this level's test, if unlocked
    onFinished: () -> Unit // Existing callback to exit the screen
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsState()
    var infoDisabled by remember { mutableStateOf(false) }
    var showInfoBottomSheet by remember { mutableStateOf(false) }

    val questions by viewModel.questions.collectAsState()
    val isRateLimitingSheetVisible by viewModel.showRateLimitSheet.collectAsState()
    val isDailyRateLimitingSheetVisible by viewModel.showRateDailyLimitSheet.collectAsState()
    val isHourlyRateLimitingSheetVisible by viewModel.showRateHourlyLimitSheet.collectAsState()

    val selectedLevel by viewModel.selectedLevel
    val selectedQuizNumber by viewModel.selectedQuizNumber
    val currentQuestionIndex by viewModel.currentQuestionIndex
    val userAnswers by viewModel.userAnswers
    val quizStatistics by viewModel.quizStatistics
    var selectedOption by remember { mutableStateOf<String?>(null) }
    var isCurrentAnswerCorrect by remember { mutableStateOf<Boolean?>(null) } // Track answer correctness

    //v2 - replace _ with correct word in displayed sentence
    //var displayedSentence by remember { mutableStateOf("") }
    var displayedSentence by remember { mutableStateOf(AnnotatedString("")) }

    //val selectedLevel by viewModel.selectedLevel
    val availableQuizzes by viewModel.availableQuizzes.collectAsState()

    val selectedQuiz by viewModel.selectedQuiz


    var isLearningExpanded by rememberSaveable { mutableStateOf(false) }
    var showDashboardSheet by remember { mutableStateOf(false) }
    val fluency by viewModel.fluency
   // val activeFilters by viewModel.activeFilters.collectAsState()
    val stats by viewModel.auditStats.collectAsState()

    val lockedAnswers by viewModel.lockedAnswers.collectAsState()
    val dontKnowIndices by viewModel.dontKnowIndices.collectAsState()
    val unlockedLevels by viewModel.unlockedLevels.collectAsState()
    val isCurrentQuestionLocked = lockedAnswers.containsKey(currentQuestionIndex)

    val heightClass = rememberHeightClass()
// 2. Derive dynamic spacing & padding values based on screen height
    val verticalPadding: Dp = when (heightClass) {
        HeightClass.COMPACT -> 0.dp   // Less padding on short screens (e.g. 360x640dp)
        HeightClass.MEDIUM -> 6.dp    // Balanced padding on standard screens
        HeightClass.EXPANDED -> 16.dp // Generous padding on tall screens
    }

    val rowVerticalPadding = when (heightClass) {
        HeightClass.COMPACT -> 0.dp  // Eliminate inner vertical padding on short screens
        HeightClass.MEDIUM -> 2.dp
        HeightClass.EXPANDED -> 8.dp
    }

    val questionTextPadding = when (heightClass) {
        HeightClass.COMPACT -> 1.dp
        else -> 4.dp
    }

    val iconSize: Dp = when (heightClass) {
        HeightClass.COMPACT -> 20.dp
        HeightClass.MEDIUM -> 24.dp
        HeightClass.EXPANDED -> 32.dp
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.ShowToast -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                // Quiz just finished - close the sheet so the base view (with the
                // relocated summary) becomes visible.
                is UiEvent.QuizCompleted -> onFinished()
            }
        }
    }

    // Preload the A2/B1/B2 audit sheets from Firestore into the local cache as soon as the audit opens,
    // so advancing past the Baseline is instant and re-entering finds them cached. Runs once, in the
    // background; harmless if the sheets aren't uploaded yet (the read falls back to the bundle).
    LaunchedEffect(Unit) {
        viewModel.preloadAuditSheets()
    }

    // Load whichever audit version we were opened for (1 = original, 2 = New Audit fresh set).
    LaunchedEffect(initialVersion) {
        viewModel.startAtVersion(initialVersion)
    }
    // When launched from "Go to your X test", jump to that level ONCE, as soon as it's actually unlocked
    // (the unlock ceiling loads asynchronously, so wait for it rather than firing a "locked" toast).
    // Guarded so it only applies the initial jump once - otherwise re-running when selectedLevel changes
    // would snap the user back here every time they tap an earlier tab to re-take it.
    var appliedInitialLevel by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialLevel, unlockedLevels) {
        if (!appliedInitialLevel && initialLevel != null && unlockedLevels.contains(initialLevel)) {
            if (initialLevel != selectedLevel) viewModel.onLevelSelected(initialLevel)
            appliedInitialLevel = true
        }
    }
    LaunchedEffect(currentQuestionIndex, questions, lockedAnswers) {
        Timber.w("Screen height ${heightClass}")
        if (questions.isNotEmpty()) {
            val question = questions[currentQuestionIndex]
            val questionText =
                if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
                    question.sentence.replace("_", "___")

                } else {
                    ""//question.sentence
                }
            val lockedOption = lockedAnswers[currentQuestionIndex]

            if (lockedOption != null) {
                // Already answered - restore a read-only view of what was chosen.
                val wasCorrect = lockedOption == question.correctOption
                selectedOption = lockedOption
                isCurrentAnswerCorrect = wasCorrect

                displayedSentence = if (wasCorrect) {
                    when (viewModel.currentFileFormat.value) {
                        viewModel.quizFillInTheBlanks -> viewModel.highlightWordInSentence(
                            sentence = question.sentence.replace(Regex("_+"), lockedOption),
                            wordToHighlight = lockedOption,
                            highlightColor = Color.Green
                        )
                        viewModel.quizDefinitions -> AnnotatedString(question.title)
                        viewModel.quizMultipleChoice -> AnnotatedString(lockedOption)
                        else -> viewModel.highlightWordInSentence(
                            sentence = lockedOption,
                            wordToHighlight = lockedOption,
                            highlightColor = Color.Green
                        )
                    }
                } else {
                    AnnotatedString(questionText)
                }
            } else {
                displayedSentence = AnnotatedString(questionText)
                // Reset the selection state for the new question
                selectedOption = null
                isCurrentAnswerCorrect = null
            }
        }
    }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawLine(
                        color = Color.White.copy(alpha = 0.1f),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .padding(vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Use full labels on wider screens (≥ 380dp), short on small devices
            val useFullLabels = LocalConfiguration.current.screenWidthDp >= 380
            val labelFor: (ReadinessAuditLevels) -> String = { if (useFullLabels) it.description else it.shortLabel }

            HorizontalLevelPicker(
                options = ReadinessAuditLevels.entries.map { labelFor(it) },
                selectedOption = labelFor(selectedLevel),
                lockedOptions = ReadinessAuditLevels.entries
                    .filterNot { unlockedLevels.contains(it) }
                    .map { labelFor(it) }
                    .toSet(),
                onOptionSelected = { newLabel ->
                    val level = ReadinessAuditLevels.entries.first { labelFor(it) == newLabel }
                    if (level != selectedLevel){
                        viewModel.onLevelSelected(level)

                        if (viewModel.doIHaveCurrentQuestionInfo()) {
                            infoDisabled = false
                        } else {
                            infoDisabled = true
                        }
                    }
                }
            )

            AuditStatusHeader(
                unlockedLevels = unlockedLevels,
                heightClass = heightClass,
                confidence = stats.confidence,
                readiness = stats.readiness,
                confidenceLabel = viewModel.getConfidenceLabel(stats.confidence),
            )

            // --- Quiz-level learning points (stays the same for all 10 questions) ---
            val learningTitleData = uiState.format7or10ListRoot?.data?.first()
            val learningTitle = learningTitleData?.learningTitle ?: "Why this quiz works"
            val learningPoints = learningTitleData?.learningPoints.orEmpty()

            AuditLearningPoints(
                learningTitle = learningTitle,
                learningPoints = learningPoints,
                isExpanded = isLearningExpanded,
                onToggle = { isLearningExpanded = !isLearningExpanded },
                verticalPadding = verticalPadding,
            )

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = greyLight2
            )

            AuditPagingDots(
                questionCount = questions.size,
                currentQuestionIndex = currentQuestionIndex,
                dontKnowIndices = dontKnowIndices,
                userAnswers = userAnswers,
                verticalPadding = verticalPadding,
            )

            // Show the "Choose the best answer" instruction only on the FIRST question of each set
            // (each source data[] entry) to save vertical space on the rest.
            if (questions.isNotEmpty() && questions[currentQuestionIndex].isFirstInSet) {
                Text(
                    text = stringResource(R.string.quiz_choose_best_answer),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
//                    .padding(top = 8.dp)
                        .padding(vertical = verticalPadding)
                )
            }

            // Scrollable question + answers area (fills remaining space)
            if (questions.isNotEmpty()) {
                val question = questions[currentQuestionIndex]

                AuditQuestionAnswers(
                    viewModel = viewModel,
                    question = question,
                    currentQuestionIndex = currentQuestionIndex,
                    heightClass = heightClass,
                    verticalPadding = verticalPadding,
                    rowVerticalPadding = rowVerticalPadding,
                    questionTextPadding = questionTextPadding,
                    isCurrentQuestionLocked = isCurrentQuestionLocked,
                    selectedOption = selectedOption,
                    isCurrentAnswerCorrect = isCurrentAnswerCorrect,
                    displayedSentence = displayedSentence,
                    onSelectedOptionChange = { selectedOption = it },
                    onAnswerCorrectChange = { isCurrentAnswerCorrect = it },
                    onDisplayedSentenceChange = { displayedSentence = it },
                    modifier = Modifier.weight(1f),
                )

                AuditNavigationRow(
                    viewModel = viewModel,
                    currentQuestionIndex = currentQuestionIndex,
                    questionCount = questions.size,
                    heightClass = heightClass,
                    isCurrentQuestionLocked = isCurrentQuestionLocked,
                    onInfoDisabledChange = { infoDisabled = it },
                )
            }

            // Statistics
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = greyLight2
            )
            AuditStatisticsRow(
                correct = quizStatistics.correct,
                tries = quizStatistics.tries,
            )
            // Paging control (dots)

        }

    AuditBottomSheets(
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
} //:QuizScreen


/** Intro text + the confidence / readiness status card. */
@Composable
private fun AuditStatusHeader(
    unlockedLevels: Set<ReadinessAuditLevels>,
    heightClass: HeightClass,
    confidence: Int,
    readiness: Int,
    confidenceLabel: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        // Collapsed by default so the quiz itself (question + answers + nav) gets priority
        // vertical space on small screens - this whole block can be long once expanded.
        var isStatusExpanded by rememberSaveable { mutableStateOf(true) }

        AnimatedVisibility(visible = isStatusExpanded) {
            Column {
                // 1. Main Introductory Text
                Text(
                    // Gate on Baseline actually being complete (Logic unlocked), not just
                    // confidence > 0, since confidence now also rises from partial progress
                    // within Baseline itself.
                    text = if (unlockedLevels.contains(ReadinessAuditLevels.BASELINE)) {
                        if (heightClass == HeightClass.COMPACT) {
                            //"Let's start with a quick check of your current skills. Completing at least the Baseline gives us an indication of how to adjust the screens."
                            "Let's start with a quick check of your current skills."
                        }else{
                            "To build an accurate roadmap for your exam success, let's start with a quick check of your current skills. Completing at least the Baseline gives us a rough indication of how we adjust the screens."
                        }

                    } else {

                        if (heightClass == HeightClass.COMPACT) {
                            "Baseline established. Complete the remaining quizzes. Finishing all 4 gives us the highest confidence."
                        }else{
                            "Baseline established. Complete the remaining quizzes (Logic, Lexis, Core) to raise OUR Confidence score - finishing all 4 gives us the highest confidence."
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1C1C1E),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AuditStatItem(
                                label = "Our Confidence",
                                value = "${confidence}%",
                                subValue = confidenceLabel
                            )

                            Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color.DarkGray))

                            AuditStatItem(
                                label = "Your Exam Readiness",
                                value = "${readiness}%",
                                subValue = "B1 Level"
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}


/** Collapsible "Why this quiz works" learning points. */
@Composable
private fun AuditLearningPoints(
    learningTitle: String,
    learningPoints: List<String>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    verticalPadding: Dp,
) {
    if (learningPoints.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = learningTitle,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )

            Text(
                text = if (isExpanded) "less" else "more…",
                style = MaterialTheme.typography.labelMedium,
                color = orangeLight,
                modifier = Modifier
                    .clickable { onToggle() }
                    .padding(8.dp)
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 8.dp)
            ) {
                learningPoints.forEach { point ->
                    Row(
                        modifier = Modifier.//padding(vertical = 2.dp),
                        padding(vertical = verticalPadding),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(text = "• ", color = orangeLight)
                        Text(
                            text = point,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

    }//: Column
}


/** The row of paging dots (one per question), coloured by state. */
@Composable
private fun AuditPagingDots(
    questionCount: Int,
    currentQuestionIndex: Int,
    dontKnowIndices: Set<Int>,
    userAnswers: MutableMap<Int, Boolean>,
    verticalPadding: Dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding),
        horizontalArrangement = Arrangement.Center // Center the dots horizontally
    ) {
        for (index in 0 until questionCount) { // Iterate through the questions
            Box(
                modifier = Modifier
                    .size(10.dp) // Set size of the dot
                    .clip(CircleShape) // Make it a circle
                    .background(
                        when {
                            index == currentQuestionIndex -> blueBright2       // current
                            dontKnowIndices.contains(index) -> orangeLight      // Don't Know
                            else -> dotColor(index, userAnswers)               // green/red/grey
                        }
                    ) // Set color based on current page
            )
            Spacer(modifier = Modifier.width(8.dp)) // Add spacing between dots
        }
    }
}


/** The scrollable question text + answer option rows for the current question. */
@Composable
private fun AuditQuestionAnswers(
    viewModel: ReadinessAuditViewModel,
    question: QuizQuestion,
    currentQuestionIndex: Int,
    heightClass: HeightClass,
    verticalPadding: Dp,
    rowVerticalPadding: Dp,
    questionTextPadding: Dp,
    isCurrentQuestionLocked: Boolean,
    selectedOption: String?,
    isCurrentAnswerCorrect: Boolean?,
    displayedSentence: AnnotatedString,
    onSelectedOptionChange: (String?) -> Unit,
    onAnswerCorrectChange: (Boolean?) -> Unit,
    onDisplayedSentenceChange: (AnnotatedString) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    // Reset scroll when question changes
    LaunchedEffect(currentQuestionIndex) {
        scrollState.scrollTo(0)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {

        val annotatedQuestionText =
            if (viewModel.currentFileFormat.value == viewModel.quizDefinitions) {
                AnnotatedString(question.title)
            } else {
                buildAnnotatedString {
                    if (isCurrentAnswerCorrect == true && selectedOption != null) {
                        val parts = question.sentence.split("_")
                        if (parts.size == 2) {
                            append(parts[0])
                            withStyle(
                                style = SpanStyle(
                                    color = Color.Green,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(selectedOption!!)
                            }
                            append(parts[1])
                        } else {
                            append(displayedSentence)
                        }
                    } else {
                        append(displayedSentence)
                    }
                }
            }

        Text(
            text = annotatedQuestionText,
//                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = if (heightClass == HeightClass.COMPACT) 15.sp else 16.sp),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = questionTextPadding),
            color = orangeLight,
        )


        //A row for each question
        question.words.forEach { option ->
            val isOptionCorrect = option == question.correctOption

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = rowVerticalPadding)
            ) {
                // Left cell takes all remaining width so a long answer WRAPS instead of
                // shoving the radio button off the right edge.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        modifier = Modifier.clickable {
                            val fullSentence =
                                if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
                                    question.sentence.replace("_", option)
                                } else {
                                    if (viewModel.currentFileFormat.value == viewModel.quizDefinitions) {
                                        option.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ")
                                            .trim()
                                    } else {
                                        option
                                    }
                                }
                            // Locked questions can still be heard (read), just not re-answered.
                            if (!isCurrentQuestionLocked) {
                                val isCorrect = option == question.correctOption
                                viewModel.updateAnswer(option, isCorrect)
                            }
                            viewModel.handleTap(fullSentence)
                        },
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Speak ${question.sentence.replace("_", option)}",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = option,
                        color = orangeLight,
//                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = if (heightClass == HeightClass.COMPACT) 13.sp else 14.sp
                        ),
                        modifier = Modifier
//                                    .padding(vertical = 2.dp)
                            .weight(1f)
                            .padding(vertical = verticalPadding)
                            .clickable {
                                val fullSentence =
                                    if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
                                        question.sentence.replace("_", option)
                                    } else {
                                        if (viewModel.currentFileFormat.value == viewModel.quizMultipleChoice) {
                                            option.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ")
                                                .trim()
                                        } else {
                                            option
                                        }
                                    }

                                if (!isCurrentQuestionLocked) {
                                    val isCorrect = option == question.correctOption
                                    viewModel.updateAnswer(option, isCorrect)
                                }
                                viewModel.handleTap(fullSentence)
                            }
                    )
                }

                // Reserved fixed-width cell so the radio button always sits in the SAME
                // place under the user's finger, regardless of how long the answer wraps.
                Box(
                    modifier = Modifier.width(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                RadioButton(
                    selected = selectedOption == option && isOptionCorrect,
                    enabled = !isCurrentQuestionLocked,
                    onClick = {
                        onSelectedOptionChange(option)
                        onAnswerCorrectChange(isOptionCorrect)
                        viewModel.updateAnswer(option, isOptionCorrect)

                        if (isOptionCorrect) {
                            var sentenceToSpeak = ""
                            if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
                                val sentence = question.sentence.replace(Regex("_+"), option)
                                onDisplayedSentenceChange(
                                    viewModel.highlightWordInSentence(
                                        sentence = sentence,
                                        wordToHighlight = option,
                                        highlightColor = Color.Green
                                    )
                                )
                                sentenceToSpeak = sentence
                            } else if (viewModel.currentFileFormat.value == viewModel.quizDefinitions) {
                                onDisplayedSentenceChange(AnnotatedString(question.title))
                                sentenceToSpeak =
                                    "${question.title}:${option}:${question.sentence}"
                            } else if (viewModel.currentFileFormat.value == viewModel.quizMultipleChoice) {
                                onDisplayedSentenceChange(AnnotatedString(option))
                                val cleaned = option.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ")
                                    .trim()
                                sentenceToSpeak = cleaned
                            } else {
                                sentenceToSpeak = option
                                onDisplayedSentenceChange(
                                    viewModel.highlightWordInSentence(
                                        sentence = option,
                                        wordToHighlight = option,
                                        highlightColor = Color.Green
                                    )
                                )
                            }

                            viewModel.handleTap(sentenceToSpeak)
                            viewModel.incQuizStat()
                        } else {
                            viewModel.incQuizStat(false)
                        }
                    },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = if (isCurrentAnswerCorrect == true) Color.Green else Color.Red,
                        unselectedColor = if (isCurrentAnswerCorrect == false && selectedOption == option) Color.Red else Color.Unspecified,
                        // Keep the correct/incorrect tint visible once the question locks -
                        // otherwise Material3's default disabled colors hide which option
                        // the user picked.
                        disabledSelectedColor = if (isCurrentAnswerCorrect == true) Color.Green else Color.Red,
                        disabledUnselectedColor = if (isCurrentAnswerCorrect == false && selectedOption == option) Color.Red else Color.Unspecified
                    ),
                    modifier = Modifier
                        .scale(if (heightClass == HeightClass.COMPACT) 0.85f else 1.0f) // Slightly scale down radio button on compact
                        .semantics { contentDescription = option }
                )
                } // fixed-width radio cell
            } // Row
        }
    } // Scrollable Column
}


/** Previous / Don't Know / Next navigation controls. */
@Composable
private fun AuditNavigationRow(
    viewModel: ReadinessAuditViewModel,
    currentQuestionIndex: Int,
    questionCount: Int,
    heightClass: HeightClass,
    isCurrentQuestionLocked: Boolean,
    onInfoDisabledChange: (Boolean) -> Unit,
) {
    val lastIndex = questionCount - 1

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        // 🔹 Previous
        IconButton(
            onClick = {
                if (currentQuestionIndex > 0) {
                    viewModel.currentQuestionIndex.value -= 1
                    onInfoDisabledChange(!viewModel.doIHaveCurrentQuestionInfo())
                    viewModel.resetInfoButtonTapped()
                }
            },
            enabled = currentQuestionIndex > 0,
            modifier = Modifier.size(if (heightClass == HeightClass.COMPACT) 36.dp else 48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Previous",
                tint = if (currentQuestionIndex == 0) Color.Gray else buttonColor,
                modifier = Modifier.size(if (heightClass == HeightClass.COMPACT) 28.dp else 36.dp)
            )
        }

        // 🔹 Expanding space left
        Spacer(modifier = Modifier.weight(1f))

        // Middle controls: always a "Don't Know" button; in debug builds the
        // "Reset Audit (D)" button sits beside it.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (BuildConfig.DEBUG) {
                Button(
                    onClick = { viewModel.resetAuditForDebug() },
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Reset Audit (D)")
                }
            }
            OutlinedButton(
                onClick = {
                    viewModel.markDontKnow()
                    // Skip forward like the Next arrow (last question completes the quiz).
                    if (currentQuestionIndex < lastIndex) {
                        viewModel.currentQuestionIndex.value += 1
                        onInfoDisabledChange(!viewModel.doIHaveCurrentQuestionInfo())
                        viewModel.resetInfoButtonTapped()
                    }
                },
                enabled = !isCurrentQuestionLocked,
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = orangeLight),
                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
            ) {
                Text("Don't Know", style = MaterialTheme.typography.bodyMedium)
            }
        }
        // 🔹 Expanding space right
        Spacer(modifier = Modifier.weight(1f))

        // 🔹 Next
        IconButton(
            onClick = {
                if (currentQuestionIndex < lastIndex) {
                    viewModel.currentQuestionIndex.value += 1
                    onInfoDisabledChange(!viewModel.doIHaveCurrentQuestionInfo())
                    viewModel.resetInfoButtonTapped()
                }
            },
            enabled = currentQuestionIndex < lastIndex,
            modifier = Modifier.size(if (heightClass == HeightClass.COMPACT) 36.dp else 48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Next",
                tint = if (currentQuestionIndex == lastIndex) Color.Gray else buttonColor,
                modifier = Modifier.size(if (heightClass == HeightClass.COMPACT) 28.dp else 36.dp)
            )
        }
    }
}


/** The Correct / Tries tally row. */
@Composable
private fun AuditStatisticsRow(correct: Int, tries: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween // Arrange items with space between
    ) {
        Text(
            text = "Correct: $correct",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                color = Color.Green
            ),
            textAlign = TextAlign.Start,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp) // Add padding to the start
        )

        Text(
            text = "Tries: $tries",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                color = Color.Red
            ),
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp), // Add padding to the end
            textAlign = TextAlign.End // Align text to the end
        )
    }
}


/** Rate-limit / info / dashboard bottom sheets for the audit screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuditBottomSheets(
    viewModel: ReadinessAuditViewModel,
    context: Context,
    isRateLimitingSheetVisible: Boolean,
    isDailyRateLimitingSheetVisible: Boolean,
    isHourlyRateLimitingSheetVisible: Boolean,
    showInfoBottomSheet: Boolean,
    onCloseInfo: () -> Unit,
    infoQuestion: QuizQuestion?,
    showDashboardSheet: Boolean,
    onCloseDashboard: () -> Unit,
    selectedLevelDescription: String,
) {
    val dashboardSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (isRateLimitingSheetVisible) {
        RateLimitOKReasonsBottomSheet(onCloseSheet = { viewModel.hideRateOKLimitSheet() })
    }
    if (isDailyRateLimitingSheetVisible) {
        if (context is androidx.activity.ComponentActivity) {
            RateLimitDailyPaywallBottomSheet(
                onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) },
                onCloseSheet = { viewModel.hideDailyRateLimitSheet() }
            )
        }
    }
    if (isHourlyRateLimitingSheetVisible) {
        if (context is androidx.activity.ComponentActivity) {
            RateLimitHourlyPaywallBottomSheet(
                onCloseSheet = { viewModel.hideHourlyRateLimitSheet() },
                onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) }
            )
        }
    }

    if (showInfoBottomSheet && infoQuestion != null) {
        QuizInfoBottomSheetView(
            infoQuestion.summary,
            infoQuestion.explain,
            onCloseSheet = onCloseInfo
        )
    }
    if (showDashboardSheet) {
        ModalBottomSheet(
            onDismissRequest = onCloseDashboard,
            sheetState = dashboardSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            // Constrain height to 90% of screen for a "Full Sheet" feel
            Box(modifier = Modifier.fillMaxHeight(0.9f)) {

                val level = selectedLevelDescription
                // We reuse the UsageDashboardScreen directly.
                // Hilt will automatically inject UsageDashboardViewModel inside it.
                UsageDashboardScreen(
                    targetLevel = level,
                    onStartQuiz = { quizId ->
                        // 1. Close the sheet
                        onCloseDashboard()



//                        val quizDetail = availableQuizzes.first { it.id == quizId }
//                        viewModel.onQuizSelected(quizDetail)                        //TODO: for now just hide

                        // 2. (Optional) Auto-select the quiz
                        // You can ask the ViewModel to switch to this quiz ID immediately
                         viewModel.selectQuizById(quizId)
                    }
                )
            }
        }
    }
}


@Composable
private fun AuditStatItem(label: String, value: String, subValue: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodySmall,  color = Color(0xFFFF9500),)
        Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(text = subValue, style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
    }
}
