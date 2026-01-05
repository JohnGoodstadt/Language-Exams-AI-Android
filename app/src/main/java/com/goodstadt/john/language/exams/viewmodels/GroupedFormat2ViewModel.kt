package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.models.Format2File
import com.goodstadt.john.language.exams.models.SubTabDefinition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class GroupedFormat2UiState(
    val subTabs: List<SubTabDefinition> = emptyList(),
    val selectedSubTab: SubTabDefinition? = null,
    val currentFormat2File: Format2File? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GroupedFormat2ViewModel @Inject constructor(
    private val appConfigRepository: AppConfigRepository,
    private val vocabRepository: ContentRepository,
//    private val userPreferencesRepository: UserPreferencesRepository, // Needed to look up the tab definition
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val parentTabId: String = savedStateHandle.get<String>("tabId")!!

    private val _uiState = MutableStateFlow(GroupedFormat2UiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadSubTabs()
    }

    private fun loadSubTabs() {
        viewModelScope.launch {
            // 1. Get the Tab Definition from the manifest (Source of Truth)
            // We need to look up the "PairsGroup" definition to find its children
            val manifest = appConfigRepository.getAppUiManifest() // Helper to get current manifest
            Timber.d("GroupedFormat2ViewModel: Manifest fetched. Registry contains ${manifest.sheetRegistry.size} items.")

            // Find the tab definition that matches our ID
            val tabDef = manifest?.sheetRegistry?.get(parentTabId)
                ?: return@launch // Handle error

            val subTabs = tabDef.subTabs ?: emptyList()

            if (subTabs.isNotEmpty()) {
                // Select first by default
                val first = subTabs.first()
                _uiState.update { it.copy(subTabs = subTabs, selectedSubTab = first) }

                // Load the content for the first tab
                loadContent(first)
            }
//
        }
    }
    fun onSubTabSelected(subTab: SubTabDefinition) {
        _uiState.update { it.copy(selectedSubTab = subTab) }
        loadContent(subTab)
    }

    private fun loadContent(subTab: SubTabDefinition) {
        val docId = subTab.firestoreDocumentId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = vocabRepository.getFormat2Data(docId)

            result.onSuccess { file ->
                _uiState.update { it.copy(isLoading = false, currentFormat2File = file) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.localizedMessage) }
            }
        }
    }
}