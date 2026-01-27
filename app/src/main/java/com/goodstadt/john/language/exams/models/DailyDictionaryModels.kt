package com.goodstadt.john.language.exams.models

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DailyDictionaryBundle(
    val fileFormat: Int,
    val bundleId: String,
    val locale: String,
    val updatedDate: Long,
    val entries: List<DictionaryEntry>,
    val wotd: WotdConfig
)

@Serializable
data class DictionaryEntry(
    val entryId: String,
    val headword: String,
    val pronunciation: Pronunciation,
    val labels: List<String> = emptyList(),
    val partsOfSpeech: List<PartOfSpeechBlock>
)

@Serializable
data class Pronunciation(
    val display: String,
    val ipaUK: String? = null,
    val ipaUS: String? = null
)

@Serializable
data class PartOfSpeechBlock(
    val ordinal: String,
    val pos: String,
    val grammarType: String? = null,
    val headwordLine: String? = null,
    val inflectionsLine: String? = null,
    val synonyms: List<String> = emptyList(),
    val senses: List<Sense>
)

@Serializable
data class Sense(
    val senseNumber: Int,
    val definition: String,
    val examples: List<String> = emptyList()
)

@Serializable
data class WotdConfig(
    val timezoneRule: String,
    val uiPolicy: UiPolicy? = null,
    val scheduledAssignments: List<WotdAssignment>
)

@Serializable
data class UiPolicy(
    val lockForwardAtToday: Boolean = true,
    val maxBrowseDaysBack: Int = 3
)

@Serializable
data class WotdAssignment(
    val date: String,     // YYYY-MM-DD
    val entryId: String
)

object DailyDictionaryBundleLoader {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun loadFromAssets(context: Context, fileName: String = "DailyDictionaryBundle.json"): DailyDictionaryBundle {
        val text = context.assets.open(fileName).bufferedReader().use { it.readText() }
        return json.decodeFromString(DailyDictionaryBundle.serializer(), text)
    }
}
