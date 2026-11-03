package com.goodstadt.john.language.exams.data.stats

import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.screens.shared.SessionFeedbackManager

/**
 * Quiz stats key grammar — the Android twin of the iOS `QuizStat`. Written to BOTH the global monthly doc
 * (stats/YYYY-MM) and the per-user doc (users/<uid>), keyed:
 *
 *     q_<platform>_<area>_<sheetId>_<metric>        e.g.  q_and_grammar_A1_PresentSimple_ok
 *
 * No language segment — each flavour uploads to its own Firebase database, so the language is implied by
 * the database. The metric is LAST so the Firestore console (which sorts field names alphabetically) keeps
 * every counter for one quiz together, and every quiz within an area together. Flat "_" delimiter.
 *
 * Recorded ONCE per COMPLETED quiz (not per answer):
 *   runs  += 1               — a completed quiz
 *   ok    += correct         — questions answered correctly
 *   notok += (tries-correct) — not-correct attempts
 *   total += tries           — all attempts   (so ok + notok == total)
 */
object QuizStat {
    enum class Area(val id: String) {
        USAGE("usage"), GRAMMAR("grammar"), VOCAB("vocab"), AUDIT("audit"), REFERENCE("reference")
    }

    const val PLATFORM = "and"

    fun key(area: Area, sheetId: String, metric: String): String =
        "q_${PLATFORM}_${area.id}_${normalize(sheetId)}_$metric"

    /** Drop a trailing "-<flavour>", split any "/" hierarchy, reduce each segment to alphanumerics, join "_". */
    fun normalize(raw: String): String {
        var s = raw
        val suffix = "-${BuildConfig.FLAVOR}"
        if (s.endsWith(suffix)) s = s.dropLast(suffix.length)
        val segments = s.split("/")
            .map { seg -> seg.filter { it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }
        return if (segments.isEmpty()) "unknown" else segments.joinToString("_")
    }
}

/**
 * Record ONE completed quiz to both GlobalStats (stats/YYYY-MM) and USER (users/<uid>). The existing
 * onAppBackgrounded()/onAppForeground() flush uploads and clears these — no extra plumbing needed.
 */
fun TTSStatsRepository.recordQuizCompletion(area: QuizStat.Area, sheetId: String, correct: Int, tries: Int) {
    val wrong = maxOf(0, tries - correct)
    val runsKey = QuizStat.key(area, sheetId, "runs")
    val okKey = QuizStat.key(area, sheetId, "ok")
    val notokKey = QuizStat.key(area, sheetId, "notok")
    val totalKey = QuizStat.key(area, sheetId, "total")
    for (doc in listOf(TTSStatsRepository.fsDOC.GlobalStats, TTSStatsRepository.fsDOC.USER)) {
        inc(doc, runsKey, 1)
        inc(doc, okKey, maxOf(0, correct))
        inc(doc, notokKey, wrong)
        inc(doc, totalKey, maxOf(0, tries))
    }
}

/**
 * Page-popularity key grammar — the page-view twin of [QuizStat], and the Android twin of iOS `PageStat`.
 * How often users open (and interact with) a screen, so we can see which reference sheets — and later other
 * screens — are popular. Same grammar as quiz stats but with a `p_` prefix:
 *
 *     p_<platform>_<area>_<sheetId>_<metric>     e.g.  p_and_reference_GermanPrepositionsTeaching_open
 *
 * Metrics: `open` (screen shown), `tap` (user interacted) and `play` (a sentence was actually heard). Reuses [QuizStat.normalize] so sheet ids are
 * cleaned identically. Written to both the global monthly doc and the per-user doc; the existing
 * background/foreground flush uploads and clears them.
 */
object PageStat {
    enum class Area(val id: String) {
        REFERENCE("reference"), ME("me"), WORD_OF_THE_DAY("wordoftheday"), SAVED("saved"),
        // The main tabs 1/2/3 (fileFormat-0).
        VOCAB("vocab")
    }
    enum class Metric(val id: String) { OPEN("open"), TAP("tap"), PLAY("play") }

    const val PLATFORM = "and"

    fun key(area: Area, sheetId: String, metric: Metric): String =
        "p_${PLATFORM}_${area.id}_${QuizStat.normalize(sheetId)}_${metric.id}"
}

/** Record ONE page event (default OPEN) to both GlobalStats (stats/YYYY-MM) and USER (users/<uid>). */
fun TTSStatsRepository.recordPageView(
    area: PageStat.Area,
    sheetId: String,
    metric: PageStat.Metric = PageStat.Metric.OPEN
) {
    val name = PageStat.key(area, sheetId, metric)
    for (doc in listOf(TTSStatsRepository.fsDOC.GlobalStats, TTSStatsRepository.fsDOC.USER)) {
        inc(doc, name, 1)
    }
    if (metric == PageStat.Metric.TAP) {
        when (area) {
            PageStat.Area.VOCAB -> sessionFeedbackManager.recordTap(SessionFeedbackManager.Area.TABS)
            PageStat.Area.REFERENCE -> sessionFeedbackManager.recordTap(SessionFeedbackManager.Area.REFERENCE)
            else -> Unit
        }
    }
}

/**
 * Podcast listening stats — how long people actually listen and which scenes get heard. Same `p_` grammar
 * as [PageStat], area `podcast`. Twin of iOS `PodcastStat`.
 *
 *     p_<platform>_podcast_<sheet>_secs                 seconds of audio heard (global monthly + user)
 *     p_<platform>_podcast_<sheet>_<scene>_play         playback entered that scene (global monthly + user)
 *     p_<platform>_podcast_<sheet>_<scene>_secs         seconds heard in that scene (global monthly + user)
 *     p_<platform>_podcast_secs_d<dd>                   all podcasts, per day of the month (global monthly)
 *     podcast_secs_<yyyy-MM-dd>                         all podcasts, per day (user doc)
 *
 * The day in a key is the day of listening (stamped at inc time), not the day the stats are flushed.
 * Seconds are whole numbers so they stay Firestore integer increments.
 */
object PodcastStat {
    private const val AREA = "podcast"

    fun secsKey(sheet: String): String =
        "p_${PageStat.PLATFORM}_${AREA}_${QuizStat.normalize(sheet)}_secs"

    fun sceneKey(sheet: String, sceneId: String, metric: String): String =
        "p_${PageStat.PLATFORM}_${AREA}_${QuizStat.normalize(sheet)}_${QuizStat.normalize(sceneId)}_$metric"

    fun dayOfMonthKey(date: java.time.LocalDate = java.time.LocalDate.now()): String =
        "p_${PageStat.PLATFORM}_${AREA}_secs_d${date.dayOfMonth.toString().padStart(2, '0')}" // ASCII digits in any locale

    fun userDayKey(date: java.time.LocalDate = java.time.LocalDate.now()): String =
        "podcast_secs_$date" // ISO yyyy-MM-dd
}

/** Playback entered [sceneId] of podcast [sheet] (counted once per entry, whatever started it). */
fun TTSStatsRepository.recordPodcastScenePlay(sheet: String, sceneId: String) {
    val name = PodcastStat.sceneKey(sheet, sceneId, "play")
    inc(TTSStatsRepository.fsDOC.GlobalStats, name)
    inc(TTSStatsRepository.fsDOC.USER, name)
}

/** [seconds] of audio heard in [sceneId] of podcast [sheet]: podcast, scene and per-day totals. */
fun TTSStatsRepository.recordPodcastListen(sheet: String, sceneId: String?, seconds: Int) {
    if (seconds <= 0) return
    val global = TTSStatsRepository.fsDOC.GlobalStats
    val user = TTSStatsRepository.fsDOC.USER
    val secs = PodcastStat.secsKey(sheet)
    inc(global, secs, seconds)
    inc(user, secs, seconds)
    sceneId?.let {
        val sceneSecs = PodcastStat.sceneKey(sheet, it, "secs")
        inc(global, sceneSecs, seconds)
        inc(user, sceneSecs, seconds)
    }
    inc(global, PodcastStat.dayOfMonthKey(), seconds)
    inc(user, PodcastStat.userDayKey(), seconds)
}
