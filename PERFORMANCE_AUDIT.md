# Performance audit — ExamReady (iOS) & LanguageExamsAI (Android)

_Read-only scan, 2026-10-16. No code was changed. Each item is self-contained: **file:line → fix → priority**. High/medium items were verified by reading the code; "audit" items come from pattern sweeps and need a confirming read before acting._

A copy of this file lives in both `Android/` and `iOS/` repo roots.

---

## ★ Headline (both platforms): SHA-256 hashing per vocab row, every render

The vocab list builds a content-ID by **SHA-256-hashing the sentence text inside the list row body**, so it re-runs for every visible row on every recomposition/redraw.

- **Android** — `app/src/main/java/com/goodstadt/john/language/exams/packages/CategoryTab/CategoryTabScreen.kt:483,490,500` calls `isHeard` + `getPlayCount` + `getSpacedCount`, **each** of which calls `FirebaseAudioService.generateContentID()` → `computeShortHash()` = a fresh `MessageDigest.getInstance("SHA-256")` + per-byte `"%02x".format()` string-fold. That's **3 SHA-256 hashes of the same sentence per row**, and the `isHeard` at line 483 is **dead** (result unused — `isSentenceAlreadyHeard = playCount > 0`). Same 2× pattern in the grid cell at `CategoryTabScreen.kt:1223,1231`.
- **iOS** — `LanguageExams/ExamLanguageCode/TabViews/Tabs123/VocabTabListView.swift:235-237,271-276` calls `generateContentID` once per row (better), but still a SHA-256 per row per render. (Devs already fixed a worse `id: \.self` hashing loop here — see their comment ~line 222.)

### Where the hash is stored
The content-ID (`CID_<prefix>_<10 hex of SHA-256>`) **is the storage key**, both locally and in Firestore: the play-history doc `users/{uid}/history/{level}` holds a `data` map whose **keys are content-IDs** and values are play counts (HistorySyncManager.kt:248-306); the same CID is also the **audio-cache filename key** (AudioCacheManager.kt). The store is keyed **by** the hash, not reverse-indexed by sentence, so to read a sentence's count you must first turn its text into the hash — that is the per-row SHA-256. The lookup itself (`history[level].items[hash]`) is a cheap O(1) map get; **the cost is deriving the key, not the lookup.**

> ⚠️ **Do NOT change the hash algorithm.** It is load-bearing for existing Firestore history and cached-audio filenames; changing it orphans every user's progress and cached audio. The fix must only avoid *recomputing* the same hash, never alter it.

### Fix (compute the CID once per sentence, not once per render), in order of effort
1. **Memoize inside `generateContentID` — quick global win (do first, both platforms).** Add a bounded cache in `FirebaseAudioService` keyed by trimmed sentence text → CID (e.g. `LruCache(4096)`). First call hashes; every later call (every render) is an O(1) lookup. One ~4-line change fixes **all ~50 call sites** at once. Safe — the function is deterministic. iOS: same cache in its `FirebaseAudioService.generateContentID`.
2. **Cache the CID on the model (clean, list-specific).** `Sentence` is a kotlinx `@Serializable` data class, so add a non-serialized `val contentID: String by lazy { FirebaseAudioService.generateContentID(sentence) }`; computed once per object, row reads `sentence.contentID`. More call-site churn than #1.
3. **Compose cleanup on the hot screen (do alongside #1).** In CategoryTabScreen compute the CID **once per row** (`remember(sentence){…}`), derive playCount/spacedCount from that single CID, and **delete the dead `isHeard` at line 483**. iOS already computes once per row, so #1 covers it there.
4. _Optional / most thorough:_ bake the CID into the vocab JSON at content-build time so the client never hashes — a data-pipeline change, only worth it if you own generation; #1 gives ~the same runtime benefit with none of the risk.

Also rewrite `computeShortHash`'s hex with a lookup table/`StringBuilder` and avoid re-creating `MessageDigest` per call (minor vs the memoize, but cheap to do).

**Recommendation:** ship **#1 on both platforms**, then **#3** on Android to kill the triple-call + dead hash. **Effort: M · Impact: H** — main scroll-jank candidate on long B1/B2 lists. Validate by scrolling a full B1/B2 vocab tab on a low-end device / older iPad before & after.

---

## Android

| # | Pri | Finding | Location | Fix |
|---|-----|---------|----------|-----|
| A1 | **H** | Per-row triple SHA-256 (headline) + dead `isHeard` | CategoryTabScreen.kt:483/490/500, 1223/1231 | cache CID per sentence; drop dead call |
| A2 | M | `removeContentInBracketsAndTrim()` compiles a `Regex` **per call, per section header**, and is **duplicated** in two files | utils/StringExtensions.kt:8 and packages/CategoryTab/CategoryTabScreen.kt:1376 | hoist one cached top-level `val` Regex; delete the duplicate |
| A3 | M | `computeShortHash` allocates a digest + builds hex via `fold("")` string-concat with `String.format` ×32 | data/repository/FirebaseAudioService.kt | hex lookup table; memoize CID |
| A4 | L→M | `collectAsState()` used in ~160 places vs `collectAsStateWithLifecycle` in ~51 — collects/recomposes while backgrounded | repo-wide | switch screen-level VM state to the lifecycle-aware variant (start Category/Quiz/Reference) |
| A5 | L | Inline `Regex(...)`/`.toRegex()` built per call in utils that run in loops | utils/IdGenerator.kt:20/28/36, TabViews/.../QuizDataConverter.kt:200, screens/CategoryTab/SectionQuizKeyDerivation.kt:13 | hoist to cached vals |
| A6 | audit | ~10 `items(...)` without a `key =` (heuristic) | sweep | ensure every Lazy list has a stable `key` (+ `contentType`) |

Good already: `Gson()` instances are cached as fields (not per-call); the vocab grid uses keyed `itemsIndexed`.

---

## iOS

| # | Pri | Finding | Location | Fix |
|---|-----|---------|----------|-----|
| I1 | **H** | Per-row SHA-256 content-ID in list body (headline) | VocabTabListView.swift:235,271 | cache CID on the model/dictionary |
| I2 | M | Inline `NSRegularExpression(pattern:)` compiled per call | LibraryBusiness.swift:91, TabViews/ReferenceTab/Quiz/QuizDataConverter.swift:491, .../SubViews/Format8/Format8ContainerView.swift:540 | hoist to `static let` — follow Extensions/String.swift:14-19 which already caches its regexes |
| I3 | M | `DateFormatter()` built per call (expensive); `getHistory()` creates **two** each call | TabViews/MeTab/Progress/MyProgressView.swift:316-317, .../DailyWords/DictionaryEntryBrowserViewModel.swift:128, Extensions/Extensions.swift:123/143 | cache as `static`/shared formatters; confirm `getHistory()` isn't called from `body` |
| I4 | audit | ~47 `ForEach(… id: \.self)` / `.indices` sites | sweep | fine for short/stable lists; for long/mutating lists use a stable identity (team already hit this trap in VocabTabListView) |
| I5 | L | ~30 `GeometryReader` uses — extra layout passes | sweep | avoid nesting inside scrollable rows |
| I6 | audit | Dead/`NotUsed/` files still in target (e.g. `Tab1VocabTabListView`, `MyProgressViewOriginal`) | — | confirm excluded from build; remove to cut compile time |

Good already: `Extensions/String.swift` caches its `NSRegularExpression`s as `static let`.

---

## Suggested execution order
1. **A1 / I1** — the scroll-path hashing (biggest felt win).
2. **A2 / A3 / I2 / I3** — cached regex/formatter items (quick, safe).
3. **A4 / A6 / I4 / I5 / I6** — sweep/audit rows; confirming read required before changing.
