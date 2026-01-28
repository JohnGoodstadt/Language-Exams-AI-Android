package com.goodstadt.john.language.exams.packages.dailydictionary

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

//@Serializable
data class DailyDictionaryBundle(
    val fileFormat: Int,
    val bundleId: String,
    val locale: String,
    val updatedDate: Long,
    val entries: List<DictionaryEntry>,
    val wotd: WotdConfig
)

//@Serializable
data class DictionaryEntry(
    val entryId: String = "",
    val headword: String = "",
    val pronunciation: Pronunciation = Pronunciation(),
    val labels: List<String> = emptyList(),
    val partsOfSpeech: List<PartOfSpeechBlock> = emptyList(),
    val version: Int = 1,
    val updatedDate: Long = 0L
)
data class Pronunciation(
    val display: String = "",
    val ipaUK: String? = null,
    val ipaUS: String? = null
)

data class PartOfSpeechBlock(
    val ordinal: String = "",
    val pos: String = "",
    val grammarType: String? = null,
    val headwordLine: String? = null,
    val inflectionsLine: String? = null,
    val synonyms: List<String> = emptyList(),
    val senses: List<Sense> = emptyList()
)

data class Sense(
    val senseNumber: Int = 1,
    val definition: String = "",
    val examples: List<String> = emptyList()
)

//@Serializable
data class WotdConfig(
    val timezoneRule: String,
    val uiPolicy: UiPolicy? = null,
    val scheduledAssignments: List<WotdAssignment>
)

//@Serializable
data class UiPolicy(
    val lockForwardAtToday: Boolean = true,
    val maxBrowseDaysBack: Int = 3
)

//@Serializable
data class WotdAssignment(
    val date: Int,
    val entryId: String,
    val label: String
)


data class PoolDoc(
    val orderedEntryIds: List<String> = emptyList(),
    val uiPolicy: UiPolicy? = null,
    val updatedDate: Long? = null,
    val timezoneRule: String? = null
)

