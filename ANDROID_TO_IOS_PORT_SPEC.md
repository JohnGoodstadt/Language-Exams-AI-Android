# Android → iOS Port Specification

**Purpose:** Since **16 July 2026** a large body of new functionality has been added to the Android version of the exam‑prep app. On that date the iOS app (this project, `ChineseHSK`) was essentially feature‑equivalent to Android. This document is the to‑do list of everything to bring across to iOS.

**How to read this:** Each section is a self‑contained mini‑spec — a title, then what the feature does, how it works, how it touches the rest of the app, and gotchas. It is written **platform‑agnostic**: where Android uses a *swipe*, this doc says "slide from the left/right edge"; implement it with the natural iOS gesture. Descriptions avoid Android API names. Implement with Claude, section by section; the sections are ordered so that infrastructure comes before the features that depend on it. **Bug fixes are deliberately excluded** — they were Android‑specific and do not apply here.

**Suggested working order:** do §1 (remote content) first, then the gating/§12–15 group early (many later screens check it), then features in any order. Each section is independently shippable.

---

## 1. Cloud‑hosted content (move data out of the app bundle)

The single biggest change. Previously all quiz/reference/vocab data shipped inside the app bundle. Now the canonical source of that content is a **cloud document store** (one collection of "sheets", each identified by a logical name), fetched at runtime and cached locally. The app still ships a bundle copy as a **fallback** for first launch / offline.

- **Sheets & formats.** Every content set is a "sheet" stored under a well‑known path. Each sheet declares a **file format** number that dictates how it is parsed and displayed. Formats in use: `0` = categories + words (vocab lists); `1` = tabs + words‑and‑sentences joined by a parent id; `2` = flat words‑and‑sentences; `7`/`10` = sectioned quiz content; `13` = sectioned content with an "explain" field. The loader dispatches on this number; each format has its own parse path. When porting, replicate the dispatch table exactly — a sheet parsed with the wrong format silently yields empty data.
- **Local cache + versioning.** Sheets are cached to local files after first fetch. A lightweight remote "versions" map tells the app when a cached sheet is stale and should be re‑fetched. On a cold/offline start the bundle copy is used, then transparently upgraded once online.
- **Per‑language logical‑name prefix.** The app is multi‑language (English / German / Chinese are separate builds). Each build prefixes its logical sheet names with a language token (`English…`, `German…`, `Chinese…`) so the same content type in different languages never collides in the store. **This iOS project is the Chinese build → use the `Chinese` prefix everywhere.** Centralise the prefix in one place; every sheet‑name mapping reads from it.
- **Admin upload tool (developer‑only).** There is an internal screen that reads local JSON files and uploads them to the store, choosing the right sub‑structure per file format, and prefixing names per language. This is **debug/developer builds only** and must never be reachable in a shipping build. On iOS, gate it behind a debug build flag. Porting the uploader is optional — the content can also be uploaded once from Android and simply consumed by iOS — but you will likely want a way to push Chinese sheets. The screen is organised as collapsible **sections → subsections → per‑file rows**, each row an Upload + Read pair; Upload dispatches by the JSON's own `fileFormat`, Read round‑trips it as a sanity check, and a second Upload of an existing sheet is blocked (there's no Delete). Sources come from bundled assets or `res/raw`; a subsection whose files aren't bundled in the current flavour is dropped. Content types uploaded this way include the vocab lists, Grammar/Section/Usage/Baseline quizzes, the Reference sheets, and a **"Reference Quiz"** subsection (fileFormat‑7 adjective quizzes named `GermanReferenceAdjectives<level>` whose Firestore doc name is the JSON's own `sheetname`).
- **Gotchas:** (a) keep the bundle fallback so first launch works offline; (b) the versions check must **not block** the UI when offline — see §21; (c) get the format‑to‑parser mapping right before anything else, or every downstream screen shows empty lists; (d) **the uploader dispatches purely on the JSON's own `fileformat` field, but a file can be mislabeled.** A sheet's *real* type is how the app READS it (its manifest `sheetDataType` — e.g. Prepositions is `VocabFile` = format 0, read via the format‑0 path), not the number written in the file. A VocabFile mislabeled `fileformat:1` gets sent to the format‑1 parser, which looks for a `data[]` array that isn't there, and "uploads" 0 rows while still writing the header doc — after which the exists‑guard blocks any retry until you delete the doc. Validate the tag against the manifest type before uploading. (e) The format‑0 upload uses the `word` text as the word doc id; some sheets legitimately repeat a word within a category (Prepositions has three `zu (Dativ)` entries with different examples), so **disambiguate colliding ids** or two‑thirds of the rows silently overwrite. Reads don't depend on the doc id (they read fields and sort by `sortOrder`), so a suffix on collision is safe.

---

## 2. Readiness Audit (initial diagnostic)

A first‑run **diagnostic quiz** that gauges the learner's starting level. The user answers a run of questions (grouped, ~10 at a time), and the app builds a picture of readiness per skill area rather than a single pass/fail score.

- Questions are drawn from dedicated audit sheets (one set per level A1/A2/B1/B2). Each question carries a **category** so results can be summarised per topic area.
- After each block of ~10 questions an **inline summary** is shown before continuing, so the experience is chunked, not a single long form.
- Results feed a **confidence** notion (see §3) rather than a raw percentage — the wording deliberately talks about confidence/readiness, and higher levels are **locked** until earlier ones are attempted, producing a gradual confidence curve.
- **Cross‑effects:** the audit results are the input to the Progress and Focus screens. **Gotcha:** it must run acceptably on first launch from bundle data (before any cloud fetch), and its content should be **preloaded** where possible (see §16‑adjacent preload note in §1) so the first experience isn't slow.

---

## 3. Progress Screen (confidence, not percentage)

A screen that visualises the learner's **confidence per skill area** derived from audit + quiz history. Deliberately framed as *confidence/readiness* rather than a score, with a gradual curve so early users aren't discouraged.

- Presents per‑area indicators and an overall sense of readiness, updated as the user completes quizzes.
- Uses colour to communicate state (see the colour‑dot idea in §16 for the visual language).
- **Cross‑effects:** reads the same history/confidence store the audit writes. **Gotcha:** keep the confidence calculation identical to Android so users switching platforms see consistent numbers.

---

## 4. Focus Screen

A **triage/practice hub** that filters what the learner should work on next and routes them into the relevant quiz. From here the user can filter the content set and launch a quiz for a chosen category.

- Shows category rows with **scores/state**, supports **filtering** the list, and each row can **launch straight into the matching quiz screen** (e.g. a Grammar quiz for that category).
- In developer builds it also exposes content‑management shortcuts (a download button per row, a "Quiz" launch button) — keep those debug‑only on iOS.
- **Cross‑effects:** deep‑links into the Grammar/Section/Usage quiz screens (§5–7); reads progress/confidence (§3). **Gotcha:** the routing needs the target quiz to accept a "start on this category/level" parameter.

---

## 5. Grammar Quiz Screen

A dedicated quiz screen for grammar questions, loaded from cloud grammar sheets (not the bundle). Presents a question, accepts an answer, marks it, and can show an explanation of the grammar point.

- Content comes from grammar sheets named with the language prefix + a logical grammar name; the loader routes through the cloud content path (§1) with a bundle fallback.
- Supports a **single‑select chip filter** (see §8) so the user sees only questions from the chosen filter.
- **Gotcha:** logical sheet names must include enough of an "area" token to avoid clashes between grammar and other content types; keep the Android naming scheme so already‑uploaded sheets resolve.
- **Reuse this screen for Reference‑tab quizzes (don't fork it).** A Reference sub‑tab whose content is a fileFormat‑7 quiz (currently **Adjectives**) drives the SAME quiz screen/VM — same marking, mastery, TTS and filters — because the JSON is identical in shape to a grammar quiz. Only the file the loader resolves differs, so the loader takes an optional "reference group" mode: instead of the grammar catalogue → `Grammar<Key>-<lang>.json` lookup, it builds the logical/Firestore doc name directly as `<Lang>Reference<Key><Level>Quiz` (e.g. `GermanReferenceAdjectivesA1Quiz`), whose bundle fallback is `Quizzes/Reference/<name>.json` (the doc name IS the file stem — no `-de` suffix). The Reference **Adjectives** sub‑tab's *Quiz* button opens this screen in a bottom sheet exactly like Focus (§4) does. Gate which groups use this per flavour — only enable a group once its Reference‑quiz JSON actually ships in that language; other groups keep their in‑app generated quiz. The logical name doubles as the stats/mastery key.
- **Gotcha — derive the key from the sub‑tab's doc id, NOT the display title.** The grouped‑sheet display title is **localised** (de shows "Adjektive"), so you cannot use it to decide "has a quiz" or to build the file name. The stable, English key lives in the sub‑tab's `firestoreDocumentId` (`GermanA1Adjectives`): strip the flavour language prefix (German/English/Chinese) then the level to get the key ("Adjectives"), and read the level (A1) from the same id. Pass the localised title along only as the label the quiz screen shows. (This is why the first cut fell through to the generated quiz — it matched on "Adjektive".)

---

## 6. Vocab Quiz & Usage Quiz (with shared marking and term explanations)

Two quiz types — **Vocab** (word‑level) and **Usage** (word‑in‑context) — that share a **common marking flow** and can **explain the quiz terms** to the learner after answering.

- Both load from cloud sheets (formats `7`/`10`, and `13` where an "explain" field is present). The marking logic is unified across the two so behaviour is consistent.
- After answering, the app can show an **explanation of the term/usage**, sourced from the sheet's explain field.
- Both support **single‑select chip filters** (§8).
- **Cross‑effects:** these are launch targets from Focus (§4). **Gotcha:** the "explain" content only exists on format‑`13` sheets — guard the explanation UI when it's absent.

---

## 7. Section Quiz Screen

A quiz built from **sectioned** content sheets (formats `7`/`10`), where questions are grouped into named sections. Similar interaction to the other quizzes; the distinction is the sectioned data shape and the section‑aware presentation.

- Loaded via the cloud content path with the language‑prefixed logical name; bundle fallback for first run.
- **Gotcha:** keep the section‑key mapping consistent with the sheet names — a mismatch shows an empty quiz. (This mapping is the thing most likely to drift between platforms.)

---

## 8. Single‑select chip filters on quizzes

Across the Grammar, Vocab and Usage quizzes, the filter chips are **single‑select**: tapping a chip filters the visible questions to just that chip's set (rather than a multi‑select toggle). Selecting a chip re‑filters the list immediately.

- Applies uniformly to the three quiz screens. The selected chip drives the question set shown.
- **Gotcha:** ensure "no chip selected" has a defined meaning (typically: show all) and that changing chips resets any in‑progress question index cleanly.

---

## 8a. Quiz answer‑marking rules (applies to §5 Grammar, §6 Vocab/Usage, §7 Section)

Every quiz question shows a set of options; each option row has **three tappable elements**: a **loudspeaker/play icon**, the **answer sentence/text**, and a **radio button**. This section defines exactly what each tap does to the two on‑screen counters — **Correct** and **Tries** — because getting this wrong silently corrupts a learner's score. The rule is uniform across all quiz screens.

- **Two independent kinds of tracking, don't conflate them.** There is (a) the **visible session score** — the "Correct: X / Tries: Y" shown at the bottom of the quiz — and (b) **analytics + mastery tracking** (per‑word spaced‑repetition outcome, category strengths/weaknesses, global usage counts). Only the *answer* action touches the visible score; the loudspeaker touches neither. When porting, keep the "record one answer attempt" operation (which bumps Tries, recomputes Correct, and advances mastery/analytics) separate from the "just play audio" operation.
- **The answer sentence and the radio button behave *identically*.** Tapping the answer text is a full answer, exactly like selecting the radio. Do not treat tapping the sentence as merely "previewing" it. (On Android both now route through one shared submit handler — mirror that so the two paths can never drift.)
- **Scoring on an answer tap (radio or sentence), while the question is unsolved:**
  - **Tries** increments on every answer attempt (right or wrong).
  - **Correct** reflects the number of questions whose answer is correct — so a correct tap raises it, a wrong tap does not.
  - A **correct** tap also: highlights the answer in the sentence, plays the sentence audio, and records the mastery/analytics outcome (first‑try‑no‑hint = best; right‑after‑a‑wrong‑try = downgraded; etc.).
- **The loudspeaker is preview‑only, always.** It plays the option's sentence and **never** changes Correct or Tries and never records a mastery/analytics outcome. (This was the core bug being fixed: the loudspeaker must not count as answering.)
- **Marking finishes when the correct answer is chosen ("lock the question").** Once a question is answered correctly, it is done. Any further tap on that question — the correct option again, another option, or the loudspeaker — **only replays audio as a preview** and must **not** change Correct or Tries again. Implement this with a per‑question "solved" flag that resets when the user navigates to another question. Without the lock, re‑tapping a solved question keeps inflating Tries.
- **Worked example.** Question with options A (wrong), B (correct), C (wrong): tap A → Tries 1, Correct 0; tap C → Tries 2, Correct 0; tap B → Tries 3, Correct 1, question locks; tap B (or the loudspeaker) again → no change, just replays audio. A flawless run answers every question right first time → Correct = Tries = number of questions.
- **Answered state persists across navigation (until the quiz is exited).** Moving between questions — forward/back, manual or via auto‑advance, and across sub‑tab pages on the Vocab quiz — must **re‑show each question's previous answer** for checking: the selected option (green if it was correct, red if the last pick was wrong) and, for a correct answer, the filled‑in/highlighted sentence. Likewise the little paging dots reflect each question's result (correct/wrong/unanswered) and keep that colouring when the user leaves and returns to a page. Persist this by a stable question identity (the word/question text), **not** a page‑local index — a per‑page index collides across sub‑tabs. It all resets when the quiz is exited and reopened (a fresh attempt starts unanswered with counts at 0). Store *which option* was chosen, not just whether it was correct, or you can't restore a wrong pick's red state.
- **Completion fires once, at the true end of a paged quiz.** The paged Vocab quiz holds only the current sub‑tab page (≤10 questions) in view, so "did the user finish?" must be judged against the **whole** quiz, not the current page. Trigger the end‑of‑quiz behaviour (the reward/banner, success sound, saving the attempt) **only when the last question of the *last* sub‑tab page is answered** — i.e. last‑question‑of‑page **and** current page is the last available page. Judging completion by "current question ≥ page size" fires wrongly at question 10 of a 12‑question quiz (end of page 1) *and* again at 12. By the true end, Correct/Tries already span the whole quiz, so the "perfect run" check is evaluated against all questions. Single‑page quizzes (≤10) and filtered views still work because "last page" is then just the only/last page.
- **Gotchas:** (a) the per‑question "solved" lock is the subtle part — most scoring bugs come from its absence; (b) the loudspeaker must be fully inert for scoring, including not recording the per‑question category/analytics result (on Android, letting the loudspeaker record a result meant tapping "hear the correct option" logged a CORRECT before the user had actually answered); (c) audio playback everywhere is subject to **rate limiting** (§14) and the **already‑heard‑is‑free** rule (§12) — the preview path and the answer path both go through the same play call, so both are gated the same way.

---

## 8b. Per‑word mastery status and whole‑quiz (category) status

The Vocab quiz tracks two levels of learning status: a **per‑word** status (already present) and a **whole‑quiz / category** status (new — rolls the per‑word statuses up into one, so a future dashboard can show a single status dot per quiz).

**Per‑word status** (recorded as the user answers, persisted locally, keyed by word):
- Each answered word gets an **outcome** — *flawless* (right first try, no hint), *assisted* (right first try but the Info/hint was used), *stumbled* (right after a wrong try), *failed* (wrong / gave up) — which feeds a spaced‑repetition engine that assigns a **mastery level**: **New** (never attempted), **Struggling** (wrong repeatedly), **Learning** (right but took >1 try), **Review** (right first time, building a streak), **Mastered** (correct first‑time across 3 separate sessions/days). These are the five levels the mastery filter chips (§8) filter by.
- Navigating away from a question with wrong attempts but no eventual correct records it as *failed* (so it drops to Struggling) — leaving a question unfinished is itself a signal.

**Whole‑quiz (category) status** — one rolled‑up status for the entire quiz, so a dashboard can flag "this set has a problem" or "this set is mastered":
- Derived by aggregating the per‑word statuses of **all** the quiz's words (unattempted words count as **New**). The rule is **"worst‑attention wins, except Mastered needs every word"**:
  - **Mastered** — *every* word is Mastered (e.g. green dot),
  - **Struggling** — any word Struggling (e.g. red dot — "there's a problem here"),
  - **Learning** — some word Learning, none Struggling (e.g. orange dot),
  - **Review** — some word in Review, none Struggling/Learning (e.g. blue dot),
  - **New** — nothing attempted yet.
- Persist, per quiz (keyed by **level + category**), the rolled‑up status **plus the counts** behind it (total, and how many words are New / Struggling / Learning / Review / Mastered) and a last‑updated timestamp. The counts let a dashboard show more than a dot — e.g. "1 struggling of 10" in a tooltip.
- **Kept current two ways:** (a) when a quiz loads, register its **full** word list and compute the status up front (needed to tell "all Mastered" from "some still New", and to seed the dashboard before the user answers anything); (b) after each answered word, roll that word's new status up into its category status and re‑persist. Expose the whole‑quiz status reactively for the live screen, and provide accessors to read one quiz's status or enumerate **all** quizzes' statuses for the dashboard.
- **Cross‑effects / gotchas:** (a) aggregate using the *same* per‑word lookup the on‑screen per‑word badges use, so the whole‑quiz dot never disagrees with the individual dots; (b) the aggregation needs the quiz's complete word list — a status computed only from *answered* words can't distinguish "all Mastered" from "some never attempted", so register the full list on load; (c) the first consumer of this status is the section‑row dots in §8e; more dashboard‑style views can reuse the same persisted status + counts; (d) the whole‑quiz "Mastered" reuses the per‑word Mastered definition (3 correct‑first‑time sessions), so don't re‑define mastery at the category level.

---

## 8c. Whole‑quiz attempt history (dated goes)

A quiz can be attempted many times, so each **go** is saved as a separate dated record — distinct from the current rolled‑up status (§8b), which is only the latest snapshot.

- One attempt = a single go, from opening a quiz to closing it. Record it **when the quiz is closed**, capturing: category, level, **timestamp** (this is what separates attempts), total questions, distinct questions answered, distinct correct, total answer taps, and a **completed** flag (`answered >= total`, i.e. every question was reached — note this is "went through them all", not "all correct").
- **Persist all attempts**, keyed by level + category → a list ordered oldest‑first, kept across sessions. Don't record an empty go (opened and closed without answering anything). Completion is the interesting case (all questions attempted), but partial goes are saved too.
- Provide accessors to read one quiz's attempts and to enumerate all of them, for a future progress view. Per‑*word* attempts are already timestamped separately; this adds the missing *whole‑quiz* history on top.
- **Gotcha:** each open of a quiz should start a fresh go — reset the in‑session counters (Correct/Tries, answered set, position) on entry so an attempt's numbers reflect only that go, not a carry‑over from the previously‑opened quiz.

---

## 8d. Auto‑advance toggle ("A" button) and quiz nav buttons

An optional convenience on all three quiz screens (§5 Grammar, §6 Vocab/Usage): when on, answering correctly moves to the next question on its own, saving a tap.

- **The control** is an **"A" in a circle**, styled exactly like the existing **info "i"** button (a bold letter in a thin circle): **grey = off, blue = on**. Both buttons should share one renderer so they always match. It sits in the quiz's bottom nav row next to the info button.
- **One shared setting for all quizzes.** The toggle is a single persisted preference (default **off**); flipping it on any quiz applies to every quiz and sticks across launches.
- **Behaviour when on:** after a **correct** answer, wait for that answer's **audio to finish playing**, then advance to the next question — as though Next was tapped — so the learner hears and reads the sentence together. Wrong answers never auto‑advance. Provide a **safety timeout** (≈8s) so it still advances if no "playback finished" signal arrives (e.g. audio was rate‑limited or cached‑silent).
- **Guards:** don't advance past the last question; don't advance if the user has already navigated away during the audio (capture the question position when scheduling and re‑check before moving); and reset the per‑question "hint used" flag on the new question, exactly as the manual Next does. Refresh the info button's enabled state on every question change, including auto‑advances.
- **Gotchas:** (a) "advance after audio finishes" needs a real playback‑completion signal — the audio layer starts playback and returns immediately, so listen for its completion event rather than assuming the play call blocks; (b) ignore the "stopped" event that starting new audio emits when it stops the previous clip, or it will advance instantly; (c) on the paged Vocab quiz, auto‑advance must cross sub‑tab page boundaries just like the Next arrow, whereas Grammar/Usage are single‑page (next is simply the next question).

---

## 8e. Section‑row quiz status: streak stars, mastery dots, and a tap‑to‑explain legend

On the vocab list screen (the tabs where sections are chosen), each section's header row shows a small summary of that section's whole‑quiz progress, placed just before the section's "Quiz" button / quiz icon: either **gold streak stars** or **coloured mastery dots** (never both — see below). This is the first UI built on the persisted whole‑quiz status (§8b) and attempt history (§8c).

- **Stars vs dots are mutually exclusive, driven by the latest attempt:**
  - **Current flawless streak ≥ 1** → show **only gold stars** (1–3). A "flawless" run = the whole section completed with no errors (every question right, one tap each: correct == tries == total).
  - **Latest run had an error (or in progress)** → show **only the coloured mastery dots**.
  - **Never taken** → **nothing** (blank row).
- **Star streak rule (the achievement):** one star per flawless completion, but each counted completion must be **at least a day apart** from the previous one (so repeating a flawless run the same day doesn't add a star). Capped at **3 stars = section mastered**. The streak counts consecutive flawless completions from the most recent attempt backwards; a run with an error breaks it (back to dots). *(Debug builds substitute a 30 s gap for "a day" — see §16's debug‑spacing convention.)*
- **What the dots show** (when the latest run wasn't flawless), from the rolled‑up status/counts:
  - **fully mastered** (every word Mastered) → **one green dot**,
  - **otherwise** one dot per state present: **red** (any Struggling), **orange** (any Learning), **blue** (any Review), **green** (some but not all Mastered), **grey** (any still New / not finished).
- **Colours match the quiz's own per‑word mastery dots** (grey/red/orange/blue/green = New/Struggling/Learning/Review/Mastered), so the row summary and the in‑quiz dots read as one language; stars are gold.
- **Tap to explain:** tapping the dots/stars on a row opens a bottom sheet legend — a title, then one row per symbol (the symbol on the left, its meaning on the right): the gold star (flawless run, one per separate day), three stars (section mastered), then each dot colour. It must state the **one‑day‑apart** rule and that **3 stars = mastered**. (Only tappable when something is shown; a never‑taken section has no indicator.)
- **Keying:** section‑quiz progress is stored under the quiz KEY at the current level, not the section's display title — so map the row's display title → key the same way the quiz does before looking up its status/streak (see §7's section‑key mapping and §1's language prefix).
- **Live refresh:** the rows must re‑read status/stars when the user returns from a quiz. Drive this off the same "quiz progress changed" signal the status/attempts use (plus a level‑change signal), so a section updates as soon as its quiz is closed. Locked (premium‑gated) sections show nothing since they've not been taken.
- **Gotcha:** the counts behind the dots (total / New / Struggling / Learning / Review / Mastered) are already persisted, so the same row can later carry a tooltip or richer detail ("1 struggling of 10") without new plumbing.

---

## 9. Reference reports (Prepositions, Conjugations, Adjectives, Word Pairs, "Sounds the Same")

A family of **read‑only reference screens** rendering reference sheets. Includes Prepositions, **Conjugations** (multiple sub‑tabs, each with collapsible section headers), Adjectives, Word Pairs, and a "sounds the same" comparison sheet.

- Most are format‑`0` sheets (categories + words); Conjugations uses **sub‑tabs** with **collapsible headers** whose bodies expand/collapse.
- These screens are **subject to freemium gating** (see §12) — typically the first few sections/rows are open and the rest are locked for non‑premium users.
- **Cross‑effects:** all read from the cloud content path (§1) with the language prefix. **Gotcha:** long lists — the gating rule "show the first N sections, lock the rest" must be applied per sub‑tab, and small lists should not be gated so aggressively that nothing is visible.

---

## 9a. Reference tab lookup: sheet ⇄ bundle ⇄ Firestore ⇄ screen (de & en)

This maps every Reference‑tab item to the **bundle file** it ships as, the **Firestore doc name** it downloads as, the **fileFormat** it's parsed as, and the **screen** that displays it. Use it to find "which screen renders which sheet, and what is the file called on disk vs in the store".

**How the pieces fit together**

- **The manifest is the source of truth for structure and screen**, not the JSON's own `fileformat` field (which can be mislabeled — see §1 gotcha d). The manifest lives in the Remote Config key **`app_ui_manifest`**. Two ways it's loaded (Android — `AppConfigRepository.getAppUiManifest`): **DEBUG builds read the bundled `assets/debug_manifest.json`** (per‑flavour, uses that flavour's real doc names); **release builds read Remote Config**, falling back to the bundled default in `res/xml/remote_config_defaults.xml` (this default currently carries *English* placeholder names for both flavours — treat it as a fallback, not the de truth). The de‑accurate structure below comes from `src/de/assets/debug_manifest.json`; the en from `src/en/assets/debug_manifest.json`.
- **`sheetDataType` → fileFormat → parser/screen:** `VocabFile` = fileFormat **0** (categories→words→sentences), `Format1` = **1**, `Format2` = **2**, `Format3` = **3**, `fixed` = a hard‑coded screen (no downloaded sheet). `screenType` names the composable: `VocabScreen` (one format‑0 list), `GroupedVocabScreen` (several format‑0 sheets as sub‑tabs), `Format1Screen`, `GroupedFormat2Screen`/`GroupedFormat3Screen` (**several format‑2/3 sheets as sub‑tabs, usually 4**), `FixedScreen`/`VocabDashboard` (fixed).
- **Firestore doc name = language prefix + logical name** (`German…` for de, `English…` for en). **Bundle file** is a `res/raw` resource (no extension in code; `.json` on disk) or an `assets/…json` file; the app resolves logical → bundle via `LanguageConfig`/mapping tables and falls back to the bundle only when the download fails. "Firestore only" below means that flavour ships **no** bundle fallback for that sheet (first run needs the network).
- **Order** is the manifest's `layouts.referenceTab.order`. Fixed screens (Usage Quiz, Vocab Quiz, Conjugations) appear first; they are covered by §5–7 and the Conjugations screen respectively.

**de variant** (`src/de/assets/debug_manifest.json`; `languagePrefix = "German"`)

| Reference item (display) | Screen (`screenType`) | Firestore doc | Bundle file | fileFormat (`sheetDataType`) |
|---|---|---|---|---|
| Quiz zur Verwendung | `FixedScreen` (Usage Quiz, §6) | — | — | fixed |
| Vokabelquiz | `VocabDashboard` (§6/§7) | — | — | fixed |
| Konjugationen | `FixedScreen` → Conjugations screen | `GermanConjugationsToHave` / `…ToBe` / `…ToDo` / `…ToGet` (4 verb sub‑tabs) | `conjugations_to_have` / `…to_be` / `…to_do` / `…to_get` (res/raw) | read as **0** (`getFormat0Data`) |
| Präpositionen | `VocabScreen` | `GermanPrepositions` | `german_prepositions` (res/raw) | VocabFile = **0** |
| Adjektive | `GroupedVocabScreen` (4 sub‑tabs) | `GermanA1Adjectives` / `A2` / `B1` / `B2` | `german_a1_adjectives` … `german_b2_adjectives` (res/raw) | VocabFile = **0** |
| Gleich klingende Wörter | `Format1Screen` | `GermanSoundsTheSame` | `german_sounds_the_same` (res/raw) | Format1 = **1** |
| Wortpaare | `GroupedFormat2Screen` (4 sub‑tabs) | `GermanKennenWissen`, `GermanFragenBitten`, `GermanBringenHolen`, `GermanHoerenZuhoeren` | `german_kennen_wissen`, `german_fragen_bitten`, `german_bringen_holen`, `german_hoeren_zuhoeren` (res/raw) | Format2 = **2** |

> The de manifest's `order` also lists `SpanishLanguage`, but there is **no registry entry** for it in the de manifest, so it is **not shown** in the de Reference tab.

**en variant** (`src/en/assets/debug_manifest.json`; `languagePrefix = "English"`)

| Reference item (display) | Screen (`screenType`) | Firestore doc | Bundle file | fileFormat (`sheetDataType`) |
|---|---|---|---|---|
| Usage Quiz | `FixedScreen` (Usage Quiz, §6) | — | — | fixed |
| Vocab Quiz | `VocabDashboard` (§6/§7) | — | — | fixed |
| Conjugations | `FixedScreen` → Conjugations screen | `EnglishConjugationsToHave` / `…ToBe` / `…ToDo` / `…ToGet` (4 verb sub‑tabs) | `conjugations_to_have` / `…to_be` / `…to_do` / `…to_get` (res/raw) | read as **0** (`getFormat0Data`) |
| Prepositions | `VocabScreen` | `EnglishPrepositions` | `prepositions_en` (res/raw) | VocabFile = **0** |
| Spanish | `GroupedFormat3Screen` (4 sub‑tabs) | `SpanishReferenceSheet1` … `SpanishReferenceSheet4` | `SpanishReferenceSheet1.json` … `4.json` (assets) | Format3 = **3** |
| Adjectives. | `GroupedVocabScreen` (4 sub‑tabs) | `EnglishA1Adjectives` / `A2` / `B1` / `B2` | Firestore only (no en bundle) | VocabFile = **0** |
| Sounds the Same | `Format1Screen` | `EnglishDefinitionsFormat1` | Firestore only (no en bundle) | Format1 = **1** |
| Pairs | `GroupedFormat2Screen` (7 sub‑tabs) | `EnglishGoodVsWell`, `EnglishSayVsTell`, `EnglishSpeakVsTalk`, `EnglishHearVsListen`, `EnglishBorrowVsLend`, `EnglishBringVsTake`, `EnglishLookVsSee` | Firestore only (no en bundle) | Format2 = **2** |

> `SpanishLanguage` is registered in the **en** manifest (Spanish pronunciation reference), so it **does** appear in the en Reference tab. The en app also bundles `PortugeseReferenceSheet1–4.json` in assets, but they aren't in the en `order`, so they aren't shown.

**Porting notes**

- Reproduce the **`app_ui_manifest`** structure on iOS and drive the Reference tab from it (order + per‑item screen/type/doc), rather than hard‑coding the list — that's how new sheets get added without a release.
- Keep the **bundle ⇄ Firestore name mapping** in one place (Android splits it across `LanguageConfig` for conjugations and the upload catalogue for the rest). The de bundles are `res/raw` snake_case; the Spanish sheets are `assets`; several en sheets are Firestore‑only, so an offline first run in en will show them empty until fetched — decide whether iOS should ship those bundles.
- Conjugations and Prepositions both parse as **format 0** even where the bundle JSON's `fileformat` field says otherwise — trust the screen's loader (`getFormat0Data`), not the file's tag.

---

## 10. Word of the Day

A rotating **single featured word** surfaced on the Me tab, giving a daily focal item (word + its detail/sentences).

- Chooses a word per day from the vocab content and displays it richly (word, definition, pronunciation, example sentences).
- **Gotcha:** the "per day" selection must be deterministic for a given date so it doesn't change on every open; base it on the date, not a random pick per launch.

---

## 11. AI‑generated practice paragraph

A feature that calls a language model to generate a **short practice paragraph** for the learner (contextual reading practice). The call is made to a configured LLM provider, with the request/parameters driven by remote config.

- The model provider and parameters are configurable remotely (the app supports more than one provider). Calls are **only made when online** (§21).
- **Cross‑effects:** shares the online/offline guard and remote‑config plumbing. **Gotcha:** these calls cost money and are a candidate for **rate limiting** (§14); handle provider errors/timeouts gracefully and never block the UI waiting on the model.

---

## 12. Freemium access policy (centralised gating)

A **single decision point** ("AccessPolicy") that decides what a non‑premium user may access. Rather than scattering `if premium` checks across screens, all screens ask this one component. This is central to monetisation and touches many screens.

- **Content areas.** Content is classed as **Vocab** or **Reference**. Rules:
  - **Vocab:** levels **A1 and A2 are entirely free**. **B1 and B2** are preview‑limited for non‑premium users.
  - **Reference:** preview‑limited for all non‑premium users.
- **Two kinds of limit:**
  - **Row/preview limit** — show the first *N* rows/items in full, lock the rest (Vocab preview ≈ 15, Reference preview ≈ 10 — treat as tunable constants).
  - **Section limit** — show the first *N* collapsible **sections**, lock the rest (Vocab ≈ 2 sections, Reference ≈ 6 sections — again tunable; note Reference/Conjugations deliberately allows more).
- **Vocab tab locking.** On the main vocab tabs (the first three tabs), for non‑premium users the **top 2 sections of each tab are unlocked and the rest locked**. A **lock indicator replaces the normal per‑row icon** on locked rows, and locked sections show a teaser rather than content.
- **Already‑heard content stays free.** A crucial rule: **audio the user has already played can always be replayed**, even if the row would otherwise be locked. Gating restricts *new* consumption, not revisiting.
- **Cross‑effects:** every reference screen (§9), the vocab tabs, and quizzes consult this. **Gotchas:** (a) the gate must re‑evaluate when premium status changes at runtime (see §15) so locks lift immediately after purchase; (b) keep the preview constants in one place; (c) make sure the "already‑heard ⇒ always replayable" exception is honoured everywhere or users will feel cheated.

---

## 13. In‑app purchase upgrade sheet

A **bottom‑sheet / modal upgrade prompt** ("Upgrade to Exam Mastery") that appears when a non‑premium user hits a lock, presenting the IAP that unlocks everything.

- Triggered from locked rows/sections. Explains the benefit and offers the purchase. On successful purchase, premium status flips and all gating (§12) lifts immediately.
- **Cross‑effects:** wired to the purchase/billing layer; premium state feeds §12 and §15. **Gotcha:** iOS uses StoreKit — this is a platform‑specific reimplementation, but the **UX and trigger points** should match: same copy intent, same "lock tapped → offer upgrade" flow, same immediate unlock on success.

---

## 14. Rate limiting (cost control)

A limiter that **caps how many costly operations** (notably audio synthesis / LLM calls) a user can trigger in a window, to control cost and nudge toward the IAP. Live limits are roughly **30/hour and 80/day** (treat as configurable).

- A central limiter is consulted before costly operations proceed; over‑limit requests are refused or deferred with appropriate messaging.
- There is a **developer toggle** to switch rate‑limit testing on/off without shipping test limits — production uses the live numbers, developers can force a low limit to test the path. Keep this debug‑only on iOS.
- **Cross‑effects:** applies to the new quiz/reference audio and the AI paragraph (§11). **Gotcha:** ensure **already‑cached / already‑heard** audio does **not** count against the limit (consistent with §12's replay rule), and that premium users bypass the limit.

---

## 15. Runtime premium override (developer aid) + Settings toggle

A **debug‑only** mechanism to force premium on/off at runtime, flippable from the Settings screen, so gating (§12) can be tested without real purchases.

- A debug flag/observable that `AccessPolicy` reads; a Settings button ("Force Premium") flips it live and the UI re‑evaluates locks immediately.
- **Must be compiled out / unreachable in shipping builds.** On iOS, gate behind a debug build configuration. **Gotcha:** the whole point is that toggling it **recomposes/refreshes** the gated screens immediately — verify locks visibly lift/return on toggle.

---

## 16. Spaced‑repetition "played" dots

A per‑item visual indicator showing how far a word has progressed through a **spaced‑repetition** schedule, drawn as a small coloured dot that advances **red → amber → green** as the user replays the item **across three separate days**.

- **The rule is calendar‑day based (three separate days), not elapsed‑time based.** Each play can advance the item **one stage**, and only when the hearing falls on a **strictly later local calendar day** than the last counted play:
  - **red** = the 1st hearing (any day),
  - **amber** = a hearing on a **later day** than the red day,
  - **green** = a hearing on a **later day** than the amber day.
  So replaying the same word many times in one day never advances the colour — the three colours are earned on three distinct, increasing days. It must **not skip stages** (one stage per hearing at most).
- **Day = the device's local calendar day.** A hearing at 23:59 followed by one at 00:01 counts as two days (correct for this rule). The very first hearing always qualifies (there is no prior day to compare against).
- **Debug testing:** real days can't be waited out, so **debug builds substitute a short seconds gap (~30s) for "a later day"** so red → amber → green can be exercised in a minute; **release builds use the true calendar‑day comparison**. Keep these two paths clearly separated.
- The dot is **drawn** (not an emoji/character) so amber and green render cleanly; colours: red `#E53935`, amber `#FFB300`, green `#43A047`.
- The spaced‑repetition state is stored **locally only** (it is not part of any cloud history sync payload); per item it need only persist the current stage (0–3) and the timestamp of the last counted play.
- **Cross‑effects:** appears on the vocab rows alongside the normal play indicator. **Gotchas:** (a) the "one stage per day, gated by a later calendar day" rule is the subtle part — compare *days*, not elapsed seconds; (b) keep the debug‑seconds vs release‑day paths separate; (c) it reads the same "has this been played" history used elsewhere; (d) this was originally built as hour‑based gaps (immediate / ~1h / ~3h) and later simplified to the day‑based rule described here — implement the day‑based version, not the old time gaps.

---

## 17. Slide‑to‑Save a word for practice

On the vocab rows, the user can **slide the row from one edge to reveal a "Save" action** (Android uses a left‑swipe; on iOS use a leading/trailing swipe action). Saving adds the word to a personal practice list; sliding Save on an already‑saved row removes it (toggle). The opposite‑edge slide reveals a secondary "More" action.

- Saving stores the **entire word entry** (word, definition, pronunciation, **all its sentences**) — not just a key — so the Saved screen (§18) can render it richly without re‑loading the source sheet.
- The list is **per level** (A1/A2/B1/B2 each have their own saved list) and stored **locally**.
- On save, an **informational sheet** appears (see §19) explaining what happened and offering reminder options.
- **Cross‑effects:** feeds the Saved screen (§18) and reminders (§19). **Gotcha:** decide the storage key (the word text) and keep it stable; the toggle relies on it.

---

## 18. "Saved" screen (on the Me tab)

A new sub‑screen on the **Me** tab listing everything the user has saved for practice (§17), positioned between the existing "Focus" and "Vocab" entries in the Me‑tab menu. Each entry shows the **word as a heading** (styled distinctly — Android uses a cyan heading to match the highlighted word in sentences), then its definition, pronunciation, and its **sentences (usually 3)** which are **tappable to play**.

- **Level‑aware:** shows only the currently‑selected level's saved list; switching level changes the list.
- **The section/category title is not shown** on this screen (it's stored but hidden).
- Each entry has a **bordered "Remove" button** (visually distinct, error‑coloured) below its sentences that removes the entry from the list **and cancels any pending reminder** for it (§19).
- **Sort order (important, mirrors an email inbox):** newest‑saved at the top by default; **but any entry whose reminder time has passed floats to the top** with a small grey **"REMIND ME"** label on the right of the word line. Playing any sentence on such a row **clears the label** and drops it back into normal order.
- **Cross‑effects:** reachable directly from a reminder notification (§19). **Gotchas:** (a) the triggered‑to‑top re‑sort is evaluated against the clock when the screen appears; (b) removing/ playing must also clear the reminder state so the label and sort stay consistent.

---

## 19. Local practice reminders ("Remind me…", inbox‑style)

After saving a word (§17) the user is offered **email‑style reminder options** — *Remind me in 1 hour, tonight, tomorrow, later, don't remind me* — and the app schedules a **local notification** for the chosen time. When it fires, the notification reads **"It's time to practice \<word\>"** / "You saved this to practice later", and tapping it opens the app on the **Me › Saved** screen.

- Choosing an option both **schedules the local notification** and **records the due‑time on the saved entry**, which drives the "REMIND ME" label and the float‑to‑top sort on the Saved screen (§18). "Don't remind me" clears any reminder.
- The reminder is **cleared** (label removed, pending notification cancelled) when the user **plays a sentence on that row** (counts as acknowledged) or **removes** the entry.
- The **on‑screen label and the system notification are independent** mechanisms aimed at the same time: the label is computed from the stored due‑time; the notification is an OS‑scheduled alert. Both are cleared by the same "played / removed" actions.
- Debug builds use **short delays** (tens of seconds) instead of real hours so the flow can be tested quickly.
- **Cross‑effects / gotchas:** (a) iOS requires **notification permission** — request it at the right moment and handle denial (no notification, but the in‑app label still works); (b) tapping the notification should **deep‑link** to Me › Saved; (c) note the deliberate design choice: tapping/dismissing the notification does **not** clear the "REMIND ME" label — only actually **playing** the word does, so the row keeps nagging until practised (decide if iOS should match or also clear on tap); (d) reminders are keyed by the word so re‑saving/re‑scheduling replaces cleanly.

---

## 20. Global loading indicator (delayed)

A **centralised loading spinner** shown during content fetches, with a **short delay before it appears** (Android uses ~2s) so quick loads don't flash a spinner.

- Used by the cloud content fetches (§1) and quiz loads. **Gotcha:** the delay‑before‑show is the point — don't show it for sub‑second loads; hide it as soon as content resolves.

---

## 21. Offline resilience

The app must **not hang or block when offline.** Remote‑config and remote content/version checks are guarded by an **"are we online?" check** and skipped (using cached/bundle data) when offline, instead of blocking on a network call.

- Applies to: the content version check (§1), the AI paragraph and any LLM/provider config calls (§11).
- User‑facing: on an action that needs the network while offline, show a **transient message ("you're offline") with a Retry** affordance rather than a dead button.
- **Gotcha:** the classic failure is an activate/fetch call that blocks ~60s offline — always gate it behind the connectivity check first.

---

## 22. Settings: "About the app" copy + upgrade explanation

Two short explanatory paragraphs on the Settings screen, above the version row: (1) a brief **statement of the app's aim** — targeted at exam takers, especially **citizenship exams**; and (2) a short explanation of the **free‑to‑download model** — A1 and A2 completely free, B1 and B2 partially locked, with an upgrade for fully unrestricted access. Each sits on its own row with an appropriate leading icon.

- **Cross‑effects:** the second paragraph is the plain‑language version of the gating in §12 — keep the wording consistent with the actual limits. **Gotcha:** none technical; just keep the copy truthful to the current free/locked split.

---

## 23. Translate sheet (German ⇄ English)

A reusable bottom sheet that translates between the two languages, à la Google Translate. Shown from a **debug‑only** Settings button (below "Upload JSON") **and** from a button on the vocab tabs (§8/CategoryTab), and built to be shown from anywhere.

- **Separate translation module.** All translation goes through one UI‑agnostic component (`translate(text, source, target) -> Result<String>`). It calls the **Google Cloud Translation API (v2 REST)** with the app's Google Cloud API key — the **same key the flavour uses for Text‑to‑Speech** — so the key's project must have the **Cloud Translation API enabled**. It HTML‑unescapes the result (the API escapes a few characters even in text mode). Keep this the single entry point so other screens can translate later without duplicating the call.
- **UI (basic for now).** Title "Translate", a subtitle, a **swap** control between the two language labels (German→English ⇄ English→German), a **source** text box, a full‑width **Translate** action placed low (near the thumb) with a progress state, an error line, and a **target** result area. Swapping carries the two texts across as well (like Google).
- **Launch points + prefill.** On the vocab tabs the launcher is a **"T" in a circle**, left‑justified in the stats row (mirroring the stats button on the right). When opened from there, the source box is **pre‑filled with the last sentence the user played** on that tab (blank if none). The source language is set to the app's own language (played sentences are in it). The remembered sentence is **cleared when the user leaves the tab**, so a later open starts blank. Opened from Settings it's simply blank. (Implement prefill as an argument to the sheet, applied once per open.)
- **Speak the result.** When the **target is German**, tapping the result speaks it through the existing audio pipeline (the TTS/audio playback used by the quizzes). Gate speech to German because the flavour's TTS voice is the app's own language voice — don't speak English with a German voice (and vice versa). On iOS use the same rule against whatever voice the build ships.
- **Microphone input (optional).** A mic button dictates the source text using the **system speech dialog** (the OS recognizer handles the mic, so no microphone permission is needed in the app), with the recognizer language set to the current source language. Show the button only when a recognizer is available; it degrades gracefully when not. (On Android this also needed a manifest `queries` entry so the recognizer is visible on newer OS versions — the iOS equivalent is `SFSpeechRecognizer`/`Speech.framework`, which *does* require a usage‑description + permission, so treat iOS mic as the permissioned path.)
- **Gotchas:** (a) the API key ships in the app, so keep translation behind the same cost/abuse thinking as TTS if it's ever exposed beyond debug (rate limiting, §14); (b) it needs network — fail gracefully offline (§21) with the error line; (c) keep the module UI‑free so the sheet, and future callers, share one implementation.

### 23a. API key handling (learned the hard way — applies to TTS *and* Translate)

- **One Google Cloud API key per language project, per flavour.** Move off any shared/legacy project: each language (English, German) gets its own Google Cloud project (reuse that language's Firebase project) with **both Cloud Text‑to‑Speech and Cloud Translation enabled** and billing active. The flavour compiles its own key; **the same key serves TTS, the TTS voice list, and Translation** for that language. Android delivers it per‑flavour (a `GOOGLE_API_KEY` build field). iOS: put each language's key in that target's config, never committed.
- **Enable APIs per project, not per app.** All flavours sharing one key = enable once on that project. Separate keys/projects = enable on each. Usage/billing are tracked **per API per project** automatically; the key isn't a billing dimension — separate **projects** are how you split English vs German spend.
- **Locking a key to your app (Android "application restriction").** A key can be restricted to your app by **package/bundle id + signing‑certificate SHA‑1**. For raw REST calls you must send two headers or the key returns `403 API_KEY_ANDROID_APP_BLOCKED`:
  - `X-Android-Package` = the app id,
  - `X-Android-Cert` = the signing cert's SHA‑1.
  Compute the SHA‑1 **at runtime from the running build's own signature** so it matches whichever cert signed it (debug vs release) automatically — don't hard‑code fingerprints.
- **⚠️ The colon trap (cost hours if missed):** the Cloud **Console stores/displays the SHA‑1 with colons** (`DA:39:…`), but the **`X-Android-Cert` header must be the bare hex with the colons stripped** (`DA39…`). Sending the colon form fails even when everything else is correct. So: **Console = colons, header = no colons.**
- **Certs are per build type, not per flavour.** All debug variants share one SHA‑1; all release variants share another. Register the *package name* per flavour but the **same debug + release SHA‑1s**. With **Play App Signing** the on‑device release cert is Google's **per‑app** key (different per package) — get that SHA‑1 from each app's Play Console.
- **iOS equivalent:** restrict each key to the app's **bundle id** (iOS app restriction) — no per‑request header dance, but it's still a client‑side key. **End state for both platforms:** proxy TTS/Translate through your own backend (with App Check/Auth) so keys never ship in the app and can be rotated without a release.

---

## 24. Play a whole section in sequence (vocab tabs)

A **Play ⇄ Pause** button on each section header (the vocab tabs 1/2/3, placed right after the section title) plays every sentence in that section back‑to‑back.

- **What plays:** the **first sentence of each word** in the section (the one shown on the row), in order — not every sentence of every word.
- **Single‑track waterfall reused.** Each sentence resolves through the existing per‑sentence audio waterfall — **local disk → Cloud Storage (download) → Google TTS (generate → save to disk → upload to cloud)** — and then plays. A section with 10+ sentences shouldn't run that full waterfall serially with a stall before each track, so:
- **Prefetch pipeline (the point of the feature).** Add a "resolve to disk **without** playing" variant of the waterfall. Then play as a pipeline: resolve track 1, play it, and **while it plays resolve the next track in the background**; when the current track finishes, the next is already cached and plays immediately. Rolling one‑ahead (not resolving all at once) deliberately avoids a burst of simultaneous TTS/network calls. For an all‑cached section this is gapless; only a track whose generation outlasts the previous track's audio adds a small gap. Each track is resolved by exactly one prefetch, so nothing is generated twice.
- **True sequencing needs a completion signal.** The play call starts a track and returns immediately (it doesn't block until the audio ends), so between tracks you must wait for the audio layer's **playback‑finished event** (natural completion or failure) before starting the next — with a per‑track safety timeout so a missed event can't hang the run. (Same event bus used by auto‑advance, §8d.)
- **History stays consistent.** Play each track through the normal single‑sentence play path (which, for a prefetched track, is an instant local‑cache hit) so it still marks the sentence heard and awards XP — exactly as tapping it would.
- **Run the same post‑play bookkeeping a tap does (the row's coloured dot + progress bar).** The play call alone records the raw heard/play stats but does **not** advance the row's **spaced‑repetition dot** (red→amber→green), bump the tab's **progress bar**, or refresh the row — on Android a single‑row tap does that afterwards. So the reusable player exposes two hooks the caller wires to the *same* logic a tap runs:
  - **`onTrackStarted(sentence)`** — fires just *before* the play. Snapshot whether the sentence was already heard here (the play marks it heard, so you must capture it first), and remember the last‑played sentence.
  - **`onTrackPlayed(sentence)`** — fires *after* each track plays successfully. Run the per‑track bookkeeping (`didPlayVocabSentence`: `recordSpacedPlay`, vocab stats on first hear, refresh UI), and — **only if the pre‑play snapshot said it was NOT already heard** — increment the tab's heard count so the progress bar advances on first hearings, exactly as a tap does.
  Without these, a section play leaves the dots and progress bar un‑updated while a manual tap of each row would have moved them.
- **Section‑complete banner fires ONCE, at the end.** Don't run the section‑completion check per track. The player exposes an **`onCompleted()`** hook that fires once, *only when the whole list played through* (not on stop/cancel/failure). Run the normal section‑completion check there (it's idempotent — exits early if already complete, fires the banner once): by the end every sentence has been heard, so it triggers the "Section Mastered / Level Complete" banner exactly as the final tap would.
- **Stop conditions:** tapping the same section again (Pause), leaving the tab, or the app going to the background all cancel the run — cancelling stops the current audio and clears the playing state. Only one section plays at a time (starting one stops another).
- **Rate‑limiting policy:** check the rate limit **once, at the start** (non‑premium only). If over the limit, show the paywall and don't start. Once started, **no check interrupts the playlist** — a section's cost is minimal. **Playback stats are still counted per track** (each play increments the usual play/heard stats via the single‑sentence play path).
- **Reusable, UI‑agnostic component (don't duplicate this).** The whole pipeline — prefetch‑ahead, single‑track play path, completion‑await, cancellation, and a `playingKey` state — lives in **one app‑wide component**, not in the screen or its view‑model, so other lists (e.g. the Reference screen) can play a sentence sequence by calling the same component. On Android this is `SequencePlayer` (a `@Singleton`, injected; owns its own coroutine scope so it's independent of any screen's lifecycle). Its API is essentially: `toggle(key, sentences, level, preflight, onTrackStarted)`, `stop()`, and an observable `playingKey`. The **caller** keeps only screen‑specific decisions: *which* sentences (here, each word's first sentence), the rate‑limit `preflight` gate (return false to abort before playback), and an `onTrackStarted` hook (here, remembering the last‑played sentence for the Translate prefill). iOS: mirror this with one shared object (e.g. an `@MainActor` `ObservableObject` / actor) that view‑models call, rather than reimplementing the loop per screen.
- **Gotchas:** (a) that one‑time gate means an all‑cached section is still blocked when the user is over the limit even though it costs nothing — acceptable per the policy above; (b) a single‑row tap during a section run fights the one shared player — either stop the section on a row tap or disable row taps while it runs; (c) the spoken voice is the flavour's own TTS voice, so this is for the app's own language content; (d) because the shared player is a singleton with one `playingKey`, only one list plays app‑wide — starting a Reference list will stop a vocab section and vice‑versa (intended).

---

## Appendix — mapping to Android commit history (for traceability)

| Feature (this doc) | Android commit signposts (dates) |
|---|---|
| §1 Cloud content + upload | Upload skeleton, AI helping upload, file types 7/10/13, reference uploads (Aug 19–22) |
| §2 Readiness Audit | Readiness Audit Screen, Readiness quiz working, locked quizzes (Jul 17–25) |
| §3 Progress | Progress displaying, confidence not progress, inline summary (Jul 20–27) |
| §4 Focus | prepare/​filter Focus screen, focus → quiz, scores on focus (Aug 13–16) |
| §5 Grammar Quiz | new quiz screen GrammarQuiz, generate grammar quizzes (Aug 15–16); reused the same screen/VM for the Reference **Adjectives** sub‑tab quiz — loader gained a "reference group" mode building `<Lang>Reference<Key><Level>Quiz` (bundle fallback `Quizzes/Reference/`), gated per flavour (de only for now); Adjectives *Quiz* button opens it in a bottom sheet like Focus (Sep 9) |
| §6 Vocab/Usage Quiz | combined marking, explain quiz terms, Usage/Vocab quiz files (Aug 16–18) |
| §7 Section Quiz | section quizzes, reorg file names for section quizzes (Jul 31, Aug 14) |
| §8 Single‑select filters | "Filters on 3 quizzes are now single select" (Aug 24) |
| §8a Quiz answer‑marking rules | radio/sentence score identically, loudspeaker preview‑only, lock on correct; answered state (selection + paging dots) persists across navigation by word until exit (Sep 5); completion/banner fires once at the last question of the last sub‑tab page (Sep 7) |
| §8b Per‑word + whole‑quiz status | per‑word mastery levels; new category (whole‑quiz) rolled‑up status + counts, persisted for a future dashboard (Sep 5) |
| §8c Whole‑quiz attempt history | dated per‑go records (timestamp/total/answered/correct/tries/completed), persisted per quiz (Sep 5) |
| §8d Auto‑advance toggle | shared "A" button (grey/blue), one persisted setting for all quizzes, advances after audio finishes (Sep 5) |
| §8e Section‑row status dots | per‑section coloured dots on the vocab list rows summarising each quiz's whole‑quiz status (Sep 5); added gold streak stars (flawless runs a day apart, 3 = mastered), stars/dots mutually exclusive, and a tap‑to‑explain legend sheet (Sep 6) |
| §9 Reference reports | prepositions/adjectives/pairs, conjugations, reference reports (Jul 31–Aug 22) |
| §10 Word of the Day | ready for WOTD, WOTD (Aug 22) |
| §11 AI paragraph | AI paragraph working (Aug 23) |
| §12–13 Freemium + IAP | Added locked parts, conjugations locked, lock tabs 1‑3, Bottom sheet buy IAP (Aug 26) |
| §14 Rate limiting | rate limiting, Rate Limit Debugging (Aug 25–26) |
| §15 Premium override | Fix premium user bug / debug override (Aug 26) |
| §16 Spaced dots | "3 played dots – spaced apart" (Aug 27); simplified to a 3‑separate‑days rule (Sep 5) |
| §17 Slide‑to‑Save | "Swipe right to save" (Aug 27) |
| §18 Saved screen | Saved screen + Remove/label work (Aug 27–28) |
| §19 Reminders | "Remind me done" (Aug 28) |
| §20 Global loading | global loading indicator for Usage Quiz (Aug 21) |
| §21 Offline | "RC fetch only if online" (Aug 26) |
| §22 Settings About | "Explanation text on settings" (Aug 26) |
| §23 Translate sheet | reusable German⇄English translate sheet via Google Cloud Translation API, tap‑to‑speak German, optional mic; Settings + vocab‑tab "T" launcher with last‑played prefill (Sep 7) |
| §23a API key handling | per‑language‑project keys per flavour (TTS+Translate), Android app restriction, and the Console‑colons / header‑no‑colons X‑Android‑Cert rule (Sep 7) |
| §24 Play section in sequence | section‑header Play/Pause plays each word's first sentence back‑to‑back (resolve‑ahead prefetch pipeline); rate limit checked once at start, plays still counted; stops on re‑tap/leave/background (Sep 9); extracted the pipeline into a reusable singleton `SequencePlayer` (UI/VM‑agnostic) so Reference and other lists reuse it — caller supplies sentences + rate‑limit preflight + onTrackStarted/onTrackPlayed/onCompleted hooks; per‑track hook runs the same bookkeeping as a tap (`didPlayVocabSentence`) so the row's dot advances and the progress bar bumps on first hearings; onCompleted fires once at the end (natural completion only) for the section‑complete banner; help‑sheet trigger deliberately omitted (Sep 9) |

*Bug‑fix commits are intentionally omitted.*
