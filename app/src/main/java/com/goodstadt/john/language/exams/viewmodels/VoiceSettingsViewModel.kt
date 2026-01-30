package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.ControlRepository
import com.goodstadt.john.language.exams.data.Gender
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VoiceOption
import com.goodstadt.john.language.exams.data.VoiceRepository
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class VoiceSheetUiState(
    val isLoading: Boolean = true,
    val error: String? = null,

    // Categorized Data
    val femaleVoices: List<VoiceOption> = emptyList(),
    val maleVoices: List<VoiceOption> = emptyList(),
    val otherVoices: List<VoiceOption> = emptyList(), //TODO: remove

    // UI State for Dropdowns
    val selectedFemale: String = "",
    val selectedMale: String = "",
    val selectedOther: String = "",

    // Which specific voice (from any dropdown) is currently the "Active" candidate to save
    val pendingSaveVoiceOption: VoiceOption? = null,
    val existingVoice: VoiceOption? = null,
)

@Suppress("UNUSED_EXPRESSION")
@HiltViewModel
class VoiceSettingsViewModel @Inject constructor(
    private val voiceRepository: VoiceRepository,
    private val vocabRepository: ContentRepository,
    private val controlRepository: ControlRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val audioPlaybackRepository: AudioPlaybackRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceSheetUiState())
    val uiState = _uiState.asStateFlow()

    private var _latestSentence:String = ""

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // 1. Get Current Preference
                val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first() ?: ""

                // 2. Get Language & Voices
                val languageCodeResult = controlRepository.getCurrentLanguageCode()

                languageCodeResult.onSuccess { languageCode ->
                    voiceRepository.getAvailableVoices(languageCode).onSuccess { allVoices ->



                        val females = allVoices.filter { it.gender == Gender.FEMALE }
                        val males = allVoices.filter { it.gender == Gender.MALE }
                      //  val others = allVoices.filter { it.gender != Gender.FEMALE && it.gender != Gender.MALE }

                        // Logic: Pre-select dropdowns.
                        // If current voice is Female "Sarah", select "Sarah" in Female dropdown.
                        // If Male dropdown has no selection, default to first available.


                        val selectedFemale: VoiceOption? = females.find { it.friendlyName == currentVoiceName } ?: females.firstOrNull()
                        val selectedMale: VoiceOption? = males.find { it.friendlyName == currentVoiceName } ?: males.firstOrNull()
                        val finalSelection = selectedFemale ?: selectedMale

                        val existingVoice = allVoices.firstOrNull { it.id ==  currentVoiceName}


                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                femaleVoices = females,
                                maleVoices = males,
//                                otherVoices = others,
                                // Set initial dropdown values
                                selectedFemale = selectedFemale?.friendlyName ?: "Unknown Name",
                                selectedMale = selectedMale?.friendlyName ?: "Unknown Name",
                                existingVoice = existingVoice,
                                pendingSaveVoiceOption = finalSelection
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load") }
            }
        }
    }

    fun onDropdownSelectionChange(voiceOption: VoiceOption) {
        // Update the specific dropdown AND set this as the candidate to save
        _uiState.update {
            it.copy(pendingSaveVoiceOption = voiceOption, existingVoice = voiceOption)
        }

//        voiceOption?.let { voice ->
//            _uiState.update {
//                it.copy(pendingSaveVoiceOption = voiceOption, existingVoice = voice)
//            }
//
//        }

        // Update specific fields for visual consistency
        when (voiceOption.gender) {
            Gender.FEMALE -> _uiState.update { it.copy(selectedFemale = voiceOption.friendlyName) }
            Gender.MALE  -> _uiState.update { it.copy(selectedMale = voiceOption.friendlyName) }
            Gender.UNKNOWN -> null
        }


        viewModelScope.launch {

            Timber.i("voice:${voiceOption.id}")

            val result = audioPlaybackRepository.playTrackSimply(
                sentence = _latestSentence,
                currentVoiceName = voiceOption.id
            )

        }

    }


    fun saveSelection(onComplete: () -> Unit) {
        viewModelScope.launch {
            val voiceToSave = _uiState.value.pendingSaveVoiceOption
            if (voiceToSave != null) {
                // Save to the external repository
                userPreferencesRepository.saveSelectedVoiceName(voiceToSave.id)
            }
            onComplete()
        }
    }

    fun setLatestSentence(sentence: String) {
        _latestSentence = sentence
    }
}