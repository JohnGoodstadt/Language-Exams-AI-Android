package com.goodstadt.john.language.exams.screens.me

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Info

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

import kotlin.text.replace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import com.goodstadt.john.language.exams.R
import com.goodstadt.john.language.exams.data.api.GoogleCloudTTS
import com.goodstadt.john.language.exams.ui.theme.accentColor
import com.goodstadt.john.language.exams.ui.theme.blueBright2
import com.goodstadt.john.language.exams.ui.theme.buttonColor
import com.goodstadt.john.language.exams.ui.theme.greyLight2
import com.goodstadt.john.language.exams.ui.theme.nonSelectedBackground
import com.goodstadt.john.language.exams.ui.theme.orangeLight
import com.goodstadt.john.language.exams.ui.theme.selectedBackground
import com.goodstadt.john.language.exams.viewmodels.QuizLevels
import com.goodstadt.john.language.exams.viewmodels.QuizViewModel


@Composable
fun QuizScreen(
    viewModel: QuizViewModel = hiltViewModel()
) {

    val options = listOf("Quiz 1", "Quiz 2 - Word Pairs","Quiz 3 - Word Order","Quiz 4 - Spelling 2","Quiz 5 - Spelling 3")
    var infoDisabled by remember { mutableStateOf(false) }
    var showInfoBottomSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
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

    val isUpgradeAppSheetVisible by viewModel.showUpgradeAppSheet.collectAsState()
    val isForceUpgradeAppSheetVisible by viewModel.showForceUpgradeAppSheet.collectAsState()

    var currentQuizFormat = viewModel.quizFillInTheBlanks

    LaunchedEffect(true) {
       println("QuizScreen LaunchedEffect")
    }

    /* TODO: review
    LaunchedEffect(true) {
        viewModel.screenStatsInc()
    }
    LaunchedEffect(key1 = true) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

     */
    //try app upgrade here
    /*
    LaunchedEffect(key1 = true) {
        if (viewModel.checkIfAppUpgradeCheckStillToDoToday()){
            println("App Upgrade check not yet done for today")

            if (viewModel.adviseUpgradeApp()){
                if (viewModel.forceUpgradeApp()){
                    viewModel.showForceAppUpgradeSheet()
                }else{
                    viewModel.showAppUpgradeSheet()
                }

            }


        }
    }
 */
    Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Level Picker
        HorizontalLevelPicker(
                options = QuizLevels.entries.map { it.description },
                selectedOption = selectedLevel.description,
                onOptionSelected = { newLevel ->
                    val level = QuizLevels.entries.first { it.description == newLevel }
                    viewModel.selectedLevel.value = level
                    viewModel.loadQuestions()

                    if (viewModel.doIHaveCurrentQuestionInfo()){
                        infoDisabled = false
                    }else{
                        infoDisabled = true
                    }
                }//,
                //fontSize = 16.sp
        )

        // Quiz Number Picker
        DropdownMenuBox(
                options = options,
                selectedOption = options.getOrNull(selectedQuizNumber - 1) ?: options[0],
                onOptionSelected = { newQuiz ->
                    viewModel.selectedQuizNumber.value = options.indexOf(newQuiz) + 1 //1 based index
                    viewModel.loadQuestions()
                }
        )

//        Divider()
        HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = greyLight2
        )
        
        // Question Display
        if (questions.isNotEmpty()) {
            val question = questions[currentQuestionIndex]
            Text(
                    text = question.sentence.replace("_", "___"),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    color = orangeLight,
            )

            var selectedOption by remember { mutableStateOf<String?>(null) } // State for selected option

            question.words.forEach { option ->
                val isOptionCorrect = option == question.correctOption // Determine if option is correct

                Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                                modifier = Modifier.clickable {
                                    //println(" ${ question.sentence.replace("_", option)}")
                                    val isCorrect = option == question.correctOption
                                    viewModel.updateAnswer(isCorrect)

                                    val fullSentence = if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
                                        question.sentence.replace("_", option)
                                    } else {
                                        option
                                    }

                                    viewModel.playTrack(fullSentence)
                                },
                                painter = painterResource(R.drawable.ic_speaker),
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
                                       // println(fullSentence)
                                        val fullSentence = if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
                                            question.sentence.replace("_", option)
                                        } else {
                                            option
                                        }

                                        val isCorrect = option == question.correctOption
                                        viewModel.updateAnswer(isCorrect)

                                       viewModel.playTrack(fullSentence)

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
                                if (isOptionCorrect){
//                                    googleCloudTTS.start(text = question.sentence.replace("_", option))
//                                    val fullSentence = question.sentence.replace("_", option)
//                                    println(fullSentence)
                                    val fullSentence = if (viewModel.currentFileFormat.value == viewModel.quizFillInTheBlanks) {
                                        question.sentence.replace("_", option)
                                    } else {
                                        option
                                    }
                                    viewModel.playTrack(fullSentence)
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

                            if (infoDisabled == false){
                                showInfoBottomSheet = true
                            }
//                            Toast.makeText(context, "Info button clicked", Toast.LENGTH_SHORT)
//                                .show()
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
                                if (viewModel.doIHaveCurrentQuestionInfo()){
                                    infoDisabled = false
                                }else{
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
                                if (viewModel.doIHaveCurrentQuestionInfo()){
                                    infoDisabled = false
                                }else{
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
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, color = Color.Green),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp) // Add padding to the start
            )
            Text(
                    text = "Tries: ${quizStatistics.tries}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, color = Color.Red),
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
                                    if (index == currentQuestionIndex) blueBright2 else dotColor(index,userAnswers)
                            ) // Set color based on current page
                )
                Spacer(modifier = Modifier.width(4.dp)) // Add spacing between dots
            }
        }
    }
    /*
    if (isRateLimitingSheetVisible){
        RateLimitOKReasonsBottomSheet(onCloseSheet = { viewModel.hideRateOKLimitSheet() })
    }
    if (isDailyRateLimitingSheetVisible){
        RateLimitDailyReasonsBottomSheet (onCloseSheet = { viewModel.hideDailyRateLimitSheet() })
    }
    if (isHourlyRateLimitingSheetVisible){
        RateLimitHourlyReasonsBottomSheet(onCloseSheet = { viewModel.hideHourlyRateLimitSheet() })
    }
    if (isUpgradeAppSheetVisible){
        UpdateAppPromptSheet(onCloseSheet = { viewModel.hideAppUpgradeSheet() })
    }
    if (isForceUpgradeAppSheetVisible){
        ForceUpdateAppPromptSheet(onUpdateClick = {
            viewModel.hideForceAppUpgradeSheet()
            openAppStore(context)
        })
    }

     */
    if (showInfoBottomSheet){
        QuizInfoBottomSheetView(
                questions[currentQuestionIndex].summary,questions[currentQuestionIndex].explain, onCloseSheet = { showInfoBottomSheet = false }
        )

    //        GiveFeedbackSheet (
//                onSend = { text ->
//                    if (text.isNotBlank()){
//                        Toast.makeText(context, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
//                        fsCreateUserFeedbackDoc(text)
//                    }else{
//                        Toast.makeText(context, "No text entered", Toast.LENGTH_SHORT).show()
//                    }
//                },
//                onCloseSheet = { showInfoBottomSheet = false }
//        )
    }
    fun openAppStore(context: Context) {
        //TODO: update url when known
        val appPackageName = context.packageName // getPackageName() from Context or Activity object
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=$appPackageName")))
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
        }
//    fsUpdateStatsPropertyCount(fb.appStoreViewCount)
    }
}

@Composable
fun DropdownMenuBox(options: List<String>, selectedOption: String, onOptionSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var buttonCoordinates by remember { mutableStateOf(Offset(0f, 0f)) } // Store button coordinates as Offset
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
fun HorizontalLevelPicker(
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit
) {
    Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { option ->
            val isSelected = selectedOption == option
            Button(
                    onClick = { onOptionSelected(option) },
                    colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) selectedBackground else nonSelectedBackground, // Dark grey for selected, light grey for unselected
                            contentColor = Color.White // White text for all buttons
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(horizontal = 1.dp)
            ) {
                Text(
                        text = option,
                        color =  if (isSelected) accentColor else Color.Transparent,
                        maxLines = 1,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                )
            }
        }
    }
}
@Composable
fun dotColor(index: Int,scores:MutableMap<Int, Boolean>): Color {

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