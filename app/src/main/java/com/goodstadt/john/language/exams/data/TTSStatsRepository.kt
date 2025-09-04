package com.goodstadt.john.language.exams.data

import android.content.Context
import android.content.SharedPreferences
import android.icu.util.Calendar
import android.util.Log
import com.goodstadt.john.language.exams.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb.TRCalls
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb.TRChars
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb.TTSCacheHit
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb.TTSChars
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb.TTSOther
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb.TTSPremium
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb.TTSQuality
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb.TTSStandard
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb.TTSStats
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb.TTSStudio
import com.goodstadt.john.language.exams.models.Category
import com.goodstadt.john.language.exams.models.Sentence
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

@Singleton
class TTSStatsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
//    private val firestore: FirebaseFirestore, //if direct call
    private val firestoreRepository:FirestoreRepository
) {
    private val PREFS_NAME = "pronounceDates"
    private val DATE_FORMAT = "yyyy-MM-dd" // ISO 8601 format
    private val dateFormatter = SimpleDateFormat(DATE_FORMAT, java.util.Locale.getDefault())
    private val DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss" // ISO 8601 format
    private val dateTimeFormatter =
        SimpleDateFormat(DATE_TIME_FORMAT, java.util.Locale.getDefault())
    private val LAST_FLUSH_DATE_KEY = "StatsManagerLastFlushDate"
    private val LAST_GLOBAL_UPDATE_DATE_KEY = "LastGlobalUpdateCheckDate"
    private val LAST_CHECK_TIMER_KEY = "last_check_time"
    private val LAST_WORD_LIST_DOWNLOAD_DATE_KEY = "LastWordListDownloadDate"
    private val LAST_CHECK_APP_UPGRADE_DATE_KEY = "LastCheckAppUpgradeDate"
    private val LAST_CHECK_LOGIN_DATE_KEY = "LastCheckLoginDate"

    // ---- Public, cached stats (like Swift's private(set) var) ----
    @Volatile
    var progressStats: ProgressStats = ProgressStats(size = 0, completed = 0)
        private set

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // --- 1. The Augmented Enum Class ---
// We add a constructor and a 'val' property to the enum.
// Each enum constant now holds its associated cost per character.
    enum class GoogleTTSChargingLevels(val costPerCharacter: Double) {
        PREMIUM(0.000030),
        STUDIO(0.000160),
        STANDARD(0.000004),
        QUALITY(0.000016),
        OTHER(0.000060)
    }

    fun calculateTTSCallCost(characterCount: Int, googleVoice: String): Double {
        // Multiply character count by the per-character cost
        val chargingLevel = googleVoiceToChargingLevel(googleVoice)
        return characterCount.toDouble() * chargingLevel.costPerCharacter
    }
    private fun googleVoiceToChargingLevel(googleVoice: String): GoogleTTSChargingLevels {
        return when {
            googleVoice.lowercase().contains("standard") -> GoogleTTSChargingLevels.STANDARD
            googleVoice.lowercase().contains("neural2") ||
                    googleVoice.lowercase().contains("polyglot") ||
                    googleVoice.lowercase().contains("wavenet") -> GoogleTTSChargingLevels.QUALITY

            googleVoice.lowercase().contains("news") -> GoogleTTSChargingLevels.PREMIUM
            googleVoice.lowercase().contains("studio") -> GoogleTTSChargingLevels.STUDIO
            googleVoice.lowercase().contains("chirp") -> GoogleTTSChargingLevels.PREMIUM
            else -> GoogleTTSChargingLevels.OTHER
        }
    }

    // Enum for category separation
    enum class fsDOC(val docName: String) {
        TRANSLATION("stats_translation"), //Translate sentence stats
        TTSStats("TTSStats"),  //Text to Speech usage
        USER("users") //stats to go to User table/Doc
    }

    //see also FirestoreRepository.kt
    // Stat names
    companion object {
        const val MP3PlayedCount = "mp3PlayedCount"
        const val OpenAITotalTokenCount = "openAITotalTokenCount"
        const val OpenAICallCount = "openAICallCount"
        const val OpenAIEstCostUSD = "openAIEstCostUSD"
        const val GeminiTotalTokenCount = "geminiTotalTokenCount"
        const val GeminiCallCount = "geminiCallCount"
        const val GeminiEstCostUSD = "geminiEstCostUSD"


        const val TTSAPIEstCostUSD = "ttsAPIEstCostUSD"
        const val TTSTotalCharCount = "ttsTotalCharCount"
        const val TTSAPICallCount = "ttsAPICallCount"
        const val currentGoogleVoiceName = "currentGoogleVoiceName"
        const val llmModel_ = "llmModel_"

        const val TTSCurrentTokenCount = "ttsCurrentTokenCount"
        const val TTSTotalTokenCount = "ttsTotalTokenCount"

        const val statProgressCompleted = "statProgress__Completed" // '__" replaced with Exam A1,A2,B1,B2
        const val statProgressTotal = "statProgress__Total"




        const val TR_CHARS = "TRChars"
        const val TR_CALLS = "TRCalls"
        const val TTS_OTHER = "TTSOther"
        const val TTS_QUALITY = "TTSQuality"
        const val TTS_CACHE_HIT = "TTSCacheHit"
        const val QUIZ_TAKEN = "quizTaken"
        const val QUIZ_COMPLETE = "quizComplete"
        const val QUIZ_COMPLETE_COUNT = "quizCompleteCount"
        const val QUIZ_QUESTION_FAIL_COUNT = "quizQuestionFailCount"
        const val QUIZ_QUESTION_SUCCESS_COUNT = "quizQuestionSuccessCount"
        const val RECORD_COUNT = "recordCount"
//        const val TTSAPIEstCostUSD = "ttsAPIEstCostUSD"

        const val FSDownloadCharCount = "FSDownloadCharCount"
        const val FSDownloadCharCalls = "FSDownloadCharCalls"
        const val FSDownloadSheetname = "FSDownloadSheetname"

        const val viewSoundsCount = "viewSoundsCount"
        const val viewWordsCount = "viewWordsCount"
        const val viewSentencesCount = "viewSentencesCount"
        const val viewLOTMCount = "viewLOTMCount"
        const val viewQuizCount = "viewQuizCount"
        const val viewSpeakCount = "viewSpeakCount"
        const val viewShadowingCount = "viewShadowingCount"
        const val viewUSvsBRCount = "viewUSvsBRCount"
        const val viewDontCount = "viewDontCount"
        const val viewSettingsCount = "viewSettingsCount"
        const val viewFindCount = "viewFindCount"
        const val viewBookmarksCount = "viewBookmarksCount"
        const val viewMyWordsCount = "viewMyWordsCount"
        const val viewTheirWordsCount = "viewTheirWordsCount"
        const val viewTryOutCount = "viewTryOutCount"
        const val viewPracticeCount = "viewPracticeCount"

        //const val rateLimitDailyViewCount = "statProgress__Total"
    }


    // Get SharedPreferences for a given category
    private fun getPrefs(fsDOC: fsDOC): SharedPreferences {
        return context.getSharedPreferences(fsDOC.docName, Context.MODE_PRIVATE)
    }

    fun getLastFlushDate99(): String {
        val prefs = getPrefs(fsDOC.USER)
        val lastFlushDate = prefs.getString(LAST_FLUSH_DATE_KEY, "")

        return lastFlushDate.toString()

    }

    /**
     * Saves a Date to SharedPreferences.
     *
     * @param key The key under which to store the date.
     * @param date The Date object to store.
     * @return True if the date was saved successfully, false otherwise.
     */
    fun updateLastFlushDate(date: Date): Boolean {
        return try {
            val dateString = dateFormatter.format(date)
            sharedPreferences.edit().putString(LAST_FLUSH_DATE_KEY, dateString).apply()
            true
        } catch (e: Exception) {
            Timber.e("Error saving date for key '$LAST_FLUSH_DATE_KEY': ${e.localizedMessage}")
            false
        }
    }

    //if any word list is updated this 1 field will be changed
    fun getLastGlobalUpdateCheckDate(): Date? {
        return try {
            val dateString = sharedPreferences.getString(LAST_GLOBAL_UPDATE_DATE_KEY, null)
            if (dateString != null) {
                dateFormatter.parse(dateString)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e("Error retrieving date for key '$LAST_GLOBAL_UPDATE_DATE_KEY': ${e.localizedMessage}")
            null
        }
    }

    /**
     * Retrieves a Date from SharedPreferences.
     *
     * @param key The key under which the date is stored.
     * @return The Date object if found, null otherwise.
     */
    fun getLastFlushDate(): Date? {
        return try {
            val dateString = sharedPreferences.getString(LAST_FLUSH_DATE_KEY, null)
            if (dateString != null) {
                dateFormatter.parse(dateString)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e("Error retrieving date for key '$LAST_FLUSH_DATE_KEY': ${e.localizedMessage}")
            null
        }
    }

    fun updateLastFlushDate99(date: Date) {
        try {
            val dateString = dateFormatter.format(date)

            val prefs = getPrefs(fsDOC.USER)
            prefs.edit().putString(LAST_FLUSH_DATE_KEY, dateString).apply()
        } catch (e: Exception) {
            Timber.e(e.localizedMessage)
        }
    }


    // Increment stat within a specific category
    fun inc(category: fsDOC, statName: String, value: Int = 1) {
        val prefs = getPrefs(category)
        val current = prefs.getInt(statName, 0)
        prefs.edit().putInt(statName, current + value).apply()
    }
    fun incDouble(category: fsDOC, statName: String, value: Double = 0.0) {
        val prefs = getPrefs(category)
        val current = prefs.getFloat(statName, 0f).toDouble()
        prefs.edit().putFloat(statName, (current + value).toFloat()).apply()
    }
    fun incSheet(category: fsDOC, sheetname: String, value: Int = 1) {
        val prefs = getPrefs(category)
        val current = prefs.getInt(sheetname, 0)
        prefs.edit().putInt(sheetname, current + value).apply()
    }

    fun update(category: fsDOC, statName: String, value: String) {
        val prefs = getPrefs(category)
        prefs.edit().putString(statName, value).apply()
    }
    fun update(category: fsDOC, statName: String, value: Int) {
        val prefs = getPrefs(category)
        prefs.edit().putInt(statName, value).apply()
    }

    // Retrieve all stats in a specific category
    fun getAllStats(filestoreDoc: fsDOC): Map<String, Any> {
        val prefs = getPrefs(filestoreDoc)
        return prefs.all.filterValues { it is Any }
            .mapValues { it.value as Any }
    }

    // Clear stats in a specific category
    fun clearStats(fsDOC: fsDOC) {
        getPrefs(fsDOC).edit().clear().apply()
    }

    fun flushStats(doc: fsDOC) {

        when (doc) {
            fsDOC.USER -> flushUserStatsToFirebase()
            fsDOC.TRANSLATION -> Timber.d("TODO: flush translation stats")
            fsDOC.TTSStats -> flushTTSStatsToFirebase()
        }
    }

    fun flushUserStatsToFirebase() {

        val stats = getAllStats(fsDOC.USER)
//        val statsPlus1: Map<String, Any> = stats.plus(fb.lastActivityDate to Date()).toMap()

        if (stats.isNotEmpty()) {
            firestoreRepository.fsUpdateUserStatsCounts(stats)
            clearStats(fsDOC.USER)
        }
    }



    fun flushTTSStatsToFirebase() {
        val stats = getAllStats(fsDOC.TTSStats)

        if (!stats.isEmpty()) {
            val statsDictionary = HashMap<String, Int>()

            statsDictionary[TTSStats] = stats[TTSStats] as? Int ?: 0
            statsDictionary[TTSChars] = stats[TTSChars] as? Int ?: 0
            statsDictionary[TTSPremium] = stats[TTSPremium] as? Int ?: 0
            statsDictionary[TTSStudio] = stats[TTSStudio] as? Int ?: 0
            statsDictionary[TTSStandard] = stats[TTSStandard] as? Int ?: 0
            statsDictionary[TTSQuality] = stats[TTSQuality] as? Int ?: 0
            statsDictionary[TTSOther] = stats[TTSOther] as? Int ?: 0
            statsDictionary[FirestoreRepository.fb.TTSCacheHit] = stats[FirestoreRepository.fb.TTSCacheHit] as? Int ?: 0
            statsDictionary[FirestoreRepository.fb.TRCalls] = stats[FirestoreRepository.fb.TRCalls] as? Int ?: 0
            statsDictionary[FirestoreRepository.fb.TRChars] = stats[FirestoreRepository.fb.TRChars] as? Int ?: 0
            statsDictionary[FirestoreRepository.fb.FSDownloadCharCalls] = stats[FirestoreRepository.fb.FSDownloadCharCalls] as? Int ?: 0
            statsDictionary[FirestoreRepository.fb.FSDownloadCharCount] = stats[FirestoreRepository.fb.FSDownloadCharCount] as? Int ?: 0
            statsDictionary[FirestoreRepository.fb.freeWordList_en] = stats[FirestoreRepository.fb.freeWordList_en] as? Int ?: 0
            statsDictionary[FirestoreRepository.fb.A1WordList_en] = stats[FirestoreRepository.fb.A1WordList_en] as? Int ?: 0
            statsDictionary[FirestoreRepository.fb.A2WordList_en] = stats[FirestoreRepository.fb.A2WordList_en] as? Int ?: 0
            statsDictionary[FirestoreRepository.fb.B1WordList_en] = stats[FirestoreRepository.fb.B1WordList_en] as? Int ?: 0
            statsDictionary[FirestoreRepository.fb.B2WordList_en] = stats[FirestoreRepository.fb.B2WordList_en] as? Int ?: 0
            statsDictionary[FirestoreRepository.fb.USvsBritishWordList_en] = stats[FirestoreRepository.fb.USvsBritishWordList_en] as? Int ?: 0
//            statsDictionary[FirestoreRepository.fb.DontWordList_en] = stats[FirestoreRepository.fb.DontWordList_en] as? Int ?: 0
//            statsDictionary[FirestoreRepository.fb.January_en] = stats[FirestoreRepository.fb.January_en] as? Int ?: 0
//            statsDictionary[FirestoreRepository.fb.February_en] = stats[FirestoreRepository.fb.February_en] as? Int ?: 0
//            statsDictionary[FirestoreRepository.fb.March_en] = stats[FirestoreRepository.fb.March_en] as? Int ?: 0
//            statsDictionary[FirestoreRepository.fb.April_en] = stats[FirestoreRepository.fb.April_en] as? Int ?: 0
//            statsDictionary[FirestoreRepository.fb.May_en] = stats[FirestoreRepository.fb.May_en] as? Int ?: 0
//            statsDictionary[FirestoreRepository.fb.June_en] = stats[FirestoreRepository.fb.June_en] as? Int ?: 0
//            statsDictionary[FirestoreRepository.fb.July_en] = stats[FirestoreRepository.fb.July_en] as? Int ?: 0
//            statsDictionary[FirestoreRepository.fb.August_en] = stats[FirestoreRepository.fb.August_en] as? Int ?: 0
//            statsDictionary[FirestoreRepository.fb.September_en] = stats[FirestoreRepository.fb.September_en] as? Int ?: 0
//            statsDictionary[FirestoreRepository.fb.October_en] = stats[FirestoreRepository.fb.October_en] as? Int ?: 0
//            statsDictionary[FirestoreRepository.fb.November_en] = stats[FirestoreRepository.fb.November_en] as? Int ?: 0
//            statsDictionary[FirestoreRepository.fb.December_en] = stats[FirestoreRepository.fb.December_en] as? Int ?: 0

            statsDictionary[viewSentencesCount] = stats[viewSentencesCount] as? Int ?: 0
            statsDictionary[viewWordsCount] = stats[viewWordsCount] as? Int ?: 0
            statsDictionary[viewSoundsCount] = stats[viewSoundsCount] as? Int ?: 0

            statsDictionary[viewLOTMCount] = stats[viewLOTMCount] as? Int ?: 0
            statsDictionary[viewQuizCount] = stats[viewQuizCount] as? Int ?: 0
            statsDictionary[viewSpeakCount] = stats[viewSpeakCount] as? Int ?: 0
            statsDictionary[viewShadowingCount] = stats[viewShadowingCount] as? Int ?: 0
            statsDictionary[viewUSvsBRCount] = stats[viewUSvsBRCount] as? Int ?: 0
            statsDictionary[viewDontCount] = stats[viewDontCount] as? Int ?: 0
            statsDictionary[viewSettingsCount] = stats[viewSettingsCount] as? Int ?: 0
            statsDictionary[viewFindCount] = stats[viewFindCount] as? Int ?: 0
            statsDictionary[viewBookmarksCount] = stats[viewBookmarksCount] as? Int ?: 0
            statsDictionary[viewMyWordsCount] = stats[viewMyWordsCount] as? Int ?: 0
            statsDictionary[viewTheirWordsCount] = stats[viewTheirWordsCount] as? Int ?: 0
            statsDictionary[viewTryOutCount] = stats[viewTryOutCount] as? Int ?: 0
            statsDictionary[viewPracticeCount] = stats[viewPracticeCount] as? Int ?: 0



            Timber.d("Updating Firebase with stats: $stats for uid: ${firestoreRepository.firebaseUid()}")
            firestoreRepository.fsUpdateGlobalStats(stats = statsDictionary)

            clearStats(fsDOC.TTSStats)
        }
    }

    //region copied from iOS
    fun updateGlobalTTSStats(words: String, googleVoiceName: String) {


        val characters = words.count()
        inc(fsDOC.TTSStats, TTSChars, characters)
        inc(fsDOC.TTSStats, TTSStats)

        val TTSLevel = googleVoiceToChargingLevel(googleVoiceName)
        when (TTSLevel) {
            GoogleTTSChargingLevels.PREMIUM -> inc(fsDOC.TTSStats, TTSPremium, characters)
            GoogleTTSChargingLevels.STUDIO -> inc(fsDOC.TTSStats, TTSStudio, characters)
            GoogleTTSChargingLevels.STANDARD -> inc(fsDOC.TTSStats, TTSStandard, characters)
            GoogleTTSChargingLevels.QUALITY -> inc(fsDOC.TTSStats, TTSQuality, characters)
            GoogleTTSChargingLevels.OTHER -> inc(fsDOC.TTSStats, TTSOther, characters)
        }

    }
    //Main grouping of updates to different stats
    fun updateTTSStatsWithCosts(
        sentence: Sentence,
        currentVoiceName: String
    ) {
        updateGlobalTTSStats(sentence.sentence, currentVoiceName)
        updateUserPlayedSentenceCount() //count how many mp3s user has played
        updateUserTTSCounts(sentence.sentence.count())


        val cost = calculateTTSCallCost(sentence.sentence.count(), currentVoiceName)
        Timber.d("Cost: $${"%.8f".format(cost)}")

        updateUserStatDouble(TTSAPIEstCostUSD, cost)
    }
    //Main grouping of updates to different stats - Convenient String only
    fun updateTTSStatsWithCosts(
        sentence: String,
        currentVoiceName: String
    ) {
        updateGlobalTTSStats(sentence, currentVoiceName)
        updateUserPlayedSentenceCount() //count how many mp3s user has played
        updateUserTTSCounts(sentence.count())


        val cost = calculateTTSCallCost(sentence.count(), currentVoiceName)
        Timber.d("Cost: $${"%.8f".format(cost)}")

        updateUserStatDouble(TTSAPIEstCostUSD, cost)
    }
    fun updateTTSStatsWithoutCosts() {
        updateUserPlayedSentenceCount() //count how many mp3s user has played
    }
    fun updateGlobalTTSStatsObsolete(words: String) {
        if (words.isEmpty()) {
            return
        }

        val characters = words.count()

        inc(fsDOC.TTSStats, TTSChars, characters)
        inc(fsDOC.TTSStats, TTSStats)
    }
    fun updateUserStatDouble(fieldName:String,value:Double) {
        incDouble(fsDOC.USER,fieldName,value)
    }
    fun updateUserStatField(fieldName:String,value:String) {
        update(fsDOC.USER,fieldName,value)
    }
    fun updateUserStatField(fieldName:String,value:Int = 1) {
        update(fsDOC.USER,fieldName,value)
    }
    fun updateUserPlayedSentenceCount() {
        inc(fsDOC.USER, MP3PlayedCount)
    }
    fun incUserStatCount(fieldNamw:String,value:Int = 1) {
        inc(fsDOC.USER, fieldNamw,value)
    }
    fun updateUserOpenAITotalTokenCount(count:Int) {
        inc(fsDOC.USER, OpenAICallCount)
        inc(fsDOC.USER, OpenAITotalTokenCount,count)
    }
    fun updateUserTTSCurrentTokenCount(value:Int) {
        update(fsDOC.USER,TTSCurrentTokenCount,value)
    }
    fun incUserTTSTotalTokenCount(value:Int = 1) {
        inc(fsDOC.USER, TTSTotalTokenCount, value)
    }
    fun updateUserGeminiTotalTokenCount(count:Int) {
        inc(fsDOC.USER, GeminiCallCount)
        inc(fsDOC.USER, GeminiTotalTokenCount,count)
    }
    fun updateUserTTSCounts(count:Int) {
        inc(fsDOC.USER, TTSAPICallCount)
        inc(fsDOC.USER, TTSTotalCharCount,count)
    }
    fun updateFSCharCount(charcount: Int, sheetname: String = "") {

        if (charcount <= 0) {
            return
        }

        inc(fsDOC.TTSStats, FSDownloadCharCount, charcount)
        inc(fsDOC.TTSStats, FSDownloadCharCalls)
        incSheet(fsDOC.TTSStats, sheetname)
    }

    //TODO: No translations yet
    fun updateTranslateStats(chars: Int, count: Int = 1) {

        if (count <= 0) {
            return
        }

        inc(fsDOC.TTSStats, TRCalls, count)
        inc(fsDOC.TTSStats, TRChars, chars)
    }

    fun updateTTSCacheHit() {
        inc(fsDOC.TTSStats, TTSCacheHit)
    }

    //endregion


    //If check has been already done today, return true
    //so; once per day
    fun checkIfAppUpgradeCheckAlreadyDoneToday(): Boolean {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time // today's date at 00:00:00

        val lastDueDate = getLastCheckAppUpgradeDate()

        Timber.d("getLastCheckAppUpgradeDate:$lastDueDate")

        return if (lastDueDate != null) {

            val lastFlushDateStartOfDay = Calendar.getInstance().apply {
                time = lastDueDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            if (lastFlushDateStartOfDay < today) {
                putLastCheckAppUpgradeDate(today)
                false
            } else {
                true //only true exit
            }
        } else { //first run
            // No last date found, so write now
            putLastCheckAppUpgradeDate(today)
            false
        }
    }

    fun checkIfStatsFlushNeeded(forced:Boolean = false): Boolean {

        if (forced || BuildConfig.DEBUG){
            Timber.d("DEBUG or forced = $forced: so flush now. don't rely on dates/times")
            return true
        }


        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time // today's date at 00:00:00

        val lastFlushDate = getLastFlushDate()

        Timber.d("lastFlushDateString:$lastFlushDate")

        return if (lastFlushDate != null) {

            val lastFlushDateStartOfDay = Calendar.getInstance().apply {
                time = lastFlushDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            if (lastFlushDateStartOfDay < today) {
                // Last flush was not today, so flush now
                updateLastFlushDate(today)
                Timber.d("Stats flushed and lastFlushDate updated.")
                true
            } else {
                false
            }
        } else { //first run
            // No last flush date found, so flush now
            updateLastFlushDate(today)
            true
        }
    }

    fun doINeedToCheckGlobalUploadDate(): Boolean {
        val lastGlobalUpdateTime = sharedPreferences.getLong(LAST_CHECK_TIMER_KEY, 0)
        val currentTime = Calendar.getInstance().timeInMillis / 1000
        if (currentTime - lastGlobalUpdateTime >= 172800) { // 172800 is the number of seconds in 2 days
            sharedPreferences.edit().putLong(LAST_CHECK_TIMER_KEY, currentTime).apply()
            return true
        } else {
            return false
        }
    }

    fun putLastWordlistDownloadDate(date: Date): Boolean {
        return try {
            val dateString = dateTimeFormatter.format(date)
            sharedPreferences.edit().putString(LAST_WORD_LIST_DOWNLOAD_DATE_KEY, dateString).apply()
            true
        } catch (e: Exception) {
            Timber.e("Error saving date for key '$LAST_WORD_LIST_DOWNLOAD_DATE_KEY': ${e.localizedMessage}")
            false
        }
    }

    fun getLastWordlistDownloadDate(): Date {
        return try {
            val dateString = sharedPreferences.getString(LAST_WORD_LIST_DOWNLOAD_DATE_KEY, null)
            if (dateString != null) {
                dateTimeFormatter.parse(dateString)
            } else {
                Date(0)
            }
        } catch (e: Exception) {
            Timber.e("Error retrieving date for key '$LAST_WORD_LIST_DOWNLOAD_DATE_KEY': ${e.localizedMessage}")
            Date(0)
        }
    }

    // region App Upgrade
    fun putLastCheckAppUpgradeDate(date: Date): Boolean {
        return try {
            val dateString = dateFormatter.format(date)
            sharedPreferences.edit().putString(LAST_CHECK_APP_UPGRADE_DATE_KEY, dateString).apply()
            true
        } catch (e: Exception) {
            Timber.e("Error saving date for key '$LAST_CHECK_APP_UPGRADE_DATE_KEY': ${e.localizedMessage}")
            false
        }
    }

    fun getLastCheckAppUpgradeDate(): Date {
        return try {
            val dateString = sharedPreferences.getString(LAST_CHECK_APP_UPGRADE_DATE_KEY, null)
            if (dateString != null) {
                dateFormatter.parse(dateString)
            } else {
                Date(0)
            }
        } catch (e: Exception) {
            Timber.e("Error retrieving date for key '$LAST_CHECK_APP_UPGRADE_DATE_KEY': ${e.localizedMessage}")
            Date(0)
        }
    }
    // end region

    // region Login
    fun checkIfLoginDue(): Boolean {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.time // today's date at 00:00:00

        val lastDueDate = getLastLoginCheckDate()

        return if (lastDueDate != null) {

            val lastFlushDateStartOfDay = Calendar.getInstance().apply {
                time = lastDueDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            if (lastFlushDateStartOfDay < today) {
                putLastLoginCheckDate(today)
                true
            } else {
                false
            }
        } else { //first run
            // No last date found, so write now
            putLastLoginCheckDate(today)
            true
        }
    }

    fun putLastLoginCheckDate(date: Date): Boolean {
        return try {
            val dateString = dateFormatter.format(date)
            sharedPreferences.edit().putString(LAST_CHECK_LOGIN_DATE_KEY, dateString).apply()
            true
        } catch (e: Exception) {
            Timber.e("Error saving date for key '$LAST_CHECK_LOGIN_DATE_KEY': ${e.localizedMessage}")
            false
        }
    }

    fun getLastLoginCheckDate(): Date {
        return try {
            val dateString = sharedPreferences.getString(LAST_CHECK_LOGIN_DATE_KEY, null)
            if (dateString != null) {
                dateFormatter.parse(dateString)
            } else {
                Date(0)
            }
        } catch (e: Exception) {
            Timber.e("Error retrieving date for key '$LAST_CHECK_LOGIN_DATE_KEY': ${e.localizedMessage}")
            Date(0)
        }
    }





    // region Progress Stats
// --- Models you already have ---
// data class Category(val title: String, val words: List<VocabWord>)
// data class VocabWord(val word: String, val sentences: List<Sentence>)
// data class Sentence(val sentence: String)

    data class ProgressStats(
        val size: Int,
        val completed: Int
    ) {
        val percent: Double
            get() = if (size == 0) 0.0 else completed.toDouble() / size.toDouble()
    }

    // Matches Swift's .removingBrackets()
    private fun String.removeBrackets(): String =
        replace(Regex("""[\[\]\(\)<>]"""), "")

    /**
     * Recalculates progress by scanning the app's files directory once,
     * then counting matches against filenames like:
     *   "<voiceName>_<wordWithoutBrackets>.<firstSentence>.mp3"
     *
     * @param context   Android context
     * @param categories List of categories
     * @param voiceName  e.g. "en-GB-Neural2-A"
     * @param directory  where the mp3s live (defaults to internal files dir)
     */
    suspend fun recalcProgressObsolete(
        context: android.content.Context,
        categories: List<Category>,
        voiceName: String,
        directory: java.io.File = context.filesDir
    ): ProgressStats = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {

        // 1) List directory once -> Set<String> of filenames
        val namesOnDisk: Set<String> = directory.list()?.toSet() ?: emptySet()

        var total = 0
        var completed = 0
        val prefix = "${voiceName}_"

        // 2) Count totals + membership matches
        for (category in categories) {
            total += category.words.size
            for (w in category.words) {
                val firstSentence = w.sentences.firstOrNull()?.sentence ?: continue
                val fname = "$prefix${w.word.removeBrackets()}.$firstSentence.mp3"
                if (namesOnDisk.contains(fname)) completed++
            }
        }

       ProgressStats(size = total, completed = completed)
       // progressStats = ProgressStats(size = total, completed = completed)
    }

    /**
     * Recalculate by scanning the directory once (off the main thread).
     * Filename pattern:
     *   "<voiceName>_<word.removeBrackets()>.<firstSentence>.mp3"
     */
    suspend fun recalcProgress(
        categories: List<Category>,
        voiceName: String,
        directory: File = context.filesDir
    ) = withContext(Dispatchers.IO) {

        val total = categories.sumOf { it.words.size }
        var completed = directory.list { _, name ->
            name.endsWith(".mp3", ignoreCase = true) &&
                    name.startsWith("${voiceName}_")
        }?.size ?: 0


        val namesOnDisk: Set<String> =
            directory.list { _, name -> name.endsWith(".mp3", ignoreCase = true) }
                ?.toSet()
                ?: emptySet()

        if (namesOnDisk.isEmpty()) {
            //total = categories.sumOf { it.words.size }
            progressStats = ProgressStats(size = total, completed = 0)
            return@withContext
        }

        for (cat in categories) {
           // total += cat.words.size
            for (w in cat.words) {
                val firstSentence = w.sentences.firstOrNull()?.sentence ?: continue
                val word = w.word.removeBrackets()

               // val fname = "$prefix${word}.$firstSentence.mp3"
                val uniqueSentenceId = generateUniqueSentenceId(word, firstSentence, voiceName)
                val fullFilename = "$uniqueSentenceId.mp3"
               // Timber.e(fullFilename)
                if (namesOnDisk.contains(fullFilename)) completed++
            }
        }

        progressStats = ProgressStats(size = total, completed = completed)
        Timber.d("recalcProgress $progressStats")
    }
    /**
     * Increment the *total* count by 1 (e.g., when user clicks-to-play).
     * No disk cache, no I/O; just update the in-memory snapshot.
     */
    fun incProgressSize(skillLevel:String,by: Int = 1) {
        synchronized(this) {
            val s = progressStats
            progressStats = s.copy(size = s.size + by)
            Timber.d("incProgressStats $progressStats skill $skillLevel")

           // val d = statProgressCompleted.replace("_", skillLevel)

            val fieldNameCompleted = statProgressCompleted.replace("__", skillLevel)
            val fieldNameTotal= statProgressTotal.replace("__", skillLevel)

            updateUserStatField(fieldNameCompleted,progressStats.completed)
            //BUG: inc on size but should be a save
            //TODO:inc on size but should be a save
            updateUserStatField(fieldNameTotal,progressStats.size)


        }
    }
    //endregion
}
