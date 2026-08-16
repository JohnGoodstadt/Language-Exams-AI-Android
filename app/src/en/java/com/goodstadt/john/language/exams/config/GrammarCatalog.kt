package com.goodstadt.john.language.exams.data

/**
 * The canonical granular grammar syllabus (A1-B2), derived from the Cambridge/Oxford/Pearson/
 * British Council/EGP consensus grid but split into individual grammar points. Drives the Focus
 * "all categories" view and maps a category to its asset file. Code-level (not scanned) so a
 * category appears even before its quiz file has real questions.
 */
object GrammarCatalog {

    val categories: List<GrammarCategory> = listOf(
        // ---- A1 ----
        GrammarCategory("Present Simple", "PresentSimple", "Present", listOf("A1")),
        GrammarCategory("Past Simple", "PastSimple", "Past", listOf("A1")),
        GrammarCategory("Future: going to", "FutureGoingTo", "Future", listOf("A1")),
        GrammarCategory("Question Formation", "QuestionFormation", "Questions", listOf("A1")),
        GrammarCategory("Modals of Ability & Permission", "ModalsAbilityPermission", "Modals", listOf("A1")),
        GrammarCategory("Indefinite & Definite Articles", "IndefiniteDefiniteArticles", "Articles", listOf("A1")),
        GrammarCategory("Plural & Countable Nouns", "PluralCountableNouns", "Nouns", listOf("A1")),
        GrammarCategory("Adjective Order", "AdjectiveOrder", "Adjectives", listOf("A1")),
        GrammarCategory("Prepositions of Place & Time", "PrepositionsPlaceTime", "Prepositions", listOf("A1")),
        GrammarCategory("Basic Conjunctions", "BasicConjunctions", "Conjunctions", listOf("A1")),
        // ---- A2 ----
        GrammarCategory("Present Continuous", "PresentContinuous", "Present", listOf("A2")),
        GrammarCategory("Past Continuous", "PastContinuous", "Past", listOf("A2")),
        GrammarCategory("Past Habits: used to / would", "PastHabitsUsedTo", "Habits", listOf("A2")),
        GrammarCategory("Future: will", "FutureWill", "Future", listOf("A2")),
        GrammarCategory("Wh- Questions", "WhQuestions", "Questions", listOf("A2")),
        GrammarCategory("Modals of Obligation & Advice", "ModalsObligationAdvice", "Modals", listOf("A2")),
        GrammarCategory("Zero Article", "ZeroArticle", "Articles", listOf("A2")),
        GrammarCategory("Personal & Possessive Pronouns", "PersonalPossessivePronouns", "Pronouns", listOf("A2")),
        GrammarCategory("Adverbs of Manner & Frequency", "AdverbsMannerFrequency", "Adverbs", listOf("A2")),
        GrammarCategory("Comparatives & Superlatives", "ComparativesSuperlatives", "Compare", listOf("A2")),
        GrammarCategory("Prepositions of Movement", "PrepositionsMovement", "Prepositions", listOf("A2")),
        GrammarCategory("Time & Reason Conjunctions", "TimeReasonConjunctions", "Conjunctions", listOf("A2")),
        GrammarCategory("Gerunds & Infinitives", "GerundsInfinitives", "Gerunds", listOf("A2")),
        GrammarCategory("Zero & First Conditional", "ZeroFirstConditional", "Conditionals", listOf("A2")),
        GrammarCategory("Prefixes & Suffixes", "PrefixesSuffixes", "Affixes", listOf("A2")),
        GrammarCategory("Basic Linkers", "BasicLinkers", "Linkers", listOf("A2")),
        // ---- B1 ----
        GrammarCategory("Present Perfect", "PresentPerfect", "Present", listOf("B1")),
        GrammarCategory("Past Perfect", "PastPerfect", "Past", listOf("B1")),
        GrammarCategory("Future Continuous & Perfect", "FutureContinuousPerfect", "Future", listOf("B1")),
        GrammarCategory("Subject & Object Questions", "SubjectObjectQuestions", "Questions", listOf("B1")),
        GrammarCategory("Modals of Deduction & Probability", "ModalsDeductionProbability", "Modals", listOf("B1")),
        GrammarCategory("Articles with Nouns & Names", "ArticlesNounsNames", "Articles", listOf("B1")),
        GrammarCategory("Quantifiers & Determiners", "QuantifiersDeterminers", "Quantifiers", listOf("B1")),
        GrammarCategory("Adjective & Adverb Distinctions", "AdjectiveAdverbDistinctions", "Adj/Adv", listOf("B1")),
        GrammarCategory("Comparative Structures", "ComparativeStructures", "Compare", listOf("B1")),
        GrammarCategory("Dependent Prepositions", "DependentPrepositions", "Prepositions", listOf("B1")),
        GrammarCategory("Contrast & Concession", "ContrastConcession", "Contrast", listOf("B1")),
        GrammarCategory("Verb + Object + Infinitive", "VerbObjectInfinitive", "Patterns", listOf("B1")),
        GrammarCategory("Second Conditional", "SecondConditional", "Conditionals", listOf("B1")),
        GrammarCategory("Present & Past Passive", "PresentPastPassive", "Passive", listOf("B1")),
        GrammarCategory("Reported Statements", "ReportedStatements", "Reported", listOf("B1")),
        GrammarCategory("Defining Relative Clauses", "DefiningRelativeClauses", "Relative", listOf("B1")),
        GrammarCategory("Common Phrasal Verbs", "CommonPhrasalVerbs", "Phrasals", listOf("B1")),
        GrammarCategory("Common Collocations", "CommonCollocations", "Collocations", listOf("B1")),
        GrammarCategory("Forming Nouns & Adjectives", "FormingNounsAdjectives", "Word Form", listOf("B1")),
        GrammarCategory("Cohesive Devices", "CohesiveDevices", "Cohesion", listOf("B1")),
        GrammarCategory("Text Organisation", "TextOrganisation", "Text", listOf("B1")),
        // ---- B2 ----
        GrammarCategory("Present Perfect Continuous & Aspect", "PresentPerfectContinuousAspect", "Present", listOf("B2")),
        GrammarCategory("Narrative Tenses", "NarrativeTenses", "Narrative", listOf("B2")),
        GrammarCategory("Advanced Future Forms", "AdvancedFutureForms", "Future", listOf("B2")),
        GrammarCategory("Indirect & Embedded Questions", "IndirectQuestions", "Questions", listOf("B2")),
        GrammarCategory("Advanced Modals & Semi-Modals", "AdvancedModals", "Modals", listOf("B2")),
        GrammarCategory("Articles: Advanced Usage", "AdvancedArticles", "Articles", listOf("B2")),
        GrammarCategory("Advanced Determiners & Reference", "AdvancedReference", "Determiners", listOf("B2")),
        GrammarCategory("Advanced Modifiers & Intensifiers", "AdvancedModifiers", "Modifiers", listOf("B2")),
        GrammarCategory("Advanced Comparison", "AdvancedComparison", "Compare", listOf("B2")),
        GrammarCategory("Prepositional Phrases & Idioms", "PrepositionalPhrasesIdioms", "Prepositions", listOf("B2")),
        GrammarCategory("Advanced Linking", "AdvancedLinking", "Linking", listOf("B2")),
        GrammarCategory("Advanced Verb Patterns", "AdvancedVerbPatterns", "Patterns", listOf("B2")),
        GrammarCategory("Third & Mixed Conditionals", "ThirdMixedConditionals", "Conditionals", listOf("B2")),
        GrammarCategory("Advanced Passive", "AdvancedPassive", "Passive", listOf("B2")),
        GrammarCategory("Reported Questions & Commands", "ReportedQuestionsCommands", "Reported", listOf("B2")),
        GrammarCategory("Non-Defining Relative Clauses", "NonDefiningRelativeClauses", "Relative", listOf("B2")),
        GrammarCategory("Advanced Phrasal Verbs", "AdvancedPhrasalVerbs", "Phrasals", listOf("B2")),
        GrammarCategory("Advanced Collocations", "AdvancedCollocations", "Collocations", listOf("B2")),
        GrammarCategory("Advanced Word Formation", "AdvancedWordFormation", "Word Form", listOf("B2")),
        GrammarCategory("Advanced Discourse Markers", "AdvancedDiscourseMarkers", "Discourse", listOf("B2")),
        GrammarCategory("Coherence & Register", "CoherenceRegister", "Register", listOf("B2")),
        GrammarCategory("Inversion & Emphasis", "InversionEmphasis", "Inversion", listOf("B2")),
        GrammarCategory("Cleft Sentences", "CleftSentences", "Cleft", listOf("B2"))
    )

    /** Flat (category, level) rows in catalogue order, for browsing / practising. */
    val rows: List<GrammarRow> =
        categories.flatMap { c -> c.levels.map { GrammarRow(c.displayName, it, c.fileKey, c.shortLabel) } }

    /** The filename key for a display category, or null if it isn't a catalogue category. */
    fun fileKeyFor(category: String): String? =
        categories.firstOrNull { it.displayName == category }?.fileKey

    /** The display category for a filename key, or the key itself if unknown. */
    fun displayNameFor(fileKey: String): String =
        categories.firstOrNull { it.fileKey == fileKey }?.displayName ?: fileKey
}
