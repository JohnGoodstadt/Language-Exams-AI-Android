package com.goodstadt.john.language.exams.packages.ReferenceTabContainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.RefreshTrigger
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.data.stats.PageStat
import com.goodstadt.john.language.exams.data.stats.recordPageView
import com.goodstadt.john.language.exams.models.AppUIManifest
import com.goodstadt.john.language.exams.models.Format0File
import com.goodstadt.john.language.exams.models.Format1File
import com.goodstadt.john.language.exams.models.Format2File
import com.goodstadt.john.language.exams.models.Format8File
import com.goodstadt.john.language.exams.models.SheetDataType
import com.goodstadt.john.language.exams.models.SheetDefinition
import com.goodstadt.john.language.exams.screens.shared.gamification.SideQuestNavTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    val format1Cache: Map<String, Format1File> = emptyMap(),
    val format2Cache: Map<String, Format2File> = emptyMap(),
    val format8Cache: Map<String, Format8File> = emptyMap(),
    val selectedFormat8Sheets: Map<String, String> = emptyMap(),

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
    private val refreshTrigger: RefreshTrigger,
    private val ttsStatsRepository: com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
) : ViewModel() {

    /**
     * Page-popularity: record that the reference sheet [tabId] was opened. One central place covers every
     * reference renderer (called from the screen on initial view and each sub-tab switch). Uses the sheet's
     * firestore doc id when it has one, else the manifest tab id (for grouped / fixed screens).
     */
    fun recordReferencePageOpen(tabId: String) {
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        // The two quizzes (Usage Quiz = FixedScreen, Vocab Quiz = VocabDashboard) keep their own quiz
        // stats, so they're excluded from page-popularity.
        val st = tab.definition.screenType
        if (st == com.goodstadt.john.language.exams.models.ScreenType.FIXED_SCREEN ||
            st == com.goodstadt.john.language.exams.models.ScreenType.VOCAB_DASHBOARD) return
        val sheetId = tab.definition.firestoreDocumentId ?: tabId
        ttsStatsRepository.recordPageView(
            com.goodstadt.john.language.exams.data.stats.PageStat.Area.REFERENCE,
            sheetId,PageStat.Metric.TAP //Was Open but don't want 2 values multiplied in DB
        )
    }

    private val _uiState = MutableStateFlow(ReferenceUiState())
    val uiState = _uiState.asStateFlow()
    private var sheetLoadJob: Job? = null
    private var sheetLoadRequest = 0L

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
            val selected = _uiState.value.tabs.firstOrNull { it.id == _uiState.value.selectedTabId }
            if (selected?.definition?.sheetDataType == SheetDataType.FORMAT_8) {
                onTabSelected(selected.id)
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
    fun onTabSelected(tabId: String) {
        // Invalidate before cancelling so an old request cannot clear the next request's spinner/error.
        sheetLoadRequest += 1
        sheetLoadJob?.cancel()
        _uiState.update { it.copy(selectedTabId = tabId, isLoadingSheet = false, sheetError = null) }
        val definition = _uiState.value.tabs.firstOrNull { it.id == tabId }?.definition ?: return
        val docId = if (definition.sheetDataType == SheetDataType.FORMAT_8) {
            _uiState.value.selectedFormat8Sheets[tabId]
                ?: definition.firestoreDocumentId
                ?: definition.subTabs?.firstOrNull()?.firestoreDocumentId
        } else {
            definition.firestoreDocumentId
        }
        if (docId.isNullOrBlank()) {
            if (definition.sheetDataType == SheetDataType.FORMAT_8) {
                _uiState.update { it.copy(sheetError = "No Format 8 sheet configured for ${definition.title}.") }
            }
            return
        }
        if (definition.sheetDataType == SheetDataType.FORMAT_8) {
            _uiState.update { it.copy(selectedFormat8Sheets = it.selectedFormat8Sheets + (tabId to docId)) }
        }
        loadSheet(definition.sheetDataType, docId)
    }

    /** Navigation can restore this destination without another chip tap. Do not duplicate an active load. */
    fun ensureFormat8TabLoaded(tabId: String) {
        val state = _uiState.value
        if (state.selectedTabId != tabId || state.isLoadingSheet || state.sheetError != null) return
        val definition = state.tabs.firstOrNull { it.id == tabId }?.definition ?: return
        if (definition.sheetDataType != SheetDataType.FORMAT_8) return
        val docId = state.selectedFormat8Sheets[tabId]
            ?: definition.firestoreDocumentId
            ?: definition.subTabs?.firstOrNull()?.firestoreDocumentId
        if (docId != null && state.format8Cache.containsKey(docId)) return
        onTabSelected(tabId)
    }

    fun onFormat8SubTabSelected(tabId: String, sheetName: String) {
        if (_uiState.value.selectedTabId != tabId) return
        val definition = _uiState.value.tabs.firstOrNull { it.id == tabId }?.definition ?: return
        if (definition.sheetDataType != SheetDataType.FORMAT_8 ||
            definition.subTabs.orEmpty().none { it.firestoreDocumentId == sheetName }) return
        _uiState.update { it.copy(selectedFormat8Sheets = it.selectedFormat8Sheets + (tabId to sheetName)) }
        onTabSelected(tabId)
    }

    private fun loadSheet(dataType: SheetDataType, docId: String) {
        val cached = when (dataType) {
            SheetDataType.VOCAB_FILE -> _uiState.value.vocabFileCache.containsKey(docId)
            SheetDataType.FORMAT_1 -> _uiState.value.format1Cache.containsKey(docId)
            SheetDataType.FORMAT_2 -> _uiState.value.format2Cache.containsKey(docId)
            SheetDataType.FORMAT_8 -> _uiState.value.format8Cache.containsKey(docId)
            else -> false
        }
        if (cached) return
        val request = sheetLoadRequest
        _uiState.update { it.copy(isLoadingSheet = true, sheetError = null) }
        sheetLoadJob = viewModelScope.launch {
            try {
                when (dataType) {
                    SheetDataType.VOCAB_FILE -> {
                        val file = vocabRepository.getFormat0Data(docId).getOrThrow()
                        _uiState.update { it.copy(vocabFileCache = it.vocabFileCache + (docId to file)) }
                    }
                    SheetDataType.FORMAT_1 -> {
                        val file = vocabRepository.getFormat1Data(docId).getOrThrow()
                        _uiState.update { it.copy(format1Cache = it.format1Cache + (docId to file)) }
                    }
                    SheetDataType.FORMAT_2 -> {
                        val file = vocabRepository.getFormat2Data(docId).getOrThrow()
                        _uiState.update { it.copy(format2Cache = it.format2Cache + (docId to file)) }
                    }
                    SheetDataType.FORMAT_8 -> {
                        val file = vocabRepository.getFormat8Data(docId).getOrThrow()
                        _uiState.update { it.copy(format8Cache = it.format8Cache + (docId to file)) }
                        Timber.d("Reference: Loaded Format 8 '$docId' (${file.sections.size} sections).")
                    }
                    else -> Timber.w("No data fetching implemented for dataType: $dataType")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (request == sheetLoadRequest) {
                    _uiState.update { it.copy(sheetError = e.localizedMessage ?: "Failed to load content") }
                }
            } finally {
                if (request == sheetLoadRequest) {
                    _uiState.update { it.copy(isLoadingSheet = false) }
                }
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