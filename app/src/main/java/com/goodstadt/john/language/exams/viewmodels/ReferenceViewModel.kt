package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.RefreshTrigger
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.models.SheetDefinition
import com.goodstadt.john.language.exams.models.TabDefinition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class DisplayTab(
    val id: String,
    val definition: SheetDefinition
)

data class ReferenceUiState(
    val tabs: List<DisplayTab> = emptyList(), // Use DisplayTab here
    val selectedTabId: String = "",

    val selectedCategoryTitleForSheet: String? = null, // Renamed for clarity
    val currentVoiceName: String = ""
)

@HiltViewModel
class ReferenceViewModel @Inject constructor(
    // Inject your existing repository
    private val appConfigRepository: AppConfigRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val refreshTrigger: RefreshTrigger
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReferenceUiState())
    val uiState = _uiState.asStateFlow()

    init {
        // Load the tabs as soon as the ViewModel is created
        loadUiConfiguration()
//        loadTabsConfiguration()
        observeVoiceName()
    }

    private fun loadUiConfiguration() {
        viewModelScope.launch {
            // 1. Get the entire manifest from the repository.
            val manifest = appConfigRepository.getAppUiManifest()

            val registry = manifest.sheetRegistry
            val tabOrder = manifest.layouts.referenceTab.order

            // 2. Perform the "join" operation (same logic as iOS).
            val newTabs = tabOrder.mapNotNull { id ->
                registry[id]?.let { definition ->
                    // Change 'id to definition' to 'DisplayTab(...)'.
                    DisplayTab(id = id, definition = definition)
                }
            }

            // 3. Update the UI state.
            _uiState.update {
                it.copy(
                    tabs = newTabs,
                    selectedTabId = newTabs.firstOrNull()?.id ?: ""
                )
            }
        }
    }
    private fun observeVoiceName() {
        viewModelScope.launch {
            userPreferencesRepository.selectedVoiceNameFlow.collect { voiceName ->
                _uiState.update { it.copy(currentVoiceName = voiceName) }
            }
        }
    }
//    private fun loadTabsConfiguration() {
//        viewModelScope.launch {
//            // Call your new repository function
//            val tabs = appConfigRepository.getReferenceTabs()
//            _uiState.update {
//                it.copy(
//                    tabs = tabs,
//                    // Immediately set the selected tab to the first one
//                    selectedTabId = tabs.firstOrNull()?.id ?: ""
//                )
//            }
//        }
//    }

    fun onTabSelected(tabId: String) {
        _uiState.update { it.copy(selectedTabId = tabId) }
    }
    fun onTileTappedForSheet(categoryTitle: String) {
        _uiState.update { it.copy(selectedCategoryTitleForSheet = categoryTitle) }
    }

    // From the old MeTabViewModel
    fun onSheetDismissed() {
        _uiState.update { it.copy(selectedCategoryTitleForSheet = null) }
        Timber.d("ReferenceViewModel", "Bottom sheet dismissed. Triggering a progress map refresh.")
        refreshTrigger.triggerProgressMapRefresh()
    }
}