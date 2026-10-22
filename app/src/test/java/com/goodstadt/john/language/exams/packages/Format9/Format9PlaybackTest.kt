package com.goodstadt.john.language.exams.packages.Format9

import androidx.lifecycle.viewModelScope
import com.goodstadt.john.language.exams.MainCoroutineRule
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.data.repository.TTSStatsRepository
import com.goodstadt.john.language.exams.managers.AccessPolicy
import com.goodstadt.john.language.exams.managers.AudioCacheManager
import com.goodstadt.john.language.exams.managers.SequencePlayer
import com.goodstadt.john.language.exams.managers.SimpleRateLimiter
import com.goodstadt.john.language.exams.models.Format9File
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Format9PlaybackTest {
    @get:Rule val main = MainCoroutineRule()
    private val models = mutableListOf<Format9ViewModel>()
    private data class Harness(val vm: Format9ViewModel, val sequence: SequencePlayer, val paused: MutableStateFlow<Boolean>)

    private fun model(file: Format9File): Harness {
        val content: ContentRepository = mockk()
        val sequence: SequencePlayer = mockk(relaxed = true)
        val policy: AccessPolicy = mockk()
        val billing: BillingRepository = mockk()
        val paused = MutableStateFlow(false)
        coEvery { content.getFormat9Data(file.sheetname) } returns Result.success(file)
        every { sequence.playingKey } returns MutableStateFlow<String?>(null)
        every { sequence.isPaused } returns paused
        every { policy.isPremium } returns true
        every { billing.isPurchased } returns MutableStateFlow(true)
        val vm = Format9ViewModel(content, sequence, policy, billing, mockk<SimpleRateLimiter>(relaxed = true),
            mockk<TTSStatsRepository>(relaxed = true), mockk<AudioCacheManager>(relaxed = true))
        models += vm
        vm.load(file.sheetname)
        assertEquals(file, vm.file.value)
        return Harness(vm, sequence, paused)
    }

    @After fun cleanUp() { models.forEach { it.stopPlayback(); it.viewModelScope.cancel() } }

    @Test fun bothStoriesPlayTheirOwnTextWithAlignedActorVoicesAndHighlightTheSpokenLine() {
        for (flavour in listOf("de", "en")) {
            val file = podcastFixture(flavour)
            val (vm, sequence) = model(file)
            val lines = file.scenes.sortedBy { it.sortorder }.flatMap { it.lines.sortedBy { line -> line.sortorder } }
            val voices = file.speakers.associate { it.id to it.voiceId }
            val onTrack = slot<(String) -> Unit>()
            vm.togglePlayAll()
            verify(exactly = 1) {
                sequence.toggle(key = any(), sentences = lines.map(file::spokenText), level = "Reference",
                    preflight = any(), onTrackStarted = capture(onTrack), onTrackPlayed = any(), onCompleted = any(),
                    sheetName = file.sheetname, isPremiumUser = true, gapMillis = null,
                    voices = lines.map { voices[it.speakerId] })
            }
            val later = file.scenes[1].lines.first()
            onTrack.captured(file.spokenText(later))
            assertEquals(file.spokenText(later), vm.currentSpokenText.value)
            assertEquals(1, vm.focusedSceneIndex.value)
            vm.toggleTranslation()
            assertEquals(file.spokenText(later), vm.spokenText(later))
            assertEquals(file.displayText(later, true), vm.displayText(later, true))
        }
    }

    @Test fun pausedSeekingUsesTheSameLanguageAndVoicesAsImmediatePlayback() {
        for (flavour in listOf("de", "en")) {
            val file = podcastFixture(flavour)
            val (vm, sequence, paused) = model(file)
            val lines = file.scenes.sortedBy { it.sortorder }.drop(1).flatMap { it.lines.sortedBy { line -> line.sortorder } }
            val voices = file.speakers.associate { it.id to it.voiceId }
            paused.value = true
            vm.seekToScene(1)
            assertEquals(file.spokenText(lines.first()), vm.currentSpokenText.value)
            verify(exactly = 1) {
                sequence.prime(key = any(), sentences = lines.map(file::spokenText), level = "Reference",
                    onTrackStarted = any(), onTrackPlayed = any(), onCompleted = any(),
                    sheetName = file.sheetname, isPremiumUser = true, gapMillis = null,
                    voices = lines.map { voices[it.speakerId] }, startIndex = 0)
            }
        }
    }

    @Test fun emptySpokenEntriesAreSkippedWithoutShiftingTheRemainingActorVoices() {
        val source = podcastFixture("de")
        val scene = source.scenes.first()
        val blank = scene.lines.first().copy(de = "", en = "")
        val spoken = scene.lines[1]
        val file = source.copy(scenes = listOf(scene.copy(lines = listOf(blank, spoken))))
        val (vm, sequence) = model(file)
        vm.togglePlayAll()
        verify {
            sequence.toggle(key = any(), sentences = listOf(spoken.de), level = "Reference", preflight = any(),
                onTrackStarted = any(), onTrackPlayed = any(), onCompleted = any(), sheetName = file.sheetname,
                isPremiumUser = true, gapMillis = null,
                voices = listOf(file.speakers.first { it.id == spoken.speakerId }.voiceId))
        }
    }
}
