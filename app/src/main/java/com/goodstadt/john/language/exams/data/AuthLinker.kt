package com.goodstadt.john.language.exams.data

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import timber.log.Timber // Assuming you use Timber, or use Log.d

class AuthLinker(private val activity: Activity) {

    private val auth = FirebaseAuth.getInstance()

    // 1. Configure Google Sign In
    private val googleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken("YOUR_WEB_CLIENT_ID_FROM_FIREBASE_CONSOLE") // Get this from Firebase Console
        .requestEmail()
        .build()

    private val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(activity, googleSignInOptions)

    // 2. Get the Intent to start the Google Activity
    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    // 3. Process the Result from Google and Link to Firebase
    fun handleSignInResult(data: Intent?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            linkGoogleCredentialToCurrentUser(account, onSuccess, onError)
        } catch (e: ApiException) {
            onError("Google Sign in failed: ${e.statusCode}")
        }
    }

    private fun linkGoogleCredentialToCurrentUser(
        googleAccount: GoogleSignInAccount,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val currentUser = auth.currentUser

        if (currentUser == null) {
            onError("No anonymous user found to link.")
            return
        }

        // Create the Credential
        val credential = GoogleAuthProvider.getCredential(googleAccount.idToken, null)

        // CRITICAL STEP: Use linkWithCredential, NOT signInWithCredential
        currentUser.linkWithCredential(credential)
            .addOnSuccessListener { authResult ->
                Timber.i("Success! User upgraded. UID remains: ${authResult.user?.uid}")
                onSuccess()
            }
            .addOnFailureListener { e ->
                // Handle "Credential already in use" error specifically
                // This happens if this Google Account is ALREADY used by another user in your app
                Timber.e(e, "Link failed")
                onError(e.message ?: "Linking failed")
            }
    }

    fun signOut(onComplete: () -> Unit) {
        // 1. Sign out of Firebase
        auth.signOut()

        // 2. Sign out of Google (So they can pick a different account next time)
        googleSignInClient.signOut().addOnCompleteListener {
            onComplete()
        }
    }
}