package com.goodstadt.john.language.exams.data.examsheets

import android.content.Context
import com.goodstadt.john.language.exams.data.FirestoreRepository.fb
import com.goodstadt.john.language.exams.models.Format8File
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import com.google.gson.reflect.TypeToken
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class Format8FirestoreRepositoryTest {
    @get:Rule val temp = TemporaryFolder()
    private val json = Json { ignoreUnknownKeys = true }
    private val gson = GsonBuilder().setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE).create()
    private val name = "GermanPronouns3Ich"

    private fun source(resource: String = "german_pronouns3_ich"): String =
        listOf(File("src/de/res/raw/$resource.json"), File("app/src/de/res/raw/$resource.json"))
            .first { it.isFile }.readText()

    private fun fields(source: String = source()): MutableMap<String, Any> =
        gson.fromJson(source, object : TypeToken<MutableMap<String, Any>>() {}.type)

    private inner class Harness {
        val firestore: FirebaseFirestore = mockk()
        val document: DocumentReference = mockk()
        val snapshot: DocumentSnapshot = mockk()
        val repository: ExamSheetRepository
        init {
            val globals: CollectionReference = mockk()
            val root: DocumentReference = mockk()
            val sheets: CollectionReference = mockk()
            every { firestore.collection(fb.global) } returns globals
            every { globals.document(fb.exam_sheets) } returns root
            every { root.collection("sheets") } returns sheets
            every { sheets.document(any()) } returns document
            every { document.get(Source.SERVER) } returns Tasks.forResult(snapshot)
            every { snapshot.exists() } returns true
            every { snapshot.data } returns fields()
            val context: Context = mockk()
            every { context.filesDir } returns temp.root
            repository = ExamSheetRepository(firestore, mockk(), context, json)
        }
        fun cache(doc: String = name) = File(temp.root, "${doc}_cache.json")
    }

    @Test fun allFifteenUploadedHierarchiesRoundTripAndReturnFromDiskWithoutAnotherRead() = runBlocking {
        val h = Harness()
        val resources = listOf(File("src/de/res/raw"), File("app/src/de/res/raw")).first { it.isDirectory }
            .listFiles()!!.filter { it.name.startsWith("german_pronouns2_") || it.name.startsWith("german_pronouns3_") }
        assertEquals(15, resources.size)
        var examples = 0
        for (resource in resources) {
            val expected = json.decodeFromString<Format8File>(resource.readText())
            val doc = expected.sheetname
            every { h.snapshot.data } returns fields(resource.readText())
            val file = h.repository.getFormat8Sheet(doc, forceRefresh = true).getOrThrow()
            assertEquals(expected.copy(sections = expected.sections.sortedBy { it.sortorder }), file)
            assertEquals(file, json.decodeFromString<Format8File>(h.cache(doc).readText()))
            assertEquals(file, h.repository.getFormat8Sheet(doc, forceRefresh = false).getOrThrow())
            examples += file.sections.sumOf { section -> section.entries.sumOf { it.examples.size } }
        }
        assertEquals(875, examples)
        verify(exactly = 15) { h.document.get(Source.SERVER) }
    }

    @Test fun sectionDocumentsAreSortedWithoutReorderingEntriesOrDuplicateExamples() = runBlocking {
        val h = Harness()
        val expected = json.decodeFromString<Format8File>(source())
        val header = fields().apply { remove("sections") }
        every { h.snapshot.data } returns header
        val sections: CollectionReference = mockk()
        val query: QuerySnapshot = mockk()
        every { h.document.collection("sections") } returns sections
        every { sections.get(Source.SERVER) } returns Tasks.forResult(query)
        val sectionFields = (fields()["sections"] as List<*>).first() as Map<*, *>
        val docs = listOf("z" to 2L, "b" to 1L, "a" to 1L).map { (id, order) ->
            mockk<com.google.firebase.firestore.QueryDocumentSnapshot>().also { doc ->
                every { doc.id } returns id
                every { doc.data } returns sectionFields.entries.associate { it.key as String to it.value!! }
                    .toMutableMap().apply { put("sortorder", order); put("title", id) }
            }
        }
        every { query.documents } returns docs
        val file = h.repository.getFormat8Sheet(name, true).getOrThrow()
        assertEquals(listOf("a", "b", "z"), file.sections.map { it.title })
        assertTrue(file.sections.all { it.entries == expected.sections.single().entries })
    }

    @Test fun metadataAcceptsAnAliasOrDocumentNameAndTimestampSeconds() = runBlocking {
        val h = Harness()
        val data = fields().apply {
            remove("sheetname")
            put("sheetName", "AliasName")
            put("updatedDate", Timestamp(1_720_000_000L, 0))
        }
        every { h.snapshot.data } returns data
        val alias = h.repository.getFormat8Sheet(name, true).getOrThrow()
        assertEquals("AliasName", alias.sheetname)
        assertEquals(1_720_000_000, alias.updatedDate)
        data.remove("sheetName")
        assertEquals(name, h.repository.getFormat8Sheet(name, true).getOrThrow().sheetname)
    }

    @Test fun forceRefreshBypassesAValidCacheAndReplacesIt() = runBlocking {
        val h = Harness()
        val original = json.decodeFromString<Format8File>(source())
        h.cache().writeText(json.encodeToString(Format8File.serializer(), original))
        every { h.snapshot.data } returns fields().apply { put("title", "Updated sheet") }
        assertEquals(original, h.repository.getFormat8Sheet(name, false).getOrThrow())
        verify(exactly = 0) { h.document.get(any<Source>()) }
        val fresh = h.repository.getFormat8Sheet(name, true).getOrThrow()
        assertEquals("Updated sheet", fresh.title)
        assertEquals(fresh, json.decodeFromString<Format8File>(h.cache().readText()))
        verify(exactly = 1) { h.document.get(Source.SERVER) }
    }

    @Test fun corruptDiskCacheIsReplacedByDownloadedData() = runBlocking {
        val h = Harness()
        h.cache().writeText("invalid json")
        val file = h.repository.getFormat8Sheet(name, false).getOrThrow()
        assertEquals(name, file.sheetname)
        assertEquals(file, json.decodeFromString<Format8File>(h.cache().readText()))
    }

    @Test fun missingDocumentAndMalformedOrWrongFormatDataNeverReplaceAValidCache() = runBlocking {
        val h = Harness()
        val original = source()
        h.cache().writeText(original)
        every { h.snapshot.exists() } returns false
        assertTrue(h.repository.getFormat8Sheet(name, true).isFailure)
        every { h.snapshot.exists() } returns true
        every { h.snapshot.data } returns fields().apply { put("fileformat", 2L) }
        assertTrue(h.repository.getFormat8Sheet(name, true).isFailure)
        every { h.snapshot.data } returns fields().apply { remove("intro") }
        assertTrue(h.repository.getFormat8Sheet(name, true).isFailure)
        every { h.snapshot.data } returns fields().apply { put("sections", emptyList<Any>()) }
        assertTrue(h.repository.getFormat8Sheet(name, true).isFailure)
        assertEquals(original, h.cache().readText())
    }

    @Test fun missingSectionSubcollectionReturnsFailureWithoutCaching() = runBlocking {
        val h = Harness()
        every { h.snapshot.data } returns fields().apply { remove("sections") }
        val sections: CollectionReference = mockk()
        val query: QuerySnapshot = mockk()
        every { h.document.collection("sections") } returns sections
        every { sections.get(Source.SERVER) } returns Tasks.forResult(query)
        every { query.documents } returns emptyList()
        assertTrue(h.repository.getFormat8Sheet(name, true).isFailure)
        assertFalse(h.cache().exists())
    }

    @Test fun networkErrorsReturnFailureAndCancellationPropagates() = runBlocking {
        val h = Harness()
        val failure = IOException("Offline")
        every { h.document.get(Source.SERVER) } returns Tasks.forException(failure)
        val returnedError = h.repository.getFormat8Sheet(name, true).exceptionOrNull()
        assertTrue(returnedError is IOException)
        assertEquals(failure.message, returnedError?.message)
        every { h.document.get(Source.SERVER) } returns Tasks.forException(CancellationException("Cancelled"))
        try {
            h.repository.getFormat8Sheet(name, true)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertFalse(h.cache().exists())
        }
    }
}
