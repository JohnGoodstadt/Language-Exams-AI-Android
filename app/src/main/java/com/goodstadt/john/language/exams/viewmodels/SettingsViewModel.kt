package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.data.ControlRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VoiceOption
import com.goodstadt.john.language.exams.data.VoiceRepository
import com.goodstadt.john.language.exams.models.ExamDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Data class to hold all the display state for the screen
data class SettingsUiState(
    val appVersion: String = "",
    val currentVoiceName: String = "",
    val currentExamName: String = "",
    val availableExams: List<ExamDetails> = emptyList(),
    val availableVoices: List<VoiceOption> = emptyList(),
    val currentFriendlyVoiceName: String = ""
)

// Sealed class to represent which bottom sheet should be shown
sealed interface SheetContent {
    object Hidden : SheetContent
    object SpeakerSelection : SheetContent
    object ExamSelection : SheetContent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val controlRepository: ControlRepository,
    private val voiceRepository: VoiceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    private val _sheetState = MutableStateFlow<SheetContent>(SheetContent.Hidden)
    val sheetState = _sheetState.asStateFlow()

    init {
        // Observe BOTH flows to keep the UI perfectly in sync
        // Observe the voice name flow to update the friendly name
        viewModelScope.launch {
            // Observe the saved voice ID
            userPreferencesRepository.selectedVoiceNameFlow.collect { voiceId ->
                // When the ID changes, get its friendly name asynchronously
                val friendlyName = voiceRepository.getFriendlyNameForVoice(voiceId)
                _uiState.update { it.copy(currentFriendlyVoiceName = friendlyName) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.selectedFileNameFlow.collect { fileName ->
                _uiState.update { it.copy(currentExamName = fileName) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.selectedVoiceNameFlow.collect { voiceName ->
                _uiState.update { it.copy(currentVoiceName = voiceName) }
            }
        }
        // Load the static data
        loadInitialData()
    }
    private fun loadInitialData() {
        viewModelScope.launch {
            val languageDetailsResult = controlRepository.getActiveLanguageDetails()
            val availableVoicesResult = voiceRepository.getAvailableVoices()
            languageDetailsResult.onSuccess { details ->
                availableVoicesResult.onSuccess { voices ->
                    _uiState.update {
                        it.copy(
                                appVersion = BuildConfig.VERSION_NAME,
                                availableVoices = voices,
                                availableExams = details.exams
                        )
                    }
                }
            }
        }
    }
//    private fun loadCurrentSettingsObsolete() {
//        viewModelScope.launch {
//            val languageDetailsResult = controlRepository.getActiveLanguageDetails()
//            languageDetailsResult.onSuccess { details ->
//                _uiState.update {
//                    it.copy(
//                            appVersion = BuildConfig.VERSION_NAME,
//                            currentVoiceName = userPreferencesRepository.getSelectedVoiceName(),
//                            // We'll use the filename for now; this can be mapped to a display name later
//                            currentExamName = userPreferencesRepository.getSelectedFileName(),
//                            availableExams = details.exams
//                    )
//                }
//            }
//        }
//    }
fun onVoiceSelected(voiceOption: VoiceOption) {
    viewModelScope.launch {
        userPreferencesRepository.saveSelectedVoiceName(voiceOption.id)
        hideBottomSheet()
    }
}
    fun onExamSelected(exam: ExamDetails) {
        viewModelScope.launch {
            // The only job here is to SAVE the new preference.
            // The flows will handle notifying the rest of the app automatically.
            userPreferencesRepository.saveSelectedFileName(exam.json)
            // Hiding the sheet is still a local UI concern.
           // hideBottomSheet()
        }
    }
    // --- NEW FUNCTION to handle exam selection ---
    fun onExamSelectedObsolete(exam: ExamDetails, onComplete: () -> Unit) {
        viewModelScope.launch {
            // Save the 'json' field (e.g., "vocab_data_a2") to preferences
            userPreferencesRepository.saveSelectedFileName(exam.json)
            // Update the UI state to reflect the new selection immediately
            _uiState.update { it.copy(currentExamName = exam.json) }
            // Hide the bottom sheet
            hideBottomSheet()
            // Call the completion handler
            onComplete()
        }
    }

    fun onSettingClicked(type: SheetContent) {
        _sheetState.value = type
    }

    fun hideBottomSheet() {
        _sheetState.value = SheetContent.Hidden
    }
}