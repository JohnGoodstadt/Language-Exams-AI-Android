package com.goodstadt.john.language.exams.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.ControlRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.VocabFile
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.models.VocabWord
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// Represents the state of our data loading
sealed interface VocabDataUiState {
    object Loading : VocabDataUiState
    data class Success(val vocabFile: VocabFile) : VocabDataUiState
    data class Error(val message: String) : VocabDataUiState
}

// --- NEW: A sealed interface to represent the playback state ---
sealed interface PlaybackState {
    object Idle : PlaybackState
    data class Playing(val sentenceId: String) : PlaybackState // Use a unique ID
    data class Error(val message: String) : PlaybackState
}

@HiltViewModel
class TabsViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val userPreferencesRepository: UserPreferencesRepository//,
   // private val controlRepository: ControlRepository
) : ViewModel() {

    // A StateFlow to hold the overall UI state for the vocab data
    private val _vocabUiState = MutableStateFlow<VocabDataUiState>(VocabDataUiState.Loading)
    val vocabUiState = _vocabUiState.asStateFlow()

    // --- NEW: StateFlow for playback status ---
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()

    // --- Data slices for each tab ---
    private val _tab1Categories = MutableStateFlow<List<Category>>(emptyList())
    val tab1Categories = _tab1Categories.asStateFlow()
    private val _tab1MenuItems = MutableStateFlow<List<String>>(emptyList())
    val tab1MenuItems = _tab1MenuItems.asStateFlow()
    private val _tab1CategoryIndexMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val tab1CategoryIndexMap = _tab1CategoryIndexMap.asStateFlow()

    private val _tab2Categories = MutableStateFlow<List<Category>>(emptyList())
    val tab2Categories = _tab2Categories.asStateFlow()
    private val _tab2MenuItems = MutableStateFlow<List<String>>(emptyList())
    val tab2MenuItems = _tab2MenuItems.asStateFlow()
    private val _tab2CategoryIndexMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val tab2CategoryIndexMap = _tab2CategoryIndexMap.asStateFlow()

    private val _tab3Categories = MutableStateFlow<List<Category>>(emptyList())
    val tab3Categories = _tab3Categories.asStateFlow()
    private val _tab3MenuItems = MutableStateFlow<List<String>>(emptyList())
    val tab3MenuItems = _tab3MenuItems.asStateFlow()
    private val _tab3CategoryIndexMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val tab3CategoryIndexMap = _tab3CategoryIndexMap.asStateFlow()
    // --- End of data slices ---

    // --- ADD THE NEW DYNAMIC LIST ---
    private val _meTabMenuItems = MutableStateFlow<List<String>>(emptyList())
    val meTabMenuItems = _meTabMenuItems.asStateFlow()

    // --- NEW STATE FOR VOICE PREFERENCE ---
    private val _selectedVoiceName = MutableStateFlow("")
    val selectedVoiceName = _selectedVoiceName.asStateFlow() // <-- 2. ADD STATEFLOW


    private val TAG = "TabsViewModel"

    init {
        // --- THIS IS THE VERIFICATION STEP ---
        Log.d(TAG, "ViewModel Initialized for flavor.")
        Log.d(TAG, "Language Greeting: ${LanguageConfig.defaultFileName}")
        Log.d(TAG, "Language Voice Name: ${LanguageConfig.voiceName}")
        // --- END OF VERIFICATION ---


// --- THIS IS THE FIX ---
        // Launch a new coroutine in the ViewModel's lifecycle scope
        viewModelScope.launch {
            // Now it's safe to call the suspend function 'collect'
            userPreferencesRepository.selectedFileNameFlow.collect { fileName ->
                loadDataForFile(fileName) // This inner block will be executed every time a new filename is saved
            }
        }
        // --- END OF FIX ---
        // --- END OF UPDATE ---

        // Load the menu items from the flavor-specific config
        _meTabMenuItems.value = LanguageConfig.meTabMenuItems

        observeVoicePreference() // <-- 3. CALL THE OBSERVER FUNCTION
    }


    // Renamed loadData to be more specific
    private fun loadDataForFile(fileName: String) {
        viewModelScope.launch {
            _vocabUiState.value = VocabDataUiState.Loading
            val result = vocabRepository.getVocabData(fileName)
            result.onSuccess { vocabFile ->
                _vocabUiState.value = VocabDataUiState.Success(vocabFile)
                // ... (processing logic is the same)
                processCategoriesForTab(vocabFile, 1, _tab1Categories, _tab1MenuItems, _tab1CategoryIndexMap)
                processCategoriesForTab(vocabFile, 2, _tab2Categories, _tab2MenuItems, _tab2CategoryIndexMap)
                processCategoriesForTab(vocabFile, 3, _tab3Categories, _tab3MenuItems, _tab3CategoryIndexMap)
            }.onFailure { error ->
                _vocabUiState.value = VocabDataUiState.Error(error.localizedMessage ?: "Unknown error")
            }
        }
    }
    /**
     * A reusable helper function to filter and process data for a given tab number.
     */
    private fun processCategoriesForTab(
        vocabFile: VocabFile,
        tabNumber: Int,
        categoriesStateFlow: MutableStateFlow<List<Category>>,
        menuItemsStateFlow: MutableStateFlow<List<String>>,
        indexMapStateFlow: MutableStateFlow<Map<String, Int>>
    ) {
        val filteredCategories = vocabFile.categories.filter { it.tabNumber == tabNumber }
        categoriesStateFlow.value = filteredCategories
        menuItemsStateFlow.value = filteredCategories.map { it.title }

        val indexMap = mutableMapOf<String, Int>()
        var currentIndex = 0
        filteredCategories.forEach { category ->
            indexMap[category.title] = currentIndex
            currentIndex += 1 + category.words.size
        }
        indexMapStateFlow.value = indexMap
    }

    // --- NEW FUNCTION TO OBSERVE PREFERENCES ---
    private fun observeVoicePreference() {
        viewModelScope.launch {
            userPreferencesRepository.selectedVoiceNameFlow.collect { voiceName ->
                _selectedVoiceName.value = voiceName
            }
        }
    }

    // --- NEW: The function called by the UI ---
    fun playTrack(word: VocabWord, sentence: Sentence) {
        // Prevent starting a new track while one is already playing
        if (_playbackState.value is PlaybackState.Playing) return


        viewModelScope.launch {
//            val googleVoice = "en-GB-Neural2-C"
            val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
            val uniqueSentenceId = generateUniqueSentenceId(word, sentence,currentVoiceName)
            _playbackState.value = PlaybackState.Playing(uniqueSentenceId)

            // 1. Get the current voice name (from prefs or flavor default)
//            val currentVoiceName = userPreferencesRepository.getSelectedVoiceName()
//            val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
            // 2. Get the corresponding language code from our config
            val currentLanguageCode = LanguageConfig.languageCode

            val result = vocabRepository.playTextToSpeech(
                    text = sentence.sentence,
                    uniqueSentenceId = uniqueSentenceId,
                    voiceName = currentVoiceName,
                    languageCode = currentLanguageCode
            )

            result.onFailure { error ->
                _playbackState.value = PlaybackState.Error(error.localizedMessage ?: "Playback failed")
            }

            // Reset state to Idle when done or on failure
            _playbackState.value = PlaybackState.Idle
        }
    }
}