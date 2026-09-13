package com.goodstadt.john.language.exams.packages.ReferencePronouns

import com.google.firebase.firestore.PropertyName
import com.google.gson.annotations.SerializedName

data class PronounsReferenceSheet(
    var fileformat: Int = 0,
    var location: Int = 0,
    var sheetname: String = "",
    var title: String = "",
    var updatedDate: Long = 0,
    var data: List<PronounsReferenceContent> = emptyList(),
)

data class PronounsReferenceContent(
    var schemaVersion: Int = 0,
    var contentType: String = "",
    var locale: String = "",
    var audienceLevels: List<String> = emptyList(),
    var audioConfig: PronounAudioConfig = PronounAudioConfig(),
    var screenConfig: PronounScreenConfig = PronounScreenConfig(),
    var cases: List<PronounCase> = emptyList(),
    var personalPronouns: List<PersonalPronounPattern> = emptyList(),
    var demonstratives: Demonstratives = Demonstratives(),
    var importantNotes: List<String> = emptyList(),
    var learningSequence: List<LearningStep> = emptyList(),
)

data class PronounAudioConfig(
    var mode: String = "deviceTextToSpeech",
    var locale: String = "",
    var rate: Double = 0.88,
    var sourceField: String = "sentence",
    var playWholePattern: Boolean = true,
    var pauseBetweenSentencesMs: Int = 450,
)

data class PronounScreenConfig(
    var defaultView: String = "byPerson",
    var caseOrder: List<String> = emptyList(),
    var views: List<PronounViewDefinition> = emptyList(),
    var showSupportText: Boolean = true,
    var showExamples: Boolean = true,
)

data class PronounViewDefinition(
    var id: String = "",
    var label: String = "",
    var description: String = "",
)

data class PronounCase(
    var id: String = "",
    var label: String = "",
    var shortLabel: String = "",
    var prompt: String = "",
    var promptSupportText: String = "",
    var description: String = "",
)

data class PersonalPronounPattern(
    var id: String = "",
    var person: Int = 0,
    var number: String = "",
    var formGroup: String? = null,
    var label: String = "",
    var note: String? = null,
    var forms: Map<String, PronounForm> = emptyMap(),
)

data class PronounForm(
    var word: String = "",
    var sentence: String = "",
    var supportText: String = "",
    var highlight: String = "",
)

data class Demonstratives(
    var description: String = "",
    var formGroupOrder: List<String> = emptyList(),
    var sets: List<DemonstrativeSet> = emptyList(),
)

data class DemonstrativeSet(
    var id: String = "",
    var label: String = "",
    var note: String? = null,
    var forms: Map<String, Map<String, String>> = emptyMap(),
    var examples: List<DemonstrativeExample> = emptyList(),
)

data class DemonstrativeExample(
    @get:PropertyName("case")
    @set:PropertyName("case")
    @field:SerializedName("case")
    var caseId: String = "",
    var formGroup: String = "",
    var sentence: String = "",
    var supportText: String = "",
    var highlight: String = "",
)

data class LearningStep(
    var step: Int = 0,
    var view: String = "",
    var instruction: String = "",
)
