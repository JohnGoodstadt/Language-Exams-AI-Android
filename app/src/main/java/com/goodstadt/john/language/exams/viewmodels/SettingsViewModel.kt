package com.goodstadt.john.language.exams.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.ControlRepository
import com.goodstadt.john.language.exams.data.StatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.data.VoiceOption
import com.goodstadt.john.language.exams.data.VoiceRepository
import com.goodstadt.john.language.exams.models.ExamDetails
import com.goodstadt.john.language.exams.models.WordAndSentence
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- MODIFICATION 1: Add pending state to UiState ---
data class SettingsUiState(
    val appVersion: String = "",
    val currentVoiceName: String = "",
    val currentExamName: String = "",
    val availableExams: List<ExamDetails> = emptyList(),
    val availableVoices: List<VoiceOption> = emptyList(),
    val currentFriendlyVoiceName: String = "",
        // Add nullable fields to hold the user's selection inside the bottom sheet
    val pendingSelectedExam: ExamDetails? = null,
    val pendingSelectedVoice: VoiceOption? = null
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
    private val voiceRepository: VoiceRepository,
    private val vocabRepository: VocabRepository,
    private val statsRepository: StatsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    private val _sheetState = MutableStateFlow<SheetContent>(SheetContent.Hidden)
    val sheetState = _sheetState.asStateFlow()

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()

    init {
        // This initialization logic is correct and remains the same.
        // It keeps the "current" state in sync with saved preferences.
        viewModelScope.launch {
            userPreferencesRepository.selectedVoiceNameFlow.collect { voiceId ->
                val friendlyName = voiceRepository.getFriendlyNameForVoice(voiceId)
                _uiState.update { it.copy(currentFriendlyVoiceName = friendlyName, currentVoiceName = voiceId) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.selectedFileNameFlow.collect { fileName ->
                _uiState.update { it.copy(currentExamName = fileName) }
            }
        }
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

    // --- MODIFICATION 2: Update onSettingClicked to set the initial pending state ---
    fun onSettingClicked(type: SheetContent) {
        viewModelScope.launch {
            val currentState = uiState.value
            // Pre-populate the pending state with the currently active selection
            val updatedUiState = when (type) {
                SheetContent.ExamSelection -> {
                    val currentExam = currentState.availableExams.find { it.json == currentState.currentExamName }
                    currentState.copy(pendingSelectedExam = currentExam)
                }
                SheetContent.SpeakerSelection -> {
                    val currentVoice = currentState.availableVoices.find { it.id == currentState.currentVoiceName }
                    currentState.copy(pendingSelectedVoice = currentVoice)
                }
                SheetContent.Hidden -> currentState
            }
            _uiState.value = updatedUiState
            _sheetState.value = type
        }
    }

    // --- MODIFICATION 3: Create functions to handle PENDING selections ---
    fun onPendingExamSelect(exam: ExamDetails) {
        // This only updates the state for the UI inside the sheet. Does NOT save.
        _uiState.update { it.copy(pendingSelectedExam = exam) }

    }

    fun onPendingVoiceSelect(voice: VoiceOption) {
        // This only updates the state for the UI inside the sheet. Does NOT save.
        _uiState.update { it.copy(pendingSelectedVoice = voice) }
        Log.d("SettingsViewModel", "Voice selected: ${voice.friendlyName} google: ${voice.id}")

        // Use val for an immutable variable, as it's only assigned once.
        val sentence = when (LanguageConfig.languageCode.substring(0, 2).lowercase()) {
            "de" -> "Hallo, ich bin ${voice.friendlyName}. Willkommen zu 'English Exam Words'"
            // The `else` branch is required for a 'when' expression, and it handles the default case.
            else -> "Hello, I'm ${voice.friendlyName}. Welcome to 'English Exam Words'."
        }

        playTrack(sentence,voice.id)
    }

    private fun playTrack(sentence: String,googleVoice:String) {
        if (_playbackState.value is PlaybackState.Playing) return

        viewModelScope.launch {
            val uniqueSentenceId = generateUniqueSentenceId(sentence,googleVoice)
            _playbackState.value = PlaybackState.Playing(uniqueSentenceId)

            val currentLanguageCode = LanguageConfig.languageCode

            val result = vocabRepository.playTextToSpeech(
                    text = sentence,
                    uniqueSentenceId = uniqueSentenceId,
                    voiceName = googleVoice,
                    languageCode = currentLanguageCode
            )
            result.onSuccess {
                //TODO: DO I need to save settings sentence?
                //statsRepository.fsUpdateSentenceHistoryIncCount(WordAndSentence(word.word, sentence))
            }
            result.onFailure { error ->
                _playbackState.value = PlaybackState.Error(error.localizedMessage ?: "Playback failed")
            }
            _playbackState.value = PlaybackState.Idle
        }
    }



    // --- MODIFICATION 4: Create a SAVE function for the new button ---
    fun saveSelection() {
        viewModelScope.launch {
            val pendingExam = _uiState.value.pendingSelectedExam
            val pendingVoice = _uiState.value.pendingSelectedVoice

            when (_sheetState.value) {
                SheetContent.ExamSelection -> {
                    pendingExam?.let { userPreferencesRepository.saveSelectedFileName(it.json) }
                }
                SheetContent.SpeakerSelection -> {
                    pendingVoice?.let { userPreferencesRepository.saveSelectedVoiceName(it.id) }
                }
                SheetContent.Hidden -> { /* Do nothing */ }
            }
            // After saving, hide the sheet, which will also clear the pending state.
            hideBottomSheet()
        }
    }


    // --- MODIFICATION 5: Update hideBottomSheet to clear pending state ---
    fun hideBottomSheet() {
        _sheetState.value = SheetContent.Hidden
        // Reset the pending states so they are fresh the next time the sheet opens
        _uiState.update {
            it.copy(
                    pendingSelectedExam = null,
                    pendingSelectedVoice = null
            )
        }
    }

    // --- These functions are now obsolete as their logic is moved into saveSelection() ---
    // You can safely remove them.
    /*
    fun onVoiceSelected(voiceOption: VoiceOption) { ... }
    fun onExamSelected(exam: ExamDetails) { ... }
    fun onExamSelectedObsolete(exam: ExamDetails, onComplete: () -> Unit) { ... }
    */
}