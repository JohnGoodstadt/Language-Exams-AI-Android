package com.goodstadt.john.language.exams.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.AuthRepository
import com.goodstadt.john.language.exams.data.RecallingItems
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- The UI State for this ViewModel is now much simpler ---
// It only holds truly global state.
data class GlobalUiState(
    val authState: AuthUiState = AuthUiState.Loading,
    val selectedVoiceName: String = ""
)

// The AuthUiState can remain as it was.
sealed interface AuthUiState {
    object Loading : AuthUiState
    data class Success(val uid: String) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

@HiltViewModel
class TabsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPreferencesRepository: UserPreferencesRepository, // For shared preferences
    private val recallingItemsManager: RecallingItems
) : ViewModel() {

    private val _uiState = MutableStateFlow(GlobalUiState())
    val uiState = _uiState.asStateFlow()

    init {
        // 1. Initialize the user session (as before).
        initializeAppSession()

        // 2. Listen for changes to shared preferences.
        viewModelScope.launch {
            userPreferencesRepository.selectedVoiceNameFlow.collect { voiceName ->
                _uiState.update { it.copy(selectedVoiceName = voiceName) }
            }
        }

        loadInitialRecalledItems()
    }

    private fun loadInitialRecalledItems() {
        viewModelScope.launch {
            // Get the currently saved exam key to ensure we load the right data.
            val currentExamKey = userPreferencesRepository.selectedFileNameFlow.first()
            Log.d("TabsViewModel", "Triggering initial load of recalled items for key: '$currentExamKey'")
            // Call the load function on the singleton manager.
            recallingItemsManager.load(currentExamKey)
        }
    }

    private fun initializeAppSession() {
        viewModelScope.launch {
            Log.d("TabsViewModel", "Initializing user session...")
            val result = authRepository.signInOrUpdateUser()

            result.onSuccess { user ->
                Log.d("TabsViewModel", "Session success. UID: ${user.uid}")
                _uiState.update { it.copy(authState = AuthUiState.Success(user.uid)) }
            }

            result.onFailure { exception ->
                Log.e("TabsViewModel", "Session failed", exception)
                _uiState.update { it.copy(authState = AuthUiState.Error(exception.message ?: "Unknown error")) }
            }
        }
    }

    // --- ALL THE OLD DATA LOADING FUNCTIONS ARE GONE ---
    // loadDataForFile(), playTrack(), all the category/menu/indexMap flows...
    // They are no longer the responsibility of this ViewModel.
    // They have been moved to CategoryTabViewModel.
}