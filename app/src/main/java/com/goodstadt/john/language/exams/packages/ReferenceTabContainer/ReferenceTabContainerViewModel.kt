package com.goodstadt.john.language.exams.packages.ReferenceTabContainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.RefreshTrigger
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.models.AppUIManifest
import com.goodstadt.john.language.exams.models.Format0File
import com.goodstadt.john.language.exams.models.Format2File
import com.goodstadt.john.language.exams.models.HeaderWordsSentencesListRoot
import com.goodstadt.john.language.exams.models.SheetDataType
import com.goodstadt.john.language.exams.models.SheetDefinition
import com.goodstadt.john.language.exams.screens.shared.gamification.SideQuestNavTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject


data class DisplayTab(
    val id: String,
    val definition: SheetDefinition
)

data class ReferenceUiState(
    val tabs: List<DisplayTab> = emptyList(), // Use DisplayTab here
    val selectedTabId: String = "",

    // Data caches (add one for each data type)
    val vocabFileCache: Map<String, Format0File> = emptyMap(),
    val format1Cache: Map<String, HeaderWordsSentencesListRoot> = emptyMap(),
    val format2Cache: Map<String, Format2File> = emptyMap(),

    // Loading/Error state for data fetching
    val isLoadingSheet: Boolean = false,
    val sheetError: String? = null,

    val selectedCategoryTitleForSheet: String? = null, // Renamed for clarity
    val currentVoiceName: String = ""
)

@HiltViewModel
class ReferenceTabContainerViewModel @Inject constructor(
    // Inject your existing repository
    private val appConfigRepository: AppConfigRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val vocabRepository: ContentRepository,
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

            val newTabs = buildTabsFromManifest(manifest)


            //val registry = manifest.sheetRegistry
            //val tabOrder = manifest.layouts.referenceTab.order

            // 2. Perform the "join" operation (same logic as iOS).
//            val newTabs = tabOrder.mapNotNull { id ->
//                registry[id]?.let { definition ->
//                     Change 'id to definition' to 'DisplayTab(...)'.
//                    DisplayTab(id = id, definition = definition)
//                }
//            }

            // 3. Update the UI state.
            _uiState.update { currentState ->

                // Safety Check:
                // If the user was already looking at a specific tab (e.g. from a deep link),
                // check if that tab still exists in the new list.
                // If yes, keep it. If no (or if ID is empty), default to the first tab.
                val validSelection = if (newTabs.any { it.id == currentState.selectedTabId }) {
                    currentState.selectedTabId
                } else {
                    newTabs.firstOrNull()?.id ?: ""
                }

                currentState.copy(
                    tabs = newTabs,
                    selectedTabId = validSelection
                )
            }

        }
    }

    private fun buildTabsFromManifestObsolete(manifest: AppUIManifest): List<DisplayTab> {
        val registry = manifest.sheetRegistry
        val tabOrder = manifest.layouts.referenceTab.order

        // Get device language code (e.g., "en", "es", "fr")
        val deviceLanguage = Locale.getDefault().language

        return tabOrder.mapNotNull { id ->

            // --- FILTER LOGIC ---
            // If the tab is "LocalLanguage", strictly require the device to be Spanish ("es")
            if (id == "SpanishLanguage" && deviceLanguage != "es") {
                 Timber.d("Hiding Local Language tab because device is $deviceLanguage")
                return@mapNotNull null
            }else{
                Timber.d("Found Local Language $deviceLanguage")
            }

            // --- MAP LOGIC ---
            registry[id]?.let { definition ->
                DisplayTab(id = id, definition = definition)
            }
        }
    }
    private fun buildTabsFromManifest(manifest: AppUIManifest): List<DisplayTab> {
        val registry = manifest.sheetRegistry
        val tabOrder = manifest.layouts.referenceTab.order

        // Get device language (e.g., "en", "es", "de", "pt")
        val deviceLanguage = Locale.getDefault().language

        return tabOrder.mapNotNull { id ->

            // 1. Get the definition
            val definition = registry[id] ?: return@mapNotNull null

            // 2. GENERIC FILTER LOGIC
            // If 'requiredLocale' is set in JSON, check against device language.
            if (!definition.requiredLocale.isNullOrBlank()) {
                // If the required language (e.g. "es") does NOT match device (e.g. "en")
                // then skip this tab.
                if (definition.requiredLocale != deviceLanguage) {
                    // Timber.d("Skipping '$id'. Requires '${definition.requiredLocale}', device is '$deviceLanguage'")
                    return@mapNotNull null
                }
            }

            // 3. Pass Validation -> Create Tab
            DisplayTab(id = id, definition = definition)
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
                when (definition.sheetDataType) {
                    SheetDataType.VOCAB_FILE -> {
                        //val result = examSheetRepository.getVocabSheet(name = docId, forceRefresh = false) // Assuming versioning happens inside
                        val result = vocabRepository.getFormat0Data(docId)
                        result.onSuccess { file ->
                            _uiState.update { it.copy(vocabFileCache = it.vocabFileCache + (docId to file)) }
                        }
                        result.onFailure { throw it }
                    }
                    SheetDataType.FORMAT_1 -> {
//                        val result = examSheetRepository.getFormat1Sheet(name = docId, forceRefresh = false) // You will need to create this
                        val result = vocabRepository.getFormat1Data(docId)
                        result.onSuccess { file ->
                            _uiState.update { it.copy(format1Cache = it.format1Cache + (docId to file)) }
                        }
                        result.onFailure { throw it }
                    }
                    SheetDataType.FORMAT_2 -> {
                        val result = vocabRepository.getFormat2Data(docId)
                        result.onSuccess { file ->
                            _uiState.update { it.copy(format2Cache = it.format2Cache + (docId to file)) }
                        }
                        result.onFailure { throw it }
                    }
                    else -> Timber.w("No data fetching implemented for dataType: ${definition.sheetDataType}")

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
    fun playTrack( sentence: String) {
        Timber.v(sentence)
    }
    // ✅ 1. Function to handle the deep link
    fun checkDeepLink(target: SideQuestNavTarget) {
        when (target) {
            is SideQuestNavTarget.Reference -> {
                // Update the internal horizontal tab
                // This will trigger the LaunchedEffect in ReferenceTabContainerScreen
                onTabSelected(target.tabId)

                // Optional: If you need to push a specific document on top,
                // you might need to handle target.documentId here too.
            }
            // Add logic for Quiz if needed
            else -> {}
        }
    }
}