package com.goodstadt.john.language.exams.packages.Format9

import android.content.Context
import android.content.res.Resources
import com.goodstadt.john.language.exams.config.LanguageConfig
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class Format9ResourceTest {
    @Test fun logicalRawAndOldA2NamesAllReadTheCurrentFlavoursNewBundleFile() = runBlocking {
        val german = LanguageConfig.languageCode.startsWith("de")
        val flavour = if (german) "de" else "en"
        val logical = if (german) "GermanPodcastFootballTripStuttgart" else "EnglishPodcastFootballTripManchester"
        val raw = if (german) "german_podcast_football_trip_stuttgart" else "english_podcast_football_trip_manchester"
        val source = podcastFixtureText(flavour)
        val resources: Resources = mockk()
        val context: Context = mockk()
        every { context.resources } returns resources
        every { context.packageName } returns "podcast.test"
        every { resources.getIdentifier(raw, "raw", "podcast.test") } returns 123
        every { resources.openRawResource(123) } answers { ByteArrayInputStream(source.toByteArray()) }
        val repository = ContentRepository(context, mockk(), mockk(), mockk(), mockk(), mockk(), mockk())
        for (name in listOf(logical, raw, logical + "A2", raw + "_a2")) {
            assertEquals(logical, LanguageConfig.normalizeToLogicalName(name))
            assertEquals(raw, LanguageConfig.mapLogicalToResourceName(LanguageConfig.normalizeToLogicalName(name)))
            val file = repository.getFormat9Data(name).getOrThrow()
            assertEquals(logical, file.sheetname)
            assertEquals(flavour, file.spokenLanguageCode)
            assertTrue(file.spokenText(file.scenes.first().lines.first()).isNotBlank())
        }
        verify(exactly = 4) { resources.getIdentifier(raw, "raw", "podcast.test") }
    }
}
