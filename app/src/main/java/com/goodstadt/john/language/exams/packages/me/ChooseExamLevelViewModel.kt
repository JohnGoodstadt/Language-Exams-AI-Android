package com.goodstadt.john.language.exams.packages.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChooseExamLevelViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    // Initialize with default skill level from your LanguageConfig
    private val _selectedLevel = MutableStateFlow(LanguageConfig.defaulSkillLevel)
    val selectedLevel: StateFlow<String> = _selectedLevel.asStateFlow()

    fun onLevelSelected(level: String) {
        _selectedLevel.value = level
    }

    fun saveLevel(selectedLevel: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            userPreferencesRepository.saveSelectedSkillLevel(selectedLevel)
            // Mark onboarding complete so this sheet doesn't reappear on the next launch. Persist
            // the flavour's proper locale code (e.g. "de-DE", NOT the bare flavour "de" which the
            // TTS voice rejects); this also sets the USER_HAS_CHOSEN_ENGLISH flag MainViewModel checks.
            userPreferencesRepository.saveSelectedLanguageCode(LanguageConfig.languageCode)
            onComplete()
        }
    }
}