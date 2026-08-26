package com.goodstadt.john.language.exams.managers

import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.config.DebugFlags
import com.goodstadt.john.language.exams.config.PremiumOverride
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * **AccessPolicy** - the single source of truth for the app's freemium gating.
 *
 * It answers small, side-effect-free questions ("is this level free?", "should this row show its
 * sentence?", "may we synthesise new audio for this row?") so that the vocab / reference screens and the
 * playback path all make the SAME decision from ONE place. This is deliberate: the recent
 * `isPremiumUser = false` bug across 11 view-models was caused by entitlement logic being copy-pasted
 * around. New gating rules should be added here, not inline in screens.
 *
 * Two independent levers are modelled separately, because they can be tuned independently:
 *  - **Detail visibility** ([isDetailVisible]) - whether a row shows its sentence/definition, or just its
 *    title/word as a locked teaser. Costs nothing; pure presentation.
 *  - **Audio** ([isAudioAllowed]) - whether the user may trigger a NEW text-to-speech synthesis for a row.
 *
 * ### What this class does NOT do (by design)
 *  - It does not replay-gate cached audio. A clip already in the local file cache ALWAYS replays; that is
 *    handled upstream in `AudioPlaybackRepository` (cache-first) and must stay that way. [isAudioAllowed]
 *    governs only fresh synthesis of gated content.
 *  - It does not do rate limiting. The hourly/daily TTS cost cap stays in `AudioPlaybackRepository` /
 *    `SimpleRateLimiter`. A row can be "audio allowed" here and still be rate-limited there.
 *  - It reads no preferences and does no IO, so every method is synchronous and cheap. Callers pass in the
 *    relevant `level` (from the sheet name, e.g. "B1" in GermanB1Vocab, or the user's selected level).
 *
 * ### Intended wiring (future changes, not done yet)
 *  - Vocab list VM/screen: for each row index, `isDetailVisible(VOCAB, level, index)` /
 *    `isAudioAllowed(VOCAB, level, index)`.
 *  - Reference VMs/screens (Conjugations, Prepositions, Adjectives, Format1/2/3): same, with `REFERENCE`.
 *  - At playback tap time, combine `isAudioAllowed(...)` with "is it already cached?" so a cached locked
 *    clip still replays (see note above); only a locked, NOT-cached row routes to the paywall.
 *
 * Nothing calls this yet - it is scaffolding to build on.
 */

/** Content domain, so the same list position can be gated differently per area. Extend as needed. */
enum class ContentArea {
    /** Main vocabulary word lists (GermanA1Vocab …). A1/A2 are entirely free. */
    VOCAB,

    /** Reference-tab lists: Conjugations, Prepositions, Adjectives, Sounds-the-Same, Word Pairs, … */
    REFERENCE
}

@Singleton
class AccessPolicy @Inject constructor(
    private val billingRepository: BillingRepository
) {

    /**
     * Live premium entitlement. Read at decision time so it reflects the current purchase state.
     *
     * In DEBUG builds only, [DebugFlags.PREMIUM_OVERRIDE] can force this true/false so the lock/unlock UI
     * can be tested without crossing the real IAP boundary. The `BuildConfig.DEBUG` guard means the
     * override is compiled out of release - production always uses the real purchase status.
     */
    val isPremium: Boolean
        get() {
            if (BuildConfig.DEBUG) {
                when (DebugFlags.PREMIUM_OVERRIDE) {
                    PremiumOverride.FORCE_PREMIUM -> return true
                    PremiumOverride.FORCE_FREE -> return false
                    PremiumOverride.USE_REAL -> Unit // fall through to the real status
                }
            }
            return billingRepository.isPurchased.value
        }

    // ---------------------------------------------------------------- tiering

    /** True for a vocab level that is entirely free (currently A1 / A2). Null / unknown -> not free. */
    fun isFreeVocabLevel(level: String?): Boolean =
        level?.trim()?.uppercase() in FREE_VOCAB_LEVELS

    /**
     * The whole (area, level) is open to the current user - no teaser at all. Premium unlocks everything;
     * otherwise only the free vocab levels are fully open. Reference lists are never fully open to a free
     * user (they get the preview), which matches "show the top few, tease the rest".
     */
    fun isFullyUnlocked(area: ContentArea, level: String? = null): Boolean = when {
        isPremium -> true
        area == ContentArea.VOCAB -> isFreeVocabLevel(level)
        else -> false
    }

    /** How many rows are shown in full before the teaser begins, for a non-fully-unlocked list. */
    fun previewCount(area: ContentArea): Int = when (area) {
        ContentArea.VOCAB -> FREE_VOCAB_PREVIEW
        ContentArea.REFERENCE -> FREE_REFERENCE_PREVIEW
    }

    // ---------------------------------------------------------------- per-row gates (index is 0-based)

    /**
     * Show this row's sentence / definition? When false, the caller should render the title / word only,
     * as a locked teaser (e.g. blurred line + lock icon that opens the paywall).
     */
    fun isDetailVisible(area: ContentArea, level: String?, index: Int): Boolean =
        isFullyUnlocked(area, level) || index < previewCount(area)

    /**
     * May the user trigger a NEW audio synthesis for this row? (Cached clips replay regardless - see the
     * class note.) Kept separate from [isDetailVisible] so audio and visibility can diverge later, even
     * though today they share the same preview depth.
     */
    fun isAudioAllowed(area: ContentArea, level: String?, index: Int): Boolean =
        isFullyUnlocked(area, level) || index < previewCount(area)

    /** True when a row is a locked teaser for the current user (convenience inverse of [isDetailVisible]). */
    fun isRowLocked(area: ContentArea, level: String?, index: Int): Boolean =
        !isDetailVisible(area, level, index)

    // ---------------------------------------------------------------- per-section gates (index is 0-based)

    /**
     * Section-level gate, for lists with collapsible headers (e.g. Conjugations) where the teaser is whole
     * sections rather than individual rows: all headers stay visible, but only the first few sections'
     * CONTENT is unlocked. [index] is the 0-based section/header position. Uses a smaller preview than the
     * per-row count because a sheet usually has only a handful of sections.
     */
    fun isSectionLocked(area: ContentArea, level: String? = null, index: Int): Boolean =
        !isFullyUnlocked(area, level) && index >= sectionPreviewCount(area)

    /** How many sections/headers are shown in full before the teaser begins. */
    fun sectionPreviewCount(area: ContentArea): Int = when (area) {
        ContentArea.VOCAB -> FREE_VOCAB_SECTION_PREVIEW
        ContentArea.REFERENCE -> FREE_REFERENCE_SECTION_PREVIEW
    }

    companion object {
        /** Vocab levels that are entirely free. */
        val FREE_VOCAB_LEVELS = setOf("A1", "A2")

        // Rows shown in full before the paywall teaser begins. Absolute counts (not a percentage) so the
        // free allowance is predictable across list sizes. TODO: source these from remote config so they
        // can be A/B tested and tuned against actual TTS spend without shipping a build.
        const val FREE_VOCAB_PREVIEW = 15
        const val FREE_REFERENCE_PREVIEW = 10

        // Sections/headers shown in full (collapsible-header lists like Conjugations). Smaller than the
        // per-row counts: a sheet has only a few headers, so 2 gives a "top few" teaser. TODO: remote config.
        const val FREE_VOCAB_SECTION_PREVIEW = 2
        const val FREE_REFERENCE_SECTION_PREVIEW = 2
    }
}
