package com.goodstadt.john.language.exams.screens.me

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
            onComplete()
        }
    }
}