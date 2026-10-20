package com.goodstadt.john.language.exams.packages.Format9

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.models.Format9File
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * STEP 1 placeholder VM for fileFormat-9 (podcasts). Loads the sheet from the bundle via
 * [ContentRepository.getFormat9Data] so the screen can confirm the new JSON reads. Step 2 replaces the
 * screen with the real multi-speaker script UI (and may extend this VM).
 */
@HiltViewModel
class Format9ViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {

    private val _file = MutableStateFlow<Format9File?>(null)
    val file: StateFlow<Format9File?> = _file.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var loadedName: String? = null

    fun load(name: String) {
        if (name.isBlank() || name == loadedName) return
        loadedName = name
        viewModelScope.launch {
            contentRepository.getFormat9Data(name)
                .onSuccess { _file.value = it; _error.value = null }
                .onFailure { _error.value = it.message; Timber.e(it, "Format9 load failed for '$name'") }
        }
    }
}
