package com.goodstadt.john.language.exams.screens.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.data.ControlRepository
import com.goodstadt.john.language.exams.data.RecallingItems
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VoiceRepository
import com.goodstadt.john.language.exams.models.ExamDetails
import com.goodstadt.john.language.exams.models.LanguageCodeDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

//}
@HiltViewModel
class ChooseEnglishViewModel  @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val controlRepository: ControlRepository,
    private val ttsStatsRepository: TTSStatsRepository,
    private val recallingItemsManager: RecallingItems,
    private val voiceRepository: VoiceRepository,
): ViewModel() {

    private val _uiState = MutableStateFlow(  ChooseEnglishUIState())
    val uiState = _uiState.asStateFlow()

    private val _showOnBoardingSheet = MutableStateFlow(false)
    val showOnboardingSheet = _showOnBoardingSheet.asStateFlow()

    data class ChooseEnglishUIState(
        val currentLanguage: String = "",
        val currentExamName: String = "",
        val availableExams: List<ExamDetails> = emptyList(),
        val availableLanguages: List<LanguageCodeDetails> = emptyList(),
        val pendingSelectedExam: ExamDetails? = null,
        val pendingSelectedLanguage: LanguageCodeDetails? = null
    )
    init
    {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val currentLanguageCode = controlRepository.getCurrentLanguageCode()
            currentLanguageCode.onSuccess { languageCode ->
                val languageDetailsResult = controlRepository.getActiveLanguageDetails()
                val languageListResult = controlRepository.getAllEnglishLanguageList()

                languageDetailsResult.onSuccess { details ->
                    languageListResult.onSuccess { languages ->
                        _uiState.update {
                            it.copy(
                                availableExams = details.exams,
                                availableLanguages = languages
                            )
                        }

                    }
                }
            }
        }
    }

    fun onPendingLanguageSelect(language: LanguageCodeDetails) {
        _uiState.update { it.copy(pendingSelectedLanguage = language) }
    }

    fun onPendingExamSelect(exam: ExamDetails) {
        _uiState.update { it.copy(pendingSelectedExam = exam) }
    }

    fun hideBottomSheet() {
        _showOnBoardingSheet.value = false
    }

    fun saveSelection() {
        Timber.e("BothSelection")
        viewModelScope.launch {
            val pendingExam = _uiState.value.pendingSelectedExam
            val pendingLanguage = _uiState.value.pendingSelectedLanguage

            ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.WORDSTATS)//so that old exam has correct stat
            pendingExam?.let { selectedExam ->
                // 1. Save the user's preference (already here)
                userPreferencesRepository.saveSelectedFileName(selectedExam.json)
                userPreferencesRepository.saveSelectedSkillLevel(selectedExam.skillLevel)
                // --- THIS IS THE NEW, CRITICAL PART ---
                // 2. Tell the shared manager to load the recalled items for the NEW exam
                Timber.d("New exam selected. Reloading recalled items for key: ${selectedExam.json}")
                recallingItemsManager.load(selectedExam.json)

            }

            pendingLanguage?.let { selectedLanguage ->
                voiceRepository.clearCache() //all voice will change
                controlRepository.clearCache() //stpred language details will change

                Timber.d(selectedLanguage.name)
                //1. default voice
                val voiceName = selectedLanguage.defaultFemaleVoice
                userPreferencesRepository.saveSelectedVoiceName(voiceName)
                userPreferencesRepository.saveSelectedLanguageCode(selectedLanguage.code)

                _uiState.update { it.copy(currentLanguage = selectedLanguage.name) } //update UI

                // 1. Save the user's preference (already here)
                //  userPreferencesRepository.saveSelectedFileName(selectedLanguage.code)
//                        userPreferencesRepository.saveSelectedSkillLevel(selectedLanguage.skillLevel)
                // --- THIS IS THE NEW, CRITICAL PART ---
                // 2. Tell the shared manager to load the recalled items for the NEW exam
                Timber.d("New exam selected. Reloading recalled items for key: ${selectedLanguage.code}")


            }



        }

    }

}
