package com.goodstadt.john.language.exams.data.examsheets

import com.goodstadt.john.language.exams.models.Format7or10File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Usage Quiz translate button only appears when the loaded sheet carries "_en" fields. These bundled
 * files are the offline fallback and the source copied from iOS, so guard that every German usage quiz
 * still decodes with the real model and still has its English translations.
 */
class GermanUsageQuizTranslationsTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun quizFiles(): List<File> =
        listOf(File("src/de/assets/Quizzes/UsageQuiz"), File("app/src/de/assets/Quizzes/UsageQuiz"))
            .first { it.isDirectory }
            .listFiles { f -> f.name.matches(Regex("UsageQuiz\\d[AB][12]-de\\.json")) }!!
            .sortedBy { it.name }

    @Test fun allTwentyFourGermanUsageQuizzesCarryEnglishTranslations() {
        val files = quizFiles()
        assertEquals(24, files.size)
        for (file in files) {
            val quiz = json.decodeFromString<Format7or10File>(file.readText())
            val list = quiz.data.first()
            assertTrue("${file.name}: learningTitle_en", !list.learningTitleEn.isNullOrBlank())
            assertTrue("${file.name}: learningPoints_en", list.learningPointsEn.size == list.learningPoints.size)
            list.sections.forEach { section ->
                assertTrue("${file.name} p${section.page}: summary_en", !section.summaryEn.isNullOrBlank())
                assertTrue("${file.name} p${section.page}: explain_en", !section.explainEn.isNullOrBlank())
            }
        }
    }
}
