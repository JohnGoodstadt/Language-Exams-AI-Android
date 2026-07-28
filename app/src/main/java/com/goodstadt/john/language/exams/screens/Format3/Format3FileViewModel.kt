package com.goodstadt.john.language.exams.screens.Format3

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.models.Format3Category
import com.goodstadt.john.language.exams.models.Format3File
import com.goodstadt.john.language.exams.models.Format3Sentence
import com.goodstadt.john.language.exams.utils.Format3AssetLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class Format3UiState(
    val isLoading: Boolean = false,
    val file: Format3File? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class Format3FileViewModel @Inject constructor(
    private val loader: Format3AssetLoader// = Format3AssetLoader()
) : ViewModel() {

    private val _uiState = MutableStateFlow(Format3UiState())
    val uiState: StateFlow<Format3UiState> = _uiState.asStateFlow()

    fun setFile(file: Format3File) {
        _uiState.value = Format3UiState(
            isLoading = false,
            file = file,
            errorMessage = null
        )
    }

    /**
     * Loads a Format3 JSON file from assets.
     *
     * @param context Android context (use LocalContext.current)
     * @param assetPath e.g. "format3/spanish/EnglishWordStressForSpanishB1.json"
     *                  or "EnglishWordStressForSpanishB1.json" if stored at assets root.
     */
    fun loadFromAssets(context: Context, assetPath: String) {
        if (_uiState.value.isLoading) return

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    loader.load(context, assetPath)
                }

                _uiState.value = Format3UiState(
                    isLoading = false,
                    file = file,
                    errorMessage = null
                )

            } catch (t: Throwable) {
                _uiState.value = Format3UiState(
                    isLoading = false,
                    file = null,
                    errorMessage = t.message ?: "Failed to load content"
                )
            }
        }
    }

    fun translateLanguageToTopTitle(language:String): String {
        val title = when (language.lowercase()) {
            "spanish" -> "Special note for Spanish speakers"
            else -> language
        }

        return title
    }

    /** Flatten words → sentences for display */
    fun sentencesFor(category: Format3Category): List<Format3Sentence> =
        category.words.sortedBy { it.sortOrder }.flatMap { it.sentences }
}
