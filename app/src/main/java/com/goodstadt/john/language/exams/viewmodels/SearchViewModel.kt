package com.goodstadt.john.language.exams.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.PlaybackResult
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UserStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.models.VocabWord
import com.goodstadt.john.language.exams.models.WordAndSentence
import com.goodstadt.john.language.exams.utils.generateUniqueSentenceId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

// A simple data class to hold a word and its first sentence for the flat list
data class SearchResult(
    val word: VocabWord,
    val firstSentence: String
)

@OptIn(FlowPreview::class) // Needed for the debounce operator
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val vocabRepository: VocabRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val userStatsRepository: UserStatsRepository,
    private val ttsStatsRepository : TTSStatsRepository,

    ) : ViewModel() {

    // Holds the complete list of all words from the current file
    private var allWords: List<VocabWord> = emptyList()

    // State for the user's search query
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // State for the filtered search results
    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    // Re-use the playback state from the TabsViewModel
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState = _playbackState.asStateFlow()

    init {
        loadFullWordList()
        observeSearchQuery()
    }

    private fun loadFullWordList() {
        viewModelScope.launch {
            val fileName = userPreferencesRepository.selectedFileNameFlow.first()
            vocabRepository.getVocabData(fileName).onSuccess { vocabFile ->
                // Flatten the entire structure into a single list of words
                allWords = vocabFile.categories.flatMap { it.words }
            }
        }
    }

    private fun observeSearchQuery() {
        viewModelScope.launch {
            searchQuery
                .debounce(300L) // Wait for 300ms of no new input
                .distinctUntilChanged() // Only search if the text has actually changed
                .map { query ->
                    if (query.length < 2) {
                        emptyList() // Return empty list if query is too short
                    } else {
                        // Filter the master list
                        allWords.filter {
                            it.word.contains(query, ignoreCase = true)
                        }.mapNotNull { word ->
                            // Convert to SearchResult, filtering out words with no sentences
                            word.sentences.firstOrNull()?.let { sentence ->
                                SearchResult(word, sentence.sentence)
                            }
                        }
                    }
                }
                .collect { results ->
                    _searchResults.value = results
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun playTrack(searchResult: SearchResult) {
        if (_playbackState.value is PlaybackState.Playing) return

        viewModelScope.launch {
            val currentVoiceName = userPreferencesRepository.selectedVoiceNameFlow.first()
            val uniqueSentenceId = generateUniqueSentenceId(searchResult.word, searchResult.word.sentences.first(),currentVoiceName)
            _playbackState.value = PlaybackState.Playing(uniqueSentenceId)

            // Use .first() to get the most recent value from the Flow
            val currentLanguageCode = LanguageConfig.languageCode

            val result = vocabRepository.playTextToSpeech(
                    text = searchResult.firstSentence,
                    uniqueSentenceId = uniqueSentenceId,
                    voiceName = currentVoiceName,
                    languageCode = currentLanguageCode
            )
//            result.onSuccess {
//                statsRepository.fsUpdateSentenceHistoryIncCount(WordAndSentence(searchResult.word.word, searchResult.firstSentence))
//            }
//            result.onFailure { error ->
//                _playbackState.value = PlaybackState.Error(error.localizedMessage ?: "Playback failed")
//            }
            when (result) {
                is PlaybackResult.PlayedFromNetworkAndCached -> {
                    userStatsRepository.fsUpdateSentenceHistoryIncCount(WordAndSentence(searchResult.word.word, searchResult.firstSentence))
                    ttsStatsRepository.updateTTSStats(  searchResult.firstSentence,currentVoiceName)
                    ttsStatsRepository.updateUserPlayedSentenceCount()
                }
                is PlaybackResult.PlayedFromCache -> {
                    userStatsRepository.fsUpdateSentenceHistoryIncCount(WordAndSentence(searchResult.word.word, searchResult.firstSentence))
                    ttsStatsRepository.updateUserPlayedSentenceCount()
                }
                is PlaybackResult.Failure -> {
//                    _playbackState.value = PlaybackState.Error(error.localizedMessage ?: "Playback failed")
                    _playbackState.value = PlaybackState.Error(result.exception.message ?: "Playback failed")
                }
            }
            _playbackState.value = PlaybackState.Idle
        }
    }
}