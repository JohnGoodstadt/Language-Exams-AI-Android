# German quiz sheets (Android)

Every quiz sheet the German Android app (`de` flavour) loads, where it comes from, and whether it is
versioned. Worked out on 3 Nov 2026 from the code and from `app/src/de/assets/`. Not checked against
Firestore itself, so whether a doc is actually uploaded is still to confirm in the console.

## Summary

| Quiz type | Sheets | Bundled | In `sheet_versions.json` |
|---|---|---|---|
| Usage quiz | 24 | all | all (v1) |
| Grammar quiz | 70 | all | all (v1) |
| Section (vocab) quiz | 140 | all | all (v1) |
| Readiness Audit | 8 | all | **none** |
| Reference quizzes | 6 | all | **none** |
| Quiz sheets (Word Pairs, Prepositions) | 2 | all | **none** |
| **Total** | **250** | **250** | 234 |

- **Nothing is missing:** every sheet the app asks for has a bundled copy, so every quiz works with
  no internet.
- **6 bundled files are never used** (see the end).
- **16 sheets aren't versioned.** Only those that load from Firestore matter, see "Exceptions".

## How a quiz sheet is loaded

Usage, Grammar, Section and Audit quizzes (and, for German, the four Adjectives quizzes) go through the
same chain (`ContentRepository.getFormat7or10Data` / `getFormat13Data`):

1. **Memory**, unless a refresh is due.
2. **Disk cache** (`filesDir/<doc name>`), unless a refresh is due.
3. **Firestore** `global/exam_sheets/sheets/<doc name>`, then saved to the disk cache.
4. **Bundled asset**, if Firestore fails or doesn't have the doc.

A refresh is due when the remote version is higher than the version stored on the device.

- The remote version comes from Remote Config `sheet_versions` in release builds, and from
  `assets/sheet_versions.json` in debug builds.
- A name missing from the list counts as **version 1**.
- The device starts at **0** for every sheet.

So on a sheet's **first** open the app always tries Firestore, versioned or not:

- If the doc is there, it's used and cached, and the device records version 1.
- If not, the bundle is used and nothing is recorded, so the next open tries Firestore again.

Offline, Firestore is skipped and the cached or bundled copy is used at once (commit `39be933`).

### What versioning does

`sheet_versions` only matters for **updating** a sheet that devices already have cached. To push a
new copy, upload the doc to Firestore and raise its number above what devices hold (1 → 2). A sheet
that isn't listed is stuck at 1. Devices fetch it once and never refetch it, until it's added to
the list with a number of 2 or higher. Sheets that are read straight from the bundle aren't affected
by versioning at all.

### Exceptions

- **Pronouns quiz** (`GermanReferencePronounsQuiz`): normally **generated in the app** from the
  `GermanReferencePronouns` reference sheet, which loads through the chain above. The bundled
  `GermanReferencePronounsQuiz.json` is read directly only as a fallback. **Never from Firestore.**
- **Prepositions quiz** (`GermanPrepositionsTeachingQuiz`): read straight from the bundle.
  **Never from Firestore.**
- **Adjectives quizzes** (`GermanReferenceAdjectives<Level>Quiz`): go through the chain above
  (`ReferenceQuizSheetMapping` enables `Adjectives` for de).
- **Section quizzes** are found by listing the bundled folder
  `Quizzes/SectionQuiz/<Level>/WordQuiz<Key><n>-de.json` (n = 1, 2, 3 …, stopping at the first gap).
  A section quiz can therefore only appear if its file is bundled. A new Firestore-only section
  sheet would never be asked for.
- **Readiness Audit**: the code lists English names (`AuditA2-1-en`), swapped to this flavour's
  suffix when that bundled file exists. When the audit screen opens, all 8 are pre-fetched from
  Firestore into the disk cache.
- **Quiz sheets** (`QuizSheetWordPairs`, `QuizSheetPrepositions`): the "Q" quiz on the Word Pairs and
  Prepositions reference pages. Read straight from the bundle (`readFormat7or10fDataFromAssets`).
  **Never from Firestore.**
- **`CategoryQuizRepository`** (Focus / weak-category questions) reads `Quizzes/ReadinessAudit`,
  `Quizzes/UsageQuiz` and `Quizzes/Grammar` **straight from the bundle**. It never sees a newer
  Firestore copy.
- **Generated in the app (no quiz file):** Conjugations quiz, the Sounds-the-Same (Format1) homophone
  quiz, and the Adjectives quiz on any group without a quiz file.

## Naming rules (doc name ↔ bundled file)

| Type | Firestore doc name | Bundled asset | Code |
|---|---|---|---|
| Usage | `German` + `UsageQuiz<n><Level>` | `Quizzes/UsageQuiz/UsageQuiz<n><Level>-de.json` | `UsageQuizSheetMapping`; list in `de/.../UsageQuizLevelsFilename.kt` |
| Grammar | `German<Level><Key>` | `Quizzes/Grammar/<Level>/Grammar<Key>-de.json` | `GrammarSheetMapping`; list in `de/.../GrammarCatalog.kt` |
| Section | `GermanSectionSheet<Level><Key><n>` | `Quizzes/SectionQuiz/<Level>/WordQuiz<Key><n>-de.json` | `SectionQuizSheetMapping`; titles → keys in `de/.../SectionQuizKeyMap.kt` |
| Audit | `German<Base>` | `Quizzes/ReadinessAudit/<Base>-de.json` | `ReadinessAuditSheetMapping`; list in `ReadinessAuditViewModel` |
| Reference | `GermanReference<Key><Level>Quiz` (same as file stem) | `Quizzes/Reference/<name>.json` | `ReferenceQuizSheetMapping` (only `Adjectives` enabled for de) |
| Quiz sheet | none (bundle only) | `Quizzes/QuizSheet/QuizSheet<Name>-de.json` | `LanguageConfig.quizSheet…Filename` |


## The sheets

### Usage quizzes (24)

| Level | Firestore doc names | Bundled asset | sheet_versions |
|---|---|---|---|
| A1 | `GermanUsageQuiz1A1` … `GermanUsageQuiz6A1` | `Quizzes/UsageQuiz/UsageQuiz<n>A1-de.json` | all 1 |
| A2 | `GermanUsageQuiz1A2` … `GermanUsageQuiz6A2` | `Quizzes/UsageQuiz/UsageQuiz<n>A2-de.json` | all 1 |
| B1 | `GermanUsageQuiz1B1` … `GermanUsageQuiz6B1` | `Quizzes/UsageQuiz/UsageQuiz<n>B1-de.json` | all 1 |
| B2 | `GermanUsageQuiz1B2` … `GermanUsageQuiz6B2` | `Quizzes/UsageQuiz/UsageQuiz<n>B2-de.json` | all 1 |

### Grammar quizzes (70)

| Level | Count | Firestore doc names (`German<Level><Key>`) | sheet_versions |
|---|---|---|---|
| A1 | 12 | PresentTense, SeinHaben, WordOrderVerbPosition, DefiniteIndefiniteArticles, NounGenderPlurals, NominativeAccusativeCase, PersonalPronouns, PossessiveArticles, ModalVerbs, NegationNichtKein, Questions, SeparableVerbs | all 1 |
| A2 | 16 | PerfectTense, SimplePastBasics, DativeCase, TwoWayPrepositions, AccusativeDativePrepositions, AdjectiveEndings, ComparativeSuperlative, SubordinateClausesWeilDass, ConditionalTimeClausesWenn, CoordinatingConjunctions, ReflexiveVerbs, DativeVerbs, Demonstratives, Imperative, AccusativeDativePronouns, VerbsWithPrepositions | all 1 |
| B1 | 20 | NarrativePast, PastPerfect, GenitiveCase, KonjunktivII, PassiveVoice, RelativeClauses, AdjectiveDeclensionAllCases, SubordinateCauseConcession, TemporalClauses, PurposeClauses, InfinitiveClausesZu, VerbsWithFixedPrepositions, PrepositionalAdverbs, IndirectQuestions, FutureTense, TwoPartConnectors, WeakNounDeclension, ReflexiveVerbsAccusativeDative, ComparisonStructures, WordOrderTimeMannerPlace | all 1 |
| B2 | 22 | KonjunktivIIPast, KonjunktivI, PassiveModalsState, PassiveAlternatives, ExtendedParticipleAttributes, ParticiplesAsAdjectives, Nominalisation, NounVerbCollocations, AdvancedRelativeClauses, GenitivePrepositions, SubjectiveModalVerbs, DiscourseConnectors, ConcessiveConsecutiveClauses, WordFormation, AdvancedVerbAdjectivePrepositions, NegationPosition, ComparativeModalClauses, FuturePerfect, ReportingVerbsIndirectSpeech, EmphasisWordOrder, ComplexPrepositionalPhrasesIdioms, TextCohesionRegister | all 1 |

Bundled as `Quizzes/Grammar/<Level>/Grammar<Key>-de.json`.

### Section (vocab) quizzes (140)

| Level | Key (files) | sheet_versions |
|---|---|---|
| A1 | Adjectives (2), Colors (2), Everyday (2), Expressions (2), Family (2), FoodandDrink (2), Hello (2), Months (2), Numbers (2), Personal (2), Prepositions (2), Questions (2), Seasons (1), Time (2), Verbs (2), WeekDays (1) | all 1 |
| A2 | Adjectives (2), Conjunctions (2), DateTime (2), Expressions (2), Family (2), FoodandDrink (2), Greetings (2), Jobs (2), Leisure (2), Personal (2), Places (2), Prepositions (2), Pronouns (2), Quantities (2), Shopping (2), Verbs (2) | all 1 |
| B1 | Adjectives (3), Adverbs (3), ComplexSentences (2), Education (3), Emotions (3), Food (3), Health (3), Leisure (3), Nature (3), Personal (3), Relationships (3), Shopping (3), Social (3), Technology (3), Travel (3), Verbs (3) | all 1 |
| B2 | Adjectives (2), Colors (2), Everyday (2), Expressions (2), Family (2), FoodandDrink (2), Hello (2), Months (2), Numbers (2), Personal (2), Prepositions (2), Questions (2), Seasons (1), Time (2), Verbs (2), WeekDays (2) | all 1 |

Doc name `GermanSectionSheet<Level><Key><n>`, bundled as `Quizzes/SectionQuiz/<Level>/WordQuiz<Key><n>-de.json`.

### Readiness Audit (8)

| Name | Bundled asset | How it loads | sheet_versions |
|---|---|---|---|
| `GermanBaselineAuditA2-1` | `Quizzes/ReadinessAudit/BaselineAuditA2-1-de.json` | Firestore, then bundle | **not listed** |
| `GermanBaselineAuditA2-2` | `Quizzes/ReadinessAudit/BaselineAuditA2-2-de.json` | Firestore, then bundle | **not listed** |
| `GermanAuditA2-1` | `Quizzes/ReadinessAudit/AuditA2-1-de.json` | Firestore, then bundle | **not listed** |
| `GermanAuditA2-2` | `Quizzes/ReadinessAudit/AuditA2-2-de.json` | Firestore, then bundle | **not listed** |
| `GermanAuditB1-1` | `Quizzes/ReadinessAudit/AuditB1-1-de.json` | Firestore, then bundle | **not listed** |
| `GermanAuditB1-2` | `Quizzes/ReadinessAudit/AuditB1-2-de.json` | Firestore, then bundle | **not listed** |
| `GermanAuditB2-1` | `Quizzes/ReadinessAudit/AuditB2-1-de.json` | Firestore, then bundle | **not listed** |
| `GermanAuditB2-2` | `Quizzes/ReadinessAudit/AuditB2-2-de.json` | Firestore, then bundle | **not listed** |

### Reference quizzes (6)

| Name | Bundled asset | How it loads | sheet_versions |
|---|---|---|---|
| `GermanReferenceAdjectivesA1Quiz` | `Quizzes/Reference/GermanReferenceAdjectivesA1Quiz.json` | Firestore, then bundle | **not listed** |
| `GermanReferenceAdjectivesA2Quiz` | `Quizzes/Reference/GermanReferenceAdjectivesA2Quiz.json` | Firestore, then bundle | **not listed** |
| `GermanReferenceAdjectivesB1Quiz` | `Quizzes/Reference/GermanReferenceAdjectivesB1Quiz.json` | Firestore, then bundle | **not listed** |
| `GermanReferenceAdjectivesB2Quiz` | `Quizzes/Reference/GermanReferenceAdjectivesB2Quiz.json` | Firestore, then bundle | **not listed** |
| `GermanReferencePronounsQuiz` | `Quizzes/Reference/GermanReferencePronounsQuiz.json` | bundle-only (fallback; quiz normally generated from GermanReferencePronouns) | **not listed** |
| `GermanPrepositionsTeachingQuiz` | `Quizzes/Reference/GermanPrepositionsTeachingQuiz.json` | bundle-only | **not listed** |

### Quiz sheets (2)

| Name | Bundled asset | How it loads | sheet_versions |
|---|---|---|---|
| `QuizSheetPrepositions-de` | `Quizzes/QuizSheet/QuizSheetPrepositions-de.json` | bundle-only | **not listed** |
| `QuizSheetWordPairs-de` | `Quizzes/QuizSheet/QuizSheetWordPairs-de.json` | bundle-only | **not listed** |

### Bundled but never used (6)

- `Quizzes/Grammar/A1/GrammarPossessiveArticlesAIExample-de.json`
- `Quizzes/UsageQuiz/UsageQuiz2B2-en.json`
- `Quizzes/UsageQuiz/UsageQuiz3B2-en.json`
- `Quizzes/UsageQuiz/UsageQuiz4B2-en.json`
- `Quizzes/UsageQuiz/UsageQuiz5B2-en.json`
- `Quizzes/UsageQuiz/UsageQuiz6B2-en.json`

These can be deleted from the bundle:

- **`GrammarPossessiveArticlesAIExample`:** not in `GrammarCatalog`, though it is listed in `sheet_versions.json`.
- **`UsageQuiz<n>B2-en`:** English copies. A missing `-de` file would fall back to them, but every `-de` file exists.

## Other findings

- **Unused version entry:** `sheet_versions.json` has `GermanA1PossessiveArticlesAIExample`, which no quiz uses.
- **Name typo:** `sheet_versions.json` has `GermanPronouns2BothCase2`, but the manifest's sheet is
  `GermanPronouns2BothCases2`. That isn't a quiz, but its version number is never matched.
- **Non-quiz entries:** the other 37 entries are vocab, reference, podcast and conjugation sheets.
- **Release reads Remote Config:** release builds read the live Remote Config `sheet_versions`, not the asset file. The two need to be kept the same by hand.
