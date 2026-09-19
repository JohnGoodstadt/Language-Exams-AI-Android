package com.goodstadt.john.language.exams.packages.Conjugations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.packages.ReferencePronouns.Format6File
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface ConjugationsTeachingUiState {
    object Loading : ConjugationsTeachingUiState
    data class Success(val sheet: Format6File) : ConjugationsTeachingUiState
    data class Error(val message: String) : ConjugationsTeachingUiState
}

/**
 * Drives the NEW fileFormat-6 conjugation teaching screen (the pattern-focused sibling of the
 * Pronouns / Prepositions "Claude" screens). Instead of a flat list of every conjugated form (the old
 * [ConjugationsViewModel]), each verb ships as a fileFormat-6 sheet whose CATEGORY chips are tenses
 * (Präsens, Präteritum, …) and whose PATTERN chips are "How it works" (the six-person paradigm table)
 * and "Examples" (one tappable sentence per person).
 *
 * The four verbs come from [LanguageConfig.conjugationOptions]; the logical sheet name is the existing
 * per-verb Firestore name with a "Teaching" suffix (e.g. "GermanConjugationsToBe" -> the teaching sheet
 * "GermanConjugationsToBeTeaching"), which [ContentRepository] maps to the bundled res/raw json.
 */
@HiltViewModel
class ConjugationsTeachingViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val billingRepository: BillingRepository
) : ViewModel() {

    /** The verb picker options, e.g. ["Haben", "Sein", "Machen", "Bekommen"] (de) — same as the old screen. */
    val verbOptions: List<String> = LanguageConfig.conjugationOptions

    private val _selectedVerb = MutableStateFlow(verbOptions.first())
    val selectedVerb: StateFlow<String> = _selectedVerb.asStateFlow()

    private val _uiState = MutableStateFlow<ConjugationsTeachingUiState>(ConjugationsTeachingUiState.Loading)
    val uiState: StateFlow<ConjugationsTeachingUiState> = _uiState.asStateFlow()

    /** Logical fileFormat-6 sheet name for the selected verb, e.g. "GermanConjugationsToBeTeaching". */
    private fun teachingSheetName(verb: String): String =
        LanguageConfig.getConjugationFirestoreSheetName(verb) + "Teaching"

    init {
        load(_selectedVerb.value)
    }

    fun onVerbSelected(verb: String) {
        if (_selectedVerb.value != verb) {
            _selectedVerb.value = verb
            load(verb)
        }
    }

    private fun load(verb: String) {
        val documentId = teachingSheetName(verb)
        viewModelScope.launch {
            _uiState.value = ConjugationsTeachingUiState.Loading
            contentRepository.getFormat6Data(documentId)
                .onSuccess { sheet ->
                    // Sort tenses and their chips by sortOrder for a stable chip order.
                    val ordered = sheet.copy(
                        categories = sheet.categories
                            .sortedBy { it.sortOrder }
                            .map { c -> c.copy(patterns = c.patterns.sortedBy { it.sortOrder }) }
                    )
                    _uiState.value = ConjugationsTeachingUiState.Success(ordered)
                }
                .onFailure { e ->
                    Timber.e(e, "ConjugationsTeaching: failed to load '$documentId'")
                    _uiState.value =
                        ConjugationsTeachingUiState.Error(e.localizedMessage ?: "Failed to load")
                }
        }
    }

    /** The last sentence tapped/played on this screen, for the Translate ("T") button to pre-fill. */
    private var lastPlayedSentence: String = ""
    fun getLatestSentence(): String = lastPlayedSentence

    /** Play a sentence via the shared audio waterfall (same stats/caching as every reference screen). */
    fun play(text: String) {
        if (text.isBlank()) return
        lastPlayedSentence = text
        viewModelScope.launch {
            audioPlaybackRepository.playTrackAndGetStatus(
                sentence = text,
                level = "Reference",
                sheetName = teachingSheetName(_selectedVerb.value),
                isPremiumUser = billingRepository.isPurchased.value
            )
        }
    }
}
