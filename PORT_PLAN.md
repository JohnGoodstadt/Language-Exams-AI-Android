# Android → iOS Port — Plan of Attack

**Companion to [`ANDROID_TO_IOS_PORT_SPEC.md`](./ANDROID_TO_IOS_PORT_SPEC.md).** The spec is the feature reference (what each thing does + gotchas); this file is the **order to build them in** to avoid touching the same subsystem twice. Section numbers (§n) refer to the spec.

Tick the boxes as you go.

---

## Progress tracker

Two checkboxes per task: **Start** when you begin it, **Done** when it's ported *and* verified on iOS. Put the date (and any PR/branch) in Notes.

| Phase | Task | Start | Done | Notes |
|---|---|:---:|:---:|---|
| 0 | Debug‑build gating + debug‑time substitution helper | ✓ | ✗ | N/A|
| 0 | Central constants (preview limits, EMA/thresholds, rate limits, loading delay) | ☐ | ☐ | |
| 0 | §21 connectivity check + §20 delayed global spinner | ☐ | ☐ | |
| 1 | §1 cloud store + cache + version map + bundle fallback + `Chinese` prefix | ☐ | ☐ | |
| 1 | §1 format‑dispatch table (0/1/2/3/6/7/10/13) — incl. Format6 up front | ☐ | ☐ | |
| 1 | §9a `app_ui_manifest` structure + bundle⇄Firestore name mapping | ☐ | ☐ | |
| 1 | §1 debug uploader (optional) | ☐ | ☐ | defer unless pushing Chinese sheets |
| 2 | §12 AccessPolicy (Vocab/Reference limits, already‑heard exception) | ☐ | ☐ | |
| 2 | §13 IAP upgrade sheet (StoreKit) | ☐ | ☐ | |
| 2 | §15 debug premium override (Settings toggle) | ☐ | ☐ | |
| 2 | §22 Settings About copy | ☐ | ☐ | |
| 3 | Per‑sentence audio waterfall | ☐ | ☐ | |
| 3 | §24 `SequencePlayer` singleton + playback‑finished event bus | ☐ | ☐ | |
| 3 | §16 spaced‑repetition dots + tap‑bookkeeping hooks | ☐ | ☐ | |
| 3 | §14 rate limiting + §23a API‑key setup | ☐ | ☐ | |
| 3 | §23 Translate module + sheet (speak‑German result) | ☐ | ☐ | |
| 4 | §25 unified `ReferenceQuizScreen`/VM + `prebuilt` entry (7 vs 11) | ☐ | ☐ | |
| 4 | §5/§6/§7 loaders (grammar / vocab‑usage / section) | ☐ | ☐ | |
| 4 | §8a marking rules + §8 single‑select filters | ☐ | ☐ | |
| 4 | §8b per‑word + whole‑quiz mastery | ☐ | ☐ | |
| 4 | §8c dated attempt history | ☐ | ☐ | |
| 4 | §8d auto‑advance (uses event bus) | ☐ | ☐ | |
| 4 | §8e section‑row status dots/stars + legend | ☐ | ☐ | |
| 4 | §25a random‑10 cap | ☐ | ☐ | |
| 5 | §2 Readiness Audit | ☐ | ☐ | |
| 5 | §3 Progress (confidence) | ☐ | ☐ | |
| 5 | §9c Reference‑strength store (isolated from audit) | ☐ | ☐ | |
| 5 | §4 Focus (deep‑links to quiz engine; renders strength areas) | ☐ | ☐ | |
| 6 | §9 baseline reference reports (format 0/1/2/3 + old Conjugations) | ☐ | ☐ | |
| 6 | §9b Pronouns (`Format6Screen`) + `PronounQuizGenerator` | ☐ | ☐ | |
| 6 | §9b‑ii Prepositions teaching (distinct route fix) + quiz (de+en) | ☐ | ☐ | |
| 6 | §9b‑iii Conjugations teaching + `ConjugationsQuizGenerator` + compare flag | ☐ | ☐ | |
| 6 | §23b Translate icon on reference screens | ☐ | ☐ | |
| 7 | §17 slide‑to‑save | ☐ | ☐ | |
| 7 | §18 Saved screen | ☐ | ☐ | |
| 7 | §19 local reminders (notification permission + deep‑link) | ☐ | ☐ | |
| 8 | §10 Word of the Day | ☐ | ☐ | |
| 8 | §11 AI paragraph | ☐ | ☐ | |

---

## The core insight

Android's chronology scattered work that belongs together. Three subsystems were each spread across many commits and **must each be built once, whole, on iOS**:

- **The quiz engine** — §5, §6, §7, §8, §8a–§8e, §25, §25a were all one screen/VM (`ReferenceQuizScreen`) grown over Aug–Sep. Build any one alone and you rewrite it for the next.
- **The fileFormat‑6 screens** — §9b (Pronouns), §9b‑ii (Prepositions teaching), §9b‑iii (Conjugations teaching). Same data model, same rendering DNA. One parser + one rendering pattern serves all three.
- **Translate** — §23, §23a, §23b share one module + one API‑key setup.

Two cross‑cutting runtime subsystems underpin many features and must exist before the screens that call them: the **cloud‑content loader** (§1) and the **shared audio / `SequencePlayer` + playback‑finished event bus** (§24, also used by §8d and §16).

> Note: iOS was feature‑equivalent to Android on **16 July 2026**, so base screens (vocab tabs, Me tab, reference‑tab shell, Settings) already exist. This plan adds the *new* functionality on top; "structural" = the new runtime subsystems and screens.

---

## Phase 0 — Conventions to establish once  ☐

Small, but everything leans on them.

- ☐ **Debug‑build gating** — one `#if DEBUG` convention. Used by the uploader (§1), premium override (§15), rate‑limit dev toggle (§14), Translate debug button (§23), and the "short gap substitutes for a day/hour" testing shortcut (§16, §19). Build the debug‑time‑substitution helper now.
- ☐ **Central constants** — preview limits (§12), EMA α + Weak/OK/Strong thresholds (§9c), rate limits (§14), loading delay (§20). The spec says "keep in one place" repeatedly.
- ☐ **Connectivity check (§21)** + **delayed global spinner (§20)** — tiny, and §1 needs them.

---

## Phase 1 — Content backbone (biggest structural change)  ☐

**§1 + §9a together.** Cloud sheet store, local cache + version map, bundle fallback, the `Chinese` language prefix, and the **format‑dispatch table** (0/1/2/3/6/7/10/13) driven by the **`app_ui_manifest`** (§9a) rather than hard‑coded lists.

- ☐ Build the **entire** parser dispatch — **including Format6** — up front, before the format‑6 screens exist. (Gotcha §1d: wrong parser → silently empty data. Most failure‑prone piece in the app.)
- ☐ Reproduce the manifest structure; keep the bundle⇄Firestore name mapping in one place.
- ☐ Local cache + remote version map + transparent upgrade when online.
- ☐ Debug **uploader** is optional (content can be pushed once from Android) — defer unless you need to push Chinese sheets.

Everything downstream is content‑driven; nothing else can be verified until this is solid.

---

## Phase 2 — Monetisation gating (early — many screens ask it)  ☐

- ☐ **§12 AccessPolicy** — one decision point; Vocab (A1/A2 free, B1/B2 preview) vs Reference (preview for all). Row/section limits from central constants.
- ☐ **§13 IAP upgrade sheet** (StoreKit) — "lock tapped → offer upgrade → immediate unlock".
- ☐ **§15 debug premium override** (Settings toggle, debug‑only, live re‑evaluate).
- ☐ **§22 Settings About copy** — keep wording truthful to the actual free/locked split.
- ☐ Get the **"already‑heard ⇒ always replayable"** exception and **runtime premium refresh** right here so later screens inherit them.

---

## Phase 3 — Shared audio, playback events, keys, rate limiting, Translate module  ☐

One runtime subsystem block, reused everywhere.

- ☐ **Per‑sentence audio waterfall** (local → Cloud Storage → TTS→save→upload).
- ☐ **`SequencePlayer` singleton** (§24) + **playback‑finished event bus** (also consumed by §8d auto‑advance). Includes §24's play‑a‑section pipeline (prefetch‑one‑ahead) and the `onTrackStarted`/`onTrackPlayed`/`onCompleted` hooks.
- ☐ **§16 spaced‑repetition dots** + the tap‑bookkeeping the hooks run — build with §24 since they're wired together.
- ☐ **§14 rate limiting** + **§23a API‑key setup** (one key per language project; TTS + Translate share it).
- ☐ **§23 Translate module** (UI‑agnostic `translate()` + the sheet + "speak German result" via the TTS path). Consumers (§23b, the "T" on format‑6 screens, vocab‑tab "T") wire to it later.

---

## Phase 4 — The quiz engine (build as ONE screen/VM)  ☐

**§25 → §5/§6/§7 loaders → §8 → §8a → §8b → §8c → §8d → §8e → §25a**, all in one `ReferenceQuizScreen`/VM.

- ☐ Start from the **unified §25 end state** — the `prebuilt` entry point + fileFormat 7 vs 11 distinction. Do **not** build the old `QuizSheetView` that §25 deleted.
- ☐ **§8a marking rules** — loudspeaker is inert for scoring; lock‑on‑correct; radio == tapping the sentence. (Subtlest bug source.)
- ☐ **§8b** per‑word + whole‑quiz mastery, **§8c** dated attempt history, **§8d** auto‑advance (uses Phase‑3 event bus), **§8** single‑select filters, **§25a** random‑10 cap, **§8e** section‑row status dots/stars + legend.
- ☐ Key all quiz/mastery state by **stable question identity (word/text)**, never page‑local index.

Consumes Phase 1 (content), Phase 2 (gating), Phase 3 (audio/events).

---

## Phase 5 — Diagnostics & progress  ☐

- ☐ **§2 Readiness Audit** (grouped ~10, inline summaries, per‑category, locked higher levels).
- ☐ **§3 Progress** (confidence, not %).
- ☐ **§9c Reference‑strength store** (EMA, hierarchical ids, rollups, `weakAreas()`).
- ☐ **§4 Focus** — deep‑links into the Phase‑4 quiz engine; renders reference‑strength areas.
- ☐ **Isolation rule (§9c):** reference quizzes feed only reference‑strength, never the grammar audit.

---

## Phase 6 — Reference content family (all fileFormat‑6 together)  ☐

- ☐ **§9 baseline reference reports** — format‑0 lists, grouped format‑2/3 sub‑tab screens, format‑1 screen, the *old* Conjugations fixed screen.
- **fileFormat‑6 group — one push (biggest efficiency win):**
  - ☐ **§9b Pronouns** (`Format6Screen`) — establishes format‑6 rendering (category/pattern chips, form chain, green highlight) + runtime `PronounQuizGenerator` + authored fallback.
  - ☐ **§9b‑ii Prepositions teaching** — reuses the screen; carries the **distinct‑route fix** (two format‑6 sheets can't share one parameterised destination) + authored per‑category quiz (de + en).
  - ☐ **§9b‑iii Conjugations teaching** — its *own* screen/VM (verb picker + merged paradigm‑then‑examples), runtime `ConjugationsQuizGenerator` (first‑green‑word blank, case‑insensitive distractor de‑dup for the blind‑swap guard, **≥3‑distinct‑forms** quiz‑visibility gate), behind the old‑vs‑new compare flag.
  - ☐ Wire every format‑6 quiz to the Phase‑4 engine (prebuilt / loader path) and to reference‑strength (§9c).
- ☐ **§23b Translate icon** on the reference screens (trivial once §23 exists).

Write the parser, chip/form/section renderer, green‑highlight helper, runtime‑quiz generator pattern, and reference‑strength wiring **once**, then vary only data/generator per screen.

---

## Phase 7 — Saved & reminders (self‑contained, parallelisable)  ☐

- ☐ **§17 slide‑to‑save** (store the whole word entry, per level, toggle).
- ☐ **§18 Saved screen** (Me tab; inbox‑style float‑to‑top on due reminder).
- ☐ **§19 local reminders** (`UNUserNotification` permission, deep‑link to Me › Saved; cleared by playing/removing).

Independent of the quiz/reference stack — can run alongside Phases 4–6.

---

## Phase 8 — Standalone leaves  ☐

- ☐ **§10 Word of the Day** (deterministic per date).
- ☐ **§11 AI paragraph** (needs Phase‑3 rate‑limit + online + provider config).

---

## Phase → section map

| Phase | Sections | Why grouped |
|---|---|---|
| 0 Foundations | debug‑gating, constants, §20, §21 | Assumed everywhere |
| 1 Content backbone | §1, §9a | Unblocks all content; do format dispatch (incl. 6) fully now |
| 2 Gating | §12, §13, §15, §22 | Many screens check it — do early |
| 3 Audio/Translate infra | §24, §16, §14, §23, §23a | One runtime subsystem + shared event bus + keys |
| 4 Quiz engine | §25, §5, §6, §7, §8, §8a–e, §25a | One screen/VM — never build piecemeal |
| 5 Progress | §2, §3, §4, §9c | Audit + strength stores; Focus needs the quiz engine |
| 6 Reference (fileFormat‑6) | §9, §9b, §9b‑ii, §9b‑iii, §23b | All format‑6 built together |
| 7 Saved | §17, §18, §19 | Cohesive; parallelisable |
| 8 Leaves | §10, §11 | Small, independent |

---

## Recurring risks to watch (each burned time on Android)

- **Format‑to‑parser dispatch** (§1d) — a mislabeled/mis‑routed sheet silently yields empty data. Trust the manifest `sheetDataType`, not the JSON's own `fileformat`.
- **Key quiz/mastery state by stable identity, not page index** (§8a, §8b) — a per‑page index collides across sub‑tabs.
- **Keep the two progress stores isolated** (§9c) — reference quizzes must not write the grammar audit.
- **Format‑6 shared‑route aliasing** (§9b‑ii) — give each format‑6 sheet its own route, or save/restore state cross‑contaminates.
- **Blind‑swap / two‑correct‑answers trap** in every runtime quiz generator (§9b, §9b‑iii) — distractors must be provably wrong in the slot; de‑dup against the answer.
