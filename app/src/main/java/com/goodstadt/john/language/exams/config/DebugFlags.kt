package com.goodstadt.john.language.exams.config

/**
 * Developer-only switches. Every flag here MUST be used behind a `BuildConfig.DEBUG` check at the
 * call site, so it can never affect a release build.
 */
object DebugFlags {

    /**
     * When true (debug builds AND the German `de` flavour only), vocab Format0 sheets load STRAIGHT
     * from the bundled resource, skipping the Firestore round-trip. German has no Firestore content
     * yet, so the network fetch otherwise always fails slowly before falling back to the bundle.
     * English is unaffected (all its content is on Firestore). Set to false to restore the normal
     * cache -> Firestore -> bundle path for German too.
     */
    const val BUNDLE_ONLY_VOCAB = true

    /**
     * Exception list to [BUNDLE_ONLY_VOCAB]: vocab sheet logical names that should STILL be fetched
     * from Firestore even while bundle-only is on. Add a sheet here once you've uploaded it and want
     * to test the live copy; every other sheet keeps loading from the bundle. Names are the logical
     * sheet names, e.g. "GermanA1Vocab". de debug builds only. Empty = all sheets from the bundle.
     *
     * Typical flow: upload GermanA2Vocab -> add "GermanA2Vocab" here -> rebuild -> only that sheet
     * loads from Firestore (and caches to files/GermanA2Vocab_cache.json) while the rest stay bundled.
     */
    val FIRESTORE_TEST_SHEETS: Set<String> = setOf(
         "GermanA1Vocab",
         "GermanA2Vocab",
         "GermanB1Vocab",
         "GermanB2Vocab",
        "GermanA1Adjectives",
        "GermanA2Adjectives",
        "GermanB1Adjectives",
        "GermanB2Adjectives",
        "GermanConjugationsToDo",
        "GermanConjugationsToHave",
        "GermanConjugationsToGet",
        "GermanConjugationsToBe",
        "GermanFragenBitten",
        "GermanHoerenZuhoeren",
        "GermanKennenWissen",
        "GermanBringenHolen",
        "GermanPrepositions",

    )
}
