package com.goodstadt.john.language.exams.data.strength

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** How strong the learner is in one reference area, bucketed from the blended mark. */
enum class ReferenceStrengthLevel { UNTESTED, WEAK, OK, STRONG }

/**
 * A resolved strength for one area id (a leaf) or a rolled-up parent path. [mark] is 0..1 (the blended
 * running score); [level] is its bucket; the totals give a confidence signal.
 */
data class ReferenceStrength(
    val areaId: String,
    val label: String,
    val mark: Float,
    val level: ReferenceStrengthLevel,
    val attempts: Int,
    val totalCorrect: Int,
    val totalAnswered: Int,
    val lastUpdated: Long
)

/**
 * Isolated, screen-agnostic store of the learner's strength in each REFERENCE-tab language area
 * (Prepositions, Adjectives, Pronouns, Sounds Similar, Word Pairs — and anything added later). A quiz
 * calls [recordQuizResult] once when it finishes; the repo blends that run into a per-area running mark
 * (an exponential moving average, so a fresh good run pulls the mark up), buckets it Weak/OK/Strong,
 * and persists it per install.
 *
 * Area ids are **hierarchical paths** ("Pronouns/Possessive/your"), so the same store answers both fine
 * questions ("your possessive is weak" — a leaf, [leaf]) and coarse ones ("your Personal pronouns are
 * fine" — a rollup, [rollup] / [topAreas]). MyProgress observes [strengths] and reads [weakAreas].
 * There is no screen dependency: adding a new quiz is a single [recordQuizResult] call.
 */
@Singleton
class ReferenceStrengthRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()
    private val fileName = "reference_strength.json"
    private val scope = CoroutineScope(Dispatchers.IO)

    // areaId -> running record. Accessed from caller threads (record*) and the IO scope (save/load),
    // and Gson iterates it while serialising, so every read/mutation/serialise holds [lock].
    private var store: MutableMap<String, Stored> = mutableMapOf()
    private val lock = Any()

    private val _strengths = MutableStateFlow<List<ReferenceStrength>>(emptyList())
    /** Every leaf area quizzed so far (level UNTESTED→STRONG, then id). MyProgress observes this. */
    val strengths: StateFlow<List<ReferenceStrength>> = _strengths.asStateFlow()

    init { loadFromDisk() }

    /**
     * Record one finished quiz run for [areaId] (a hierarchical path such as "Pronouns/Possessive").
     * [label] is a human name for that exact area (used in a weak-areas list). [correct] out of
     * [answered] (each quiz is ≤ 10). The run's percentage is blended into the running mark with
     * [ALPHA] — the first run seeds it outright, later runs move it — so improving pulls a weak area up.
     * A no-op when [answered] ≤ 0.
     */
    fun recordQuizResult(areaId: String, label: String, correct: Int, answered: Int) {
        if (answered <= 0) return
        val pct = (correct.toFloat() / answered).coerceIn(0f, 1f)
        synchronized(lock) {
            val prev = store[areaId]
            val mark = if (prev == null || prev.attempts == 0) pct
            else ALPHA * pct + (1 - ALPHA) * prev.mark
            store[areaId] = Stored(
                label = label.ifBlank { prev?.label ?: areaId },
                mark = mark,
                attempts = (prev?.attempts ?: 0) + 1,
                totalCorrect = (prev?.totalCorrect ?: 0) + correct,
                totalAnswered = (prev?.totalAnswered ?: 0) + answered,
                lastUpdated = System.currentTimeMillis()
            )
        }
        Timber.d("ReferenceStrength: '$areaId' <- $correct/$answered")
        publish()
    }

    /** The exact strength stored for [areaId], or null if that area was never quizzed. */
    fun leaf(areaId: String): ReferenceStrength? =
        synchronized(lock) { store[areaId]?.toStrength(areaId) }

    /**
     * A rolled-up strength for everything under [prefix] (inclusive): e.g. `rollup("Pronouns/Personal")`
     * or the whole-area `rollup("Pronouns")`. The mark is the descendant leaves' marks weighted by how
     * many questions each has answered; the level uses the same thresholds. null if nothing is under it.
     */
    fun rollup(prefix: String, label: String = prefix): ReferenceStrength? = synchronized(lock) {
        val members = store.filterKeys { it == prefix || it.startsWith("$prefix/") }.values
        if (members.isEmpty()) return null
        val answered = members.sumOf { it.totalAnswered }
        val correct = members.sumOf { it.totalCorrect }
        val attempts = members.sumOf { it.attempts }
        val mark = if (answered > 0)
            (members.sumOf { (it.mark * it.totalAnswered).toDouble() } / answered).toFloat() else 0f
        ReferenceStrength(
            prefix, label, mark, levelOf(mark, answered, attempts),
            attempts, correct, answered, members.maxOf { it.lastUpdated }
        )
    }

    /** One rolled-up strength per top-level area segment (Pronouns, Adjectives …) — the coarse summary. */
    fun topAreas(): List<ReferenceStrength> =
        synchronized(lock) { store.keys.map { it.substringBefore('/') }.distinct() }
            .mapNotNull { rollup(it) }
            .sortedBy { it.level.ordinal }

    /** Leaves currently rated WEAK, weakest first — the raw material for a "weak areas" section. */
    fun weakAreas(): List<ReferenceStrength> =
        _strengths.value.filter { it.level == ReferenceStrengthLevel.WEAK }.sortedBy { it.mark }

    // --- internals ---

    private fun levelOf(mark: Float, totalAnswered: Int, attempts: Int): ReferenceStrengthLevel = when {
        attempts == 0 || totalAnswered < MIN_ANSWERED -> ReferenceStrengthLevel.UNTESTED
        mark < WEAK_CEILING -> ReferenceStrengthLevel.WEAK
        mark < STRONG_FLOOR -> ReferenceStrengthLevel.OK
        else -> ReferenceStrengthLevel.STRONG
    }

    private fun Stored.toStrength(areaId: String) = ReferenceStrength(
        areaId, label, mark, levelOf(mark, totalAnswered, attempts),
        attempts, totalCorrect, totalAnswered, lastUpdated
    )

    private fun snapshotStrengths(): List<ReferenceStrength> =
        synchronized(lock) { store.map { (id, s) -> s.toStrength(id) } }
            .sortedWith(compareBy({ it.level.ordinal }, { it.areaId }))

    private fun publish() {
        _strengths.value = snapshotStrengths()
        val json = synchronized(lock) { gson.toJson(store) }
        scope.launch {
            try {
                File(context.filesDir, fileName).writeText(json)
            } catch (e: Exception) {
                Timber.e(e, "ReferenceStrength: save failed")
            }
        }
    }

    private fun loadFromDisk() {
        scope.launch {
            try {
                val file = File(context.filesDir, fileName)
                if (!file.exists()) return@launch
                val type = object : TypeToken<MutableMap<String, Stored>>() {}.type
                val loaded: MutableMap<String, Stored>? = gson.fromJson(file.readText(), type)
                if (loaded != null) {
                    synchronized(lock) { store = loaded }
                    _strengths.value = snapshotStrengths()
                }
            } catch (e: Exception) {
                Timber.e(e, "ReferenceStrength: load failed")
            }
        }
    }

    /** On-disk record: raw running fields; the [ReferenceStrengthLevel] is derived on read. */
    private data class Stored(
        val label: String = "",
        val mark: Float = 0f,
        val attempts: Int = 0,
        val totalCorrect: Int = 0,
        val totalAnswered: Int = 0,
        val lastUpdated: Long = 0
    )

    companion object {
        /** EMA weight for the newest run (0..1). Higher = more responsive to the latest quiz. */
        private const val ALPHA = 0.5f
        /** Below this many lifetime answered questions an area stays UNTESTED (too little signal). */
        private const val MIN_ANSWERED = 3
        private const val WEAK_CEILING = 0.5f   // mark < 0.50  -> WEAK
        private const val STRONG_FLOOR = 0.8f   // mark >= 0.80 -> STRONG (in between -> OK)
    }
}
