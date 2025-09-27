package com.goodstadt.john.language.exams.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await
import sanitizedForFirestore
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    //see also TTSStatsRepository
    object fb {
        const val global = "global"
        const val WordListControl = "WordListControl"

        const val users = "users"
        const val categories = "categories"
        const val subcategories = "subcategories"
        const val permissions = "permissions"
        const val isAnon = "isAnon"
        const val email = "email"
        const val isEmailVerified = "isEmailVerified"
        const val sounds = "sounds"
        const val tabs = "tabs"
        const val sections = "sections"
        const val scenario = "scenario"
        const val wordsAndSentences = "wordsAndSentences"
        const val languageCode = "languageCode"
        const val regionCode = "regionCode"
        const val version = "version"
        const val lastActivityDate = "lastActivityDate"
        const val premiumUser = "premiumUser"
        const val premiumDate = "premiumDate"
        const val premiumToken = "premiumToken"
        const val premiumError = "premiumError"


        const val activityDays = "activityDays"
        const val updatedDate = "updatedDate"
        const val uploadDate = "uploadDate"
        const val sheetname = "sheetname"
        const val location = "location"
        const val fileformat = "fileformat"
        const val voicename = "voicename"
        const val googlevoiceprefix = "googlevoiceprefix"
        const val name = "name"
        const val explanation = "explanation"
        const val id = "id"
        const val native = "native"
        const val nativename = "nativename"
        const val words = "words"
        const val sentenceCount = "sentenceCount"
        const val countTimestamp = "countTimestamp"
        const val sortorder = "sortorder"
        const val quiz = "quiz"
        const val googleVoices = "googleVoices"


        const val en = "en"
        const val A1WordList_en = "A1WordList-en" //NOTE: "_" vs "-"
        const val A2WordList_en = "A2WordList-en"
        const val B1WordList_en = "B1WordList-en"
        const val B2WordList_en = "B2WordList-en"
        const val freeWordList_en = "freeWordList-en"
        const val USvsBritishWordList_en = "USvsBritishWordList-en"
        const val bookmarks = "bookmarks"
        const val bookmarked = "bookmarked"
        const val timestamp = "timestamp"

        const val shownAppUpgradeSheetActioned = "shownAppUpgradeSheetActioned"
        const val shownAppUpgradeSheet = "shownAppUpgradeSheet"

        const val shownForceAppUpgradeSheetActioned = "shownForceAppUpgradeSheetActioned"
        const val shownForceAppUpgradeSheet = "shownForceAppUpgradeSheet"
        const val rateLimitDailyViewCount = "rateLimitDailyViewCount"
        const val rateLimitHourlyViewCount = "rateLimitHourlyViewCount"
        const val appStoreViewCount = "appStoreViewCount"
        const val limits = "limits"
        const val feedback = "feedback"
        const val shadowingDownloadCount = "shadowingDownloadCount"


        const val stats = "stats"
        const val TTSTotal = "TTSTotal"
        const val TTSChars = "TTSChars"
        const val TTSStats = "TTSStats"


        const val TTSPremium = "TTSPremium"
        const val TTSStudio = "TTSStudio"
        const val TTSStandard = "TTSStandard"
        const val TTSQuality = "TTSQuality"
        const val TTSOther = "TTSOther"
        const val TTSCacheHit = "TTSCacheHit"
        const val TRCalls = "TRCalls"
        const val TRChars = "TRChars"
        const val FSDownloadCharCalls = "FSDownloadCharCalls"
        const val FSDownloadCharCount = "FSDownloadCharCount"

        //    const val freeWordList_en = "freeWordList_en"
//    const val A1WordList_en = "A1WordList_en"
//    const val A2WordList_en = "A2WordList_en"
//    const val B1WordList_en = "B1WordList_en"
//    const val B2WordList_en = "B2WordList_en"
//    const val USvsBritishWordList_en = "USvsBritishWordList_en"
//        const val DontWordList_en = "DontWordList-en"
//        const val January_en = "January-en"
//        const val February_en = "February-en"
//        const val March_en = "March-n"
//        const val April_en = "April-en"
//        const val May_en = "May-en"
//        const val June_en = "June-en"
//        const val July_en = "July-en"
//        const val August_en = "August-en"
//        const val September_en = "September-en"
//        const val October_en = "October-en"
//        const val November_en = "November-en"
//        const val December_en = "December-en"
        //
//    const val downloadedCEFRA1 = "downloadedCEFRA1"
//    const val downloadedCEFRA2 = "downloadedCEFRA2"
//    const val downloadedCEFRB1 = "downloadedCEFRB1"
//    const val downloadedCEFRB2 = "downloadedCEFRB2"
        const val downloadedTOEFLElementary = "downloadedTOEFLElementary"
        const val downloadedTOEFLIntermediate = "downloadedTOEFLIntermediate"
        const val downloadedTOEFLUpperIntermediate = "downloadedTOEFLUpperIntermediate"
        const val downloadedIELTSBeginner = "downloadedIELTSBeginner"
        const val downloadedIELTSElementary = "downloadedIELTSElementary"
        const val downloadedIELTSIntermediate = "downloadedIELTSIntermediate"
        const val downloadedIELTSUpperIntermediate = "downloadedIELTSUpperIntermediate"
        const val downloadedESOLA2 = "downloadedESOLA2"
        const val downloadedESOLB1 = "downloadedESOLB1"
        const val downloadedESOLB2 = "downloadedESOLB2"
        const val downloadedFree = "downloadedFree"

        const val A1WordList = "A1WordList"
        const val A2WordList = "A2WordList"
        const val B1WordList = "B1WordList"
        const val B2WordList = "B2WordList"
        const val freeWordList = "freeWordList"
    }

    //region Non Async Functions
    fun isUserFullyLoggedIn(): Boolean {
        return isUserLoggedIn() && isUserAnonymous() == false
    }

    fun isUserLoggedIn(): Boolean {
        val auth = FirebaseAuth.getInstance()
        return auth.currentUser != null
    }

    fun isUserNotLoggedIn(): Boolean {
        return !isUserLoggedIn()
    }

    fun isUserAnonymous(): Boolean {
        return FirebaseAuth.getInstance().currentUser?.isAnonymous ?: true
    }

    fun firebaseUid(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }

    fun firebaseSignOutOfGoogle() {
        FirebaseAuth.getInstance().signOut()
    }

//endregion

    fun fsUpdateUserGoogleVoices(voice: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        val date = Date()
        val db = FirebaseFirestore.getInstance()
        val itemRef = db.collection(fb.users).document(currentUser.uid)

        val updates = mapOf(
            fb.lastActivityDate to date,
            fb.googleVoices to FieldValue.arrayUnion(voice)
        )

        itemRef.update(updates)
            .addOnFailureListener { e ->
                Timber.e("Error updating field ${fb.lastActivityDate}: $e")
            }
    }

    fun fsUpdateUserStatsCounts(statToUpload: kotlin.collections.Map<String, Any>) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return


        val itemRef = FirebaseFirestore.getInstance().collection(fb.users).document(currentUser.uid)

        try {
            val incrementedStats = transformToIncrements(statToUpload)
//        Timber.e(statToUpload)
//        Timber.e(incrementedStats)

            itemRef.update(incrementedStats)

        } catch (e: Exception) {
            Timber.e(e.localizedMessage, e)
        }
    }

    fun fsIncUserProperty(property: String, value: Int = 1) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

       // val formattedDate = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

        val itemRef = FirebaseFirestore.getInstance()
            .collection(fb.users)
            .document(currentUser.uid)

        try {
            itemRef.update(property, FieldValue.increment(1))
        } catch (e: Exception) {
            Timber.e(e.localizedMessage)
        }
    }

    fun fbUpdateUserProperty(property: String, value: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        val date = Date()

        val itemRef = FirebaseFirestore.getInstance()
            .collection(fb.users)
            .document(currentUser.uid)
        val updates = mapOf(
            fb.lastActivityDate to date,
            property to value
        )

        itemRef.update(updates)
            .addOnFailureListener { e ->
                println("Error updating field ${property}: $e")
            }
    }

    fun fbUpdateUsePurchasedProperty(purchaseToken:String,orderId:String,productId:String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        val date = Date()

        val itemRef = FirebaseFirestore.getInstance()
            .collection(fb.users)
            .document(currentUser.uid)
        val updates = mapOf(
            fb.lastActivityDate to date,
            fb.premiumUser to true,
            fb.premiumDate to date,
            fb.premiumToken to purchaseToken,
        )

        itemRef.update(updates)
            .addOnFailureListener { e ->
                println("Error updating fbUpdateUsePurchasedProperty(): $e")
            }
    }
    fun fbUpdateUseFailedPurchasedProperty(erormMssage:String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        val date = Date()

        val itemRef = FirebaseFirestore.getInstance()
            .collection(fb.users)
            .document(currentUser.uid)
        val updates = mapOf(
            fb.lastActivityDate to date,
            fb.premiumDate to date,
            fb.premiumError to erormMssage,
        )

        itemRef.update(updates)
            .addOnFailureListener { e ->
                println("Error updating fbUpdateUsePurchasedProperty(): $e")
            }
    }

    private fun transformToIncrements(originalMap: Map<String, Any>): Map<String, Any> {
        val transformedMap = mutableMapOf<String, Any>()

        // Iterate over each entry
        for ((key, value) in originalMap) {
            transformedMap[key] = when (value) {
                is Int -> FieldValue.increment(value.toLong())  // Increment integer fields
                else -> value                                   // Pass strings and other types unchanged
            }
        }

        return transformedMap
    }

    fun fsUpdateGlobalStats(stats: Map<String, Int>) {
        FirebaseAuth.getInstance().currentUser ?: return

        val formattedDate =
            SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()) // e.g., 2024-04

        val firestoreUpdateFields = mutableMapOf<String, FieldValue>()
        for ((key, value) in stats) {
            val count: Long = value.toLong() // Convert Int to Long
            firestoreUpdateFields[key] = FieldValue.increment(count)
        }

        // 1. Update global month totals
        val docRef = FirebaseFirestore.getInstance().collection(fb.stats).document(formattedDate)
        docRef.update(firestoreUpdateFields as Map<String, Any>)
            .addOnFailureListener { e ->
                Timber.e("Error updating inc field fsUpdateGlobalStats (Download field): $e")
            }

        // 2. Individual user stats area for month (TODO: Implement this part if needed)
        //TODO: implement
        // fbUpdateUserGlobalStats(stats)
    }

    fun fsUpdateWordHistoryIncCounts(
        currentExamName: String,
        stats: Map<String, Any>
    ) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (stats.isEmpty()) return

        val db = FirebaseFirestore.getInstance()


        val docRef = firestore.collection(fb.users).document(uid)
            .collection(fb.words).document(currentExamName)

        val updates = mutableMapOf<String, Any>()

        for ((rawKey, rawValue) in stats) {
            val key = rawKey.sanitizedForFirestore()

            when (rawValue) {
                is Int -> {
                    updates[key] = FieldValue.increment(rawValue.toLong())
                }

                is Long -> {
                    updates[key] = FieldValue.increment(rawValue)
                }

                is Double -> {
                    updates[key] = FieldValue.increment(rawValue)
                }

                is Float -> {
                    updates[key] = FieldValue.increment(rawValue.toDouble())
                }
                // ignore non-numeric types
            }
        }

        if (updates.isEmpty()) return


        docRef.update(updates)
            .addOnFailureListener { e ->
                Timber.e("Error updating field ${fb.lastActivityDate}: $e")
                val fxe = e as? FirebaseFirestoreException
                if (fxe?.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                    // No doc yet — create it with initial values
                    fsCreateUserWordStatsDoc(currentExamName, stats)
                } else {
                    Timber.e("Error updating inc fields for '$currentExamName': ${e.message}", e)
                }
            }

    }

    /**
     * Creates or overwrites a document containing word statistics for a specific user and exam.
     * It takes a map of stats, sanitizes the keys to be Firestore-safe, and converts all
     * numeric values to Longs before writing.
     *
     * @param currentExamName The name of the exam, which will be the document ID.
     * @param stats A map where the key is the word/field and the value is a numeric count.
     * @return A [Result] indicating success or failure.
     */
    fun fsCreateUserWordStatsDoc(
        currentExamName: String,
        stats: Map<String, Any>
    ): Result<Unit> {

        // 1. Get the current user's ID. Fail early if not logged in.
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return Result.success(Unit)

        // 2. Guard clause: If there's nothing to write, succeed immediately.
        if (stats.isEmpty()) {
            return Result.success(Unit)
        }

        return try {
            // 3. Point to the specific document in the subcollection:
            //    users/{userId}/words/{examName}
            val docRef = firestore.collection(fb.users).document(currentUser.uid)
                .collection(fb.words).document(currentExamName)

            // 4. Sanitize and transform the input map into a Firestore-safe map.
            //    This is the core logic, made clean with Kotlin's collection functions.
            val firestoreUpdateFields = stats.mapKeys { (key, _) ->
                key.sanitizedForFirestore() // Sanitize each key
            }.mapValues { (_, value) ->
                // Convert the value to a Long, handling both Int and Double.
                when (value) {
                    is Number -> value.toLong() // toLong() works for Int, Double, Float, etc.
                    else -> 0L // Default to 0 if the value is not a number
                }
            }

            // 5. Perform the database write using .set().
            //    This will create the document if it doesn't exist or completely
            //    overwrite it if it does. Use SetOptions.merge() for a non-destructive update.
            docRef.set(firestoreUpdateFields)
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e("Failed to create word stats for '$currentExamName'", e)
            Result.failure(e)
        }
    }

    fun fsUpdateUserParagraph(paragraph: String,model:String,skilllevel:String = "") {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        val finalParagraph = paragraph.take(900) //fs limit 1000

        data class ParagraphStat(
            val UID: String,
            val paragraph: String,
            val model: String,
            val skill: String,
            val createdAt: Date
        )

        val paragraphStat = ParagraphStat(currentUser.uid,finalParagraph,model,skilllevel,Date())


        try {
            FirebaseFirestore.getInstance().collection(fb.users).document(currentUser.uid).collection("paragraphs").document() //uniqueID
                .set(paragraphStat)
        } catch (e: Exception) {
            Timber.e(e.localizedMessage, e)
        }
    }

//    fun fbUpdateUserGlobalStats(stats: Map<String, Int>) {
//        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
//
//        val formattedDate = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()) // e.g., 2024-04
//
//        val firestoreUpdateFields = mutableMapOf<String, FieldValue>()
//        for ((key, value) in stats) {
//            val count: Long = value.toLong() // Convert Int to Long
//            firestoreUpdateFields[key] = FieldValue.increment(count)
//        }
//
//        // totals for this user for month
//        val docRef = FirebaseFirestore.getInstance().collection(fb.stats).document(formattedDate).collection(fb.usersPronounce).document(currentUser.uid)
//        docRef.update(firestoreUpdateFields as Map<String, Any>)
//            .addOnFailureListener { e ->
//                //TODO: could plus in real figures. here we miss the first creation
//                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
//                    fsCreateUserTTSStatsDoc(
//                        0, 0, 0, 0, 0,
//                        0, 0, 0, 0, 0
//                    )
//                    // NOTE: can be loop fbUpdateUserStatsPropertyCount(property)
//                } else {
//                    Timber.e("Error updating inc field: fbUpdateUserGlobalStats: ${e.localizedMessage}", e)
//                }
//            }
//    }
// endregion
}