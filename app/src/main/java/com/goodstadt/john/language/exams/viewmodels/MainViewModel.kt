package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.AuthRepository
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// Represents the possible states of our initial authentication
enum class AuthState {
    LOADING,
    SIGNED_IN,
    ERROR
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState.LOADING)
    val authState = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser = _currentUser.asStateFlow()

    init {
        // When the ViewModel is created (on app start), trigger the sign-in check.
       // performAnonymousSignIn()
    }

    private fun performAnonymousSignIn() {
        viewModelScope.launch {
            _authState.value = AuthState.LOADING
            val user = authRepository.signInAnonymouslyIfNeeded()
            if (user != null) {
                _currentUser.value = user
                _authState.value = AuthState.SIGNED_IN
                // Optional: Save a record to Firestore after successful sign-in
                authRepository.fsCreateUserDoc()
            } else {
                _authState.value = AuthState.ERROR
            }
        }
    }
}