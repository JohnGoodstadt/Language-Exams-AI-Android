package com.goodstadt.john.language.exams.data.stats

import com.goodstadt.john.language.exams.BuildConfig
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository

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
