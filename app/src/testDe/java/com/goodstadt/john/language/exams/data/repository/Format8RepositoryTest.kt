package com.goodstadt.john.language.exams.data.repository

import android.content.Context
import android.content.res.Resources
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.AppConfigRepository
import com.goodstadt.john.language.exams.data.examsheets.ExamSheetRepository
import com.goodstadt.john.language.exams.models.Format8EntryKind
import com.goodstadt.john.language.exams.models.Format8File
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

/** German-only tests: these raw resources and mappings intentionally belong to the de flavour. */
class Format8RepositoryTest {
    private val mapping = linkedMapOf(
        "GermanPronouns2Accusative2" to "german_pronouns2_accusative2",
        "GermanPronouns2BothCases2" to "german_pronouns2_both_case2",
        "GermanPronouns2Dative2" to "german_pronouns2_both_dative2",
        "GermanPronouns2LearningVerbs2" to "german_pronouns2_both_learning_verbs2",
        "GermanPronouns2Nominative2" to "german_pronouns2_both_nominative2",
        "GermanPronouns2Patterns2" to "german_pronouns2_both_patterns2",
        "GermanPronouns3Ich" to "german_pronouns3_ich",
        "GermanPronouns3Du" to "german_pronouns3_du",
        "GermanPronouns3Er" to "german_pronouns3_er",
        "GermanPronouns3SieSingular" to "german_pronouns3_sie_singular",
        "GermanPronouns3Es" to "german_pronouns3_es",
        "GermanPronouns3Wir" to "german_pronouns3_wir",
        "GermanPronouns3Ihr" to "german_pronouns3_ihr",
        "GermanPronouns3SiePlural" to "german_pronouns3_sie_plural",
        "GermanPronouns3SieFormal" to "german_pronouns3_sie_formal"
    )
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun fixture(resourceName: String): String {
        val relative = "src/de/res/raw/$resourceName.json"
        val file = listOf(File(relative), File("app/$relative")).firstOrNull { it.isFile }
        requireNotNull(file) { "Missing fixture: $resourceName" }
        return file.readText(Charsets.UTF_8)
    }

    private class Dependencies {
        val resources: Resources = mockk()
        val context: Context = mockk()
        val config: AppConfigRepository = mockk()
        val sheets: ExamSheetRepository = mockk()
        init {
            every { context.resources } returns resources
            every { context.packageName } returns "format8.test"
            coEvery { config.getRemoteSheetVersions() } returns emptyMap()
            every { config.getLocalVersion(any()) } returns 1
            coEvery { sheets.getFormat8Sheet(any(), any()) } returns Result.failure(IllegalStateException("Remote sheet unavailable"))
        }
        fun repository() = ContentRepository(context, config, sheets, mockk(), mockk(), mockk(), mockk())
    }

    @Test
    fun allFormat8ResourcesRoundTripWithEveryExampleAndHighlightKeyPreserved() {
        var count = 0
        for ((logicalName, resourceName) in mapping) {
            assertEquals(resourceName, LanguageConfig.mapLogicalToResourceName(logicalName))
            assertEquals(logicalName, LanguageConfig.normalizeToLogicalName(resourceName))
            assertEquals(logicalName, LanguageConfig.normalizeToLogicalName(logicalName))
            val source = fixture(resourceName)
            val file = json.decodeFromString<Format8File>(source)
            assertEquals(8, file.fileformat)
            assertEquals(json.parseToJsonElement(source), json.parseToJsonElement(json.encodeToString(file)))
            val examples = file.sections.flatMap { it.entries }.flatMap { it.examples }
            assertTrue(examples.all { it.key.isNotEmpty() && it.german.contains(it.key) })
            count += examples.size
        }
        assertEquals(875, count)
    }

    @Test
    fun enumKindsUseTheSameLowercaseValuesAsSwift() {
        val names = listOf("conjugation", "comparison", "pronoun", "pattern", "learning", "review")
        for ((kind, name) in Format8EntryKind.entries.zip(names)) {
            assertEquals("\"$name\"", json.encodeToString(kind))
            assertEquals(kind, json.decodeFromString<Format8EntryKind>("\"$name\""))
        }
    }

    @Test
    fun logicalAndResourceNamesShareTheMemoryCacheAfterBundleFallback() = runBlocking {
        val deps = Dependencies()
        val repo = deps.repository()
        for ((index, entry) in mapping.entries.withIndex()) {
            val id = index + 1
            val (logicalName, resourceName) = entry
            every { deps.resources.getIdentifier(resourceName, "raw", "format8.test") } returns id
            every { deps.resources.openRawResource(id) } answers {
                ByteArrayInputStream(fixture(resourceName).toByteArray(Charsets.UTF_8))
            }
            val first = repo.getFormat8Data(logicalName).getOrThrow()
            val second = repo.getFormat8Data(resourceName).getOrThrow()
            assertSame(first, second)
            verify(exactly = 1) { deps.resources.openRawResource(id) }
            coVerify(exactly = 1) { deps.sheets.getFormat8Sheet(logicalName, false) }
        }
    }

    @Test
    fun downloadedFilesUpdateTheirVersionAndUseMemoryOnTheNextRead() = runBlocking {
        val deps = Dependencies()
        val repo = deps.repository()
        val logical = "GermanPronouns3Ich"
        val remote = json.decodeFromString<Format8File>(fixture("german_pronouns3_ich"))
        coEvery { deps.config.getRemoteSheetVersions() } returns mapOf(logical to 2)
        every { deps.config.getLocalVersion(logical) } returnsMany listOf(1, 2)
        every { deps.config.updateLocalVersion(logical, 2) } returns Unit
        coEvery { deps.sheets.getFormat8Sheet(logical, true) } returns Result.success(remote)
        assertSame(remote, repo.getFormat8Data(logical).getOrThrow())
        assertSame(remote, repo.getFormat8Data("german_pronouns3_ich").getOrThrow())
        verify(exactly = 1) { deps.config.updateLocalVersion(logical, 2) }
        coVerify(exactly = 1) { deps.sheets.getFormat8Sheet(logical, any()) }
        verify(exactly = 0) { deps.resources.openRawResource(any()) }
    }

    @Test
    fun missingResourcesReturnFailureAndCanBeRetried() = runBlocking {
        val deps = Dependencies()
        val repo = deps.repository()
        val resource = mapping.values.first()
        val logical = mapping.keys.first()
        every { deps.resources.getIdentifier(resource, "raw", "format8.test") } returns 0
        assertTrue(repo.getFormat8Data(logical).isFailure)
        every { deps.resources.getIdentifier(resource, "raw", "format8.test") } returns 1
        every { deps.resources.openRawResource(1) } answers {
            ByteArrayInputStream(fixture(resource).toByteArray(Charsets.UTF_8))
        }
        assertTrue(repo.getFormat8Data(logical).isSuccess)
        coVerify(exactly = 2) { deps.sheets.getFormat8Sheet(logical, false) }
    }

    @Test
    fun wrongFormatsAreRejectedAndFailuresDoNotPoisonTheCache() = runBlocking {
        val deps = Dependencies()
        val repo = deps.repository()
        val resource = mapping.values.first()
        val logical = mapping.keys.first()
        var source = fixture(resource)
        val wrong = json.parseToJsonElement(source).jsonObject.toMutableMap()
        wrong["fileformat"] = JsonPrimitive(2)
        source = kotlinx.serialization.json.JsonObject(wrong).toString()
        every { deps.resources.getIdentifier(resource, "raw", "format8.test") } returns 1
        every { deps.resources.openRawResource(1) } answers {
            ByteArrayInputStream(source.toByteArray(Charsets.UTF_8))
        }
        assertTrue(repo.getFormat8Data(logical).isFailure)
        source = fixture(resource)
        assertTrue(repo.getFormat8Data(logical).isSuccess)
        verify(exactly = 2) { deps.resources.openRawResource(1) }
        coVerify(exactly = 2) { deps.sheets.getFormat8Sheet(logical, false) }
    }
}
