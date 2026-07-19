package com.goodstadt.john.language.exams.screens.reference

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.goodstadt.john.language.exams.models.UsageMastery
import com.goodstadt.john.language.exams.viewmodels.UsageQuizViewModel
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.res.Configuration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.platform.LocalConfiguration
import com.goodstadt.john.language.exams.viewmodels.ReadinessAuditLevels
import com.goodstadt.john.language.exams.viewmodels.ReadinessAuditViewModel
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadinessAuditScreen(
    viewModel: ReadinessAuditViewModel = hiltViewModel()
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
        "Hear, and then choose the best answer"
    } else {
        "Choose the best answer"
    }

    var isLearningExpanded by rememberSaveable { mutableStateOf(false) }
    var showDashboardSheet by remember { mutableStateOf(false) }
    val dashboardSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fluency by viewModel.fluency
   // val activeFilters by viewModel.activeFilters.collectAsState()
    val stats by viewModel.auditStats.collectAsState()

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
        // Use full labels on wider screens (≥ 380dp), short on small devices
        val useFullLabels = LocalConfiguration.current.screenWidthDp >= 380
        val labelFor: (ReadinessAuditLevels) -> String = { if (useFullLabels) it.description else it.shortLabel }

        HorizontalLevelPicker(
            options = ReadinessAuditLevels.entries.map { labelFor(it) },
            selectedOption = labelFor(selectedLevel),
            onOptionSelected = { newLabel ->
                val level = ReadinessAuditLevels.entries.first { labelFor(it) == newLabel }
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



        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            // 1. Main Introductory Text
            Text(
                // ✅ FIX 2: Use 'stats' (the collected state), not 'viewModel.auditStats.value'
                text = if (stats.confidence == 0) {
                    "To provide an accurate roadmap for your exam success, we must first verify your current skills. Part 1: Baseline Verification is the minimum requirement to generate your initial profile."
                } else {
                    "Baseline established. Complete the remaining modules (Logic, Lexis, Core) to increase Audit Confidence and identify specific exam risks."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.LightGray,
                lineHeight = 20.sp,
                modifier = Modifier.padding(bottom = 24.dp)
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
                    Text(
                        text = "AUDIT STATUS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AuditStatItem(
                            label = "Audit Confidence",
                            value = "${stats.confidence}%",
                            // ✅ FIX 3: Call the now-public function
                            subValue = viewModel.getConfidenceLabel(stats.confidence)
                        )

                        Box(modifier = Modifier.width(1.dp).height(40.dp).background(Color.DarkGray))

                        AuditStatItem(
                            label = "Exam Readiness",
                            value = "${stats.readiness}%",
                            subValue = "B1 Level"
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "AUDITOR’S VERDICT",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = viewModel.getAuditorVerdictText(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFFF9500),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

            // 2. The Icon (Right Aligned)
//            IconButton(
//                onClick = {  showDashboardSheet = true },
//                modifier = Modifier.align(Alignment.CenterEnd) // 👈 Locks to right
//            ) {
//                Icon(
//                    imageVector = Icons.Default.WorkspacePremium, // Or Insight/Chart icon
//                    contentDescription = "Stats",
//                    tint = Color(0xFFFF9800)
//                )
//            }
//        }

        // --- Quiz-level learning points (stays the same for all 10 questions) ---

//        val fred = selectedQuiz
//        print(fred)

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
//        UsageMasteryFilterChips(
//            activeFilters = activeFilters,
//            onToggle = { viewModel.toggleFilter(it) },
//            onSelectAll = { viewModel.selectAllFilters() }
//        )

        // Question counter with filter info
        if (questions.isNotEmpty()) {
            Text(
                text = "Showing ${currentQuestionIndex + 1} of ${viewModel.totalQuestionCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Filtered-empty state

        if (currentQuestionIndex == 0 && questions.isNotEmpty()) {
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                modifier = Modifier.clickable {
                                    val isCorrect = option == question.correctOption
                                    viewModel.updateAnswer(isCorrect)

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
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
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

                                        val isCorrect = option == question.correctOption
                                        viewModel.updateAnswer(isCorrect)
                                        viewModel.handleTap(fullSentence)
                                    }
                            )
                        }

                        // Radio button on the far right
                        RadioButton(
                            selected = selectedOption == option && isOptionCorrect,
                            onClick = {
                                selectedOption = option
                                isCurrentAnswerCorrect = isOptionCorrect
                                viewModel.updateAnswer(isOptionCorrect)

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
                                } else {
                                    viewModel.incQuizStat(false)
                                }
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = if (isCurrentAnswerCorrect == true) Color.Green else Color.Red,
                                unselectedColor = if (isCurrentAnswerCorrect == false && selectedOption == option) Color.Red else Color.Unspecified
                            ),
                            modifier = Modifier.semantics { contentDescription = option }
                        )
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

                // 🔹 Info Button (centered)
//                InfoButtonRow(
//                    infoDisabled = infoDisabled,
//                    onClick = {
//                        if (!infoDisabled) {
//                            showInfoBottomSheet = true
//                            viewModel.onInfoButtonTapped() //mark user getting help
//                        }
//                    }
//                )

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
fun DropdownMenuBoxObsolete2(
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
private fun AuditStatItem(label: String, value: String, subValue: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(text = subValue, style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
    }
}
