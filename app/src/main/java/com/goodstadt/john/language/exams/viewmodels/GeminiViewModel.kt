package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// A data class to hold the UI state
data class GeminiUiState(
    val resultText: String = "Tap the button to ask Gemini a question.",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class GeminiViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(GeminiUiState())
    val uiState = _uiState.asStateFlow()

    private val generativeModel: GenerativeModel

    init {
        // Initialize the GenerativeModel with the API key from BuildConfig
        generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash", // Use a fast and efficient model
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    fun generateContent() {
        // Update the state to show the loading indicator
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                // The prompt for the AI
                val prompt = "Why is the sky blue? Explain it simply."

                // The main API call. This is a suspend function.
                val response = generativeModel.generateContent(prompt)

                // Update the state with the successful response
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        resultText = response.text ?: "No text was generated."
                    )
                }
            } catch (e: Exception) {
                // Update the state with the error message
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "An unknown error occurred."
                    )
                }
                e.printStackTrace()
            }
        }
    }
}