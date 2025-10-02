package com.goodstadt.john.language.exams.data.examsheets

import kotlinx.serialization.Serializable

// Custom error for clear, specific failures
sealed class DataFetchError : Exception() {
    data class DownloadDecodingError(override val message: String) : DataFetchError()
    data object CacheDecodingError : DataFetchError()
    data object DocumentNotFoundError : DataFetchError()
}

// --- DTOs that perfectly match the Firestore document structure ---
@Serializable
internal data class VocabFileDTO(
    val fileformat: Int,
    val location: Int,
    val sheetName: String,
    val updatedDate: Int,
    val uploadDate: Double,
    val id: String,
    val native: String,
    val name: String,
    val romanized: Boolean,
    val nativeName: String,
    val googleVoicePrefix: String,
    val voiceName: String,
    val tabtitles: List<String>
)

@Serializable
internal data class FirestoreCategoryDTO(
    val title: String,
    val tabNumber: Int,
    val sortOrder: Int
)

@Serializable
internal data class FirestoreWordDTO(
    val id: Int,
    val sortOrder: Int,
    val translation: String,
    val romanisation: String,
    val partOfSpeech: String,
    val word: String,
    val group: String,
    val sentences: List<String>,
    val translations: List<String>
)