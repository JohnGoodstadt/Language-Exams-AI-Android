package com.goodstadt.john.language.exams.data

import android.content.Context
import com.goodstadt.john.language.exams.models.TestMyselfListRoot
import com.goodstadt.john.language.exams.packages.UsageQuiz.QuizQuestion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds category practice quizzes on the fly from the already-tagged question banks
 * (ReadinessAudit + UsageQuiz). No per-category files needed: every question carries a `category`
 * + `level`, so a "Modal Verbs" quiz is just every question tagged Modal Verbs, pooled across all
 * files (any level). The asset dirs are scanned, so newly-added tagged files are picked up with no
 * code change; untagged/foreign questions (blank category) are ignored.
 */
@Singleton
class CategoryQuizRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val quizDirs = listOf("Quizzes/ReadinessAudit", "Quizzes/UsageQuiz")

    // category -> every tagged question for it (across all files & levels). Built once, cached.
    @Volatile
    private var index: Map<String, List<QuizQuestion>>? = null

    private fun buildIndex(): Map<String, List<QuizQuestion>> {
        val byCategory = mutableMapOf<String, MutableList<QuizQuestion>>()
        for (dir in quizDirs) {
            val files = context.assets.list(dir).orEmpty().filter { it.endsWith(".json") }
            for (file in files) {
                val root = runCatching {
                    context.assets.open("$dir/$file").bufferedReader().use {
                        json.decodeFromString<TestMyselfListRoot>(it.readText())
                    }
                }.getOrNull() ?: continue

                for (group in root.data) {
                    for (sec in group.sections) {
                        val cat = sec.category?.trim().orEmpty()
                        if (cat.isEmpty()) continue // untagged / foreign-language questions
                        val question = QuizQuestion(
                            sentence = sec.sentence,
                            words = sec.words.map { it.word },
                            correctOption = sec.words.firstOrNull { it.ok }?.word ?: "",
                            summary = sec.summary,
                            explain = sec.explain,
                            title = sec.title,
                            page = sec.page,
                            level = sec.level,
                            category = cat,
                            fileFormat = root.fileFormat // 7 = fill-blank, 10 = choose-the-answer
                        )
                        byCategory.getOrPut(cat) { mutableListOf() }.add(question)
                    }
                }
            }
        }
        return byCategory
    }

    private suspend fun ensureIndex(): Map<String, List<QuizQuestion>> =
        index ?: withContext(Dispatchers.IO) {
            synchronized(this@CategoryQuizRepository) {
                index ?: buildIndex().also { index = it }
            }
        }

    /**
     * A practice quiz for [category].
     *
     * Canonical [GrammarCatalog] categories load directly from their own file
     * `Quizzes/Grammar/<level>/Grammar<key>-<lang>.json` (level = subfolder, category = filename key).
     * Anything else (legacy audit/usage categories) is pooled from the scanned index: questions at
     * [level] (exact CEFR match) if any, otherwise the whole category. Shuffled and capped at [max].
     */
    suspend fun quizForCategory(category: String, level: String? = null, max: Int = 10): List<QuizQuestion> {
        GrammarCatalog.fileKeyFor(category)?.let { key ->
            val fromGrammar = withContext(Dispatchers.IO) { loadGrammarFile(key, level) }
            if (fromGrammar.isNotEmpty()) return fromGrammar.shuffled().take(max)
            // Empty/missing grammar file -> fall through to the legacy pool below.
        }
        val all = ensureIndex()[category].orEmpty()
        val atLevel = if (level.isNullOrBlank()) all
        else all.filter { it.level.equals(level, ignoreCase = true) }
        return atLevel.ifEmpty { all }.shuffled().take(max)
    }

    /** The catalogue rows (category × level) for the Focus "all categories" browse view. */
    fun grammarCatalog(): List<GrammarRow> = GrammarCatalog.rows

    /**
     * Load one canonical grammar quiz file. The file is found by prefix so the flavour's language
     * suffix (-en / -de) doesn't need to be hard-coded here.
     */
    private fun loadGrammarFile(fileKey: String, level: String?): List<QuizQuestion> {
        if (level.isNullOrBlank()) return emptyList()
        val dir = "Quizzes/Grammar/$level"
        val file = context.assets.list(dir).orEmpty()
            .firstOrNull { it.startsWith("Grammar$fileKey-") && it.endsWith(".json") }
            ?: return emptyList()
        val root = runCatching {
            context.assets.open("$dir/$file").bufferedReader().use {
                json.decodeFromString<TestMyselfListRoot>(it.readText())
            }
        }.getOrNull() ?: return emptyList()

        val display = GrammarCatalog.displayNameFor(fileKey)
        return root.data.flatMap { group ->
            group.sections.map { sec ->
                QuizQuestion(
                    sentence = sec.sentence,
                    words = sec.words.map { it.word },
                    correctOption = sec.words.firstOrNull { it.ok }?.word ?: "",
                    summary = sec.summary,
                    explain = sec.explain,
                    title = sec.title,
                    page = sec.page,
                    level = sec.level ?: level,
                    category = sec.category?.trim()?.ifEmpty { null } ?: display,
                    fileFormat = root.fileFormat
                )
            }
        }
    }

    /** All categories that currently have at least one tagged question. */
    suspend fun categories(): Set<String> = ensureIndex().keys
}
