package com.goodstadt.john.language.exams.data // Or wherever your repositories live

import android.app.Application
import android.os.Build
import android.util.Log
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.models.UserFirebase
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val application: Application,
) {

    private object fb {
        const val users = "users"
        const val lastActivityDate = "lastActivityDate"
        const val activityDays = "activityDays"
        const val isAnon = "isAnon"
        const val email = "email"
        const val isEmailVerified = "isEmailVerified"
        const val languageCode = "languageCode"
        const val regionCode = "regionCode"
        const val version = "version"
    }
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
    suspend fun fsCreateUserDocObsolete(user: FirebaseUser) {
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
    // region Create Functions

    suspend fun fsCreateUserDoc(user: UserFirebase) {
        val db = Firebase.firestore
        try {
            db.collection(fb.users).document(user.UID)
                .set(user)
                .await()
        } catch (e: Exception) {
            Timber.e(e.localizedMessage, e)

        }
    }
    suspend fun fsCreateUserDoc() {

        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        val packageName = application.applicationContext.packageName

        val user = UserFirebase(id = currentUser?.uid ?: "",
            UID = currentUser?.uid ?: "",
            name = currentUser?.displayName ?: "",
            spokenName = "",
            loggedIn = true,
            isAnon = true,
            provider = currentUser?.providerId ?: "",
            providerCompany = "",
            email = currentUser?.email ?: "unknown",
            platform = "android",
            version = BuildConfig.VERSION_NAME,
            isEmailVerified = currentUser.isEmailVerified ?: false,
            lastUpdateDate = Date(),
            lastLoggedInDate = Date(),
            lastLoggedOutDate = Date(),
            lastActivityDate = Date(),
            languageCode = Locale.getDefault().language,
            regionCode = Locale.getDefault().country,
            packageName = packageName,
            deviceManufacturer = Build.MANUFACTURER,
            deviceModel = Build.MODEL,
            deviceBrand = Build.BRAND,
            deviceProduct = Build.PRODUCT,
            deviceHardware = Build.HARDWARE,
            deviceBoard = Build.BOARD,
            createdAt = Date()
        )

        fsCreateUserDoc(user)

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

                Timber.d("uid is ${newUser.uid}")
//                createUserRecord(newUser)
                fsCreateUserDoc()
                Result.success(newUser)
            } else {
                Timber.d("uid is ${user.uid}")
                // CASE 2: RETURNING USER - Just update the timestamp
                if (false && BuildConfig.DEBUG) { //Just for JG 10 July 2025
                    val exists = fsDoesUserExist()
                    if (exists == false){
                        fsCreateUserDoc()
                    }
                }else {
//                    updateLastLoginTimestamp(user.uid)
                    fsUpdateUserActivityProperty()
                    fsUpdateUserMainStats(
                        property1 = fb.languageCode,
                        value1 =  Locale.getDefault().language,
                        property2 = fb.regionCode,
                        value2 = Locale.getDefault().country,
                        property3 = fb.version,
                        value3 = BuildConfig.VERSION_NAME
                    )
                }

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
    private suspend fun createUserRecordObsolete(user: FirebaseUser) {
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
    private suspend fun updateLastLoginTimestampObsolete(uid: String) {
        val timestampUpdate = mapOf(
            "lastLoginAt" to FieldValue.serverTimestamp() // Best practice: use server time
        )
        firestore.collection("users").document(uid).update(timestampUpdate).await()
    }
    suspend fun fsDoesUserExist(): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false

        return try {
            val snapshot = FirebaseFirestore.getInstance()
                .collection(fb.users) // Replace with your actual collection name
                .document(uid)
                .get()
                .await()
            snapshot.exists()
        } catch (e: Exception) {
            // Handle the exception, e.g., log it
            Timber.e(e)
            false
        }
    }
    private fun fsUpdateUserActivityProperty() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        val date = Date()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(date)

        val db = FirebaseFirestore.getInstance()
        val itemRef = db.collection(fb.users).document(currentUser.uid)

        val updates = mapOf(
            fb.lastActivityDate to date,
            fb.activityDays to FieldValue.arrayUnion(today)
        )

        itemRef.update(updates)
            .addOnFailureListener { e ->
                Timber.e("Error updating field ${fb.lastActivityDate}: $e")
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                    applicationScope.launch {
                        fsCreateUserDoc()
                    }
                }
            }
    }

    suspend fun <T, U, V> fsUpdateUserMainStats(
        property1: String,
        value1: T,
        property2: String,
        value2: U,
        property3: String,
        value3: V
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        val isAnon = currentUser.isAnonymous
        val email = currentUser.email ?: "unknown"
        val isEmailVerified = currentUser.isEmailVerified

        val db = FirebaseFirestore.getInstance()
        val itemRef = db.collection(fb.users).document(currentUser.uid)

        val updates = mapOf(
            property1 to value1,
            property2 to value2,
            property3 to value3,
            fb.isAnon to isAnon,
            fb.email to email,
            fb.isEmailVerified to isEmailVerified
        )

        itemRef.update(updates)
            .addOnFailureListener { e ->
                // Handle failure

                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                    applicationScope.launch {
                        fsCreateUserDoc()
                    }
                }
            }
    }
}