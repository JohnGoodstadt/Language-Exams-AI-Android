package com.goodstadt.john.language.exams.screens.shared.speakerSelection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.ControlRepository
import com.goodstadt.john.language.exams.data.FirestoreRepository
import com.goodstadt.john.language.exams.data.Gender
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VoiceOption
import com.goodstadt.john.language.exams.data.VoiceRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class SpeakerSelectionUiState(
    val isSheetVisible: Boolean = false,
    val availableVoices: List<VoiceOption> = emptyList(),
    val pendingSelectedVoice: VoiceOption? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SpeakerSelectionViewModel @Inject constructor(
    private val voiceRepository: VoiceRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val ttsStatsRepository: TTSStatsRepository,
    private val firestoreRepository: FirestoreRepository,
    private val controlRepository: ControlRepository,
//    private val audioPreviewPlayer: AudioPreviewPlayer // your existing playTrack wrapper
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpeakerSelectionUiState())
    val uiState: StateFlow<SpeakerSelectionUiState> = _uiState.asStateFlow()

    /** Call from any screen to open the sheet */
    fun show() {
        _uiState.update { it.copy(isSheetVisible = true, error = null) }
        ensureVoicesLoaded()
    }

    /** Called by sheet dismiss/cancel */
    fun hide() {
        _uiState.update { it.copy(isSheetVisible = false, pendingSelectedVoice = null) }
    }

    /** Load the voice list only when needed */
    private fun ensureVoicesLoaded() {
        if (_uiState.value.availableVoices.isNotEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val currentLanguageCode = controlRepository.getCurrentLanguageCode()
                //val voices = voiceRepository.getAllVoices() // you likely already have this
                currentLanguageCode.onSuccess { languageCode ->
                    val availableVoicesResult = voiceRepository.getAvailableVoices(languageCode)

                    availableVoicesResult.onSuccess { voices ->
                        Timber.e("${voices}")

                        val femaleVoices = voices.filter { it.gender == Gender.FEMALE }
                        val maleVoices = voices.filter { it.gender == Gender.MALE }

                        _uiState.update { it.copy(isLoading = false, availableVoices = voices) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Failed to load voices")
                }
            }
        }
    }

    /** Updates pending voice + plays preview */
    fun onPendingVoiceSelect(voice: VoiceOption) {
        _uiState.update { it.copy(pendingSelectedVoice = voice) }
        Timber.d("Voice selected: ${voice.friendlyName} google: ${voice.id}")

        viewModelScope.launch {
            val sentence = "Hello, I'm ${voice.friendlyName}. Welcome to 'English Exam Words'."
           // audioPreviewPlayer.play(sentence, voice.id) // wrap your playTrack(...)
        }
    }

    /** Save pending voice to preferences + stats + firestore, then hide */
    fun saveSelection(currentGoogleVoiceNameField: String) {
        val pending = _uiState.value.pendingSelectedVoice ?: run {
            hide()
            return
        }

        viewModelScope.launch {
            try {
                val voiceName = pending.id

                userPreferencesRepository.saveSelectedVoiceName(voiceName)
                ttsStatsRepository.updateUserStatField(currentGoogleVoiceNameField, voiceName)
                firestoreRepository.fsUpdateUserGoogleVoices(voiceName)

                hide()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to save voice") }
            }
        }
    }
}
