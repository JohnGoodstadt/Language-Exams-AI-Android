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
     * A practice quiz for [category]. Prefers questions at [level] (exact CEFR match); if there are
     * none at that level, falls back to the whole category. Shuffled and capped at [max].
     */
    suspend fun quizForCategory(category: String, level: String? = null, max: Int = 10): List<QuizQuestion> {
        val all = ensureIndex()[category].orEmpty()
        val atLevel = if (level.isNullOrBlank()) all
        else all.filter { it.level.equals(level, ignoreCase = true) }
        return atLevel.ifEmpty { all }.shuffled().take(max)
    }

    /** All categories that currently have at least one tagged question. */
    suspend fun categories(): Set<String> = ensureIndex().keys
}
