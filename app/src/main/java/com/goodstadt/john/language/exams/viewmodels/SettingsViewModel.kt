package com.goodstadt.john.language.exams.viewmodels

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.data.AuthRepository
import com.goodstadt.john.language.exams.data.BillingRepository
import com.goodstadt.john.language.exams.data.ConnectivityRepository
import com.goodstadt.john.language.exams.data.ControlRepository
import com.goodstadt.john.language.exams.data.CreditsRepository
import com.goodstadt.john.language.exams.data.FirestoreRepository
import com.goodstadt.john.language.exams.data.GoogleTTSInfoRepository
import com.goodstadt.john.language.exams.data.PlaybackResult
//import com.goodstadt.john.language.exams.data.PremiumStatus
import com.goodstadt.john.language.exams.data.RecallingItems
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import com.goodstadt.john.language.exams.data.TTSStatsRepository.Companion.currentGoogleVoiceName
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.data.VoiceOption
import com.goodstadt.john.language.exams.data.VoiceRepository
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.models.ExamDetails
import com.goodstadt.john.language.exams.models.LanguageCodeDetails
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber

// --- MODIFICATION 1: Add pending state to UiState ---
data class SettingsUiState(
//    val premiumStatus: PremiumStatus = PremiumStatus.Checking,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val appVersionCode: Int = BuildConfig.VERSION_CODE,

    val currentLanguage: String = "",
    val currentVoiceName: String = "",
    val currentExamName: String = "",
    val availableExams: List<ExamDetails> = emptyList(),
    val availableVoices: List<VoiceOption> = emptyList(),
    val availableLanguages: List<LanguageCodeDetails> = emptyList(),
    val currentFriendlyVoiceName: String = "",
    // Add nullable fields to hold the user's selection inside the bottom sheet
    val pendingSelectedExam: ExamDetails? = null,
    val pendingSelectedLanguage: LanguageCodeDetails? = null,
    val pendingSelectedVoice: VoiceOption? = null,
    val showIAPBottomSheet: Boolean = false, //IAP Info sheet

    val hourlyLimit: Int = 0,
    val dailyLimit: Int = 0,
)

// Sealed class to represent which bottom sheet should be shown
sealed interface SheetContent {
    object Hidden : SheetContent
    object SpeakerSelection : SheetContent
    object ExamSelection : SheetContent
    object LanguageSelection : SheetContent
    object BothSelection : SheetContent //TODO: remove
}

sealed interface SettingsUiEvent {
    data class ShowSnackbar(val message: String, val actionLabel: String? = null) : SettingsUiEvent
    // You could add other events here later, like:
    // data object NavigateToProfileScreen : SettingsUiEvent
}


@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val controlRepository: ControlRepository,
    private val voiceRepository: VoiceRepository,
    private val vocabRepository: VocabRepository,
    private val ttsStatsRepository: TTSStatsRepository,
    private val recallingItemsManager: RecallingItems,
    private val googleTtsInfoRepository: GoogleTTSInfoRepository,
    private val firestoreRepository: FirestoreRepository,
    private val billingRepository: BillingRepository,
    @ApplicationContext private val context: Context,
    private val rateLimiter: SimpleRateLimiter,
    private val connectivityRepository: ConnectivityRepository,
    private val authRepository: AuthRepository,
    private val creditsRepository: CreditsRepository,
) : ViewModel() {

    val isPurchased = billingRepository.isPurchased
    val productDetails = billingRepository.productDetails
    val billingError = billingRepository.billingError


    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    private val _sheetState = MutableStateFlow<SheetContent>(SheetContent.Hidden)
    val sheetState = _sheetState.asStateFlow()

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()

    private val _uiEvent = MutableSharedFlow<SettingsUiEvent>()
    val uiEvent = _uiEvent.asSharedFlow()

    init {

        // This initialization logic is correct and remains the same.
        // It keeps the "current" state in sync with saved preferences.
        viewModelScope.launch {
            userPreferencesRepository.selectedVoiceNameFlow.collect { voiceId ->

                val languageDetailsResult = controlRepository.getActiveLanguageDetails()

                languageDetailsResult.onSuccess { details ->
                    val friendlyName = voiceRepository.getFriendlyNameForVoice(voiceId,details.code)
                    _uiState.update {
                        it.copy(
                            currentFriendlyVoiceName = friendlyName,
                            currentVoiceName = voiceId
                        )
                    }
                }

            }
        }
        viewModelScope.launch {
            userPreferencesRepository.selectedFileNameFlow.collect { fileName ->
                _uiState.update { it.copy(currentExamName = fileName) }
            }
        }

        viewModelScope.launch {
            val languageDetailsResult = controlRepository.getActiveLanguageDetails()

            languageDetailsResult.onSuccess { details ->
                Timber.e(details.name)
                _uiState.update { it.copy(currentLanguage = details.name) }
            }
        }

//        viewModelScope.launch {
//            // 1. Fetch the one-time, non-flow data first.
//            val activeLanguageDetails = controlRepository.getActiveLanguageDetails().getOrNull()
//
//            // 2. Define the flows you want to listen to.
//            val voiceFlow = userPreferencesRepository.selectedVoiceNameFlow
//            val fileFlow = userPreferencesRepository.selectedFileNameFlow
//
//            // 3. Use 'combine' to create a NEW Flow<YourUiState>.
//            //    This is the core of the fix.
//            combine(voiceFlow, fileFlow) { voiceId, fileName ->
//                // This lambda's only job is to TRANSFORM the inputs into a new state object.
//                // It runs whenever voiceId or fileName changes.
//                val friendlyName = voiceRepository.getFriendlyNameForVoice(voiceId)
//
//                // Create and return the complete state object.
//                SettingsUiState(
//                    currentFriendlyVoiceName = friendlyName,
//                    currentVoiceName = voiceId,
//                    currentExamName = fileName,
//                    currentLanguage = activeLanguageDetails?.name ?: "",
//                    // We can also merge in other existing state values
//                   // isLoading = _uiState.value.isLoading // Preserve other state if needed
//                )
//            }.collect { newUiState ->
//                // 4. The 'collect' block now receives the fully-formed state object.
//                //    Its only job is to update the ViewModel's state.
//                _uiState.value = newUiState
//            }
//        }

        loadInitialData()

        initializeBilling()

        updateRateLimiterState()

    }

    private fun initializeBilling() {
        viewModelScope.launch {
            try {
                billingRepository.connect()
                billingRepository.checkPurchases()
                billingRepository.logCurrentStatus()  // Debug log on init
            } catch (e: Exception) {
                Timber.e("${e.message}")
                // Handle connection or query failure
//                billingRepository._billingError.value = e.message
            }
        }
    }

    private fun updateRateLimiterState() {
        _uiState.update {
            it.copy(
                hourlyLimit = rateLimiter.hourlyLimit,
                dailyLimit = rateLimiter.dailyLimit
            )
        }
    }
    fun isGooglePlayServicesAvailable(context: Context): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
        return resultCode == ConnectionResult.SUCCESS
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            val currentLanguageCode = controlRepository.getCurrentLanguageCode()
            currentLanguageCode.onSuccess { languageCode ->
                val languageDetailsResult = controlRepository.getActiveLanguageDetails()
                val languageListResult = controlRepository.getAllEnglishLanguageList()
                val availableVoicesResult = voiceRepository.getAvailableVoices(languageCode)

                languageDetailsResult.onSuccess { details ->
                    languageListResult.onSuccess { languages ->

                        Timber.e("${languages}")
                        availableVoicesResult.onSuccess { voices ->
                            _uiState.update {
                                it.copy(
                                    appVersion = BuildConfig.VERSION_NAME,
                                    appVersionCode = BuildConfig.VERSION_CODE,
                                    availableVoices = voices,
                                    availableExams = details.exams,
                                    availableLanguages = languages
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- MODIFICATION 2: Update onSettingClicked to set the initial pending state ---
    fun onSettingClicked(type: SheetContent) {
        viewModelScope.launch {
            val currentState = uiState.value
            // Pre-populate the pending state with the currently active selection
            val updatedUiState = when (type) {
                SheetContent.ExamSelection -> {
                    val currentExam = currentState.availableExams.find { it.json == currentState.currentExamName }
                    currentState.copy(pendingSelectedExam = currentExam)
                }

                SheetContent.SpeakerSelection -> {
                    val currentVoice = currentState.availableVoices.find { it.id == currentState.currentVoiceName }
                    currentState.copy(pendingSelectedVoice = currentVoice)
                }

                SheetContent.Hidden -> currentState

                SheetContent.LanguageSelection -> {
                    val currentLanguage = currentState.availableLanguages.find { it.code == currentState.currentLanguage }
                    currentState.copy(pendingSelectedLanguage = currentLanguage)

                }
                SheetContent.BothSelection -> {
                    val currentExam = currentState.availableExams.find { it.json == currentState.currentExamName }
                    val currentVoice = currentState.availableLanguages.find { it.code == currentState.currentLanguage }

                    currentState.copy(pendingSelectedLanguage = currentVoice,pendingSelectedExam = currentExam)

                }
            }
            _uiState.value = updatedUiState
            _sheetState.value = type
        }
    }

    fun onShowBottomSheetClicked() {

        if (!connectivityRepository.isCurrentlyOnline()) {
            viewModelScope.launch {
                _uiEvent.emit(SettingsUiEvent.ShowSnackbar("No internet connection", actionLabel = "Retry" ))
                Timber.e("No Internet")

            }
            return
        }

        _uiState.update { it.copy(showIAPBottomSheet = true) }
        firestoreRepository.fsIncUserProperty("premiumShownInfoSheet")
    }

    // --- Action to HIDE the sheet ---
    // This is called when the user dismisses the sheet (e.g., by swiping down).
    fun onBottomSheetDismissed() {
        _uiState.update { it.copy(showIAPBottomSheet = false) }
        firestoreRepository.fsIncUserProperty("premiumShownInfoSheetNotYet")
    }

    // --- MODIFICATION 3: Create functions to handle PENDING selections ---
    fun onPendingExamSelect(exam: ExamDetails) {
        // This only updates the state for the UI inside the sheet. Does NOT save.
        _uiState.update { it.copy(pendingSelectedExam = exam) }

    }
    fun onPendingLanguageSelect(language: LanguageCodeDetails) {
        // This only updates the state for the UI inside the sheet. Does NOT save.
        _uiState.update { it.copy(pendingSelectedLanguage = language) }

    }

    fun onPendingVoiceSelect(voice: VoiceOption) {
        // This only updates the state for the UI inside the sheet. Does NOT save.
        _uiState.update { it.copy(pendingSelectedVoice = voice) }
        Timber.d("Voice selected: ${voice.friendlyName} google: ${voice.id}")

        viewModelScope.launch {
            val selectedLanguageCode = userPreferencesRepository.selectedLanguageCodeFlow.first()
            // Use val for an immutable variable, as it's only assigned once.
            val sentence = when (selectedLanguageCode.substring(0, 2).lowercase()) {
                "de" -> "Hallo, ich bin ${voice.friendlyName}. Willkommen zu 'English Exam Words'"
                // The `else` branch is required for a 'when' expression, and it handles the default case.
                else -> "Hello, I'm ${voice.friendlyName}. Welcome to 'English Exam Words'."
            }

            playTrack(sentence, voice.id)
        }


    }

    private fun playTrack(sentence: String, googleVoice: String) {
        if (_playbackState.value is PlaybackState.Playing) return

        viewModelScope.launch {
//            val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
            val uniqueSentenceId = generateUniqueSentenceId(sentence, googleVoice)
            _playbackState.value = PlaybackState.Playing(uniqueSentenceId)

            val currentLanguageCode =  userPreferencesRepository.selectedLanguageCodeFlow.first()

            val result = vocabRepository.playTextToSpeech(
                text = sentence,
                uniqueSentenceId = uniqueSentenceId,
                voiceName = googleVoice,
                languageCode = currentLanguageCode
            )

            when (result) {
                is PlaybackResult.PlayedFromNetworkAndCached -> {
//                    ttsStatsRepository.updateGlobalTTSStats( sentence,googleVoice)
//                    ttsStatsRepository.updateUserTTSCounts(sentence.count())
                    ttsStatsRepository.updateTTSStatsWithCosts(sentence, googleVoice)
                    ttsStatsRepository.incWordStats(sentence)
                }

                is PlaybackResult.PlayedFromCache -> {
                    ttsStatsRepository.updateTTSStatsWithoutCosts()
                    ttsStatsRepository.incWordStats(sentence)
                }

                is PlaybackResult.Failure -> {
                    _playbackState.value =
                        PlaybackState.Error(result.exception.message ?: "Playback failed")
                }
                PlaybackResult.CacheNotFound -> Timber.e("Cache found to exist but not played")
            }
            _playbackState.value = PlaybackState.Idle
        }
    }


    // --- MODIFICATION 4: Create a SAVE function for the new button ---
    fun saveSelection() {
        viewModelScope.launch {
            val pendingExam = _uiState.value.pendingSelectedExam
            val pendingVoice = _uiState.value.pendingSelectedVoice
            val pendingLanguage = _uiState.value.pendingSelectedLanguage

            when (_sheetState.value) {
                SheetContent.ExamSelection -> {

                    ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.WORDSTATS)//so that old exam has correct stat
                    pendingExam?.let { selectedExam ->
                        // 1. Save the user's preference (already here)
                        userPreferencesRepository.saveSelectedFileName(selectedExam.json)
                        userPreferencesRepository.saveSelectedSkillLevel(selectedExam.skillLevel)
                        // --- THIS IS THE NEW, CRITICAL PART ---
                        // 2. Tell the shared manager to load the recalled items for the NEW exam
                        Timber.d("New exam selected. Reloading recalled items for key: ${selectedExam.json}")
                        recallingItemsManager.load(selectedExam.json)

                    }


                    // pendingExam?.let { userPreferencesRepository.saveSelectedFileName(it.json) }
                }

                SheetContent.SpeakerSelection -> {
                    pendingVoice?.let {
                        val voiceName = it.id
                        userPreferencesRepository.saveSelectedVoiceName(voiceName)
                        ttsStatsRepository.updateUserStatField(currentGoogleVoiceName, voiceName)

                        firestoreRepository.fsUpdateUserGoogleVoices(voiceName)
                    }

                }

                SheetContent.LanguageSelection -> {
                    pendingLanguage?.let { selectedLanguage ->
                        voiceRepository.clearCache() //all voice will change
                        controlRepository.clearCache() //stpred language details will change

                        Timber.d(selectedLanguage.name)
                        //1. default voice
                        val voiceName = selectedLanguage.defaultFemaleVoice
                        userPreferencesRepository.saveSelectedVoiceName(voiceName)
                        userPreferencesRepository.saveSelectedLanguageCode(selectedLanguage.code)

                        _uiState.update { it.copy(currentLanguage = selectedLanguage.name) } //update UI

                        // 1. Save the user's preference (already here)
                      //  userPreferencesRepository.saveSelectedFileName(selectedLanguage.code)
//                        userPreferencesRepository.saveSelectedSkillLevel(selectedLanguage.skillLevel)
                        // --- THIS IS THE NEW, CRITICAL PART ---
                        // 2. Tell the shared manager to load the recalled items for the NEW exam
                        Timber.d("New exam selected. Reloading recalled items for key: ${selectedLanguage.code}")


                    }
                }
                SheetContent.BothSelection -> {
                    Timber.e("BothSelection")
                    ttsStatsRepository.flushStats(TTSStatsRepository.fsDOC.WORDSTATS)//so that old exam has correct stat
                    pendingExam?.let { selectedExam ->
                        // 1. Save the user's preference (already here)
                        userPreferencesRepository.saveSelectedFileName(selectedExam.json)
                        userPreferencesRepository.saveSelectedSkillLevel(selectedExam.skillLevel)
                        // --- THIS IS THE NEW, CRITICAL PART ---
                        // 2. Tell the shared manager to load the recalled items for the NEW exam
                        Timber.d("New exam selected. Reloading recalled items for key: ${selectedExam.json}")
                        recallingItemsManager.load(selectedExam.json)

                    }

                    pendingLanguage?.let { selectedLanguage ->
                        voiceRepository.clearCache() //all voice will change
                        controlRepository.clearCache() //stpred language details will change

                        Timber.d(selectedLanguage.name)
                        //1. default voice
                        val voiceName = selectedLanguage.defaultFemaleVoice
                        userPreferencesRepository.saveSelectedVoiceName(voiceName)
                        userPreferencesRepository.saveSelectedLanguageCode(selectedLanguage.code)

                        _uiState.update { it.copy(currentLanguage = selectedLanguage.name) } //update UI

                        // 1. Save the user's preference (already here)
                        //  userPreferencesRepository.saveSelectedFileName(selectedLanguage.code)
//                        userPreferencesRepository.saveSelectedSkillLevel(selectedLanguage.skillLevel)
                        // --- THIS IS THE NEW, CRITICAL PART ---
                        // 2. Tell the shared manager to load the recalled items for the NEW exam
                        Timber.d("New exam selected. Reloading recalled items for key: ${selectedLanguage.code}")


                    }
                }
                SheetContent.Hidden -> { /* Do nothing */
                }

            }
            // After saving, hide the sheet, which will also clear the pending state.
            hideBottomSheet()
        }
    }


    // --- MODIFICATION 5: Update hideBottomSheet to clear pending state ---
    fun hideBottomSheet() {
        _sheetState.value = SheetContent.Hidden
        // Reset the pending states so they are fresh the next time the sheet opens
        _uiState.update {
            it.copy(
                pendingSelectedExam = null,
                pendingSelectedVoice = null
            )
        }
    }

    fun downloadAndSaveVoiceList() {
        viewModelScope.launch {
            _uiEvent.emit(SettingsUiEvent.ShowSnackbar("Fetching voice list..."))

            val result = googleTtsInfoRepository.fetchVoices()

            result.onSuccess { voicesResponse ->
                // Use a Json parser with pretty printing for a readable file
                val jsonParser = Json { prettyPrint = true }
                val jsonString = jsonParser.encodeToString(voicesResponse)

                // Save the pretty-printed string to a file
                val saveResult =
                    googleTtsInfoRepository.saveContentToFile(jsonString, "google_tts_voices.json")

                saveResult.onSuccess { path ->
                    _uiEvent.emit(SettingsUiEvent.ShowSnackbar(path))
                }
                saveResult.onFailure { error ->
                    _uiEvent.emit(SettingsUiEvent.ShowSnackbar("Error saving file: ${error.message}"))
                }
            }

            result.onFailure { error ->
                _uiEvent.emit(SettingsUiEvent.ShowSnackbar("Error fetching voices: ${error.message}"))
            }
        }
    }

    fun firebaseUid(): String {
        return firestoreRepository.firebaseUid()?.substring(0, 4) ?: "unknown uid"
    }

    fun onDebugPrintBillingStatus(activity: Activity) {
        billingRepository.logCurrentStatus()
        Timber.i("and now logGmsState")
        billingRepository.logGmsState(activity)
        Timber.i("and now initializeGoogleServices")
        billingRepository.initializeGoogleServices(activity)
    }

//    fun purchasePremium(activity: Activity) {
//        Timber.i("purchasePremium()")
//        viewModelScope.launch {
//            billingRepository.launchPurchase(activity)
//        }
//    }

    fun onDebugResetPurchases() {
        Timber.i("onDebugResetPurchases()")
        billingRepository.debugResetAllPurchases()
    }

    fun buyPremiumButtonPressed(activity: Activity) {
        Timber.i("purchasePremium()")

        viewModelScope.launch {
            billingRepository.launchPurchase(activity)
        }

        firestoreRepository.fsIncUserProperty("premiumShownBuyScreen")
    }

    fun debugAppValues(){

        if ( authRepository.fsCurrentUID() == "SkmfAlqdG6hj216UC2DTkIIvaUx1" ){ //JG onSamsung phone
            val productDetails = billingRepository.printableCurrentStatus()
            Timber.w(productDetails)
            Timber.w(rateLimiter.printCurrentStatus)

           Timber.w(creditsRepository.printableCredits())
        }
    }
    fun debugAppRateLimiting() : String {

        if ( authRepository.fsCurrentUID() == "SkmfAlqdG6hj216UC2DTkIIvaUx1" ){ //JG onSamsung phone
            return rateLimiter.printableStatus()
        }

        return ""
    }
    fun debugAppBilling() : String {

        if ( authRepository.fsCurrentUID() == "SkmfAlqdG6hj216UC2DTkIIvaUx1" ){ //JG onSamsung phone
            val productDetails = billingRepository.printableCurrentStatus()
           return productDetails
        }

        return ""
    }
    fun debugAppLLMCredits() : String {

        if ( authRepository.fsCurrentUID() == "SkmfAlqdG6hj216UC2DTkIIvaUx1" ){ //JG onSamsung phone
            return creditsRepository.printableCredits()
        }

        return ""
    }

    fun isItMe(): Boolean {
        return authRepository.fsCurrentUID() == "SkmfAlqdG6hj216UC2DTkIIvaUx1" || authRepository.fsCurrentUID() =="TECvYwk9i7RJcyLhFver15Ywbp93"
    }


}