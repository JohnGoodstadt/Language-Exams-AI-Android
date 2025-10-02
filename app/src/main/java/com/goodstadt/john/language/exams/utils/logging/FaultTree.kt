package com.goodstadt.john.language.exams.utils.logging

import android.util.Log
import com.goodstadt.john.language.exams.data.FirestoreRepository
import com.goodstadt.john.language.exams.data.FirestoreRepository.UserError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ServerTimestamp
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale



/**
 * A custom Timber Tree for release builds that specifically listens for a custom
 * 'FAULT' priority level and writes those logs to a 'faults' collection in Firestore.
 *
 * @param firestore The Firestore instance to use for logging.
 * @param minLogPriority Only messages with this priority or higher will be processed.
 */
class FaultTree(
    private val firestore: FirebaseFirestore,
    private val minLogPriority: Int = Log.ASSERT, // ASSERT is the level for Timber.wtf()
   // private val firestoreRepository: FirestoreRepository
) : Timber.Tree() {


//    override fun log(priority: Int, tag: String?, message: String, t: Throwable?,secondaryText:String = "",area:String = "") {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // We only care about messages that meet our minimum priority level.
        if (priority < minLogPriority) {
            return
        }

        data class UserError(
            val uid: String = "",
            val text: String = "",
            val secondaryText: String = "",
            val area: String = "",
            @ServerTimestamp val timestamp: Date? = null // Use Date? and @ServerTimestamp
        )

        val currentUser = FirebaseAuth.getInstance().currentUser ?:  return
        val currentUid = currentUser.uid

        try {
            // 2. Create an instance of the UserError data class.
            //    We only need to provide the properties we have. 'timestamp' will be null.
            val errorLog = UserError(
                uid = currentUid,
                text = message,
                secondaryText = "",
                area = ""
            )

            // 3. Point to the 'user_errors' collection and create a new document.
            //    Calling .document() with no path generates a unique, random document ID.
            val docId = SimpleDateFormat("yyyy-MM-dd-HH:mm:ss.SSS", Locale.ROOT).format(Date())
            firestore.collection("faults").document(docId)
                .set(errorLog)
//                .await() // Wait for the operation to complete

            Timber.d("Successfully logged user error to Firestore.")

        } catch (e: Exception) {
            Timber.e(e, "Failed to log user error to Firestore.")
        }


        // --- THIS IS THE FIRESTORE LOGIC ---
        // Create a data map for the Firestore document
//        val faultData = hashMapOf(
//            "timestamp" to com.google.firebase.Timestamp.now(),
//            "priority" to priorityToString(priority),
//            "tag" to tag,
//            "message" to message,
//            "exception" to t?.stackTraceToString() // Include the stack trace if an exception was passed
//            // You can add more device/user info here if needed
//            // "userId" to FirebaseAuth.getInstance().currentUser?.uid,
//            // "appVersion" to BuildConfig.VERSION_NAME
//        )
//
//        // Create a unique document ID based on the current timestamp
//        val docId = SimpleDateFormat("yyyy-MM-dd-HH:mm:ss.SSS", Locale.ROOT).format(Date())
//
//        // "Fire-and-forget" write to the 'faults' collection in Firestore.
//        firestore.collection("faults").document(docId)
//            .set(faultData)
//            .addOnSuccessListener {
//                // Log locally that the remote log was successful (optional)
//                Log.i("FaultTree", "Successfully logged fault to Firestore.")
//            }
//            .addOnFailureListener { e ->
//                // Log locally that the remote log failed
//                Log.e("FaultTree", "Failed to log fault to Firestore.", e)
//            }
    }

    private fun priorityToString(priority: Int): String {
        return when (priority) {
            Log.VERBOSE -> "VERBOSE"
            Log.DEBUG -> "DEBUG"
            Log.INFO -> "INFO"
            Log.WARN -> "WARN"
            Log.ERROR -> "ERROR"
            Log.ASSERT -> "FAULT (ASSERT)"
            else -> "UNKNOWN"
        }
    }
}