package com.goodstadt.john.language.exams.packages.Format8

import com.goodstadt.john.language.exams.models.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Locale

class Format8PresentationTest {
    private fun fixture(name: String): Format8File {
        val path = "src/de/res/raw/$name.json"
        return Json.decodeFromString(listOf(File(path), File("app/$path")).first { it.isFile }.readText())
    }
    @Test fun abbreviationsMatchSwiftAndOtherLabelsStayUnchanged() {
        assertEquals("You (s), you (p), you (f), (m.a.), (m.d.).",
            Format8Presentation.compactEnglish("You (singular), you (plural), you (formal), (Masculine Accusative), (Masculine Dative)."))
        assertEquals("I know her (the woman).", Format8Presentation.compactEnglish("I know her (the woman)."))
    }
    @Test fun singleSentencesLoseOnlyTheirFinalFullStop() {
        assertEquals("Er ist groß", Format8Presentation.displaySentence("Er ist groß."))
        assertEquals("He is tall", Format8Presentation.displaySentence("He is tall.", Locale.ENGLISH))
        assertEquals("I help him (m.d.)", Format8Presentation.displaySentence("I help him. (m.d.)", Locale.ENGLISH))
        assertEquals("Who is here?", Format8Presentation.displaySentence("Who is here?", Locale.ENGLISH))
        assertEquals("Wait...", Format8Presentation.displaySentence("Wait...", Locale.ENGLISH))
    }
    @Test fun multipleSentencesRetainTheirPunctuation() {
        for (text in listOf("Er ist hier. Er lernt.", "He is here. He studies.", "Who is here? He is.")) {
            assertEquals(text, Format8Presentation.displaySentence(text, Locale.ENGLISH))
        }
    }
    @Test fun highlightingIsCaseSensitiveAndMatchesWholeWordsOnly() {
        val text = "Der Lehrer sieht sie. Sie sieht sie."
        val styled = Format8Presentation.highlight(text, listOf("er", "sie"))
        assertEquals(listOf("sie", "sie"), styled.spanStyles.map { text.substring(it.start, it.end) })
        assertEquals(text, styled.text)
    }
    @Test fun bothIntroductionsHighlightAllRequestedTokens() {
        val inputs = listOf(
            "german_pronouns2_accusative2" to listOf("mich", "dich", "ihn", "sie", "es", "uns", "euch", "Sie"),
            "german_pronouns2_both_dative2" to listOf("antworten", "helfen", "danken", "folgen", "glauben", "vertrauen", "zuhören", "gratulieren"))
        for ((name, tokens) in inputs) {
            val file = fixture(name)
            val styled = Format8Presentation.introduction(file)
            for (token in tokens) {
                for (match in Format8Presentation.tokenRegex(token).findAll(file.intro)) {
                    assertTrue("Not highlighted: $token", styled.spanStyles.any { it.start == match.range.first && it.end == match.range.last + 1 })
                }
            }
        }
    }
    @Test fun repeatGapMatchesSwiftLengthScaling() {
        assertEquals(1000L, Format8Presentation.speakAloudGapMillis(""))
        assertEquals(2000L, Format8Presentation.speakAloudGapMillis("12345678"))
    }
    private fun quizFile(vararg examples: Pair<String, String>) = Format8File(8, 1, "test", "Test", "", 0, "",
        listOf(Format8Section("", 1, "", "", listOf(Format8Entry("test", Format8EntryKind.PRONOUN, "", "",
            examples.map { (text, key) -> Format8Example(text, "", key, "", key) })))) )
    @Test fun quizSkipsSingleKeyAndAmbiguousBlanks() {
        assertTrue(Format8QuizGenerator.generate(quizFile("Ich kenne ihn." to "ihn", "Du kennst ihn." to "ihn")).isEmpty())
        assertTrue(Format8QuizGenerator.generate(quizFile("Er ist hier." to "Er", "Sie ist hier." to "Sie", "Es ist hier." to "Es")).isEmpty())
    }
    @Test fun quizProducesOneCorrectAnswerAndDistinctWrongOptions() {
        val questions = Format8QuizGenerator.generate(quizFile("Ich bin hier." to "Ich", "Du bist hier." to "Du", "Wir sind hier." to "Wir"))
        assertEquals(3, questions.size)
        questions.forEach { q ->
            assertTrue(q.sentence.contains("_"))
            assertEquals(1, q.words.count { it.ok })
            assertEquals(q.words.size, q.words.map { it.word.lowercase() }.distinct().size)
        }
    }
}
