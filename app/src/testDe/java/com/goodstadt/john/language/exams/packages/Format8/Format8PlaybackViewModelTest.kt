package com.goodstadt.john.language.exams.packages.Format8

import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.MainCoroutineRule
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.repository.AudioPlaybackRepository
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.managers.*
import com.goodstadt.john.language.exams.models.AudioPlaybackStatus
import com.goodstadt.john.language.exams.models.Format8Example
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Format8PlaybackViewModelTest {
    @get:Rule val main = MainCoroutineRule()
    private val audio: AudioPlaybackRepository = mockk(relaxed = true)
    private val sequence: SequencePlayer = mockk(relaxed = true)
    private val policy: AccessPolicy = mockk()
    private val billing: BillingRepository = mockk()
    private val preferences: UserPreferencesRepository = mockk()
    private val gap = MutableStateFlow(false)
    private val playing = MutableStateFlow<String?>(null)
    private val models = mutableListOf<Format8PlaybackViewModel>()
    private val example = Format8Example("Ich kenne ihn.", "I know him.", "ihn", "", "ihn")
    private fun model(): Format8PlaybackViewModel {
        every { sequence.playingKey } returns playing
        every { policy.isRowLocked(any(), any(), any()) } returns false
        every { policy.isPremium } returns true
        every { billing.isPurchased } returns MutableStateFlow(true)
        every { preferences.format8SpeakAloudGapFlow } returns gap
        return Format8PlaybackViewModel(audio, sequence, policy, billing, mockk(relaxed = true), preferences,
            mockk<TTSStatsRepository>(relaxed = true), mockk<AudioCacheManager>(relaxed = true)).also { models += it }
    }
    @After fun cleanUp() { models.forEach { it.stopPlayback(); it.viewModelScope.cancel() } }

    @Test fun sentenceTapUsesOriginalAudioTextAndStopsOnLeaving() {
        val vm = model()
        coEvery { audio.playTrackAndGetStatus(any(), any(), any(), any(), any()) } returns AudioPlaybackStatus.PlayedFromLocalCache
        vm.playExample("sheet", 0, example)
        assertEquals(example.german, vm.lastSentence)
        coVerify { audio.playTrackAndGetStatus(example.german, "Reference", "sheet", true, true) }
        vm.stopPlayback()
        verify { audio.stopPlayback() }
    }
    @Test fun lockedRowsOpenPremiumWithoutPlayingAnything() {
        val vm = model()
        every { policy.isRowLocked(any(), any(), any()) } returns true
        vm.playExample("sheet", 99, example)
        assertEquals(Format8PlaybackViewModel.Prompt.PREMIUM, vm.prompt.value)
        coVerify(exactly = 0) { audio.playTrackAndGetStatus(any(), any(), any(), any(), any()) }
    }
    @Test fun rateLimitUsesExistingDailyPaywallPrompt() {
        val vm = model()
        coEvery { audio.playTrackAndGetStatus(any(), any(), any(), any(), any()) } returns
            AudioPlaybackStatus.RateLimited(SimpleRateLimiter.FailReason.DAILY)
        vm.playExample("sheet", 0, example)
        assertEquals(Format8PlaybackViewModel.Prompt.DAILY, vm.prompt.value)
        vm.dismissPrompt(); assertNull(vm.prompt.value)
    }
    @Test fun repeatToggleWritesThePersistentPreference() {
        val vm = model()
        coEvery { preferences.setFormat8SpeakAloudGap(any()) } coAnswers { gap.value = firstArg() }
        vm.setGapEnabled(true)
        coVerify { preferences.setFormat8SpeakAloudGap(true) }
        assertTrue(vm.gapEnabled.value)
    }
    @Test fun leavingAnIdleScreenDoesNotStopAnotherScreensSequence() {
        val vm = model()
        playing.value = "vocab-section"
        vm.stopPlayback()
        verify(exactly = 0) { sequence.stop() }
        verify(exactly = 0) { audio.stopPlayback() }
    }
}
