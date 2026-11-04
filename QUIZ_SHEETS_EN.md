# English quiz sheets (Android)

Every quiz sheet the English Android app (`en` flavour) loads, where it comes from, and whether it is
versioned. Worked out on 3 Nov 2026 from the code and from `app/src/en/assets/`. Not checked against
Firestore itself, so whether a doc is actually uploaded is still to confirm in the console.

## Summary

| Quiz type | Sheets | Bundled | In `sheet_versions.json` |
|---|---|---|---|
| Usage quiz | 24 | all | all (v1) |
| Grammar quiz | 70 | all | all (v1) |
| Section (vocab) quiz | 120 | all | all (v1) |
| Readiness Audit | 8 | all | **none** |
| Reference quizzes | 2 | all | **none** |
| Quiz sheets (Word Pairs, Prepositions) | 2 | all | **none** |
| **Total** | **226** | **226** | 214 |

- **Nothing is missing:** every sheet the app asks for has a bundled copy, so every quiz works with
  no internet.
- **33 bundled files are never used** (see the end).
- **12 sheets aren't versioned.** Only those that load from Firestore matter, see "Exceptions".

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

- **Pronouns quiz** (`EnglishReferencePronounsQuiz`): normally **generated in the app** from the
  `EnglishReferencePronouns` reference sheet, which loads through the chain above. The bundled
  `EnglishReferencePronounsQuiz.json` is read directly only as a fallback. **Never from Firestore.**
- **Prepositions quiz** (`EnglishPrepositionsTeachingQuiz`): read straight from the bundle.
  **Never from Firestore.**
- **Adjectives:** English has no Adjectives quiz file (`ReferenceQuizSheetMapping` enables no groups
  for en), so the Adjectives "Q" quiz is generated in the app from the adjective sheets.
- **Section quiz keys** come from the vocab category title (`SectionQuizKeyMap` has no English
  overrides): text in brackets is removed, then everything except letters and digits
  (`deriveSectionQuizKey`). Checked against the bundled `res/raw/vocab_data_<level>.json` titles:
  every category has a quiz file. If a Firestore vocab title differs from the bundled one, that
  category's Quiz button disappears.
- **Usage quiz region swap:** `UsageQuizViewModel.resolveLocalizedBaseName` has the region
  **hard-coded to `"in"`** (`val regionCode = "in"`), so it asks for `UsageQuiz<n><Level>-in` and
  finds `UsageQuiz1B1-in.json`. It has no effect, though: the doc name drops the suffix
  (`EnglishUsageQuiz1B1`) and the bundle fallback always opens the `-en` file. So the `-in` file is
  never read. This looks like a leftover test value.
- **Section quizzes** are found by listing the bundled folder
  `Quizzes/SectionQuiz/<Level>/WordQuiz<Key><n>-en.json` (n = 1, 2, 3 …, stopping at the first gap).
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
| Usage | `English` + `UsageQuiz<n><Level>` | `Quizzes/UsageQuiz/UsageQuiz<n><Level>-en.json` | `UsageQuizSheetMapping`; list in `en/.../UsageQuizLevelsFilename.kt` |
| Grammar | `English<Level><Key>` | `Quizzes/Grammar/<Level>/Grammar<Key>-en.json` | `GrammarSheetMapping`; list in `en/.../GrammarCatalog.kt` |
| Section | `EnglishSectionSheet<Level><Key><n>` | `Quizzes/SectionQuiz/<Level>/WordQuiz<Key><n>-en.json` | `SectionQuizSheetMapping`; titles → keys in `en/.../SectionQuizKeyMap.kt` |
| Audit | `English<Base>` | `Quizzes/ReadinessAudit/<Base>-en.json` | `ReadinessAuditSheetMapping`; list in `ReadinessAuditViewModel` |
| Reference | `EnglishReference<Key><Level>Quiz` | `Quizzes/Reference/<name>.json` | `ReferenceQuizSheetMapping` (no groups enabled for en) |
| Quiz sheet | none (bundle only) | `Quizzes/QuizSheet/QuizSheet<Name>-en.json` | `LanguageConfig.quizSheet…Filename` |


## The sheets

### Usage quizzes (24)

| Level | Firestore doc names | Bundled asset | sheet_versions |
|---|---|---|---|
| A1 | `EnglishUsageQuiz1A1` … `EnglishUsageQuiz6A1` | `Quizzes/UsageQuiz/UsageQuiz<n>A1-en.json` | all 1 |
| A2 | `EnglishUsageQuiz1A2` … `EnglishUsageQuiz6A2` | `Quizzes/UsageQuiz/UsageQuiz<n>A2-en.json` | all 1 |
| B1 | `EnglishUsageQuiz1B1` … `EnglishUsageQuiz6B1` | `Quizzes/UsageQuiz/UsageQuiz<n>B1-en.json` | all 1 |
| B2 | `EnglishUsageQuiz1B2` … `EnglishUsageQuiz6B2` | `Quizzes/UsageQuiz/UsageQuiz<n>B2-en.json` | all 1 |

### Grammar quizzes (70)

| Level | Count | Firestore doc names (`English<Level><Key>`) | sheet_versions |
|---|---|---|---|
| A1 | 10 | PresentSimple, PastSimple, FutureGoingTo, QuestionFormation, ModalsAbilityPermission, IndefiniteDefiniteArticles, PluralCountableNouns, AdjectiveOrder, PrepositionsPlaceTime, BasicConjunctions | all 1 |
| A2 | 16 | PresentContinuous, PastContinuous, PastHabitsUsedTo, FutureWill, WhQuestions, ModalsObligationAdvice, ZeroArticle, PersonalPossessivePronouns, AdverbsMannerFrequency, ComparativesSuperlatives, PrepositionsMovement, TimeReasonConjunctions, GerundsInfinitives, ZeroFirstConditional, PrefixesSuffixes, BasicLinkers | all 1 |
| B1 | 21 | PresentPerfect, PastPerfect, FutureContinuousPerfect, SubjectObjectQuestions, ModalsDeductionProbability, ArticlesNounsNames, QuantifiersDeterminers, AdjectiveAdverbDistinctions, ComparativeStructures, DependentPrepositions, ContrastConcession, VerbObjectInfinitive, SecondConditional, PresentPastPassive, ReportedStatements, DefiningRelativeClauses, CommonPhrasalVerbs, CommonCollocations, FormingNounsAdjectives, CohesiveDevices, TextOrganisation | all 1 |
| B2 | 23 | PresentPerfectContinuousAspect, NarrativeTenses, AdvancedFutureForms, IndirectQuestions, AdvancedModals, AdvancedArticles, AdvancedReference, AdvancedModifiers, AdvancedComparison, PrepositionalPhrasesIdioms, AdvancedLinking, AdvancedVerbPatterns, ThirdMixedConditionals, AdvancedPassive, ReportedQuestionsCommands, NonDefiningRelativeClauses, AdvancedPhrasalVerbs, AdvancedCollocations, AdvancedWordFormation, AdvancedDiscourseMarkers, CoherenceRegister, InversionEmphasis, CleftSentences | all 1 |

Bundled as `Quizzes/Grammar/<Level>/Grammar<Key>-en.json`.

### Section (vocab) quizzes (120)

| Level | Key (files) | sheet_versions |
|---|---|---|
| A1 | Adjectives (2), Colors (1), Dates (3), Everyday (3), Family (2), Hello (2), Numbers (4), Occupations (1), Places (2), Verbs (1), upinonatby (2) | all 1 |
| A2 | Education (2), FoodandDrink (2), Health (2), Home (2), Jobs (2), Leisure (2), Nature (2), PastTense (4), Patterns (3), Personal (2), Shopping (1), Social (2), Technology (1), Travel (2), Weather (2), inandbigquickly (5) | all 1 |
| B1 | Adjectives (3), Adverbs (3), ComplexSentences (2), Education (3), Emotions (3), Food (3), Health (3), Leisure (3), Nature (3), Personal (3), Relationships (3), Shopping (3), Social (3), Technology (3), Travel (3), Verbs (3) | all 1 |
| B2 | Adjectives (1), Adverbs (1), Education (1), Emotions (1), Entertainment (1), Health (1), Nature (1), PersonalDevelopment (1), Relationships (1), SocialIssues (1), Technology (1), Travel (1), Verbs (1), Work (1) | all 1 |

Doc name `EnglishSectionSheet<Level><Key><n>`, bundled as `Quizzes/SectionQuiz/<Level>/WordQuiz<Key><n>-en.json`.

### Readiness Audit (8)

| Name | Bundled asset | How it loads | sheet_versions |
|---|---|---|---|
| `EnglishBaselineAuditA2-1` | `Quizzes/ReadinessAudit/BaselineAuditA2-1-en.json` | Firestore, then bundle | **not listed** |
| `EnglishBaselineAuditA2-2` | `Quizzes/ReadinessAudit/BaselineAuditA2-2-en.json` | Firestore, then bundle | **not listed** |
| `EnglishAuditA2-1` | `Quizzes/ReadinessAudit/AuditA2-1-en.json` | Firestore, then bundle | **not listed** |
| `EnglishAuditA2-2` | `Quizzes/ReadinessAudit/AuditA2-2-en.json` | Firestore, then bundle | **not listed** |
| `EnglishAuditB1-1` | `Quizzes/ReadinessAudit/AuditB1-1-en.json` | Firestore, then bundle | **not listed** |
| `EnglishAuditB1-2` | `Quizzes/ReadinessAudit/AuditB1-2-en.json` | Firestore, then bundle | **not listed** |
| `EnglishAuditB2-1` | `Quizzes/ReadinessAudit/AuditB2-1-en.json` | Firestore, then bundle | **not listed** |
| `EnglishAuditB2-2` | `Quizzes/ReadinessAudit/AuditB2-2-en.json` | Firestore, then bundle | **not listed** |

### Reference quizzes (2)

| Name | Bundled asset | How it loads | sheet_versions |
|---|---|---|---|
| `EnglishReferencePronounsQuiz` | `Quizzes/Reference/EnglishReferencePronounsQuiz.json` | bundle-only (fallback; quiz normally generated from EnglishReferencePronouns) | **not listed** |
| `EnglishPrepositionsTeachingQuiz` | `Quizzes/Reference/EnglishPrepositionsTeachingQuiz.json` | bundle-only | **not listed** |

### Quiz sheets (2)

| Name | Bundled asset | How it loads | sheet_versions |
|---|---|---|---|
| `QuizSheetPrepositions-en` | `Quizzes/QuizSheet/QuizSheetPrepositions-en.json` | bundle-only | **not listed** |
| `QuizSheetWordPairs-en` | `Quizzes/QuizSheet/QuizSheetWordPairs-en.json` | bundle-only | **not listed** |

### Bundled but never used (33)

- `Quizzes/Quiz1Advanced-en.json`
- `Quizzes/Quiz1Advanced-hi.json`
- `Quizzes/Quiz1Elementary-en.json`
- `Quizzes/Quiz1Elementary-enLLM.json`
- `Quizzes/Quiz1Elementary-hi.json`
- `Quizzes/Quiz1Inter-en.json`
- `Quizzes/Quiz1Inter-hi.json`
- `Quizzes/Quiz1Upper-en.json`
- `Quizzes/Quiz1Upper-hi.json`
- `Quizzes/Quiz2Advanced-en.json`
- `Quizzes/Quiz2Elementary-en.json`
- `Quizzes/Quiz2Inter-en.json`
- `Quizzes/Quiz2Upper-en.json`
- `Quizzes/Quiz3Advanced-en.json`
- `Quizzes/Quiz3Elementary-en.json`
- `Quizzes/Quiz3Inter-en.json`
- `Quizzes/Quiz3Upper-en.json`
- `Quizzes/Quiz4Advanced-en.json`
- `Quizzes/Quiz4Elementary-en.json`
- `Quizzes/Quiz4Inter-en.json`
- `Quizzes/Quiz4Upper-en.json`
- `Quizzes/Quiz5Advanced-en.json`
- `Quizzes/Quiz5Elementary-en.json`
- `Quizzes/Quiz5Inter-en.json`
- `Quizzes/Quiz5Upper-en.json`
- `Quizzes/Quiz6Advanced-en.json`
- `Quizzes/Quiz6Elementary-en.json`
- `Quizzes/Quiz6Upper-en.json`
- `Quizzes/UsageQuiz/UsageQuiz1A1-enAI.json`
- `Quizzes/UsageQuiz/UsageQuiz1B1-eg.json`
- `Quizzes/UsageQuiz/UsageQuiz1B1-in.json`
- `Quizzes/UsageQuiz/UsageQuiz1B1-tr.json`
- `Quizzes/UsageQuiz/UsageQuiz1B1-vn.json`

These can be deleted from the bundle:

- **`Quizzes/Quiz<n><Level>-*.json` (28 files):** the old Usage-quiz naming (Elementary / Inter / Upper / Advanced), including `-hi` and `-enLLM` variants. Nothing reads them now.
- **`UsageQuiz1A1-enAI`:** an experimental copy.
- **`UsageQuiz1B1-eg`, `-in`, `-tr`, `-vn`:** regional variants. None is ever read (see "Usage quiz region swap" above).

## Other findings

- **Non-quiz entries:** all 32 entries in `sheet_versions.json` that aren't quizzes are vocab, reference, podcast, conjugation and the Spanish / Portuguese reference sheets. No stale quiz names.
- **Release reads Remote Config:** release builds read the live Remote Config `sheet_versions`, not the asset file. The two need to be kept the same by hand.
