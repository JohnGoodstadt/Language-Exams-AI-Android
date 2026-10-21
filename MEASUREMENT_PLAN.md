# Measurement Plan — Reference-tab engagement

> Companion to `ANALYTICS_STRATEGY.md`. That doc is the *why* (area popularity, RFM user
> classification, build-vs-buy). This doc is the *what to instrument*: a per-format checklist for the
> Reference sheets, the one schema change it needs, and the GA4 event that answers "did they explore the
> screen." A twin copy lives in the iOS repo — keep the two in step.

## 0. Platform note (Android)

- Stat store: **`TTSStatsRepository`** (`.../data/repository/TTSStatsRepository.kt`),
  `SharedPreferences`-buffered, flushed to Firestore. Platform segment `and`.
- Grammar: `PageStat` / `QuizStat` in `.../data/stats/QuizStat.kt`, via the `recordPageView` /
  `recordQuizCompletion` extension functions. Both write to **the global monthly doc *and* the per-user
  doc**.
- **Android-specific issue (read §3 and §4 first):** unlike iOS, Android records **only `TAP`** — there is
  effectively **no `.open` signal**, and several `recordPageView(..., TAP)` calls fire on sheet *load*, so
  open and interaction are currently conflated. The central call in
  `ReferenceTabContainerViewModel.kt:79` was deliberately switched from `OPEN` to `TAP`
  (`//Was Open but don't want 2 values multiplied in DB`). Fixing this is Android's biggest single item.

iOS is the twin (`StatsManager`, platform `ios`, `PageStat.record` / `recordCompletion`). iOS keeps a
genuine central `.open` at `ReferenceView.swift:489`; Android should restore an equivalent (§4).

---

## 1. Goal

Know **which reference areas are popular** and **whether users actually engage** with them (vs open and
bounce) — with special attention to the DE and EN sheets below, and to the Pronouns screen (fileFormat 6),
where the question is not just "did they open it" but "did they tap many of the tappable buttons."

**DE sheets:** conjugations · GermanPrepositionsTeaching · AdjectivesGroup · GermanReferencePronouns ·
GermanReferencePronounsOpenAI · GermanReferenceObjectPronouns · GermanReferencePodcastsGroup ·
GermanSoundsTheSame · PairsGroup

**EN sheets:** conjugations · EnglishPrepositions · SpanishLanguage · AdjectivesGroup ·
EnglishReferencePronouns · EnglishDefinitionsFormat1 · PairsGroup

Likely mapping to file formats / packages (confirm per sheet; routing is by `fileFormat`, not by name):

| File format | Sheets | Android package(s) |
|---|---|---|
| **1** | EnglishDefinitionsFormat1 | `Format1` |
| **2** | GermanSoundsTheSame, PairsGroup | `Format2`, `Format2Grouped` |
| **3** | SpanishLanguage | `Format3`, `Format3Grouped` |
| **6** | conjugations, Prepositions, all Pronouns + ObjectPronouns | `ReferencePronouns` (PronounsClaude), `Conjugations`, `ReferenceGeneric` |
| **8** | AdjectivesGroup | `Format8` |
| **9** | GermanReferencePodcastsGroup | `Format9` |

---

## 2. The grammar to standardise on (and the ad-hoc system to retire)

Two stat systems coexist in the Reference tab. **Pick one: the normalised grammar.**

**Keep — normalised grammar** (`PageStat` / `QuizStat`):
```
p_<platform>_<area>_<sheetId>_<metric>      p_and_reference_GermanReferencePronouns_tap
q_<platform>_<area>_<sheetId>_<metric>      q_and_reference_..._runs | ok | notok | total
```
- `p_` page / `q_` quiz · `platform` ios|and · `area` reference|me|… · `sheetId` normalised (drops the
  `-<lang>` suffix, splits `/` hierarchy, alphanumerics only) · `metric`.
- Writes to **both** the global monthly doc (`stats/YYYY-MM`) and the per-user doc (`users/<uid>`), so it
  feeds both area-popularity *and* the per-user RFM scoring in `ANALYTICS_STRATEGY.md`.

**Retire — ad-hoc strings** (`"${statSheetQuizCount}_$sheetName"`, `statSideQuestCount_...`), still present
in `ReferenceGenericViewModel.kt:499,508`, `Format1ViewModel.kt:274,283`,
`Format2ViewModel.kt:288,296`, `Format2GroupedViewModel.kt:319,328`, `GroupedSheetViewModel.kt:381,391`:
- Free-form (not normalised, won't join to the `p_*`/`q_*` series), **global doc only** (invisible to
  per-user scoring), and only on the Quiz / Side-Quest buttons.
- Migrate these to `recordPageView(..., Metric.TAP)` (or a `recordQuizCompletion` where a quiz is actually
  taken) and delete the string builders.

---

## 3. Two signals you should have, and the breadth one you can't get from counters

1. **Open popularity — currently MISSING on Android.** Every `recordPageView` uses `TAP`; the central
   container call was changed off `OPEN`. Without a real `.open`, "which sheet is popular by opens" and the
   `tap ÷ open` ratio cannot be computed. **Restore a single genuine `.open` per sheet open (§4).** The
   "don't want 2 values multiplied" worry is unfounded: `open` and `tap` are *different keys*
   (`..._open` vs `..._tap`), so they are summed independently, never multiplied — recording both is
   exactly what makes the ratio work.
2. **Engagement volume — present but conflated.** `TAP` is recorded, but some calls fire on load (really
   opens) rather than on a user interaction. Move `TAP` to genuine interaction sites only (§5).
3. **Engagement *breadth* — counters can't give it.** "Did they tap *many different* buttons" is a
   distinct-count question; a `tap` counter can't tell one button tapped 30× from 30 buttons tapped once.
   That one needs GA4 event params (§8) or a per-user threshold flag (§6).

---

## 4. Current coverage (Android)

`recordPageView` is wired more widely than on iOS, but **all of it is `TAP`** and some fires on load:

| Format | `.open` | `TAP` today | Fires on load? | Ad-hoc still present | Main gap |
|---|---|---|---|---|---|
| **central** | — (was OPEN, now TAP) | `ReferenceTabContainerViewModel.kt:79` | yes | — | **restore a real `.open` here** |
| **1** | — | `Format1ViewModel.kt:219` | likely (load) | `:274,:283` | true content-tap vs load; retire ad-hoc |
| **2** | — | `Format2ViewModel.kt:212` | likely (load) | `:288,:296` | same |
| **2Grouped** | — | `Format2GroupedViewModel.kt:267` | likely (load) | `:319,:328` | same |
| **3** | — | — | — | — | all interaction untracked |
| **6** | — | `PronounsClaudeViewModel.kt:42`, `ConjugationsViewModel.kt:285`, `ReferenceGenericViewModel.kt:367` | yes (load/select) | ReferenceGeneric `:499,:508` | **sentence taps** vs the load-time call; retire ad-hoc |
| **8** | — | `Format8PlaybackViewModel.kt:75,108` | no (play) | — | sub-tab switch tap |
| **9** | — | `Format9ViewModel.kt:176` | no (per track) | — | distinguish scrubber-seek vs line-tap vs translate |
| grouped | — | `GroupedSheetViewModel.kt:345` | likely (load) | `:381,:391` | retire ad-hoc |

---

## 5. What to implant / fix, per format

Record at the genuine *interaction* site (a user action), once per action, as
`recordPageView(PageStat.Area.REFERENCE, sheetName, PageStat.Metric.TAP)`.

- **Central (do first)** — in `ReferenceTabContainerViewModel.kt:79`, record a single
  `PageStat.Metric.OPEN` when a sheet is shown, and keep `TAP` strictly for interactions elsewhere. This
  restores open-popularity and the `tap ÷ open` ratio for every sheet at once.
- **Format 1 / 2 / 2Grouped / grouped** — ensure the existing `recordPageView(TAP)` fires on an actual
  **content tap** (sentence row), not on load. Migrate the Quiz / Side-Quest buttons off the ad-hoc
  strings onto `TAP` (or `recordQuizCompletion` on completion). Delete the string builders.
- **Format 3** — add `.open` routing (via the central fix) and `TAP` on row/play taps.
- **Format 6 (priority)** — the load-time call in `PronounsClaudeViewModel` records that the screen was
  reached; add a **separate `TAP` on each sentence tap** so you capture interaction, not just arrival.
  Retire the ReferenceGeneric ad-hoc strings.
- **Format 8** — add `TAP` on **sub-tab switch** (which adjective group gets used) alongside the existing
  per-play taps.
- **Format 9** — keep the one `TAP`, but differentiate the *kind* of interaction via the GA4 event in §8;
  the Firestore `TAP` stays a single volume counter.

Do **not** add a separate counter constant per button — breadth is a GA4 concern (§8), not a counter one.

---

## 6. Pronouns (Format 6) — count vs duration

**Decision: count, not duration.**

- **Duration is a trap on mobile.** A screen left open (app backgrounded, user distracted) inflates it;
  you would be measuring "didn't navigate away," not engagement. Don't build a duration counter.
- **Volume via `TAP`** (§5) + a restored central `.open` gives **tap ÷ open per sheet** — the cheap,
  immediate "engaging vs bounce" signal. Ship the open fix + sentence taps first.
- **Breadth** ("tapped *many* of the buttons") is the real question and needs one of:
  - **GA4 event with a `pattern`/`kind` param (§8)** — preferred; lets you ask "what % of Pronouns openers
    tapped ≥ N distinct patterns in a session," plus free engagement-time.
  - **A per-user threshold flag** — if you want it in Firestore without GA4: fire a one-shot
    `p_and_reference_<sheet>_deep` the first time a user crosses N distinct interactions in a session.
    Coarser (a boolean per user), but counts across users and needs no new tool.

---

## 7. Schema change — add a `play` metric

`PageStat.Metric` is currently `OPEN | TAP`. Add **`PLAY`** (a sentence/section was actually *heard*, not
just tapped) so three states are distinguishable: opened → interacted (`tap`) → listened (`play`). This
matters most for the audio-heavy sheets (Format 6 sentences, Format 8, Format 9). Keep the enum this
small — resist per-button metrics.

(Shared change: add the same value to iOS's `PageStat.Metric` so the series line up.)

---

## 8. GA4 event spec (breadth + dwell)

GA4 is already integrated (see `ANALYTICS_STRATEGY.md §8`). For the breadth question, add **one** event
next to the `TAP` counter write:

```
event:  reference_interaction
params: sheet   = <normalised sheetId>     // joins to the p_* series
        area    = "reference"
        kind    = "chip" | "sentence" | "section_play" | "sub_tab" | "scrubber" | "translate"
        pattern = <pattern/scene id, when applicable>   // Format 6/9 only
```
This gives, in GA4/BigQuery and nowhere else cheaply: distinct patterns per session (breadth),
funnel open→interact→listen, and automatic engagement-time (trustworthy dwell). The Firestore `TAP`
counter stays the fast volume number; GA4 answers "how much of the screen did they explore."

---

## 9. Keeping Android and iOS in step

The Format views are shared-shaped, but **coverage is not symmetrical today**:

- **Android records only `TAP`; iOS keeps a real central `.open`.** This is the main divergence: restore
  Android's `.open` (§5) so both platforms can compute open-popularity and `tap ÷ open`.
- **Android's `recordPageView` is wired into more ViewModels** (Format1/2/2Grouped/8/9, Conjugations,
  GroupedSheet, ReferenceGeneric, Pronouns) — good reach, but audit each for open-vs-tap intent per §4.
- **Both still carry the ad-hoc strings** (`statSheetQuizCount` / `statSideQuestCount`) — retire on both.
- Add the `play` metric to **both** enums, and emit the identical `reference_interaction` GA4 event with
  the same param names so the two platforms' series are directly comparable.

---

## 10. Suggested order of work

1. **Restore a real central `.open`** in `ReferenceTabContainerViewModel.kt` (keep `TAP` for interactions)
   — unblocks open-popularity and `tap ÷ open` for all sheets at once.
2. **Format 6 sentence `TAP`** — the Pronouns gap you called out; add interaction tracking distinct from
   the load-time call.
3. **Format 1 / 2 content `TAP` + migrate the ad-hoc buttons** — removes the legacy writes and fixes
   load-vs-tap intent.
4. **Format 3 / 8 taps** (sub-tab switch for 8).
5. **Add the `PLAY` metric** to both platforms; emit it where audio is heard.
6. **`reference_interaction` GA4 event** with `kind`/`pattern` — unlocks breadth and dwell for Pronouns.
7. **Mirror every step on iOS** (§9) so the `p_*` / GA4 series stay comparable.
