package com.goodstadt.john.language.exams.config

/** Debug override for the freemium premium check (see [DebugFlags.PREMIUM_OVERRIDE]). */
enum class PremiumOverride {
    /** Use the real in-app-purchase status (normal behaviour). */
    USE_REAL,

    /** Pretend the user IS premium - everything unlocked. */
    FORCE_PREMIUM,

    /** Pretend the user is NOT premium - teasers/locks visible even if the IAP is actually owned. */
    FORCE_FREE
}

/**
 * Developer-only switches. Every flag here MUST be used behind a `BuildConfig.DEBUG` check at the
 * call site, so it can never affect a release build.
 */
object DebugFlags {

    /**
     * Test the lock / unlock UI without crossing the real in-app-purchase boundary (which is slow and
     * clumsy on Android). Only honoured in DEBUG builds - [com.goodstadt.john.language.exams.managers.AccessPolicy]
     * applies it behind a `BuildConfig.DEBUG` check, so it can never affect release. Set the value,
     * rebuild, and every gated screen behaves as if premium is USE_REAL / FORCE_PREMIUM / FORCE_FREE.
     * Leave as [PremiumOverride.USE_REAL] for normal runs.
     */
    val PREMIUM_OVERRIDE: PremiumOverride = PremiumOverride.USE_REAL

    /**
     * Rate-limit testing. Only honoured in DEBUG builds (RateLimiterModule applies it behind a
     * `BuildConfig.DEBUG` check, so release ALWAYS uses the live HOURLY_LIMIT / DAILY_LIMIT).
     *   true  -> use the low [RATE_LIMIT_TEST_HOURLY] / [RATE_LIMIT_TEST_DAILY] limits so the paywall
     *            triggers after just a few plays;
     *   false -> use the live limits even in debug (test normal behaviour).
     * Flip this instead of editing RateLimiterModule.
     */
    const val RATE_LIMIT_TEST = false

    /** Low limits used when [RATE_LIMIT_TEST] is on (debug only). Tune to taste. */
    const val RATE_LIMIT_TEST_HOURLY = 2
    const val RATE_LIMIT_TEST_DAILY = 4
}
