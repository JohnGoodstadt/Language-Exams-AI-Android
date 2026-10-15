package com.goodstadt.john.language.exams.packages.Format8

import com.goodstadt.john.language.exams.MainCoroutineRule
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.managers.SequencePlayer
import com.goodstadt.john.language.exams.models.AudioPlaybackStatus
import com.goodstadt.john.language.exams.utils.PlaybackEvent
import com.goodstadt.john.language.exams.utils.PlaybackEventBus
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Format8SequencePlayerTest {
    @get:Rule val mainRule = MainCoroutineRule()
    private val content: ContentRepository = mockk()
    private val audio: AudioPlaybackRepository = mockk(relaxed = true)
    private val preferences: UserPreferencesRepository = mockk()
    private val events = PlaybackEventBus()
    private val played = mutableListOf<String>()
    private fun player(): SequencePlayer {
        every { preferences.selectedVoiceNameFlow } returns flowOf("testVoice")
        every { preferences.selectedLanguageCodeFlow } returns flowOf("de")
        coEvery { content.ensureAudioCached(any(), any(), any(), any()) } returns true
        return SequencePlayer(content, audio, preferences, events)
    }
    private fun instantAudio() {
        coEvery { audio.playTrackAndGetStatus(any(), any(), any(), any(), any()) } coAnswers {
            played += firstArg<String>()
            events.tryEmit(PlaybackEvent.Completed())
            AudioPlaybackStatus.PlayedFromLocalCache
        }
    }
    @Test fun veryShortAudioStillPlaysAllRowsInOrderWithReferenceMetadata() = runTest {
        val player = player(); instantAudio()
        var completed = false
        player.toggle("entry", listOf("a", "b", "c"), "Reference", onCompleted = { completed = true }, sheetName = "Sheet", isPremiumUser = true)
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), played)
        assertTrue(completed); assertNull(player.playingKey.value)
        coVerify { audio.playTrackAndGetStatus("a", "Reference", "Sheet", true, false) }
    }
    @Test fun pauseDuringRepeatGapPreventsAllRemainingRows() = runTest {
        val player = player(); instantAudio()
        player.toggle("entry", listOf("a", "b"), "Reference", gapMillis = { 5000L })
        runCurrent()
        assertEquals(listOf("a"), played)
        advanceTimeBy(4999)
        assertEquals(listOf("a"), played)
        player.stop(); advanceUntilIdle()
        assertEquals(listOf("a"), played); assertNull(player.playingKey.value)
    }
    @Test fun oldCancelledRunCannotClearOrStopNewSection() = runTest {
        val player = player()
        val gate = CompletableDeferred<Unit>()
        coEvery { audio.playTrackAndGetStatus(any(), any(), any(), any(), any()) } coAnswers {
            if (firstArg<String>() == "old") withContext(NonCancellable) { gate.await() }
            AudioPlaybackStatus.PlayedFromLocalCache
        }
        player.toggle("old-key", listOf("old"), "Reference"); runCurrent()
        player.toggle("new-key", listOf("new"), "Reference"); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals("new-key", player.playingKey.value)
        events.tryEmit(PlaybackEvent.Completed()); runCurrent()
        assertNull(player.playingKey.value)
    }
    @Test fun failedResolutionAndRejectedPreflightNeverPlayAudio() = runTest {
        val player = player()
        coEvery { content.ensureAudioCached(any(), any(), any(), any()) } returns false
        var completed = false
        player.toggle("failed", listOf("a"), "Reference", onCompleted = { completed = true }); advanceUntilIdle()
        assertFalse(completed); assertNull(player.playingKey.value)
        player.toggle("blocked", listOf("b"), "Reference", preflight = { false }); advanceUntilIdle()
        coVerify(exactly = 0) { audio.playTrackAndGetStatus(any(), any(), any(), any(), any()) }
    }
}
