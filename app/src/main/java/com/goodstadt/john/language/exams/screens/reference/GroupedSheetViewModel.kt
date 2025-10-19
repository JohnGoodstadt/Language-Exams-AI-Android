package com.goodstadt.john.language.exams.screens.reference

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.examsheets.ExamSheetRepository
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.SubTabDefinition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ContentState {
    object Idle : ContentState // The initial state before anything is loaded
    object Loading : ContentState
    data class Success(val categories: List<Category>) : ContentState
    data class Error(val message: String) : ContentState
}

// 2. The main UI State data class for the entire screen
data class GroupedSheetUiState(
    val title: String = "", // The main title for the screen (e.g., "Adjectives")
    val subTabs: List<SubTabDefinition> = emptyList(),
    val selectedSubTab: SubTabDefinition? = null,
    val contentState: ContentState = ContentState.Idle
    // You could also add PlaybackState and other sheet visibility booleans here
    // if this screen will also play audio, just like in your other ViewModels.
)

@HiltViewModel
class GroupedSheetViewModel @Inject constructor(
    private val appConfigRepository: AppConfigRepository,
    private val examSheetRepository: ExamSheetRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupedSheetUiState())
    val uiState = _uiState.asStateFlow()

    // A simple in-memory cache to avoid re-fetching data when a user taps back and forth
    private val contentCache = mutableMapOf<String, List<Category>>()

    init {
        // Get the parent tab's ID from the navigation arguments
        val tabId: String? = savedStateHandle.get("tabId")
        if (tabId != null) {
            initializeState(tabId)
        } else {
            _uiState.update { it.copy(contentState = ContentState.Error("Parent Tab ID was not provided.")) }
        }
    }

    private fun initializeState(tabId: String) {
        viewModelScope.launch {
            // Get all tabs from the config to find our specific tab
            val allTabs = appConfigRepository.getReferenceTabs()
            val myTab = allTabs.find { it.id == tabId }

            if (myTab != null && !myTab.subTabs.isNullOrEmpty()) {
                val initialSubTab = myTab.subTabs.first()
                _uiState.update {
                    it.copy(
                        title = myTab.title,
                        subTabs = myTab.subTabs,
                        selectedSubTab = initialSubTab
                    )
                }
                // Load the content for the initially selected sub-tab
                loadContentForSubTab(initialSubTab)
            } else {
                _uiState.update { it.copy(contentState = ContentState.Error("Configuration for tab '$tabId' not found or is empty.")) }
            }
        }
    }

    /**
     * Called by the UI when the user selects a different sub-tab from the picker.
     */
    fun onSubTabSelected(subTab: SubTabDefinition) {
        // Do nothing if the user taps the already selected tab
        if (_uiState.value.selectedSubTab == subTab) return

        _uiState.update { it.copy(selectedSubTab = subTab) }
        loadContentForSubTab(subTab)
    }

    /**
     * Loads the vocab data for a given sub-tab, utilizing an in-memory cache.
     */
    private fun loadContentForSubTab(subTab: SubTabDefinition) {
        viewModelScope.launch {
            // Check the cache first
            val cachedCategories = contentCache[subTab.firestoreDocumentId]
            if (cachedCategories != null) {
                _uiState.update { it.copy(contentState = ContentState.Success(cachedCategories)) }
                return@launch
            }

            // If not in cache, show loading and fetch from the repository
            _uiState.update { it.copy(contentState = ContentState.Loading) }

            try {
                val result = examSheetRepository.getExamSheetBy(subTab.firestoreDocumentId, forceRefresh = false)
                result.onSuccess { vocabFile ->
                    val categories = vocabFile.categories
                    // Add to cache for next time
                    contentCache[subTab.firestoreDocumentId] = categories
                    // Update UI with success
                    _uiState.update { it.copy(contentState = ContentState.Success(categories)) }
                }
                result.onFailure { error ->
                    _uiState.update { it.copy(contentState = ContentState.Error(error.localizedMessage ?: "Failed to load content.")) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(contentState = ContentState.Error(e.localizedMessage ?: "An unexpected error occurred.")) }
            }
        }
    }
}
