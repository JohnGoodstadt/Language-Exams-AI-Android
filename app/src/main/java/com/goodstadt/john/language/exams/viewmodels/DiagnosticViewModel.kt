package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.DiagnosticRepository
import com.goodstadt.john.language.exams.managers.AuditEngine
import com.goodstadt.john.language.exams.models.DiagnosticUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagnosticViewModel @Inject constructor(
    private val repository: DiagnosticRepository,
    // Add your asset loader here
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadDiagnosticQuestions()
    }

    private fun loadDiagnosticQuestions() {
        // Here you would load your JSON file using your existing asset reader
        // For now, assume it populates _uiState.value.questions
    }

    fun onAnswerSelected(isCorrect: Boolean) {
        val currentState = _uiState.value
        val newScore = if (isCorrect) currentState.score + 1 else currentState.score
        val nextIndex = currentState.currentQuestionIndex + 1

        if (nextIndex < currentState.questions.size) {
            _uiState.update { it.copy(
                currentQuestionIndex = nextIndex,
                score = newScore
            )}
        } else {
            // Test finished
            completeTest(newScore)
        }
    }

    private fun completeTest(finalScore: Int) {
        viewModelScope.launch {
            repository.saveResult(finalScore)
            _uiState.update { it.copy(showResult = true, score = finalScore) }
        }
    }

    fun skipTest() {
        viewModelScope.launch {
            repository.skipDiagnostic()
            // Logic to navigate to Dashboard
        }
    }
    // Example: {1: 8, 2: 7}
    fun updateUI(currentScores: Map<Int, Int>) {
        val report = AuditEngine.calculate(currentScores)

        // This 'report' object now tells you exactly what to show the user
        // report.confidence -> "Audit Confidence: 65%"
        // report.readiness -> "Exam Readiness: 74%"
    }
    fun getAuditorVerdict(readiness: Int): Pair<String, String> {
        return when (readiness) {
            in 0..35 -> "Foundational Work Needed" to
                    "You are currently at an A2 level. We recommend focusing on basic sentence structure and the A2 word bank."

            in 36..55 -> "Borderline B1 Candidate" to
                    "You are at risk of failing the exam. You need to master at least 200 more words to reach a safe passing threshold."

            in 56..80 -> "B1/B2 Ready" to
                    "Good work! You are on track for a pass. Focus on your 'Struggling' words to ensure a high band score."

            else -> "Elite Performance" to
                    "Your logic is near-perfect. Use the app for daily revision to maintain this level until exam day."
        }
    }
}