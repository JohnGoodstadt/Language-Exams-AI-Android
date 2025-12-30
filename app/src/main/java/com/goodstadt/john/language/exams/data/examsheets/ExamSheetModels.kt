package com.goodstadt.john.language.exams.data.examsheets

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

// Custom error for clear, specific failures
sealed class DataFetchError : Exception() {
    data class DownloadDecodingError(override val message: String) : DataFetchError()
    data object CacheDecodingError : DataFetchError()
    data object DocumentNotFoundError : DataFetchError()
}

// --- DTOs that perfectly match the Firestore document structure ---
@Serializable
@Keep
internal data class VocabFileDTO(
    val fileformat: Int = 0,
    val location: Int = 0,
    val sheetName: String = "",
    val updatedDate: Int = 0,
    val uploadDate: Double = 0.0,
    val id: String = "",
    val native: String = "",
    val name: String = "",
    val romanized: Boolean = false,
    val nativeName: String = "",
    val googleVoicePrefix: String = "",
    val voiceName: String = "",
    val tabtitles: List<String> = emptyList()
)

@Serializable
@Keep
internal data class FirestoreCategoryDTO(
    val title: String = "",
    val tabNumber: Int = 0,
    val sortOrder: Int = 0
)

@Serializable
@Keep
internal data class FirestoreWordDTO(
    val id: Int = 0,
    val sortOrder: Int = 0,
    val translation: String = "",
    val romanisation: String = "",
    val partOfSpeech: String = "",
    val word: String = "",
    val definition: String = "",
    val IPA: String = "",
    val pronounce: String = "",
    val group: String = "",
    val sentences: List<String> = emptyList(),
    val translations: List<String> = emptyList()
)