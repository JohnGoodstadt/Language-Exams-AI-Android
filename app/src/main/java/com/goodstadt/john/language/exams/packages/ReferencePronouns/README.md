# Pronouns reference feature

This folder contains one language-independent Compose feature. The same models,
repository, ViewModel, screen and text-to-speech helper render either JSON file;
only the selected `languageTag` changes.

The files are supplied as a drop-in design because the Android project source
was not present in this workspace.

## Data locations

- German Firestore document:
  `/global/exam_sheets/sheets/PronounsReference-de`
- German bundled fallback asset:
  `src/main/assets/PronounsReference-de.json`
- English Firestore document:
  `/global/exam_sheets/sheets/PronounsReference-en`
- English bundled fallback asset:
  `src/main/assets/PronounsReference-en.json`

`PronounsReferenceSources` maps a language tag to its Firestore document and
fallback asset. This keeps those storage details out of the UI classes.

## Reference tab integration

If the product flavour is exactly `de` or `en`, add this where the existing
Reference tab chooses its content:

```kotlin
PronounsReferenceDestination(
    languageTag = BuildConfig.FLAVOR,
    modifier = Modifier.fillMaxSize(),
)
```

You can instead pass the app's own content-language setting:

```kotlin
PronounsReferenceDestination(
    languageTag = selectedLanguageTag,
    modifier = Modifier.fillMaxSize(),
)
```

Both simple tags such as `de` and regional tags such as `de-DE` are accepted.
If the Reference tab uses Navigation Compose, the destination can be:

```kotlin
composable("reference/pronouns") {
    PronounsReferenceDestination(languageTag = BuildConfig.FLAVOR)
}
```

## Language-independent source names

- `PronounsReferenceModels.kt`
- `PronounsReferenceRepository.kt`
- `PronounsReferenceViewModel.kt`
- `PronounsReferenceScreen.kt`
- `PronounsReferenceDestination.kt`
- `PronounSpeaker.kt`

The JSON schema is also shared. Localised wording lives in neutral fields such
as `label`, `prompt`, `sentence` and `supportText`; locale-specific field names
are not required.

## Dependencies expected by the sample

```kotlin
implementation("androidx.lifecycle:lifecycle-runtime-compose:<version>")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:<version>")
implementation("com.google.firebase:firebase-firestore")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:<version>")
implementation("com.google.code.gson:gson:<version>")
```

Use the versions or Firebase BoM already selected by the app. Android
`TextToSpeech` requires no microphone or audio-file permission. Each displayed
reference sentence can be played separately, and each case pattern can be
played as one sequence.
