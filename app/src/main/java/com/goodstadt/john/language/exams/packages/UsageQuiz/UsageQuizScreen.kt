package com.goodstadt.john.language.exams.packages.UsageQuiz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.R
import com.goodstadt.john.language.exams.models.UsageMastery
import com.goodstadt.john.language.exams.packages.reference.QuizInfoBottomSheetView
import com.goodstadt.john.language.exams.packages.reference.UsageDashboardScreen
import com.goodstadt.john.language.exams.packages.reference.shared.HorizontalLevelPicker
import com.goodstadt.john.language.exams.screens.RateLimitDailyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.UsageQuiz.UsageQuizLevelsFilename
import com.goodstadt.john.language.exams.screens.shared.AutoAdvanceToggleButton
import com.goodstadt.john.language.exams.screens.shared.InfoCircleButton
import com.goodstadt.john.language.exams.ui.theme.blueBright2
import com.goodstadt.john.language.exams.ui.theme.buttonColor
import com.goodstadt.john.language.exams.ui.theme.greyLight2
import com.goodstadt.john.language.exams.ui.theme.nonSelectedBackground
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageQuizScreen(
    viewModel: UsageQuizViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsState()
    var infoDisabled by remember { mutableStateOf(false) }
    var showInfoBottomSheet by remember { mutableStateOf(false) }

    val questions by viewModel.questions.collectAsState()
    val autoAdvance by viewModel.autoAdvance.collectAsState()
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
    val availableQuizzesObsolete by viewModel.availableQuizzesObsolete
    val availableQuizzes by viewModel.availableQuizzes.collectAsState()

    val selectedQuiz by viewModel.selectedQuiz

//    val displayText = if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
//        "Hear, and then choose the best answer"
//    } else {
//        "Choose the best answer"
//    }

    val displayText = if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
        stringResource(R.string.quiz_hear_choose_best_answer)
    } else {
        stringResource(R.string.quiz_choose_best_answer)
    }

    var isLearningExpanded by rememberSaveable { mutableStateOf(false) }
    var showDashboardSheet by remember { mutableStateOf(false) }
    val dashboardSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fluency by viewModel.fluency
    val activeFilters by viewModel.activeFilters.collectAsState()

    LaunchedEffect(currentQuestionIndex, questions) {
        val question = questions.getOrNull(currentQuestionIndex)
        if (question != null) {
            val questionText =
                if ( (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks)) {
//                    question.sentence.replace("_", "___")
                    // Regex logic:
                    // (?<!_) -> Check that there is NOT an underscore before
                    // _      -> The actual underscore to match
                    // (?!_)  -> Check that there is NOT an underscore after
                    question.sentence.replace(Regex("(?<!_)_(?!_)"), "___")
                } else {
                    ""//question.sentence
                }

            // Restore any previous answer for this question so navigating back (manual or auto-advance)
            // re-shows the radio selection for review. Answers persist until the quiz is exited/restarted.
            val prevOption = viewModel.selectedOptionFor(question.page)
            val prevCorrect = viewModel.answerCorrectFor(question.page)
            selectedOption = prevOption
            isCurrentAnswerCorrect = prevCorrect
            displayedSentence = if (prevCorrect == true && prevOption != null) {
                // Rebuild the filled-in sentence exactly as answering does, per file format.
                when (viewModel.currentFileFormat.value) {
                    viewModel.quizFillInTheBlanks -> viewModel.highlightWordInSentence(
                        sentence = question.sentence.replace(Regex("_+"), prevOption),
                        wordToHighlight = prevOption,
                        highlightColor = Color.Green
                    )
                    viewModel.quizDefinitions -> AnnotatedString(question.title)
                    viewModel.quizMultipleChoice -> AnnotatedString(prevOption)
                    else -> viewModel.highlightWordInSentence(
                        sentence = prevOption,
                        wordToHighlight = prevOption,
                        highlightColor = Color.Green
                    )
                }
            } else {
                AnnotatedString(questionText)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Use full labels on wider screens (≥ 380dp), short on small devices
        val useFullLabels = LocalConfiguration.current.screenWidthDp >= 380
        val labelFor: (UsageQuizLevelsFilename) -> String = { if (useFullLabels) it.description else it.shortLabel }

        HorizontalLevelPicker(
            options = UsageQuizLevelsFilename.entries.map { labelFor(it) },
            selectedOption = labelFor(selectedLevel),
            onOptionSelected = { newLabel ->
                val level = UsageQuizLevelsFilename.entries.first { labelFor(it) == newLabel }
                if (level != selectedLevel){
                    viewModel.onLevelSelected(level)
                    viewModel.loadQuestions()

                    if (viewModel.doIHaveCurrentQuestionInfo()) {
                        infoDisabled = false
                    } else {
                        infoDisabled = true
                    }
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Optional: Add horizontal padding to keep icon off the very edge
                .padding(horizontal = 4.dp)
        ) {

            Text(
                text = fluency.label,
                color = fluency.color,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .background(fluency.color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .align(Alignment.CenterStart)
//                    .padding(horizontal = 8.dp, vertical = 1.dp)
            )

            // 1. The Dropdown (Centered)
            // We wrap it to ensure it aligns to the Box's center, not the Column's
            Box(modifier = Modifier.align(Alignment.Center)) {
                DropdownMenuBox(
                    options = availableQuizzes.map { it.title },
                    selectedOption = selectedQuiz?.title ?: "Select a Quiz",
                    onOptionSelected = { newQuizTitle ->
                        val quizDetail = availableQuizzes.first { it.title == newQuizTitle }
                        viewModel.onQuizSelected(quizDetail)
                    }
                )
            }

            // 2. The Icon (Right Aligned)
            IconButton(
                onClick = {  showDashboardSheet = true },
                modifier = Modifier.align(Alignment.CenterEnd) // 👈 Locks to right
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium, // Or Insight/Chart icon
                    contentDescription = "Stats",
                    tint = Color(0xFFFF9800)
                )
            }
        }

        // --- Quiz-level learning points (stays the same for all 10 questions) ---

//        val fred = selectedQuiz
//        print(fred)

        val learningTitleData = uiState.format7or10ListRoot?.data?.first()
        val learningTitle = learningTitleData?.learningTitle ?: "Why this quiz works"
        val learningPoints = learningTitleData?.learningPoints.orEmpty()


        if (learningPoints.isNotEmpty()) {
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
                        text = if (isLearningExpanded) "less" else "more…",
                        style = MaterialTheme.typography.labelMedium,
                        color = orangeLight,
                        modifier = Modifier
                            .clickable { isLearningExpanded = !isLearningExpanded }
                            .padding(8.dp)
                    )
                }

                AnimatedVisibility(visible = isLearningExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 8.dp)
                    ) {
                        learningPoints.forEach { point ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
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

//                HorizontalDivider(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(top = 6.dp),
//                    thickness = 1.dp,
//                    color = greyLight2
//                )
            }//: Column
        }//:learningPoints



        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = greyLight2
        )

        // Mastery Filter Chips
        UsageMasteryFilterChips(
            activeFilters = activeFilters,
            onToggle = { viewModel.toggleFilter(it) },
            onSelectAll = { viewModel.selectAllFilters() }
        )

        // Question counter with filter info
        if (activeFilters.isNotEmpty() && questions.isNotEmpty()) {
            Text(
                text = "Showing ${questions.size} of ${viewModel.totalQuestionCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Filtered-empty state
        if (questions.isEmpty() && activeFilters.isNotEmpty()) {
            val filterNames = activeFilters.joinToString(", ") { it.name }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "No $filterNames questions to show.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Explain what the tapped filter(s) actually mean.
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            )
        }

        // Scrollable question + answers area (fills remaining space)
        if (questions.isNotEmpty()) {
            val question = questions[currentQuestionIndex]
            val scrollState = rememberScrollState()

            // Reset scroll when question changes
            LaunchedEffect(currentQuestionIndex) {
                scrollState.scrollTo(0)
                // Keep the info button correct after any move, including auto-advance.
                infoDisabled = !viewModel.doIHaveCurrentQuestionInfo()
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
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
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    color = orangeLight,
                )

                // Per-question mastery badge
                val masteryDisplay = viewModel.getQuestionMasteryDisplay(question.page)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = masteryDisplay.first,
                        color = masteryDisplay.second,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .background(masteryDisplay.second.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    )
                }

                question.words.forEach { option ->
                    val isOptionCorrect = option == question.correctOption

                    // Format-aware sentence for the loudspeaker preview (and for replaying a solved question).
                    val previewSentence =
                        if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
                            question.sentence.replace("_", option)
                        } else {
                            if (viewModel.currentFileFormat.value == viewModel.quizDefinitions) {
                                option.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ").trim()
                            } else {
                                option
                            }
                        }

                    // Answering the question: used by BOTH the radio button AND tapping the answer sentence,
                    // so they score identically. The loudspeaker does NOT call this - it only previews.
                    // Marking finishes once the correct answer is chosen: after that, a tap just replays a
                    // preview and never changes Correct/Tries again.
                    val submitAnswer: () -> Unit = {
                        if (isCurrentAnswerCorrect == true) {
                            // Already solved -> preview only, no scoring.
                            viewModel.handleTap(previewSentence)
                        } else {
                            selectedOption = option
                            isCurrentAnswerCorrect = isOptionCorrect
                            // Remember this selection so navigating back re-shows it (until the quiz restarts).
                            viewModel.rememberSelection(question.page, option, isOptionCorrect)
                            viewModel.updateAnswer(isOptionCorrect) // Tries++ (and Correct recomputed)

                            if (isOptionCorrect) {
                                var sentenceToSpeak = ""
                                if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
                                    val sentence = question.sentence.replace(Regex("_+"), option)
                                    displayedSentence = viewModel.highlightWordInSentence(
                                        sentence = sentence,
                                        wordToHighlight = option,
                                        highlightColor = Color.Green
                                    )
                                    sentenceToSpeak = sentence
                                } else if (viewModel.currentFileFormat.value == viewModel.quizDefinitions) {
                                    displayedSentence = AnnotatedString(question.title)
                                    sentenceToSpeak =
                                        "${question.title}:${option}:${question.sentence}"
                                } else if (viewModel.currentFileFormat.value == viewModel.quizMultipleChoice) {
                                    displayedSentence = AnnotatedString(option)
                                    val cleaned = option.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ")
                                        .trim()
                                    sentenceToSpeak = cleaned
                                } else {
                                    sentenceToSpeak = option
                                    displayedSentence = viewModel.highlightWordInSentence(
                                        sentence = option,
                                        wordToHighlight = option,
                                        highlightColor = Color.Green
                                    )
                                }

                                viewModel.handleTap(sentenceToSpeak)
                                viewModel.incQuizStat()
                                // Auto-advance (if on): move to the next question once the audio finishes.
                                viewModel.onCorrectAnswered()
                            } else {
                                viewModel.incQuizStat(false)
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Left cell: takes all remaining width so a long sentence WRAPS instead of
                        // shoving the radio button off the edge.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                // Loudspeaker: preview the sentence only. Never affects Correct/Tries.
                                modifier = Modifier.clickable {
                                    viewModel.handleTap(previewSentence)
                                },
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Speak ${question.sentence.replace("_", option)}",
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                // Tapping the answer sentence marks it, exactly like the radio button.
                                text = option,
                                color = orangeLight,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 2.dp)
                                    .clickable { submitAnswer() }
                            )
                        }

                        // Reserved fixed-width cell so the radio button always sits in the SAME place
                        // under the user's finger, regardless of sentence length.
                        Box(
                            modifier = Modifier.width(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            RadioButton(
                                selected = selectedOption == option && isOptionCorrect,
                                onClick = { submitAnswer() },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = if (isCurrentAnswerCorrect == true) Color.Green else Color.Red,
                                    unselectedColor = if (isCurrentAnswerCorrect == false && selectedOption == option) Color.Red else Color.Unspecified
                                ),
                                modifier = Modifier.semantics { contentDescription = option }
                            )
                        }
                    } // Row
                }
            } // Scrollable Column
//            Row(
//                horizontalArrangement = Arrangement.SpaceBetween,
//                modifier = Modifier.fillMaxWidth()
//            ) {
//                InfoButtonRow(infoDisabled = infoDisabled,
//                    onClick = {
//
//                        if (infoDisabled == false) {
//                            showInfoBottomSheet = true
//                        }
//                    })
//            }// row

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                // 🔹 Previous
                IconButton(
                    onClick = {
                        if (currentQuestionIndex > 0) {
                            viewModel.currentQuestionIndex.value -= 1
                            infoDisabled = !viewModel.doIHaveCurrentQuestionInfo()
                            viewModel.resetInfoButtonTapped()
                        }
                    },
                    enabled = currentQuestionIndex > 0
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous",
                        tint = if (currentQuestionIndex == 0) Color.Gray else buttonColor,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // 🔹 Expanding space left
                Spacer(modifier = Modifier.weight(1f))

                // 🔹 Info + Auto-advance buttons (centered)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InfoCircleButton(
                        infoDisabled = infoDisabled,
                        onClick = {
                            if (!infoDisabled) {
                                showInfoBottomSheet = true
                                viewModel.onInfoButtonTapped() //mark user getting help
                            }
                        }
                    )
                    // Auto-advance toggle: grey = off, blue = on. Shared setting across all quiz types.
                    AutoAdvanceToggleButton(
                        enabled = autoAdvance,
                        onClick = { viewModel.toggleAutoAdvance() }
                    )
                }

                // 🔹 Expanding space right
                Spacer(modifier = Modifier.weight(1f))

                // 🔹 Next
                IconButton(
                    onClick = {
                        if (currentQuestionIndex < questions.lastIndex) {
                            viewModel.currentQuestionIndex.value += 1
                            infoDisabled = !viewModel.doIHaveCurrentQuestionInfo()
                            viewModel.resetInfoButtonTapped()
                        }
                    },
                    enabled = currentQuestionIndex < questions.lastIndex
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next",
                        tint = if (currentQuestionIndex == questions.lastIndex) Color.Gray else buttonColor,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // Statistics
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = greyLight2
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween // Arrange items with space between
        ) {
            Text(
                text = "Correct: ${quizStatistics.correct}",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    color = Color.Green
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp) // Add padding to the start
            )
            Text(
                text = "Tries: ${quizStatistics.tries}",
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
        // Paging control (dots)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center // Center the dots horizontally
        ) {
            for (index in 0 until questions.size) { // Iterate through the questions
                Box(
                    modifier = Modifier
                        .size(10.dp) // Set size of the dot
                        .clip(CircleShape) // Make it a circle
                        .background(
                            if (index == currentQuestionIndex) blueBright2 else dotColor(
                                index,
                                userAnswers
                            )
                        ) // Set color based on current page
                )
                Spacer(modifier = Modifier.width(4.dp)) // Add spacing between dots
            }
        }
    }
    if (isRateLimitingSheetVisible) {
        RateLimitOKReasonsBottomSheet(onCloseSheet = { viewModel.hideRateOKLimitSheet() })
    }
    if (isDailyRateLimitingSheetVisible) {
//        RateLimitDailyReasonsBottomSheet (onCloseSheet = { viewModel.hideDailyRateLimitSheet() })
        if (context is androidx.activity.ComponentActivity) {
//            RateLimitDailyReasonsBottomSheet(
//                onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) },
//                onCloseSheet = { viewModel.hideDailyRateLimitSheet() }
//            )
            RateLimitDailyPaywallBottomSheet(
                onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) },
                onCloseSheet = { viewModel.hideDailyRateLimitSheet() }
            )
        }
    }
    if (isHourlyRateLimitingSheetVisible) {
        if (context is androidx.activity.ComponentActivity) {
//            RateLimitHourlyReasonsBottomSheet(
//                onCloseSheet = { viewModel.hideHourlyRateLimitSheet() },
//                onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) }
//            )
            RateLimitHourlyPaywallBottomSheet(
                onCloseSheet = { viewModel.hideHourlyRateLimitSheet() },
                onBuyPremiumButtonPressed = { viewModel.buyPremiumButtonPressed(context) }
            )
        }
    }

    if (showInfoBottomSheet) {
        QuizInfoBottomSheetView(
            questions[currentQuestionIndex].summary,
            questions[currentQuestionIndex].explain,
            onCloseSheet = { showInfoBottomSheet = false }
        )
    }
    if (showDashboardSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDashboardSheet = false },
            sheetState = dashboardSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            // Constrain height to 90% of screen for a "Full Sheet" feel
            Box(modifier = Modifier.fillMaxHeight(0.9f)) {

                val level = selectedLevel.description
                // We reuse the UsageDashboardScreen directly.
                // Hilt will automatically inject UsageDashboardViewModel inside it.
                UsageDashboardScreen(
                    targetLevel = level,
                    onStartQuiz = { quizId ->
                        // 1. Close the sheet
                        showDashboardSheet = false



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
} //:QuizScreen

@Composable
fun DropdownMenuBoxObsolete(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var buttonCoordinates by remember {
        mutableStateOf(
            Offset(
                0f,
                0f
            )
        )
    } // Store button coordinates as Offset
    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { layoutCoordinates ->
                // Capture the button's position in pixels
                buttonCoordinates = layoutCoordinates.positionInWindow()
            },
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = { expanded = true },
            modifier = Modifier.wrapContentWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = nonSelectedBackground, // Dark grey background
                contentColor = Color.White // White text color
            ),
            shape = RoundedCornerShape(4.dp) // Small rounded corners
        ) {
            Text(selectedOption)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(nonSelectedBackground) // Dark grey background for dropdown
                .clip(RoundedCornerShape(4.dp)), // Small rounded corners for dropdown
            offset = with(density) {
                DpOffset(
                    x = 100.dp,//buttonCoordinates.x.toDp(), // Convert X coordinate to Dp
                    y = 0.dp//buttonCoordinates.y.toDp()// + 48.dp // Convert Y coordinate to Dp and add padding
                )
            }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = Color.White) }, // White text color for items
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
@Composable
fun DropdownMenuBox(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // A simple Row that acts as the trigger
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp)) // Optional: rounds the ripple effect
                .clickable { expanded = true }
                .padding(vertical = 12.dp, horizontal = 16.dp), // Generous tap target
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = selectedOption,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White
            )

            Spacer(modifier = Modifier.width(4.dp))

            // The Arrow is the "Hero" here—it signals interactivity perfectly
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Select Option",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(nonSelectedBackground)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = if (option == selectedOption) orangeLight else Color.White
                        )
                    },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
@Composable
fun dotColor(index: Int, scores: MutableMap<Int, Boolean>): Color {

    scores[index]?.let {
        return if (it) Color.Green else Color.Red // Green for correct, red for incorrect
    }

    return Color.LightGray
}

@Composable
fun UsageMasteryFilterChips(
    activeFilters: Set<UsageMastery>,
    onToggle: (UsageMastery) -> Unit,
    onSelectAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // "All" chip
        UsageMasteryChip(
            label = "All",
            isSelected = activeFilters.isEmpty(),
            dotColor = MaterialTheme.colorScheme.primary,
            onClick = onSelectAll
        )

        // Per-level chips
        UsageMastery.entries.forEach { level ->
            UsageMasteryChip(
                label = level.name,
                isSelected = activeFilters.contains(level),
                dotColor = usageMasteryChipColor(level),
                onClick = { onToggle(level) }
            )
        }
    }
}

@Composable
fun UsageMasteryChip(
    label: String,
    isSelected: Boolean,
    dotColor: Color,
    onClick: () -> Unit
) {
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .animateContentSize()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) dotColor.copy(alpha = 0.15f) else surfaceVariant)
            .then(
                if (isSelected) Modifier.border(1.5.dp, dotColor, RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable { onClick() }
            .padding(
                horizontal = if (isSelected || label == "All") 10.dp else 8.dp,
                vertical = 6.dp
            )
    ) {
        // Colored dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )

        // Label when selected, or always for "All"
        if (isSelected || label == "All") {
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = dotColor
            )
        }
    }
}

fun usageMasteryChipColor(level: UsageMastery): Color {
    return when (level) {
        UsageMastery.New -> Color.Gray
        UsageMastery.Struggling -> Color.Red
        UsageMastery.Learning -> Color(0xFFFF9800) // Orange
        UsageMastery.Review -> Color(0xFF2196F3) // Blue
        UsageMastery.Mastered -> Color(0xFF4CAF50) // Green
    }
}

/** Short, plain-English definition of each mastery level, shown in the filtered-empty state. */
fun usageMasteryExplanation(level: UsageMastery): String {
    return when (level) {
        UsageMastery.New -> "New: not attempted yet."
        UsageMastery.Struggling -> "Struggling: answered wrong — comes back soon to try again."
        UsageMastery.Learning -> "Learning: got it right, but only after a wrong try."
        UsageMastery.Review -> "Review: right first time — building towards mastery over spaced sessions."
        UsageMastery.Mastered -> "Mastered: correct first time in 3 separate sessions."
    }
}

@Composable
fun InfoButtonRow(
    infoDisabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = !infoDisabled
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = "Info",
            modifier = Modifier.size(32.dp),
            tint = if (infoDisabled) Color.Gray else buttonColor
        )
    }
}