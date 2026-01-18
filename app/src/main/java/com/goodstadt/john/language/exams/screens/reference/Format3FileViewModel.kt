package com.goodstadt.john.language.exams.screens.reference

import com.goodstadt.john.language.exams.models.Format3Category
import com.goodstadt.john.language.exams.models.Format3File
import com.goodstadt.john.language.exams.models.Format3Sentence
import com.goodstadt.john.language.exams.utils.Format3AssetLoader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Format3UiState(
    val isLoading: Boolean = false,
    val file: Format3File? = null,
    val errorMessage: String? = null
)

class Format3FileViewModel(
    app: Application,
    private val loader: Format3AssetLoader = Format3AssetLoader()
) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(Format3UiState())
    val uiState: StateFlow<Format3UiState> = _uiState.asStateFlow()

    fun load(filename: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    loader.load(getApplication(), filename)
                }
                _uiState.value = Format3UiState(isLoading = false, file = file, errorMessage = null)
            } catch (t: Throwable) {
                _uiState.value = Format3UiState(isLoading = false, file = null, errorMessage = t.message ?: "Load failed")
            }
        }
    }

    /** Flatten words -> sentences for a category (same as Swift) */
    fun sentencesFor(category: Format3Category): List<Format3Sentence> =
        category.words
            .sortedBy { it.sortOrder }
            .flatMap { it.sentences }
}
