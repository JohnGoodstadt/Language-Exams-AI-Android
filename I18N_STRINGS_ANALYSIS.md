# Android — Moving UI strings into `strings.xml` (analysis)

_Date: 2026-11-18 · Branch analysed: `develop` · Read-only analysis, no code changed._

Goal: move hard-coded UI text (e.g. `"Choose the best answer"`) into string resources so the app
can be translated later.

## 1. Current state

- 307 Kotlin files, ~70,000 lines (tests excluded).
- `main/res/values/strings.xml` holds only **9** strings: `app_name`, the Facebook ids,
  `welcome_greeting` and 5 `quiz_*` prompts. The `de` flavor overrides the same 9.
- Only ~20 places read resources (`stringResource` / `R.string`).

| Kind of UI text | Count |
|---|---|
| `Text("…")` on one line | ~223 |
| `Text(` with the literal on the next line | ~222 |
| `contentDescription = "…"` (screen-reader labels) | 83 |
| `title = "…"` / `subtitle =` / `message =` in models and ViewModels | ~235 |
| Literals shown on screen with templates, e.g. `"$count words"` | ~120 |
| `Text(variable)` | 101 |
| `BuildConfig.FLAVOR` / which-app checks | 27 |
| `…Unused` folders | 2 files |

The busiest files are `ReadinessAuditScreen`, `OfflineDownloadSheet`, `SettingsScreen`,
`UploadJsonScreen`, `SideQuestStatsSheet`, `UsageDashboardScreen` and `RecallScreen`.

### Why this is more work than iOS
Compose has **no automatic extraction**. Every literal must be:

1. moved into `strings.xml` with a key name,
2. replaced with `stringResource(R.string.key)`,
3. or, in ViewModels and models where there is no Compose context, passed as `@StringRes Int` or a
   small `UiText` wrapper and turned into text in the UI.

## 2. Is it safe? (overall)

**Mostly yes, with a few specific traps.**

- **Missing keys are caught at compile time.** A wrong `R.string.x` will not build, so a typo
  cannot reach users.
- **English-only output stays identical** if the text is copied exactly.
- **The real risks:**
  - **Format-argument crashes.** This is the one way this work can crash Android at runtime.
    `getString(R.string.x, arg)` throws `IllegalFormatConversionException` if `%1$d` receives
    text or `%1$s` mismatches. A literal `%` also needs to be written `%%`.
  - **XML escaping.** Apostrophes and quotes must be escaped (`\'`). An unescaped `'` fails the
    build (safe), but `\\\'`-style double escaping shows backslashes on screen. The existing
    `welcome_greeting` already shows this.
  - **Flavor fallback.** If the `de` flavor forgets to override a key, it **silently** shows the
    `main` (English-app) wording. There is no error.
  - **Identifiers mistaken for display text.** Titles feed `SectionQuizKeyDerivation`
    (`SectionQuizKeyMap`), and analytics event names (12 `logEvent`) and Firestore and
    preference keys are strings too. These must **never** be moved into resources.

## 3. Phases, effort and risk

| Phase | Work | By hand | With Claude extracting | Risk | Notes |
|---|---|---|---|---|---|
| 0 | Delete the `…Unused` folders | 0.25 | 0.25 | 🟢 Low | The compiler catches anything still referenced. |
| 1 | Key-naming convention (`quiz_*`, `settings_*`…), add a `UiText` helper, turn on lint `HardcodedText` as a warning | 0.5–1 | 0.5 | 🟢 **Very low** | Adds tools, changes no behaviour. **Safe to do alone.** |
| 2 | Move ~445 `Text` literals and 83 `contentDescription`s into `strings.xml` | 4–5 | 1–2 (review) | 🟢 **Low** | Compile-checked and mechanical. The only risks are typos and escaping. Do one screen per commit. |
| 3 | Move ~235 model/ViewModel `title`/`message` strings to `@StringRes`/`UiText` | 2–3 | 1–1.5 | 🟠 **Medium** | Changes types in ViewModels and models, and **unit tests** that check text (e.g. MockK/`assertEquals` on titles) will need updating. Must skip identifier strings such as `SectionQuizKeyMap` titles, analytics names and Firestore keys. |
| 4 | Templates and plurals (`%1$s`, `%1$d`, `<plurals>`, `pluralStringResource`) ~120 | 1–2 | 1 | 🟠 **Medium** | **The only runtime crash risk** (format-argument mismatch). Check each one on screen. |
| 5 | Replace the 27 flavor checks with per-flavor resource overrides (`en/`, `de/`, `zh/` `res/values`) | 0.5–1 | 0.5 | 🔴 **Higher** | A missing override silently shows the wrong app's wording. **Recommend leaving the flavor checks as they are** and deciding separately. |
| 6 | Check every screen in both apps on a phone and a tablet (pseudo-locales `en-XA`, `ar-XB` show missed text) | 1–2 | 1 | — | |
| | **Total** | **~9–14 days** | **~4–6 days** | | |

### Recommendation
- **Phases 0–2 are the safe core.** They are compile-checked with no behaviour change, and do most
  of the visible strings.
- **Phases 3–4 should be done carefully,** screen by screen, with the unit tests run each time.
- **Phase 5 can wait.** Decide on it separately, if ever. The current flavor code works.
- Keep a rule: **display text goes to resources; identifiers never do.**
- **Flavor folders are not translations.** `app/src/de/res/values/` holds the German *app's*
  wording, which is English text for German-exam learners. A real interface translation goes in
  `values-es/`, `values-zh/` and so on.
- Mark `facebook_app_id` and `facebook_client_token` `translatable="false"`.

### Not in scope
Learning content (vocab, quizzes, sentences) and text that comes from Firebase or Remote Config.

## 4. Benefits

1. **Translating becomes simple.** A new language is one new `values-xx/strings.xml` file, and
   translators never touch Kotlin.
2. **Per-app language settings.** On Android 13 and later, users can pick the app's language
   separately from the phone's. This only works once texts are in resources.
3. **Built-in test languages.** `en-XA` (longer accented text) and `ar-XB` (right-to-left) reveal
   truncation and leftover hard-coded text with no translation needed.
4. **Lint keeps it clean.** `HardcodedText` warns when someone adds a new literal.
5. **Less flavor code.** If Phase 5 is done, wording differences live in each flavor's resources
   instead of `BuildConfig.FLAVOR` checks.
6. **Correct plurals and number formats** through `<plurals>` and format arguments.
7. **Better accessibility.** The 83 TalkBack labels get translated too.
8. **Play Console translation service** works directly on `strings.xml`, and more listed
   languages help store reach.
9. **One place to fix wording**, with texts like "Done" and "Try again" reused.
10. **Matches iOS.** Shared key names, such as `quiz_choose_best_answer`, let one translation
    sheet fill both apps.
