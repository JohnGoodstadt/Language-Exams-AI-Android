package com.goodstadt.john.language.exams.viewmodels


import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialResponse
import com.goodstadt.john.language.exams.data.FirestoreRepository
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import dagger.hilt.android.lifecycle.HiltViewModel
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val firestoreRepository: FirestoreRepository
) : ViewModel() {

    private val auth = firestoreRepository.getFirebaseAuth()//FirebaseAuth.getInstance()
/*
22. CLAUDE SignInViewModel bypasses Hilt DI for FirebaseAuth
File: SignInViewModel.kt line 31
FirebaseAuth.getInstance() is called directly despite FirebaseModule already providing it via Hilt. This creates a second instance and bypasses any future testing/mocking.

Fix: Inject FirebaseAuth via the constructor like your other ViewModels.
 */
    // UI State
    private val _isUserLoggedIn = MutableStateFlow(auth.currentUser?.isAnonymous == false)
    val isUserLoggedIn = _isUserLoggedIn.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    /**
     * Entry point for the new Credential Manager result
     */
    fun handleSignIn(result: GetCredentialResponse) {
        val credential = result.credential

        // Validate that the credential is a Google ID Token
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken

                // Link the token to the existing Firebase user
                linkFirebaseAccount(idToken)

            } catch (e: GoogleIdTokenParsingException) {
                Timber.e(e, "Google ID Token parsing failed")
                _errorMessage.value = "Invalid sign-in data."
            }
        } else {
            _errorMessage.value = "Unexpected login type."
        }
    }

    private fun linkFirebaseAccountOriginal(idToken: String) {
        val user = auth.currentUser ?: return
        val credential = GoogleAuthProvider.getCredential(idToken, null)

        viewModelScope.launch {
            try {
                // Link anonymous account to Google
                user.linkWithCredential(credential).await()

                // Update Firestore via Repository
                firestoreRepository.updateUserInFirestore(user)

                // Update UI State
                _isUserLoggedIn.value = true
                _errorMessage.value = null
                Timber.i("Successfully linked account. UID: ${user.uid}")

            } catch (e: Exception) {
                Timber.e(e, "Link failed")
                // Specific check: If account exists already, you might need to sign in instead of link
                _errorMessage.value = "Link failed: ${e.localizedMessage}"
            }
        }
    }
    private fun linkFirebaseAccount(idToken: String) {
        val user = auth.currentUser ?: return
        val credential = GoogleAuthProvider.getCredential(idToken, null)

        viewModelScope.launch {
            try {
                // 1. Try to Link (Keep current progress)
                user.linkWithCredential(credential).await()
                Timber.i("Account Linked successfully.")

                // Update Firestore for the current UID
                firestoreRepository.updateUserInFirestore(user)
                _isUserLoggedIn.value = true

            } catch (e: FirebaseAuthUserCollisionException) {
                // 2. COLLISION: The Google account is already linked to a DIFFERENT UID
                Timber.w("Collision detected. Google account already belongs to another user. Switching to Sign-In...")

                try {
                    // Perform a standard Sign-In instead of a Link
                    val result = auth.signInWithCredential(credential).await()
                    val existingUser = result.user

                    if (existingUser != null) {
                        // Update Firestore for the existing (old) UID
                        firestoreRepository.updateUserInFirestore(existingUser)
                        _isUserLoggedIn.value = true
                        Timber.i("Signed into existing account: ${existingUser.uid}")
                    }
                } catch (signInError: Exception) {
                    _errorMessage.value = "Sign-in failed: ${signInError.localizedMessage}"
                }

            } catch (e: Exception) {
                // General failure
                _errorMessage.value = "Auth failed: ${e.localizedMessage}"
            }
        }
    }
    fun onSignOutClicked() {
        auth.signOut()
        _isUserLoggedIn.value = false

        // Immediate Anonymous Login to preserve app functionality
        auth.signInAnonymously().addOnSuccessListener {
            Timber.i("Signed in anonymously after logout")
        }
    }
}