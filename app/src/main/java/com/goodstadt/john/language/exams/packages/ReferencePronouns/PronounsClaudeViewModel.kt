package com.goodstadt.john.language.exams.packages.ReferencePronouns

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed interface PronounsClaudeUiState {
    object Loading : PronounsClaudeUiState
    data class Success(val sheet: Format6File) : PronounsClaudeUiState
    data class Error(val message: String) : PronounsClaudeUiState
}

/**
 * Drives the fileFormat-6 Pronouns reference screen. Loads the sheet named by the "documentId" nav arg
 * (e.g. "GermanReferencePronouns") through [ContentRepository.getFormat6Data], and plays example
 * sentences / table cells through the shared [AudioPlaybackRepository] (same audio waterfall + stats as
 * every other reference screen).
 */
@HiltViewModel
class PronounsClaudeViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val audioPlaybackRepository: AudioPlaybackRepository,
    private val billingRepository: BillingRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Firestore/bundle doc name for this sheet, from the route arg; falls back to the de sheet. */
    private val documentId: String =
        savedStateHandle.get<String>("documentId") ?: "GermanReferencePronouns"

    /** Bundled fileFormat-7 quiz sheet for this reference sheet, e.g. "GermanReferencePronounsQuiz". */
    val quizSheetName: String get() = documentId + "Quiz"

    /** This generic fileFormat-6 screen serves several teaching sheets. Both the pronoun sheets and the
     *  Prepositions teaching sheet ship a per-category quiz, so the "Q" button shows for those. */
    val hasQuiz: Boolean get() = documentId.endsWith("Pronouns") || documentId.endsWith("PrepositionsTeaching")

    /** The Prepositions teaching sheet has its own authored quiz (no runtime generation), routed via a
     *  different loader than the pronouns quiz. */
    val isPrepositionsSheet: Boolean get() = documentId.endsWith("PrepositionsTeaching")

    private val _uiState = MutableStateFlow<PronounsClaudeUiState>(PronounsClaudeUiState.Loading)
    val uiState: StateFlow<PronounsClaudeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = PronounsClaudeUiState.Loading
            contentRepository.getFormat6Data(documentId)
                .onSuccess { sheet ->
                    // Sort categories and their item chips by sortOrder for a stable chip order.
                    val ordered = sheet.copy(
                        categories = sheet.categories
                            .sortedBy { it.sortOrder }
                            .map { c -> c.copy(patterns = c.patterns.sortedBy { it.sortOrder }) }
                    )
                    _uiState.value = PronounsClaudeUiState.Success(ordered)
                }
                .onFailure { e ->
                    Timber.e(e, "PronounsClaude: failed to load '$documentId'")
                    _uiState.value = PronounsClaudeUiState.Error(e.localizedMessage ?: "Failed to load")
                }
        }
    }

    /** The last sentence tapped/played on this screen, for the Translate ("T") button to pre-fill. */
    private var lastPlayedSentence: String = ""
    fun getLatestSentence(): String = lastPlayedSentence

    /** Play a sentence (or a single pronoun form) via the shared audio waterfall. */
    fun play(text: String) {
        if (text.isBlank()) return
        lastPlayedSentence = text
        viewModelScope.launch {
            audioPlaybackRepository.playTrackAndGetStatus(
                sentence = text,
                level = "Reference",
                sheetName = documentId,
                isPremiumUser = billingRepository.isPurchased.value
            )
        }
    }
}
