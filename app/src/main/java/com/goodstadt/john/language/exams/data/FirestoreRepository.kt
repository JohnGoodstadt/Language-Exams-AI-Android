package com.goodstadt.john.language.exams.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreRepository  @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    object fb {
        const val global = "global"
        const val WordListControl = "WordListControl"

        const val usersPronounce = "usersPronounce"
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
        const val DontWordList_en = "DontWordList-en"
        const val January_en = "January-en"
        const val February_en = "February-en"
        const val March_en = "March-n"
        const val April_en = "April-en"
        const val May_en = "May-en"
        const val June_en = "June-en"
        const val July_en = "July-en"
        const val August_en = "August-en"
        const val September_en = "September-en"
        const val October_en = "October-en"
        const val November_en = "November-en"
        const val December_en = "December-en"
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

    fun fsUpdateUserStatsCounts(statToUpload: kotlin.collections.Map<String, Any>) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return


        val itemRef = FirebaseFirestore.getInstance().collection(fb.usersPronounce).document(currentUser.uid)

        try {
            val incrementedStats = transformToIncrements(statToUpload)
//        println(statToUpload)
//        println(incrementedStats)

            itemRef.update(incrementedStats)

        } catch (e: Exception) {
            Log.e("Firebase Library", e.localizedMessage, e)
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
        val _ = FirebaseAuth.getInstance().currentUser ?: return

        val formattedDate = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()) // e.g., 2024-04

        val firestoreUpdateFields = mutableMapOf<String, FieldValue>()
        for ((key, value) in stats) {
            val count: Long = value.toLong() // Convert Int to Long
            firestoreUpdateFields[key] = FieldValue.increment(count)
        }

        // 1. Update global month totals
        val docRef = FirebaseFirestore.getInstance().collection(fb.stats).document(formattedDate)
        docRef.update(firestoreUpdateFields as Map<String, Any>)
            .addOnFailureListener { e ->
                println("Error updating inc field fsUpdateGlobalStats (Download field): $e")
            }

        // 2. Individual user stats area for month (TODO: Implement this part if needed)
    //TODO: implement
    // fbUpdateUserGlobalStats(stats)
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
//                    Log.e("Firebase Library", "Error updating inc field: fbUpdateUserGlobalStats: ${e.localizedMessage}", e)
//                }
//            }
//    }
// endregion
}