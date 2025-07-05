package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.VocabFile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Represents the state of our data loading
sealed interface VocabDataUiState {
    object Loading : VocabDataUiState
    data class Success(val vocabFile: VocabFile) : VocabDataUiState
    data class Error(val message: String) : VocabDataUiState
}

@HiltViewModel
class TabsViewModel @Inject constructor(
    private val vocabRepository: VocabRepository // Hilt injects our singleton repository
) : ViewModel() {

    // A StateFlow to hold the overall UI state for the vocab data
    private val _vocabUiState = MutableStateFlow<VocabDataUiState>(VocabDataUiState.Loading)
    val vocabUiState = _vocabUiState.asStateFlow()

    // --- NEW: StateFlows specifically for Tab 1's filtered data ---
    private val _tab1Categories = MutableStateFlow<List<Category>>(emptyList())
    val tab1Categories = _tab1Categories.asStateFlow()

    // We will now populate this dynamically from the JSON
    private val _tab1MenuItems = MutableStateFlow<List<String>>(emptyList())
    val tab1MenuItems = _tab1MenuItems.asStateFlow()
    // --- End of New StateFlows ---


    // --- NEW: A map to store the index of each category's header ---
    private val _categoryIndexMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val categoryIndexMap = _categoryIndexMap.asStateFlow()
    // --- End of New StateFlow ---

    // The other menu item flows can remain for now for the other tabs
    private val _tab2MenuItems = MutableStateFlow(listOf("Travel", "Leisure", "Weather"))
    val tab2MenuItems = _tab2MenuItems.asStateFlow()
    private val _tab3MenuItems = MutableStateFlow(listOf("Past Tense", "Patterns", "Verbs"))
    val tab3MenuItems = _tab3MenuItems.asStateFlow()
    private val _tab5MenuItems = MutableStateFlow(listOf("Settings", "Search", "Quiz"))
    val tab5MenuItems = _tab5MenuItems.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _vocabUiState.value = VocabDataUiState.Loading
            val result = vocabRepository.getVocabData()
            result.onSuccess { vocabFile ->
                _vocabUiState.value = VocabDataUiState.Success(vocabFile)

                val filteredCategoriesForTab1 = vocabFile.categories.filter { it.tabNumber == 1 }
                _tab1Categories.value = filteredCategoriesForTab1
                _tab1MenuItems.value = filteredCategoriesForTab1.map { it.title }

                // --- NEW: Calculate the index map ---
                val indexMap = mutableMapOf<String, Int>()
                var currentIndex = 0
                filteredCategoriesForTab1.forEach { category ->
                    indexMap[category.title] = currentIndex
                    // The next index is the current one + 1 (for the header) + the number of words in this category
                    currentIndex += 1 + category.words.size
                }
                _categoryIndexMap.value = indexMap
                // --- End of New Logic ---

            }.onFailure { error ->
                _vocabUiState.value = VocabDataUiState.Error(error.localizedMessage ?: "Unknown error")
            }
        }
    }
}