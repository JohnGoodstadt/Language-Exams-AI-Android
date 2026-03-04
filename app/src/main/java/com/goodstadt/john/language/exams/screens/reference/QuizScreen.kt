package com.goodstadt.john.language.exams.screens.reference

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.goodstadt.john.language.exams.screens.RateLimitDailyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.reference.shared.HorizontalLevelPicker
import com.goodstadt.john.language.exams.ui.theme.blueBright2
import com.goodstadt.john.language.exams.ui.theme.buttonColor
import com.goodstadt.john.language.exams.ui.theme.greyLight2
import com.goodstadt.john.language.exams.ui.theme.nonSelectedBackground
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.viewmodels.QuizLevels
import com.goodstadt.john.language.exams.viewmodels.QuizViewModel
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet


@Composable
fun QuizScreen(
    viewModel: QuizViewModel = hiltViewModel()
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
    val availableQuizzesObsolete by viewModel.availableQuizzesObsolete
    val availableQuizzes by viewModel.availableQuizzes.collectAsState()

    val selectedQuiz by viewModel.selectedQuiz
    val displayText = if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
        "Hear, and then choose the correct answer"
    } else {
        "Choose the correct answer"
    }

    var isLearningExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(currentQuestionIndex, questions) {
        if (questions.isNotEmpty()) {
            val question = questions[currentQuestionIndex]

            val questionText =
                if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
                    question.sentence.replace("_", "___")
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
        HorizontalLevelPicker(
            options = QuizLevels.entries.map { it.description },
            selectedOption = selectedLevel.description,
            onOptionSelected = { newLevel ->
                val level = QuizLevels.entries.first { it.description == newLevel }
//                    viewModel.selectedLevel.value = level
                viewModel.onLevelSelected(level)
                viewModel.loadQuestions()

                if (viewModel.doIHaveCurrentQuestionInfo()) {
                    infoDisabled = false
                } else {
                    infoDisabled = true
                }
            }//,
            //fontSize = 16.sp
        )

        // Quiz Number Picker
        DropdownMenuBox(
            options = availableQuizzes.map { it.title },
            selectedOption = selectedQuiz?.title ?: "Select a Quiz",
            onOptionSelected = { newQuizTitle ->
                // Find the QuizDetail object that matches the selected title
                val quizDetail = availableQuizzes.first { it.title == newQuizTitle }
                // Call the new ViewModel function
                viewModel.onQuizSelected(quizDetail)
            }
        )

        // --- Quiz-level learning points (stays the same for all 10 questions) ---

        val fred = selectedQuiz
        print(fred)

        val learningTitleData = uiState.testMyselfListRoot?.data?.first()
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
                        text = if (isLearningExpanded) "Less" else "More…",
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

        if (currentQuestionIndex == 0) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    // ✅ THE FIX: Add vertical padding.
                    // This will add 16.dp of space on the top AND 16.dp on the bottom.
                    .padding(vertical = 16.dp)
            )
        } else {
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
                if (viewModel.currentFileFormat.value == viewModel.quizDefinitions) {
                    AnnotatedString(question.title)
                } else {
                    buildAnnotatedString {
                        if (isCurrentAnswerCorrect == true && selectedOption != null) {
                            // --- SUCCESS STATE ---
                            // The user has answered correctly.
                            val parts = question.sentence.split("_")
                            if (parts.size == 2) {
                                append(parts[0]) // Append part before the blank
                                withStyle(
                                    style = SpanStyle(
                                        color = Color.Green,
                                        fontWeight = FontWeight.Bold
                                    )
                                ) {
                                    append(selectedOption!!) // Append the correct word in green
                                }
                                append(parts[1]) // Append part after the blank
                            } else {
                                // Fallback for complex sentences
                                append(displayedSentence)
                            }
                        } else {
                            // --- QUESTION STATE ---
                            // Not answered yet, or answered incorrectly.
                            append(displayedSentence)
                        }
                    }
                }

            Text(
                text = annotatedQuestionText,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                color = orangeLight,
            )


            question.words.forEach { option ->
                val isOptionCorrect =
                    option == question.correctOption // Determine if option is correct

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            modifier = Modifier.clickable {
                                //Timber.v(" ${ question.sentence.replace("_", option)}")
                                val isCorrect = option == question.correctOption
                                viewModel.updateAnswer(isCorrect)

                                val fullSentence =
                                    if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
                                        question.sentence.replace("_", option)
                                    } else {
                                        if (viewModel.currentFileFormat.value == viewModel.quizDefinitions) {
                                            option.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ")
                                                .trim()//remove ()
                                        } else {
                                            option
                                        }
                                    }
//                                viewModel.playTrack(fullSentence)
                                viewModel.handleTap(fullSentence)

                            },
//                            painter = painterResource(R.drawable.ic_speaker),
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Speak ${question.sentence.replace("_", option)}",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = option,
                            color = orangeLight,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .clickable {
                                    // val fullSentence = question.sentence.replace("_", option)
                                    // Timber.v(fullSentence)
                                    val fullSentence =
                                        if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
                                            question.sentence.replace("_", option)
                                        } else {
                                            if (viewModel.currentFileFormat.value == viewModel.quizMultipleChoice) {
                                                option.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ")
                                                    .trim()//remove ()
                                            } else {
                                                option
                                            }
                                        }

                                    val isCorrect = option == question.correctOption
                                    viewModel.updateAnswer(isCorrect)

//                                    viewModel.playTrack(fullSentence)
                                    viewModel.handleTap(fullSentence)

                                }
                        )
                    }

                    // Radio button on the far right
                    RadioButton(
                        selected = selectedOption == option && isOptionCorrect, // Select only if correct
                        onClick = {
                            selectedOption = option
                            isCurrentAnswerCorrect = isOptionCorrect
                            viewModel.updateAnswer(isOptionCorrect)

                            if (isOptionCorrect) {

                                // val wordToHilight: String
                                //val sentenceToStyle: String

                                var sentenceToSpeak = ""
                                if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
                                    val sentence = question.sentence.replace(Regex("_+"), option)
//                                        val sentenceToStyle = option
                                    displayedSentence = viewModel.highlightWordInSentence(
                                        sentence = sentence,
                                        wordToHighlight = option,
                                        highlightColor = Color.Green
                                    )
                                    sentenceToSpeak = sentence
                                } else if (viewModel.currentFileFormat.value == viewModel.quizDefinitions) {
//                                        val wordToHilight = question.title
//                                        val sentence = "${wordToHilight}:${question.sentence}"
                                    displayedSentence = AnnotatedString(question.title)
                                    sentenceToSpeak =
                                        "${question.title}:${option}:${question.sentence}"
                                } else if (viewModel.currentFileFormat.value == viewModel.quizMultipleChoice) {
                                    displayedSentence = AnnotatedString(option)
                                    val cleaned = option.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ")
                                        .trim()//remove ()
                                    sentenceToSpeak = cleaned
                                } else { // Multiple Choice
                                    // val wordToHilight = question.sentence
                                    //val sentenceToStyle = option
                                    sentenceToSpeak = option
                                    displayedSentence = viewModel.highlightWordInSentence(
                                        sentence = option,
                                        wordToHighlight = question.sentence,
                                        highlightColor = Color.Green
                                    )
                                }

//                                viewModel.playTrack(sentenceToSpeak)
                                viewModel.handleTap(sentenceToSpeak)
                                viewModel.incQuizStat()

                            }
                            else{ //incorrect
                                viewModel.incQuizStat(false)
                            }


                        },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = if (isCurrentAnswerCorrect == true) Color.Green else Color.Red, // Conditional color
                            unselectedColor = if (isCurrentAnswerCorrect == false && selectedOption == option) Color.Red else Color.Unspecified // Conditional color
                        ),
                        modifier = Modifier.semantics { contentDescription = option }
                    )
                } // Row
            }

            Spacer(Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                InfoButtonRow(infoDisabled = infoDisabled,
                    onClick = {

                        if (infoDisabled == false) {
                            showInfoBottomSheet = true
                        }
                    })
            }// row

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        if (currentQuestionIndex > 0) {
                            viewModel.currentQuestionIndex.value -= 1
                            if (viewModel.doIHaveCurrentQuestionInfo()) {
                                infoDisabled = false
                            } else {
                                infoDisabled = true
                            }
                        }
                    },
                    enabled = currentQuestionIndex > 0
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous",
                        tint = if (currentQuestionIndex == 0) Color.Gray else buttonColor, // Conditional color
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (currentQuestionIndex < questions.lastIndex) {
                            viewModel.currentQuestionIndex.value += 1
                            if (viewModel.doIHaveCurrentQuestionInfo()) {
                                infoDisabled = false
                            } else {
                                infoDisabled = true
                            }
                        }
                    },
                    enabled = currentQuestionIndex < questions.lastIndex

                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next",
                        tint = if (currentQuestionIndex == questions.lastIndex) Color.Gray else buttonColor, // Conditional color
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
} //:QuizScreen

@Composable
fun DropdownMenuBox(
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
fun dotColor(index: Int, scores: MutableMap<Int, Boolean>): Color {

    scores[index]?.let {
        return if (it) Color.Green else Color.Red // Green for correct, red for incorrect
    }

    return Color.LightGray
}

@Composable
fun InfoButtonRow(infoDisabled: Boolean, onClick: () -> Unit) {
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
//                tint = Color.White
        )
        Spacer(modifier = Modifier.weight(1f)) // Pushes the icon to the left
    }
}