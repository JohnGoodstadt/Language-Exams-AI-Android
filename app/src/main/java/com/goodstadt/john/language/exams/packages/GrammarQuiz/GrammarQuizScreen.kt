package com.goodstadt.john.language.exams.packages.GrammarQuiz

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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.R
import com.goodstadt.john.language.exams.packages.UsageQuiz.UsageMasteryFilterChips
import com.goodstadt.john.language.exams.packages.UsageQuiz.dotColor
import com.goodstadt.john.language.exams.packages.UsageQuiz.usageMasteryExplanation
import com.goodstadt.john.language.exams.packages.reference.QuizInfoBottomSheetView
import com.goodstadt.john.language.exams.screens.RateLimitDailyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.RateLimitHourlyPaywallBottomSheet
import com.goodstadt.john.language.exams.screens.shared.AutoAdvanceToggleButton
import com.goodstadt.john.language.exams.screens.shared.InfoCircleButton
import com.goodstadt.john.language.exams.ui.theme.blueBright2
import com.goodstadt.john.language.exams.ui.theme.buttonColor
import com.goodstadt.john.language.exams.ui.theme.greyLight2
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.johngoodstadt.memorize.language.ui.screen.RateLimitOKReasonsBottomSheet

/**
 * Grammar practice quiz for one [category] at one [level]. Adapted from
 * [com.goodstadt.john.language.exams.packages.UsageQuiz.UsageQuizScreen] with the level picker and the
 * quiz dropdown removed (one file is loaded per launch). Keeps the speaker/TTS playback, the
 * correct/incorrect mastery filter, per-question badge, stats and paging dots. Designed to be hosted
 * in a bottom sheet from the Focus screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarQuizScreen(
    category: String,
    level: String,
    viewModel: GrammarQuizViewModel = hiltViewModel()
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

    val currentQuestionIndex by viewModel.currentQuestionIndex
    val userAnswers by viewModel.userAnswers
    val quizStatistics by viewModel.quizStatistics
    var selectedOption by remember { mutableStateOf<String?>(null) }
    var isCurrentAnswerCorrect by remember { mutableStateOf<Boolean?>(null) }

    var displayedSentence by remember { mutableStateOf(AnnotatedString("")) }

    val displayText = if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
        stringResource(R.string.quiz_hear_choose_best_answer)
    } else {
        stringResource(R.string.quiz_choose_best_answer)
    }

    var isLearningExpanded by rememberSaveable { mutableStateOf(false) }
    val fluency by viewModel.fluency
    val activeFilters by viewModel.activeFilters.collectAsState()

    // One file per launch: (re)load whenever the requested category/level changes.
    LaunchedEffect(category, level) {
        viewModel.loadGrammarQuiz(category, level)
    }

    LaunchedEffect(currentQuestionIndex, questions) {
        val question = questions.getOrNull(currentQuestionIndex)
        if (question != null) {
            val questionText =
                if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
//                    question.sentence.replace("_", "___")
                    // Regex logic:
                    // (?<!_) -> Check that there is NOT an underscore before
                    // _      -> The actual underscore to match
                    // (?!_)  -> Check that there is NOT an underscore after
                    question.sentence.replace(Regex("(?<!_)_(?!_)"), "___")
                } else {
                    ""
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
        // Header: one fixed category/level per launch, so a plain title replaces the picker + dropdown.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            Text(
                text = fluency.label,
                color = fluency.color,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .background(fluency.color.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                    .align(Alignment.CenterStart)
            )
            Text(
                text = "$category · $level",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = orangeLight,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // --- Quiz-level learning points (stays the same for all questions) ---
        val learningTitleData = uiState.format7or10ListRoot?.data?.firstOrNull()
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
                    Text(text = learningTitle, style = MaterialTheme.typography.titleSmall, color = Color.White)
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
                                Text(text = point, style = MaterialTheme.typography.bodyMedium, color = Color.White, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.fillMaxWidth(), thickness = 1.dp, color = greyLight2)

        // Mastery Filter Chips
        UsageMasteryFilterChips(
            activeFilters = activeFilters,
            onToggle = { viewModel.toggleFilter(it) },
            onSelectAll = { viewModel.selectAllFilters() }
        )

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
                Text(text = "No $filterNames questions to show.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Explain what the tapped filter(s) actually mean.
                Text(
                    text = activeFilters.joinToString("\n") { usageMasteryExplanation(it) },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = "Select All to see more.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
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
                                    withStyle(style = SpanStyle(color = Color.Green, fontWeight = FontWeight.Bold)) {
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
                        modifier = Modifier.background(masteryDisplay.second.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
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
                            } else option
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
                                    displayedSentence = viewModel.highlightWordInSentence(sentence = sentence, wordToHighlight = option, highlightColor = Color.Green)
                                    sentenceToSpeak = sentence
                                } else if (viewModel.currentFileFormat.value == viewModel.quizDefinitions) {
                                    displayedSentence = AnnotatedString(question.title)
                                    sentenceToSpeak = "${question.title}:${option}:${question.sentence}"
                                } else if (viewModel.currentFileFormat.value == viewModel.quizMultipleChoice) {
                                    displayedSentence = AnnotatedString(option)
                                    sentenceToSpeak = option.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ").trim()
                                } else {
                                    sentenceToSpeak = option
                                    displayedSentence = viewModel.highlightWordInSentence(sentence = option, wordToHighlight = option, highlightColor = Color.Green)
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
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                Spacer(modifier = Modifier.weight(1f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    InfoCircleButton(
                        infoDisabled = infoDisabled,
                        onClick = {
                            if (!infoDisabled) {
                                showInfoBottomSheet = true
                                viewModel.onInfoButtonTapped()
                            }
                        }
                    )
                    // Auto-advance toggle: grey = off, blue = on. Shared setting across all quiz types.
                    AutoAdvanceToggleButton(
                        enabled = autoAdvance,
                        onClick = { viewModel.toggleAutoAdvance() }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

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
        HorizontalDivider(modifier = Modifier.fillMaxWidth(), thickness = 1.dp, color = greyLight2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Correct: ${quizStatistics.correct}",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, color = Color.Green),
                modifier = Modifier.weight(1f).padding(start = 16.dp)
            )
            Text(
                text = "Tries: ${quizStatistics.tries}",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, color = Color.Red),
                modifier = Modifier.weight(1f).padding(end = 16.dp),
                textAlign = TextAlign.End
            )
        }
        // Paging control (dots)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            for (index in 0 until questions.size) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (index == currentQuestionIndex) blueBright2 else dotColor(index, userAnswers))
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }

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

    if (showInfoBottomSheet) {
        QuizInfoBottomSheetView(
            questions[currentQuestionIndex].summary,
            questions[currentQuestionIndex].explain,
            onCloseSheet = { showInfoBottomSheet = false }
        )
    }
}
