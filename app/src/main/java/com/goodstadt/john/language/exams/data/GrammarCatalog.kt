package com.goodstadt.john.language.exams.data

/**
 * One canonical grammar category and the CEFR level it is taught at.
 *
 * [fileKey] is the language-independent token in the asset filename
 * `Quizzes/Grammar/<level>/Grammar<fileKey>-<lang>.json` (e.g. "PresentSimple"). The English
 * [displayName] is what the Focus screen shows and what lands in the (category|level) tally, so it
 * stays identical across flavours - the `de` files reuse the same English filename/key with German
 * question content inside. The displayName MUST match the `category` field inside the JSON exactly.
 */
data class GrammarCategory(
    val displayName: String,
    val fileKey: String,
    val levels: List<String>
)

/** A single (category, level) cell of the grammar grid - one practisable quiz. */
data class GrammarRow(
    val category: String,
    val level: String,
    val fileKey: String
)

/**
 * The canonical granular grammar syllabus (A1-B2), derived from the Cambridge/Oxford/Pearson/
 * British Council/EGP consensus grid but split into individual grammar points (Present Simple,
 * Present Continuous, Present Perfect ...). Drives the Focus "all categories" view and maps a
 * category to its asset file. Code-level (not scanned) so a category appears even before its quiz
 * file has real questions.
 */
object GrammarCatalog {

    val categories: List<GrammarCategory> = listOf(
        // ---- A1 ----
        GrammarCategory("Present Simple", "PresentSimple", listOf("A1")),
        GrammarCategory("Past Simple", "PastSimple", listOf("A1")),
        GrammarCategory("Future: going to", "FutureGoingTo", listOf("A1")),
        GrammarCategory("Question Formation", "QuestionFormation", listOf("A1")),
        GrammarCategory("Modals of Ability & Permission", "ModalsAbilityPermission", listOf("A1")),
        GrammarCategory("Indefinite & Definite Articles", "IndefiniteDefiniteArticles", listOf("A1")),
        GrammarCategory("Plural & Countable Nouns", "PluralCountableNouns", listOf("A1")),
        GrammarCategory("Adjective Order", "AdjectiveOrder", listOf("A1")),
        GrammarCategory("Prepositions of Place & Time", "PrepositionsPlaceTime", listOf("A1")),
        GrammarCategory("Basic Conjunctions", "BasicConjunctions", listOf("A1")),
        // ---- A2 ----
        GrammarCategory("Present Continuous", "PresentContinuous", listOf("A2")),
        GrammarCategory("Past Continuous", "PastContinuous", listOf("A2")),
        GrammarCategory("Past Habits: used to / would", "PastHabitsUsedTo", listOf("A2")),
        GrammarCategory("Future: will", "FutureWill", listOf("A2")),
        GrammarCategory("Wh- Questions", "WhQuestions", listOf("A2")),
        GrammarCategory("Modals of Obligation & Advice", "ModalsObligationAdvice", listOf("A2")),
        GrammarCategory("Zero Article", "ZeroArticle", listOf("A2")),
        GrammarCategory("Personal & Possessive Pronouns", "PersonalPossessivePronouns", listOf("A2")),
        GrammarCategory("Adverbs of Manner & Frequency", "AdverbsMannerFrequency", listOf("A2")),
        GrammarCategory("Comparatives & Superlatives", "ComparativesSuperlatives", listOf("A2")),
        GrammarCategory("Prepositions of Movement", "PrepositionsMovement", listOf("A2")),
        GrammarCategory("Time & Reason Conjunctions", "TimeReasonConjunctions", listOf("A2")),
        GrammarCategory("Gerunds & Infinitives", "GerundsInfinitives", listOf("A2")),
        GrammarCategory("Zero & First Conditional", "ZeroFirstConditional", listOf("A2")),
        GrammarCategory("Prefixes & Suffixes", "PrefixesSuffixes", listOf("A2")),
        GrammarCategory("Basic Linkers", "BasicLinkers", listOf("A2")),
        // ---- B1 ----
        GrammarCategory("Present Perfect", "PresentPerfect", listOf("B1")),
        GrammarCategory("Past Perfect", "PastPerfect", listOf("B1")),
        GrammarCategory("Future Continuous & Perfect", "FutureContinuousPerfect", listOf("B1")),
        GrammarCategory("Subject & Object Questions", "SubjectObjectQuestions", listOf("B1")),
        GrammarCategory("Modals of Deduction & Probability", "ModalsDeductionProbability", listOf("B1")),
        GrammarCategory("Articles with Nouns & Names", "ArticlesNounsNames", listOf("B1")),
        GrammarCategory("Quantifiers & Determiners", "QuantifiersDeterminers", listOf("B1")),
        GrammarCategory("Adjective & Adverb Distinctions", "AdjectiveAdverbDistinctions", listOf("B1")),
        GrammarCategory("Comparative Structures", "ComparativeStructures", listOf("B1")),
        GrammarCategory("Dependent Prepositions", "DependentPrepositions", listOf("B1")),
        GrammarCategory("Contrast & Concession", "ContrastConcession", listOf("B1")),
        GrammarCategory("Verb + Object + Infinitive", "VerbObjectInfinitive", listOf("B1")),
        GrammarCategory("Second Conditional", "SecondConditional", listOf("B1")),
        GrammarCategory("Present & Past Passive", "PresentPastPassive", listOf("B1")),
        GrammarCategory("Reported Statements", "ReportedStatements", listOf("B1")),
        GrammarCategory("Defining Relative Clauses", "DefiningRelativeClauses", listOf("B1")),
        GrammarCategory("Common Phrasal Verbs", "CommonPhrasalVerbs", listOf("B1")),
        GrammarCategory("Common Collocations", "CommonCollocations", listOf("B1")),
        GrammarCategory("Forming Nouns & Adjectives", "FormingNounsAdjectives", listOf("B1")),
        GrammarCategory("Cohesive Devices", "CohesiveDevices", listOf("B1")),
        GrammarCategory("Text Organisation", "TextOrganisation", listOf("B1")),
        // ---- B2 ----
        GrammarCategory("Present Perfect Continuous & Aspect", "PresentPerfectContinuousAspect", listOf("B2")),
        GrammarCategory("Narrative Tenses", "NarrativeTenses", listOf("B2")),
        GrammarCategory("Advanced Future Forms", "AdvancedFutureForms", listOf("B2")),
        GrammarCategory("Indirect & Embedded Questions", "IndirectQuestions", listOf("B2")),
        GrammarCategory("Advanced Modals & Semi-Modals", "AdvancedModals", listOf("B2")),
        GrammarCategory("Articles: Advanced Usage", "AdvancedArticles", listOf("B2")),
        GrammarCategory("Advanced Determiners & Reference", "AdvancedReference", listOf("B2")),
        GrammarCategory("Advanced Modifiers & Intensifiers", "AdvancedModifiers", listOf("B2")),
        GrammarCategory("Advanced Comparison", "AdvancedComparison", listOf("B2")),
        GrammarCategory("Prepositional Phrases & Idioms", "PrepositionalPhrasesIdioms", listOf("B2")),
        GrammarCategory("Advanced Linking", "AdvancedLinking", listOf("B2")),
        GrammarCategory("Advanced Verb Patterns", "AdvancedVerbPatterns", listOf("B2")),
        GrammarCategory("Third & Mixed Conditionals", "ThirdMixedConditionals", listOf("B2")),
        GrammarCategory("Advanced Passive", "AdvancedPassive", listOf("B2")),
        GrammarCategory("Reported Questions & Commands", "ReportedQuestionsCommands", listOf("B2")),
        GrammarCategory("Non-Defining Relative Clauses", "NonDefiningRelativeClauses", listOf("B2")),
        GrammarCategory("Advanced Phrasal Verbs", "AdvancedPhrasalVerbs", listOf("B2")),
        GrammarCategory("Advanced Collocations", "AdvancedCollocations", listOf("B2")),
        GrammarCategory("Advanced Word Formation", "AdvancedWordFormation", listOf("B2")),
        GrammarCategory("Advanced Discourse Markers", "AdvancedDiscourseMarkers", listOf("B2")),
        GrammarCategory("Coherence & Register", "CoherenceRegister", listOf("B2")),
        GrammarCategory("Inversion & Emphasis", "InversionEmphasis", listOf("B2")),
        GrammarCategory("Cleft Sentences", "CleftSentences", listOf("B2"))
    )

    /** Flat (category, level) rows in catalogue order, for browsing / practising. */
    val rows: List<GrammarRow> =
        categories.flatMap { c -> c.levels.map { GrammarRow(c.displayName, it, c.fileKey) } }

    /** The filename key for a display category, or null if it isn't a catalogue category. */
    fun fileKeyFor(category: String): String? =
        categories.firstOrNull { it.displayName == category }?.fileKey

    /** The display category for a filename key, or the key itself if unknown. */
    fun displayNameFor(fileKey: String): String =
        categories.firstOrNull { it.fileKey == fileKey }?.displayName ?: fileKey
}
