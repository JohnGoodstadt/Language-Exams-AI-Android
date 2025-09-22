package com.goodstadt.john.language.exams.data

import android.content.Context
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.models.LanguageCodeDetails
import com.goodstadt.john.language.exams.models.LanguagesControlFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ControlRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    // Cache only the single, relevant LanguageCodeDetails object
    private var cachedActiveLanguageDetails: LanguageCodeDetails? = null
    private val detailsCache = mutableMapOf<String, LanguageCodeDetails>()

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Loads control data and returns only the LanguageCodeDetails object
     * that matches the current app flavor's language code.
     */
    suspend fun getCurrentLanguageCode(): Result<String> = withContext(Dispatchers.IO) {
        val currentLanguage = userPreferencesRepository.selectedLanguageCodeFlow.first()
        return@withContext Result.success(currentLanguage)
    }
    suspend fun clearCache(){
        //when swapping languages
        cachedActiveLanguageDetails = null
    }
    suspend fun getActiveLanguageDetails(): Result<LanguageCodeDetails> = withContext(Dispatchers.IO) {

        val currentLanguageCode = userPreferencesRepository.selectedLanguageCodeFlow.first()

        detailsCache[currentLanguageCode]?.let {
            return@withContext Result.success(it)
        }
        // Return from cache if available
        cachedActiveLanguageDetails?.let {
            return@withContext Result.success(it)
        }

        try {
            // Read the file from the assets folder
            val jsonString = context.assets.open("languagesControl.json")
                .bufferedReader()
                .use { it.readText() }

            // Parse the JSON string into a list of objects
            // --- THE FIX IS HERE ---
            // 1. Parse the JSON into our new top-level object, NOT a List.
            val controlFile = jsonParser.decodeFromString<LanguagesControlFile>(jsonString)

            // 2. Now, access the .codes property of the parsed object
            val allCodes = controlFile.codes
            // --- END OF FIX ---

            // --- THIS IS THE NEW FILTERING LOGIC ---
            // 1. Get the language code for the current app flavor (e.g., "en", "de")
           // val currentLanguageCode = LanguageConfig.languageCode

            // 2. Search through all data to find the matching 'codes' entry
            //val activeDetails = allCodes.find { it.code == currentLanguageCode }
            val activeDetails = allCodes.find { it.code.equals(currentLanguageCode, ignoreCase = true) }

            if (activeDetails != null) {
                cachedActiveLanguageDetails = activeDetails
                Result.success(activeDetails)
            } else {
                Result.failure(Exception("Language details for code '$currentLanguageCode' not found in languagesControl.json"))
            }
            // --- END OF NEW LOGIC ---

        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    suspend fun getAllEnglishLanguageList(): Result<List<LanguageCodeDetails>> = withContext(Dispatchers.IO) {

//        viewModelScope.launch {
            val currentCode = userPreferencesRepository.selectedLanguageCodeFlow.first()
            // Now you can use 'currentCode' (e.g., "en-us") for your logic
            Timber.d( "The current language code is: $currentCode")
//        }

        try {
            // Read the file from the assets folder
            val jsonString = context.assets.open("languagesControl.json")
                .bufferedReader()
                .use { it.readText() }

            // Parse the JSON string into a list of objects
            // --- THE FIX IS HERE ---
            // 1. Parse the JSON into our new top-level object, NOT a List.
            val controlFile = jsonParser.decodeFromString<LanguagesControlFile>(jsonString)

            // 2. Now, access the .codes property of the parsed object
            Result.success( controlFile.codes.filter { it.code.take(2) == "en"  })


        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}