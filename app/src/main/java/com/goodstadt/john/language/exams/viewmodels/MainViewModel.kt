package com.goodstadt.john.language.exams.viewmodels

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.AuthRepository
import com.goodstadt.john.language.exams.data.RecallingItems
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UpdateState
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.navigation.Screen
import com.goodstadt.john.language.exams.utils.AppLifecycleObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar
import javax.inject.Inject

// --- The UI State for this ViewModel is now much simpler ---
// It only holds truly global state.
data class GlobalUiState(
    val authState: AuthUiState = AuthUiState.Loading,
    val selectedVoiceName: String = "",
    val badgeCounts: Map<String, Int> = emptyMap(),
    val isPremiumUser: Boolean = false
)

// The AuthUiState can remain as it was.
sealed interface AuthUiState {
    object Loading : AuthUiState
    data class Success(val uid: String) : AuthUiState
    data class Error(val message: String) : AuthUiState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPreferencesRepository: UserPreferencesRepository, // For shared preferences
    private val recallingItemsManager: RecallingItems,
    private val ttsStatsRepository : TTSStatsRepository,
    private val appConfigRepository: AppConfigRepository,
//    private val billingRepository: BillingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GlobalUiState())
    val uiState = _uiState.asStateFlow()

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.NoUpdateNeeded)
    val updateState = _updateState.asStateFlow()

    private val lifecycleObserver = AppLifecycleObserver(
        onAppBackgrounded = {
            // This lambda will be called when onStop() is triggered.
            if (ttsStatsRepository.checkIfStatsFlushNeeded()) {
                viewModelScope.launch {
                    ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.TTSStats)
                    ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
                    ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.WORDSTATS)
                }
            }
        },
        onAppForeground = {
            if (ttsStatsRepository.checkIfStatsFlushNeeded()) {
                viewModelScope.launch {
                    ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.TTSStats)
                    ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.USER)
                    ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.WORDSTATS)
                }
            }
        }
    )

    init {
        // 1. Initialize the user session (as before).
        initializeAppSession()

        // 2. Listen for changes to shared preferences.
        viewModelScope.launch {
            userPreferencesRepository.selectedVoiceNameFlow.collect { voiceName ->
                _uiState.update { it.copy(selectedVoiceName = voiceName) }
            }
        }

//        viewModelScope.launch {
//            // This creates a permanent subscription. Whenever the BillingRepository
//            // updates the premium status, this block will run.
//            billingRepository.isPremiumUser.collect { isPremium ->
//                // Update the global UI state with the new value.
//                _uiState.update { it.copy(isPremiumUser = isPremium) }
//            }
//        }

        loadInitialRecalledItems()

        calcBadgeNumber()

        checkForAppUpdate()
    }
    fun registerLifecycleObserver(lifecycle: Lifecycle) {
        lifecycle.addObserver(lifecycleObserver)
    }

    private fun flushStatsToFirestore() {
        // Launch a coroutine to do the background work
        viewModelScope.launch {
            //statsRepository.flushPendingStats() // Assume a function like this exists
            Timber.d("flushStatsToFirestore")
        }
    }

    // Ensure the observer is removed when the ViewModel is cleared.
    override fun onCleared() {
        // This step is not strictly necessary if you add the observer to the
        // process lifecycle, but it's good practice.
        super.onCleared()
    }

    private fun calcBadgeNumber() {
        viewModelScope.launch {
            // Collect the flow of recalled items from the singleton manager.
            recallingItemsManager.items.collect { recalledItems ->
                // Every time the list of recalled items changes, this block will run.
                val todayItemsCount = recalledItems.count { item ->
                    // Use the same "isOverdue" logic from your RecallViewModel
                    val today = Calendar.getInstance()
                    val nextEventCal =
                        Calendar.getInstance().apply { timeInMillis = item.nextEventTime }
                    item.nextEventTime < today.timeInMillis ||
                            (today.get(Calendar.DAY_OF_YEAR) == nextEventCal.get(Calendar.DAY_OF_YEAR) &&
                                    today.get(Calendar.YEAR) == nextEventCal.get(Calendar.YEAR))
                }

                // Update the badge count for the "Focusing" tab (Tab 4).
                _uiState.update {
                    it.copy(
                        badgeCounts = it.badgeCounts.toMutableMap().apply {
                            this[Screen.Tab4.route] = todayItemsCount
                        }
                    )
                }
            }
        }
    }

    private fun loadInitialRecalledItems() {
        viewModelScope.launch {
            val currentExamKey = userPreferencesRepository.selectedFileNameFlow.first()
            recallingItemsManager.load(currentExamKey)
        }
    }

    private fun initializeAppSession() {

        _uiState.update { it.copy(authState = AuthUiState.Loading) }

        viewModelScope.launch {
            Timber.d("Initializing user session...")
            val result = authRepository.signInOrUpdateUser()

            result.onSuccess { user ->
                Timber.d("Session success. UID: ${user.uid}")
                _uiState.update { it.copy(authState = AuthUiState.Success(user.uid)) }
            }

            result.onFailure { exception ->
                Timber.e("Session failed ${exception.localizedMessage}")
                Timber.e("Session failed", exception)
//                _uiState.update { it.copy(authState = AuthUiState.Error(exception.message ?: "Unknown error")) }
                val errorMessage = when (exception) {
                    is java.net.UnknownHostException -> "Could not connect. Please check your internet connection."
                    else -> exception.message ?: "An unknown error occurred."
                }
                _uiState.update { it.copy(authState = AuthUiState.Error(errorMessage)) }
            }
        }
    }

    fun onRetryConnection() {
        initializeAppSession()
    }

    // --- ALL THE OLD DATA LOADING FUNCTIONS ARE GONE ---
    // loadDataForFile(), playTrack(), all the category/menu/indexMap flows...
    // They are no longer the responsibility of this ViewModel.
    // They have been moved to CategoryTabViewModel.
    private fun checkForAppUpdate() {
        viewModelScope.launch {
            _updateState.value = appConfigRepository.checkAppUpdateStatus()
        }
    }

    // A function the UI can call to dismiss the optional dialog
    fun dismissOptionalUpdate() {
        _updateState.value = UpdateState.NoUpdateNeeded
    }
}