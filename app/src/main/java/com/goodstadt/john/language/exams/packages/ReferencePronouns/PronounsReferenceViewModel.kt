package com.goodstadt.john.language.exams.packages.ReferencePronouns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PronounReferenceSection {
    PERSONAL,
    DEMONSTRATIVES,
}

data class PronounsReferenceUiState(
    val loading: Boolean = true,
    val sheet: PronounsReferenceSheet? = null,
    val selectedSection: PronounReferenceSection = PronounReferenceSection.PERSONAL,
    val selectedPatternId: String? = null,
    val selectedDemonstrativeSetId: String? = null,
    val selectedFormGroup: String = "",
    val errorMessage: String? = null,
)

class PronounsReferenceViewModel(
    private val repository: PronounsReferenceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PronounsReferenceUiState())
    val uiState: StateFlow<PronounsReferenceUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            runCatching { repository.load() }
                .onSuccess { sheet ->
                    val content = sheet.data.single()
                    _uiState.update {
                        it.copy(
                            loading = false,
                            sheet = sheet,
                            selectedPatternId = content.personalPronouns.firstOrNull()?.id,
                            selectedDemonstrativeSetId = content.demonstratives.sets.firstOrNull()?.id,
                            selectedFormGroup = content.demonstratives.formGroupOrder.firstOrNull()
                                .orEmpty(),
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            errorMessage = error.message ?: "Unable to load pronouns",
                        )
                    }
                }
        }
    }

    fun selectSection(section: PronounReferenceSection) {
        _uiState.update { it.copy(selectedSection = section) }
    }

    fun selectPattern(id: String) {
        _uiState.update { it.copy(selectedPatternId = id) }
    }

    fun selectDemonstrativeSet(id: String) {
        _uiState.update { it.copy(selectedDemonstrativeSetId = id) }
    }

    fun selectFormGroup(formGroup: String) {
        _uiState.update { it.copy(selectedFormGroup = formGroup) }
    }

    class Factory(
        private val repository: PronounsReferenceRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PronounsReferenceViewModel::class.java))
            return PronounsReferenceViewModel(repository) as T
        }
    }
}
