package com.goodstadt.john.language.exams.viewmodels


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.QuizHistoryManager
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.managers.XPManager
import com.goodstadt.john.language.exams.managers.XpActionType
import com.goodstadt.john.language.exams.models.Format7or10Section
import com.goodstadt.john.language.exams.models.Format7or10Word
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuizSheetUiState(
    val questions: List<Format7or10Section> = emptyList(),
    val title: String = "",
    val currentIndex: Int = 0,
    val scores: Map<Int, Boolean> = emptyMap(), // Index -> IsCorrect
    val totalTries: Int = 0,
    val userAnswers: Map<Int, Format7or10Word> = emptyMap(),
    val isComplete: Boolean = false,
    val showInfoSheet: Boolean = false
)

@HiltViewModel
class QuizSheetViewModel @Inject constructor(
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val xpManager: XPManager,
    private val quizHistoryManager: QuizHistoryManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizSheetUiState())
    val uiState = _uiState.asStateFlow()

    // Initialize with data passed from the View
    fun setup(questions: List<Format7or10Section>, title: String) {
        if (_uiState.value.questions.isEmpty()) {
            _uiState.update { it.copy(questions = questions, title = title) }
        }
    }

    fun onAnswerSelected(option: Format7or10Word) {
        val state = _uiState.value
        val index = state.currentIndex
        val question = state.questions[index]

        // 1. Check Logic (Format 1/Swap Quiz Logic)
        // In swap quizzes, 'ok' is boolean true for correct answer
        val isCorrect = option.ok

        _uiState.update { current ->
            val newAnswers = current.userAnswers.toMutableMap()
            newAnswers[index] = option

            val newScores = current.scores.toMutableMap()
            if (isCorrect) {
                newScores[index] = true
            }

            val newTries = current.totalTries + 1
            val isComplete = newAnswers.size == current.questions.size

            current.copy(
                userAnswers = newAnswers,
                scores = newScores,
                totalTries = newTries,
                isComplete = isComplete
            )
        }

        // 2. Play Audio
        if (isCorrect) {
            playAudio(option.word) // Or option.tts if available
        }

        // 3. Handle Completion
        if (_uiState.value.isComplete) {
            onQuizFinished()
        }
    }

    //private
    fun playAudio(sentence: String) {
        viewModelScope.launch {
            // Use the centralized repo (fire and forget for quiz feedback)
//            audioPlaybackRepository.playTrackAndGetResult(
//                sentence = sentence,
//                level = "Quiz", // Or "Reference"
//                isPremiumUser = false, //TODO: fill this in? i.e. Not Hard Coded
//                sheetName = _uiState.value.title
//            )

            audioPlaybackRepository.playTrackAndGetStatus(
                sentence = sentence,
                level = "Quiz", // Or "Reference"
                isPremiumUser = false, //TODO: fill this in? i.e. Not Hard Coded
                sheetName = _uiState.value.title
            )
        }
    }

    private fun onQuizFinished() {
        val state = _uiState.value
        val correctCount = state.scores.size
        val total = state.questions.size

        viewModelScope.launch {
            // 1. Award XP
            if (correctCount == total) {
                xpManager.registerAction(XpActionType.PerfectQuiz)
            } else {
                xpManager.registerAction(XpActionType.CompleteQuiz)
            }

            // 2. Save History (Optional for dynamic quizzes, but good for tracking)
            // Note: Since these are dynamic, ID generation might need care.
            // For now, we just track XP.
        }
    }

    // Navigation
    fun nextQuestion() {
        _uiState.update { it.copy(currentIndex = (it.currentIndex + 1).coerceAtMost(it.questions.size - 1)) }
    }

    fun prevQuestion() {
        _uiState.update { it.copy(currentIndex = (it.currentIndex - 1).coerceAtLeast(0)) }
    }

    fun toggleInfo() {
        _uiState.update { it.copy(showInfoSheet = !it.showInfoSheet) }
    }


}