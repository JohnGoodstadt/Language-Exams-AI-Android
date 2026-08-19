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
}
