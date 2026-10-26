package com.goodstadt.john.language.exams.packages.ReferencePronouns

import kotlinx.serialization.Serializable

/**
 * Firestore storage layout for fileFormat-6 sheets. The on-screen result is identical either way - this
 * only changes how the sheet is shredded into Firestore.
 *
 *   true  (Option A, current): TWO collections - `categories -> patterns`. Each pattern document carries
 *         its `forms` and `sections` (with their `sentences`/`green`) as nested array fields. Fewer docs,
 *         far fewer read round-trips.
 *   false (legacy): FOUR collections - `categories -> patterns -> sections -> sentences`. Every sentence
 *         is its own flat document (easiest to edit one sentence in the console, most docs/reads).
 *
 * The uploader and the fetch both read this one flag, so they always agree. To switch back: flip to false,
 * then re-upload the sheet from the Upload-JSON tool (the uploader wipes the old tree first).
 */
object Format6Layout {
    const val NESTED = true
}

/**
 * fileFormat 6 - a pattern-focused pronoun reference. Deliberately light on prose: the learner picks a
 * CATEGORY chip (Personal, This/That, …), then an ITEM chip (I/me, you, he, …), and sees that item's
 * isolated declension chain (e.g. ich -> mich -> mir) with a few example sentences, the focused pronoun
 * highlighted in green.
 *
 * Language-agnostic: the SAME UI renders GermanReferencePronouns and EnglishReferencePronouns -
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
