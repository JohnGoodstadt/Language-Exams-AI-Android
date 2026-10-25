# Dark / Light Mode — Analysis & Plan (iOS + Android)

Goal: make each app follow the **system** light/dark setting, and add a **Settings** control to
override it (**System / Light / Dark**). Covers both ExamReady iOS and the Android app.

## Current state — both apps are hard-locked to Dark

**iOS**
- `UIUserInterfaceStyle = Dark` in both Info.plists (`LanguageExams/InfoPlists/GermanExamAI-Info.plist`
  and `English-Exam-AI-Info.plist`) forces Dark globally.
- No `preferredColorScheme` at the `WindowGroup` root (`LanguageExamWordsAIApp.swift`).
- `ColorManager.swift` holds a single hardcoded (light-ish) "skin"; used by only **4 views** — effectively dead.
- Colours: **103** adaptive `Color(.systemX)` (fine), but **58** literal `Color(red:…)` + **134 `.white`**
  + **41 `.black`** (fixed — won't flip), across ~**84 files**.

**Android**
- Forced Dark in three places: `MainActivity` → `LanguageExamsAITheme(darkTheme = true)`; `Theme.kt`
  light branch falls back to `AppDarkColorScheme`; status bar pinned (`isAppearanceLightStatusBars = false`).
- **But** `AppLightColorScheme` is already defined and the theme already defaults to `isSystemInDarkTheme()`
  — the infrastructure is ~90% there, just overridden.
- Colours: **56** hardcoded hex in `ui/theme/Color.kt` (`Orange`, `CardBG`, `nonSelectedBackground`, greys…)
  used directly in ~**30 files**, bypassing `MaterialTheme.colorScheme`.

## What the toggle needs (the easy part, ~0.5–1 day/platform)

Shared 3-way setting **System / Light / Dark**, persisted and read at the app root.
- **iOS**: remove `UIUserInterfaceStyle`; `@AppStorage("appearanceMode")`; at `WindowGroup` apply
  `.preferredColorScheme(mode == .system ? nil : (dark ? .dark : .light))`; add a Picker in Settings.
- **Android**: `Theme.kt` light branch → `AppLightColorScheme`, status bar follows `darkTheme`; replace
  `darkTheme = true` with `when(mode){LIGHT->false;DARK->true;SYSTEM->isSystemInDarkTheme()}`; persist in
  DataStore/prefs; add a segmented/radio control in Settings.

Unlocking "follow system" **before** colours are adaptive makes Light mode look broken — so colours come first.

## Colour centralization (the real work) — YES, needed

| | Fixed/non-adaptive colours | Files |
|---|---|---|
| iOS | 134 `.white` + 41 `.black` + 58 literal `Color(red:…)` | ~84 |
| Android | 56 hardcoded hex (greys/backgrounds/brand) | ~30 |

- **iOS**: build an **Asset Catalog** of ~15–25 *semantic* colour sets with Light+Dark appearances
  (accent/brand, backgrounds, surfaces/cards, text primary/secondary, success/warning/error, dividers),
  then sweep the ~84 files replacing `.white`/`.black`/literal RGB; keep the adaptive `Color(.systemX)`.
  Retire or rebuild `ColorManager` as the typed accessor.
- **Android**: fill out `AppLightColorScheme`; add an **`ExtendedColors`** CompositionLocal (light+dark) for
  brand/surface colours that aren't Material roles; sweep the ~30 files to `MaterialTheme.colorScheme.*` /
  `LocalExtendedColors.current.*`.

Effort for centralization + sweep: iOS ~2–4 days, Android ~1.5–3 days, plus a Light-mode QA pass per screen.

## Phased plan (never ship a broken Light mode)

1. **Build the adaptive palette** (iOS asset colours; Android: complete `AppLightColorScheme` + add
   `ExtendedColors`). No visible change — the app stays forced-Dark.
2. **Sweep usages** onto the semantic/extended colours.
3. **Unlock + toggle**: remove the forced Dark, wire System/Light/Dark + Settings UI, QA Light mode per screen.

## Progress
- [x] **Phase 1 — Android** (this change): completed `AppLightColorScheme`; fixed the `Theme.kt` light branch
  and status-bar wiring (dormant while still forced Dark); added `ExtendedColors` + `LocalExtendedColors`
  (light/dark) in `ui/theme/`. App behaviour unchanged (MainActivity still forces Dark).
- [x] **Phase 2 — Android** (this change): swept 25 files off the raw `Color.kt` brand vals onto
  `LocalExtendedColors.current.*` (brandOrange/accent/buttonBlue/teal/speakerTeal/mutedGrey/
  rowSelected/rowUnselected/cardBackground). Still visually inert in Dark (DarkExtendedColors == the
  original values). Not swept (deliberately, out of scope here): inline `Color(0xFFFF9800)` "orange"
  literals in gamification/stats screens (a different shade, many in non-composable `when` mappers),
  and two files that redefine `private val CardBG` locally (`VocabQuiz/CategoryDetailScreen.kt`,
  `VocabQuiz/VocabQuizDashboard.kt`).
- [x] **Phase 3 — Android** (this change): theme-mode setting wired end to end.
  - `ThemeMode` enum (System/Light/Dark) in `ui/theme/`.
  - Persisted in DataStore (`UserPreferencesRepository.themeModeFlow` / `setThemeMode`, default `SYSTEM`).
  - Settings UI: an "Appearance" section with three choice-chips (`AppearanceSettingItem` in
    `SettingsScreen.kt`), backed by `SettingsViewModel.themeMode` / `setThemeMode`.
  - `MainActivity` now reads the persisted mode and drives `LanguageExamsAITheme(darkTheme = …)`:
    `SYSTEM → isSystemInDarkTheme()`, `LIGHT → false`, `DARK → true`. The hard-coded
    `darkTheme = true` is gone — Light mode is now reachable.
  - ⚠️ **Not yet release-ready:** needs an on-device Light-mode QA pass per screen. Known rough spots
    are the Phase-2 leftovers that still don't adapt: inline `Color(0xFFFF9800)`/`.white`/`Color.LightGray`
    literals and the two private `CardBG` cards (VocabQuiz CategoryDetail / Dashboard). Builds green
    (compileDeDebugKotlin + compileEnDebugKotlin); not yet run on a device/emulator in this session.
- [x] **Phase 3b — Android** (this change): folded the hard dark-background screens into the theme so
  Light mode no longer shows dark cards / invisible text. Added `ExtendedColors.screenBackground`
  (dark near-black / light white). Converted the 7 screens that hard-coded dark backgrounds —
  `Focus/FocusScreen`, `Format3/Format3FileScreen`, `Format3/Format3SheetView`, `MyProgress/MyProgressScreen`,
  `ReadinessAudit/ReadinessAuditScreen`, `VocabQuiz/CategoryDetailScreen`, `VocabQuiz/VocabQuizDashboard` —
  mapping dark card/screen backgrounds to `LocalExtendedColors.current.cardBackground` / `screenBackground`
  and the white/light content on them to `MaterialTheme.colorScheme.onSurface` / `onSurfaceVariant` /
  `outlineVariant`. Left genuinely theme-independent colours as-is: status colours (orange/green/red
  mastery, `AccentOrange`/`SuccessGreen`), black-on-orange button text, disabled-state greys, and
  translucent `Color.Gray.copy(...)` tracks/borders. Builds green (de+en). Still pending: on-device
  Light-mode QA, and the long tail of individually-correct `.white`/`.gray` usages that don't break.
- [ ] Phases 1–3 — iOS.
