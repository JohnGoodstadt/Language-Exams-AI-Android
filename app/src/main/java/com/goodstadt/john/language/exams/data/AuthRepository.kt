package com.goodstadt.john.language.exams.data // Or wherever your repositories live

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    /**
     * A Flow that emits the current user, or null if logged out.
     * The UI layer can collect this to react to auth state changes.
     */
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    /**
     * Signs the user in anonymously if they are not already signed in.
     * This is a suspend function, so it must be called from a coroutine.
     * @return The FirebaseUser if successful, or null on failure.
     */
    suspend fun signInAnonymouslyIfNeeded(): FirebaseUser? {
        return try {
            if (currentUser == null) {
                // Not signed in, so perform anonymous sign-in
                auth.signInAnonymously().await().user
            } else {
                // Already signed in, just return the current user
                currentUser
            }
        } catch (e: Exception) {
            // Handle exceptions like no network connection, etc.
            e.printStackTrace()
            null
        }
    }

    /**
     * Example of how to expand your "library" for Firestore.
     * Saves or updates a user record in a 'users' collection.
     */
    suspend fun saveUserRecord(user: FirebaseUser) {
        try {
            val userRecord = mapOf(
                "uid" to user.uid,
                "createdAt" to System.currentTimeMillis(),
                "isAnonymous" to user.isAnonymous
            )
            // Use the user's UID as the document ID
            firestore.collection("users").document(user.uid)
                .set(userRecord, SetOptions.merge()) // .merge() prevents overwriting existing fields
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    /**
     * The main entry point for user session handling.
     * If the user is not signed in, it performs an anonymous sign-in and creates a new user record in Firestore.
     * If the user IS already signed in, it simply updates their 'lastLoginAt' timestamp.
     *
     * @return A Result object containing the FirebaseUser on success or an Exception on failure.
     */
    suspend fun signInOrUpdateUser(): Result<FirebaseUser> {
        return try {
            val user = auth.currentUser
            if (user == null) {
                // CASE 1: NEW USER - Perform sign-in and create record
                val newUser = auth.signInAnonymously().await().user
                    ?: throw IllegalStateException("Firebase returned a null user after anonymous sign-in.")

                createUserRecord(newUser)
                Result.success(newUser)
            } else {
                // CASE 2: RETURNING USER - Just update the timestamp
                updateLastLoginTimestamp(user.uid)
                Result.success(user)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    /**
     * Creates the initial user document in Firestore.
     * Called only once when a new anonymous user is created.
     */
    private suspend fun createUserRecord(user: FirebaseUser) {
        val userRecord = mapOf(
            "uid" to user.uid,
            "isAnonymous" to true,
            "createdAt" to FieldValue.serverTimestamp(), // Use server time for consistency
            "lastLoginAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("users").document(user.uid).set(userRecord).await()
    }
    /**
     * Updates the 'lastLoginAt' field for an existing user.
     * Called every time a returning user opens the app.
     */
    private suspend fun updateLastLoginTimestamp(uid: String) {
        val timestampUpdate = mapOf(
            "lastLoginAt" to FieldValue.serverTimestamp() // Best practice: use server time
        )
        firestore.collection("users").document(uid).update(timestampUpdate).await()
    }
}