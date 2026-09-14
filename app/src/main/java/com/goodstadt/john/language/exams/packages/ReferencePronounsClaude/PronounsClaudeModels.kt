package com.goodstadt.john.language.exams.packages.ReferencePronounsClaude

import kotlinx.serialization.Serializable

/**
 * fileFormat 6 - a pattern-focused pronoun reference. Deliberately light on prose: the learner picks a
 * CATEGORY chip (Personal, This/That, …), then an ITEM chip (I/me, you, he, …), and sees that item's
 * isolated declension chain (e.g. ich -> mich -> mir) with a few example sentences, the focused pronoun
 * highlighted in green.
 *
 * Language-agnostic: the SAME UI renders GermanReferencePronounsClaude and EnglishReferencePronounsClaude -
 * only the bundle/Firestore filename differs. Sentences carry only the text (+ translation); the app
 * TTS-generates and caches the audio. All fields default so a partial sheet still parses.
 */
@Serializable
data class Format6File(
    val fileformat: Int = 6,
    val location: Int = 0,
    val sheetname: String = "",
    val title: String = "",
    val updatedDate: Long = 0,
    /** Top-level chips. */
    val categories: List<PronounCategory> = emptyList()
)

/** A top-level chip, e.g. "Personal" or "This / That". */
@Serializable
data class PronounCategory(
    val id: String = "",
    val label: String = "",
    val sortOrder: Int = 0,
    /** Optional one-liner shown above the item chips. */
    val note: String = "",
    /** The second row of chips (the items in this category). */
    val patterns: List<PronounPattern> = emptyList()
)

/** One item chip, e.g. "I / me" or "he", with its declension chain and examples. */
@Serializable
data class PronounPattern(
    val id: String = "",
    /** Chip text, e.g. "I / me". */
    val chip: String = "",
    /** Short description of the slot, e.g. "1st person singular" or "3rd person · masculine". */
    val subtitle: String = "",
    val sortOrder: Int = 0,
    /** The nominative -> accusative -> dative (…) chain for this item. */
    val forms: List<PronounForm> = emptyList(),
    /** Optional extra note under the chain (e.g. a warning like "sie also = they / Sie"). */
    val note: String = "",
    /** Example sentences, grouped into coloured-header sections (basics, combinations, irregular, …). */
    val sections: List<ExampleSection> = emptyList()
)

/** One form in the chain (a case slot). */
@Serializable
data class PronounForm(
    val case: String = "",   // nom | acc | dat | gen
    val text: String = "",   // the pronoun, e.g. "mich"
    val gloss: String = ""   // plain meaning, e.g. "me - direct object"
)

/** A coloured-header group of sentences (like a Prepositions section). */
@Serializable
data class ExampleSection(
    val header: String = "",
    /** Header colour bucket: "normal" | "combinations" | "tense" | "irregular" | "advanced". */
    val tone: String = "normal",
    val sentences: List<PronounSentence> = emptyList()
)

/** One tappable sentence. [green] lists the words to colour green (all whole-word occurrences). */
@Serializable
data class PronounSentence(
    val text: String = "",
    val green: List<String> = emptyList()
)
