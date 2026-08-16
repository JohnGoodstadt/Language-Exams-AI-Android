package com.goodstadt.john.language.exams.data

/**
 * German (de flavour) grammar catalogue - the German CEFR grammar syllabus (Goethe/telc/DTZ style),
 * split into individual grammar points. English display titles are the language-independent keys
 * (as in the English catalogue); [fileKey] is ASCII-only so it is safe as a file / Firestore name.
 * Shared [GrammarCategory] / [GrammarRow] types live in main.
 */
object GrammarCatalog {

    val categories: List<GrammarCategory> = listOf(
        // ---- A1 ----
        GrammarCategory("Present Tense", "PresentTense", "Present", listOf("A1")),
        GrammarCategory("sein & haben", "SeinHaben", "sein/haben", listOf("A1")),
        GrammarCategory("Word Order & Verb Position", "WordOrderVerbPosition", "Word Order", listOf("A1")),
        GrammarCategory("Definite & Indefinite Articles", "DefiniteIndefiniteArticles", "Articles", listOf("A1")),
        GrammarCategory("Noun Gender & Plurals", "NounGenderPlurals", "Nouns", listOf("A1")),
        GrammarCategory("Nominative & Accusative Case", "NominativeAccusativeCase", "Nom/Acc", listOf("A1")),
        GrammarCategory("Personal Pronouns", "PersonalPronouns", "Pronouns", listOf("A1")),
        GrammarCategory("Possessive Articles", "PossessiveArticles", "Possessive", listOf("A1")),
        GrammarCategory("Modal Verbs", "ModalVerbs", "Modals", listOf("A1")),
        GrammarCategory("Negation: nicht & kein", "NegationNichtKein", "Negation", listOf("A1")),
        GrammarCategory("Questions", "Questions", "Questions", listOf("A1")),
        GrammarCategory("Separable Verbs", "SeparableVerbs", "Separable", listOf("A1")),
        // ---- A2 ----
        GrammarCategory("Perfect Tense", "PerfectTense", "Perfect", listOf("A2")),
        GrammarCategory("Simple Past: sein, haben & Modals", "SimplePastBasics", "Past", listOf("A2")),
        GrammarCategory("Dative Case", "DativeCase", "Dative", listOf("A2")),
        GrammarCategory("Two-Way Prepositions", "TwoWayPrepositions", "Two-Way", listOf("A2")),
        GrammarCategory("Accusative & Dative Prepositions", "AccusativeDativePrepositions", "Acc/Dat Prep", listOf("A2")),
        GrammarCategory("Adjective Endings", "AdjectiveEndings", "Adjectives", listOf("A2")),
        GrammarCategory("Comparative & Superlative", "ComparativeSuperlative", "Compare", listOf("A2")),
        GrammarCategory("Subordinate Clauses: weil & dass", "SubordinateClausesWeilDass", "Subordinate", listOf("A2")),
        GrammarCategory("Conditional & Time Clauses: wenn", "ConditionalTimeClausesWenn", "wenn", listOf("A2")),
        GrammarCategory("Coordinating Conjunctions", "CoordinatingConjunctions", "Conjunctions", listOf("A2")),
        GrammarCategory("Reflexive Verbs", "ReflexiveVerbs", "Reflexive", listOf("A2")),
        GrammarCategory("Dative Verbs", "DativeVerbs", "Dative Verbs", listOf("A2")),
        GrammarCategory("Demonstratives", "Demonstratives", "Demonstr.", listOf("A2")),
        GrammarCategory("Imperative", "Imperative", "Imperative", listOf("A2")),
        GrammarCategory("Accusative & Dative Pronouns", "AccusativeDativePronouns", "Pronouns", listOf("A2")),
        GrammarCategory("Verbs with Prepositions", "VerbsWithPrepositions", "Verb+Prep", listOf("A2")),
        // ---- B1 ----
        GrammarCategory("Narrative Past", "NarrativePast", "Past", listOf("B1")),
        GrammarCategory("Past Perfect", "PastPerfect", "Past Perfect", listOf("B1")),
        GrammarCategory("Genitive Case", "GenitiveCase", "Genitive", listOf("B1")),
        GrammarCategory("Konjunktiv II", "KonjunktivII", "Konj. II", listOf("B1")),
        GrammarCategory("Passive Voice", "PassiveVoice", "Passive", listOf("B1")),
        GrammarCategory("Relative Clauses", "RelativeClauses", "Relative", listOf("B1")),
        GrammarCategory("Adjective Declension: All Cases", "AdjectiveDeclensionAllCases", "Adjectives", listOf("B1")),
        GrammarCategory("Subordinate Clauses: Cause & Concession", "SubordinateCauseConcession", "Cause/Conc", listOf("B1")),
        GrammarCategory("Temporal Clauses", "TemporalClauses", "Temporal", listOf("B1")),
        GrammarCategory("Purpose Clauses: damit & um zu", "PurposeClauses", "Purpose", listOf("B1")),
        GrammarCategory("Infinitive Clauses with zu", "InfinitiveClausesZu", "zu-Clauses", listOf("B1")),
        GrammarCategory("Verbs with Fixed Prepositions", "VerbsWithFixedPrepositions", "Verb+Prep", listOf("B1")),
        GrammarCategory("Prepositional Adverbs", "PrepositionalAdverbs", "Prep Adv", listOf("B1")),
        GrammarCategory("Indirect Questions", "IndirectQuestions", "Indirect Q", listOf("B1")),
        GrammarCategory("Future Tense", "FutureTense", "Future", listOf("B1")),
        GrammarCategory("Two-Part Connectors", "TwoPartConnectors", "Connectors", listOf("B1")),
        GrammarCategory("Weak Noun Declension", "WeakNounDeclension", "n-Decl.", listOf("B1")),
        GrammarCategory("Accusative & Dative Reflexive Verbs", "ReflexiveVerbsAccusativeDative", "Reflexive", listOf("B1")),
        GrammarCategory("Comparison Structures", "ComparisonStructures", "Compare", listOf("B1")),
        GrammarCategory("Word Order: Time, Manner, Place", "WordOrderTimeMannerPlace", "Word Order", listOf("B1")),
        // ---- B2 ----
        GrammarCategory("Konjunktiv II: Past & Unreal Conditionals", "KonjunktivIIPast", "Konj. II", listOf("B2")),
        GrammarCategory("Konjunktiv I: Reported Speech", "KonjunktivI", "Konj. I", listOf("B2")),
        GrammarCategory("Passive with Modals & State Passive", "PassiveModalsState", "Passive", listOf("B2")),
        GrammarCategory("Passive Alternatives", "PassiveAlternatives", "Passive Alt", listOf("B2")),
        GrammarCategory("Extended Participle Attributes", "ExtendedParticipleAttributes", "Part. Attr", listOf("B2")),
        GrammarCategory("Participles as Adjectives", "ParticiplesAsAdjectives", "Participles", listOf("B2")),
        GrammarCategory("Nominalisation", "Nominalisation", "Nominal.", listOf("B2")),
        GrammarCategory("Noun-Verb Collocations", "NounVerbCollocations", "Collocations", listOf("B2")),
        GrammarCategory("Advanced Relative Clauses", "AdvancedRelativeClauses", "Relative", listOf("B2")),
        GrammarCategory("Genitive Prepositions", "GenitivePrepositions", "Gen Prep", listOf("B2")),
        GrammarCategory("Subjective Modal Verbs", "SubjectiveModalVerbs", "Modals", listOf("B2")),
        GrammarCategory("Discourse Connectors", "DiscourseConnectors", "Connectors", listOf("B2")),
        GrammarCategory("Concessive & Consecutive Clauses", "ConcessiveConsecutiveClauses", "Conc/Cons", listOf("B2")),
        GrammarCategory("Word Formation", "WordFormation", "Word Form", listOf("B2")),
        GrammarCategory("Advanced Verb & Adjective Prepositions", "AdvancedVerbAdjectivePrepositions", "Verb+Prep", listOf("B2")),
        GrammarCategory("Negation & Its Position", "NegationPosition", "Negation", listOf("B2")),
        GrammarCategory("Comparative & Modal Clauses", "ComparativeModalClauses", "Compare", listOf("B2")),
        GrammarCategory("Future Perfect", "FuturePerfect", "Future II", listOf("B2")),
        GrammarCategory("Reporting Verbs & Indirect Speech", "ReportingVerbsIndirectSpeech", "Reported", listOf("B2")),
        GrammarCategory("Emphasis & Word Order", "EmphasisWordOrder", "Emphasis", listOf("B2")),
        GrammarCategory("Complex Prepositional Phrases & Idioms", "ComplexPrepositionalPhrasesIdioms", "Prep Idioms", listOf("B2")),
        GrammarCategory("Text Cohesion & Register", "TextCohesionRegister", "Cohesion", listOf("B2"))
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
