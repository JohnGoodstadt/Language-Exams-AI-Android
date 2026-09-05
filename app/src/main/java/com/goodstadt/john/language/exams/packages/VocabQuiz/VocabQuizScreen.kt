package com.goodstadt.john.language.exams.packages.VocabQuiz

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
import com.goodstadt.john.language.exams.models.WordMasteryLevel
import com.goodstadt.john.language.exams.screens.RateLimitDailyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyPaywallBottomSheet
import com.goodstadt.john.language.exams.packages.UsageQuiz.InfoButtonRow
import com.goodstadt.john.language.exams.packages.UsageQuiz.dotColor
import com.goodstadt.john.language.exams.packages.reference.WordQuizInfoBottomSheetView
import com.goodstadt.john.language.exams.packages.reference.shared.ScrollableHorizontalLevelPicker
import com.goodstadt.john.language.exams.ui.theme.blueBright2
import com.goodstadt.john.language.exams.ui.theme.buttonColor
import com.goodstadt.john.language.exams.ui.theme.greyLight2
import com.goodstadt.john.language.exams.ui.theme.nonSelectedBackground
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.viewmodels.VocabQuizLevels
import com.goodstadt.john.language.exams.viewmodels.VocabSectionQuizViewModel
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet
import timber.log.Timber


@Composable
fun VocabQuizScreen(
    viewModel: VocabSectionQuizViewModel = hiltViewModel(),
//    viewModel: VocabQuizViewModel = hiltViewModel(),
    autoLoad: Boolean = true
) {
    val context = LocalContext.current
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
//    val availableQuizzesObsolete by viewModel.availableQuizzesObsolete
//    val availableQuizzes by viewModel.availableQuizzes.collectAsState()

    val selectedQuiz by viewModel.selectedQuiz
//    val displayText = if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
//        "Listen, and then choose the correct answer"
//    } else if (viewModel.currentFileFormat.value == viewModel.quizWordDefinition) {
//        "Choose the most suitable usage for the word"
//    } else {
//        "Choose the correct answer"
//    }

    val displayText = when (viewModel.currentFileFormat.value) {
        viewModel.quizFillInTheBlanks ->
            stringResource(R.string.quiz_listen_choose_correct_answer)

        viewModel.quizWordDefinition ->
            stringResource(R.string.quiz_choose_word_usage)

        else ->
            stringResource(R.string.quiz_choose_correct_answer)
    }

    val isSectionMode by viewModel.isSectionMode.collectAsState()
    val availableIndices by viewModel.availableSectionIndices.collectAsState()
    val currentIndex by viewModel.currentSectionIndex.collectAsState()
    val activeFilters by viewModel.activeFilters.collectAsState()
    //val showInfoSheet by viewModel.showInfoSheet.collectAsState()



    LaunchedEffect(currentQuestionIndex, questions) {
        if (questions.isNotEmpty()) {
            val question = questions[currentQuestionIndex]

            val questionText =
                if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
                    question.question.replace("_", "___")
                } else if (viewModel.currentFileFormat.value == viewModel.quizWordDefinition) {
                    question.question
                } else {
                    ""//question.sentence
                }
            displayedSentence = AnnotatedString(questionText)
            // Reset the selection state for the new question
            selectedOption = null
            isCurrentAnswerCorrect = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {


        if (isSectionMode) {
            // Only show picker if we have more than 1 quiz
            if (availableIndices.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    // ✅ FIX: Use spacedBy to add a gap, but keep the whole group centered
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    availableIndices.forEach { index ->
                        val isSelected = (index == currentIndex)

                        val accentOrange = Color.Green//Color(0xFFFF9800)
                        val borderColor = if (isSelected) accentOrange else Color.Gray.copy(alpha = 0.5f)
                        val backgroundColor = if (isSelected) accentOrange.copy(alpha = 0.1f) else Color.Transparent
                        val textColor = if (isSelected) accentOrange else MaterialTheme.colorScheme.onSurfaceVariant

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .background(backgroundColor, CircleShape)
                                .border(1.dp, borderColor, CircleShape)
                                .clip(CircleShape)
                                .clickable { viewModel.onSectionIndexSelected(index) }
                        ) {
                            Text(
                                text = "$index",
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            } else { //TODO: This could be deleted if file is only Section Quiz
                // If only 1 quiz, maybe just show the title
                Text(
                    text = viewModel.getSectionTitle(),// quizStatistics.title, // "Quiz 1"
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = orangeLight, //MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                HorizontalDivider()
            }
        }

        // 2. IF MAIN MODE: Show standard pickers (Existing logic)
        else {
            ScrollableHorizontalLevelPicker(
                options = VocabQuizLevels.entries.map { it.description },
                selectedOption = selectedLevel.description,
                onOptionSelected = { newLevel ->
                    val level = VocabQuizLevels.entries.first { it.description == newLevel }
//                    viewModel.selectedLevel.value = level
                    viewModel.onLevelSelected(level)
                    viewModel.loadQuestions()

                    if (viewModel.doIHaveCurrentQuestionInfo()) {
                        viewModel.onInfoClicked()
//                        infoDisabled = false
                    } else {
//                        viewModel.showInfoSheet = false
//                        infoDisabled  = true
                    }
                }//,
                //fontSize = 16.sp
            )
        }


        // Mastery Filter Chips
        if (isSectionMode) {
            MasteryFilterChips(
                activeFilters = activeFilters,
                onToggle = { viewModel.toggleFilter(it) },
                onSelectAll = { viewModel.selectAllFilters() }
            )

            // Question counter with filter info
            if (activeFilters.isNotEmpty()) {
                val filteredTotal = viewModel.filteredQuestionCount
                if (filteredTotal > 0) {
                    Text(
                        text = "Showing $filteredTotal of ${viewModel.totalQuestionCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }

        // Filtered-empty state: filters active but no matching questions
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
                    text = activeFilters.joinToString("\n") { vocabMasteryExplanation(it) },
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
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp)
            )
        } else if (questions.isNotEmpty()) {
            Text( //still keep the space
                text = "",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            )
        }

        if (questions.isNotEmpty()) {
            val question = questions[currentQuestionIndex]

            val annotatedQuestionText =
                buildAnnotatedString {
                    if (isCurrentAnswerCorrect == true && selectedOption != null) {
                        // --- SUCCESS STATE ---

                        append(displayedSentence)


                    } else {
                        // --- QUESTION STATE ---
                        // Not answered yet, or answered incorrectly.
                        append(displayedSentence)
                        withStyle(
                            style = SpanStyle(
                                color = Color.Green,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            // append(displayedSentence) // Append the correct word in green
                        }
                    }
                }


            if (isCurrentAnswerCorrect == true) {
                Text(
                    text = annotatedQuestionText,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    color = orangeLight,
                )
            } else {
                Text(
                    text = annotatedQuestionText,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                    color = Color.Green
                )
            }

            val fluency = viewModel.fluencyStats(annotatedQuestionText.text)
            val stats = viewModel.getWordStats(annotatedQuestionText.text)
            Column {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Optional: Add horizontal padding to keep icon off the very edge
                        .padding(horizontal = 2.dp)

                ) {
                    Timber.v(stats.toString())
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        // This spreads items out: one at the start, one in the middle (if 3), one at the end
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                       // Text("Status: ${fluency.first}", color = fluency.second, fontWeight = FontWeight.Bold)
//                    Text("Current Streak: ${stats.correctStreak}🔥")

                        Text(
                            text = fluency.first,
                            color = fluency.second,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .background(fluency.second.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
//                                .align(Alignment.CenterStart)
//                    .padding(horizontal = 8.dp, vertical = 1.dp)
                        )

//                if (stats.nextReviewTime > 0) {
//                    Text("Next review: ${viewModel.getFormattedNextReviewTime(stats.word)}")
//                }
                    }

                }

            }//: Column


            question.answers.forEach { option ->
                val isOptionCorrect =
                    option == question.correctOption // Determine if option is correct

                // Format-aware sentence for the loudspeaker preview (and for replaying a solved question).
                val previewSentence =
                    if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
                        question.question.replace("_", option)
                    } else {
                        if (viewModel.currentFileFormat.value == viewModel.quizDefinitions) {
                            option.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ").trim() // remove ()
                        } else {
                            option
                        }
                    }

                // Answering the question: used by BOTH the radio button AND tapping the answer sentence,
                // so they score identically. The loudspeaker does NOT call this - it only previews.
                // Marking finishes once the correct answer is chosen: after that, a tap just replays the
                // option as a preview and never changes Correct/Tries again.
                val submitAnswer: () -> Unit = {
                    if (isCurrentAnswerCorrect == true) {
                        // Already solved -> preview only, no scoring.
                        viewModel.handleTap(option)
                    } else {
                        selectedOption = option
                        isCurrentAnswerCorrect = isOptionCorrect
                        viewModel.updateAnswer(isOptionCorrect) // Tries++ (and Correct recomputed)
                        viewModel.vocabQuizAttemptStats(isOptionCorrect, question.question)

                        if (isOptionCorrect) {
                            displayedSentence = viewModel.highlightWordInSentence(
                                sentence = option,
                                wordToHighlight = question.question,
                                highlightColor = Color.Green
                            )
                            viewModel.handleTap(option)
                            viewModel.incQuizStat()
                        } else { // incorrect
                            viewModel.incQuizStat(false)
                        }

                        viewModel.markAnswerSelected(question.question, isOptionCorrect)
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
                            contentDescription = "Speak ${question.question.replace("_", option)}",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            // Tapping the answer sentence marks it, exactly like the radio button.
                            text = option,
                            color = orangeLight,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp)
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
                            selected = selectedOption == option && isOptionCorrect, // Select only if correct
                            onClick = { submitAnswer() },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = if (isCurrentAnswerCorrect == true) Color.Green else Color.Red, // Conditional color
                                unselectedColor = if (isCurrentAnswerCorrect == false && selectedOption == option) Color.Red else Color.Unspecified // Conditional color
                            ),
                            modifier = Modifier.semantics { contentDescription = option }
                        )
                    }
                } // Row
            }

            Spacer(Modifier.height(4.dp))
            Spacer(modifier = Modifier.weight(1f))//push the reset to teh bottom


            // Next/Previous flow across sub-tab pages: at the last question of a page, Next auto-selects
            // the next sub-tab (first question); at the first question of a page, Previous steps back into
            // the previous sub-tab (last question). Computed from observed state so the arrows enable/grey
            // correctly across page boundaries.
            val canGoPrevious = currentQuestionIndex > 0 || (currentIndex - 1) in availableIndices
            val canGoNext = currentQuestionIndex < questions.lastIndex || (currentIndex + 1) in availableIndices

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        viewModel.goToPreviousQuestion()
                        infoDisabled = !viewModel.doIHaveCurrentQuestionInfo()
                    },
                    enabled = canGoPrevious
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous",
                        tint = if (!canGoPrevious) Color.Gray else buttonColor, // Conditional color
                        modifier = Modifier.size(36.dp)
                    )
                }

                InfoButtonRow(infoDisabled = infoDisabled,
                    onClick = {

                        if (infoDisabled == false) {
                            showInfoBottomSheet = true
                            viewModel.onInfoClicked()
                        }
                    })

                IconButton(
                    onClick = {
                        viewModel.goToNextQuestion()
                        infoDisabled = !viewModel.doIHaveCurrentQuestionInfo()
                    },
                    enabled = canGoNext
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next",
                        tint = if (!canGoNext) Color.Gray else buttonColor, // Conditional color
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
        WordQuizInfoBottomSheetView(
            questions[currentQuestionIndex].summary,
            questions[currentQuestionIndex].explain,
            onCloseSheet = { showInfoBottomSheet = false }
        )
    }
} //:QuizScreen

@Composable
fun WordDropdownMenuBox(
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
fun WorddotColor(index: Int, scores: MutableMap<Int, Boolean>): Color {

    scores[index]?.let {
        return if (it) Color.Green else Color.Red // Green for correct, red for incorrect
    }

    return Color.LightGray
}

/** Short, plain-English definition of each mastery level, shown in the filtered-empty state. */
fun vocabMasteryExplanation(level: WordMasteryLevel): String {
    return when (level) {
        WordMasteryLevel.New -> "New: not attempted yet."
        WordMasteryLevel.Struggling -> "Struggling: answered wrong — comes back soon to try again."
        WordMasteryLevel.Learning -> "Learning: got it right, but only after a wrong try."
        WordMasteryLevel.Review -> "Review: right first time — building towards mastery over spaced sessions."
        WordMasteryLevel.Mastered -> "Mastered: correct first time in 3 separate sessions."
    }
}

@Composable
fun MasteryFilterChips(
    activeFilters: Set<WordMasteryLevel>,
    onToggle: (WordMasteryLevel) -> Unit,
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
        MasteryChip(
            label = "All",
            isSelected = activeFilters.isEmpty(),
            dotColor = MaterialTheme.colorScheme.primary,
            onClick = onSelectAll
        )

        // Per-level chips
        WordMasteryLevel.entries.forEach { level ->
            MasteryChip(
                label = level.name,
                isSelected = activeFilters.contains(level),
                dotColor = masteryChipColor(level),
                onClick = { onToggle(level) }
            )
        }
    }
}

@Composable
fun MasteryChip(
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
                horizontal = if (isSelected || label == "All" || label == "New") 10.dp else 8.dp,
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
        if (isSelected || label == "All" || label == "New") {
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

fun masteryChipColor(level: WordMasteryLevel): Color {
    return when (level) {
        WordMasteryLevel.New -> Color.Gray
        WordMasteryLevel.Struggling -> Color.Red
        WordMasteryLevel.Learning -> Color(0xFFFF9800) // Orange
        WordMasteryLevel.Review -> Color(0xFF2196F3) // Blue
        WordMasteryLevel.Mastered -> Color(0xFF4CAF50) // Green
    }
}

@Composable
fun WordInfoButtonRow(infoDisabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween, // Equivalent to Spacer() on both sides
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.weight(1f)) // Pushes the icon to the right
        Icon(
            imageVector = Icons.Outlined.Info, // Use a built-in Material icon
            contentDescription = "Info", // Accessibility description
            modifier = Modifier
                .size(32.dp)
                .clickable(enabled = !infoDisabled, onClick = onClick)
                .padding(start = 4.dp),
            tint = if (infoDisabled) Color.Gray else buttonColor
        )
        Spacer(modifier = Modifier.weight(1f)) // Pushes the icon to the left
    }
}