package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.RefreshTrigger
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.data.examsheets.ExamSheetRepository
import com.goodstadt.john.language.exams.models.DataType
import com.goodstadt.john.language.exams.models.HeaderWordsSentencesListRoot
import com.goodstadt.john.language.exams.models.SheetDefinition
import com.goodstadt.john.language.exams.models.TabDefinition
import com.goodstadt.john.language.exams.models.VocabFile
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

    // Data caches (add one for each data type)
    val vocabFileCache: Map<String, VocabFile> = emptyMap(),
    val format1Cache: Map<String, HeaderWordsSentencesListRoot> = emptyMap(),

    // Loading/Error state for data fetching
    val isLoadingSheet: Boolean = false,
    val sheetError: String? = null,

    val selectedCategoryTitleForSheet: String? = null, // Renamed for clarity
    val currentVoiceName: String = ""
)

@HiltViewModel
class ReferenceViewModel @Inject constructor(
    // Inject your existing repository
    private val appConfigRepository: AppConfigRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val vocabRepository: VocabRepository,
//    private val examSheetRepository: ExamSheetRepository,
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
    fun onTabSelectedOriginal(tabId: String) {
        _uiState.update { it.copy(selectedTabId = tabId) }
    }
    // ✅ ADD this new function, the equivalent of fetchSheetIfNeeded
    fun onTabSelected(tabId: String) {
        // First, update the selection state to change the UI
        _uiState.update { it.copy(selectedTabId = tabId) }

        // Then, launch a coroutine to fetch data for the new tab
        viewModelScope.launch {
            val selectedTab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return@launch
            val definition = selectedTab.definition
            val docId = definition.firestoreDocumentId ?: return@launch

            // Check if data is already cached to avoid re-fetching
            if (_uiState.value.vocabFileCache.containsKey(docId) || _uiState.value.format1Cache.containsKey(docId)) {
                return@launch
            }

            _uiState.update { it.copy(isLoadingSheet = true, sheetError = null) }

            try {
                // Use a 'when' block on the safe enum to call the correct repository function
                when (definition.dataType) {
                    DataType.VOCAB_FILE -> {
                        //val result = examSheetRepository.getVocabSheet(name = docId, forceRefresh = false) // Assuming versioning happens inside
                        val result = vocabRepository.getVocabData(docId)
                        result.onSuccess { file ->
                            _uiState.update { it.copy(vocabFileCache = it.vocabFileCache + (docId to file)) }
                        }
                        result.onFailure { throw it }
                    }
                    DataType.FORMAT_1 -> {
//                        val result = examSheetRepository.getFormat1Sheet(name = docId, forceRefresh = false) // You will need to create this
                        val result = vocabRepository.getFormat1Data(docId)
                        result.onSuccess { file ->
                            _uiState.update { it.copy(format1Cache = it.format1Cache + (docId to file)) }
                        }
                        result.onFailure { throw it }
                    }
                    else -> Timber.w("No data fetching implemented for dataType: ${definition.dataType}")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(sheetError = e.localizedMessage ?: "Failed to load content") }
            } finally {
                _uiState.update { it.copy(isLoadingSheet = false) }
            }
        }
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