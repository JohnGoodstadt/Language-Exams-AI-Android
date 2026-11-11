package com.goodstadt.john.language.exams.packages.Format9

import android.content.Context
import android.content.SharedPreferences
import com.goodstadt.john.language.exams.data.repository.BillingRepository
import com.goodstadt.john.language.exams.data.repository.ContentRepository
import com.goodstadt.john.language.exams.managers.SequencePlayer
import com.goodstadt.john.language.exams.models.Format9File
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class Format9SelectionTest {
    private val repository: ContentRepository = mockk()
    private val sequence: SequencePlayer = mockk(relaxed = true)
    private val playingKey = MutableStateFlow<String?>(null)
    private lateinit var viewModel: Format9ViewModel
    private val football by lazy {
        Json { ignoreUnknownKeys = true }.decodeFromString<Format9File>(podcastFixtureText("de"))
    }
    private val second by lazy { football.copy(sheetname = "SecondPodcast", title = "Another story") }

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        val context: Context = mockk()
        val prefs: SharedPreferences = mockk(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getInt(any(), any()) } answers { secondArg() }
        val billing: BillingRepository = mockk()
        every { billing.isPurchased } returns MutableStateFlow(false)
        every { sequence.playingKey } returns playingKey
        every { sequence.isPaused } returns MutableStateFlow(false)
        every { sequence.stop() } answers { playingKey.value = null }
        viewModel = Format9ViewModel(repository, sequence, mockk(relaxed = true), billing,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), context)
    }

    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun selectingAnotherPodcastStopsAudioAndClearsThePreviousStoryWhileLoading() = runTest {
        val pending = CompletableDeferred<Result<Format9File>>()
        coEvery { repository.getFormat9Data(football.sheetname) } returns Result.success(football)
        coEvery { repository.getFormat9Data(second.sheetname) } coAnswers { pending.await() }
        viewModel.load(football.sheetname)
        runCurrent()
        viewModel.toggleTranslation()
        viewModel.seekToScene(1)
        playingKey.value = viewModel.playAllKey()

        viewModel.load(second.sheetname)

        verify(exactly = 1) { sequence.stop() }
        assertNull(viewModel.file.value)
        assertNull(viewModel.error.value)
        assertEquals(0, viewModel.focusedSceneIndex.value)
        assertFalse(viewModel.showTranslation.value)
        runCurrent()
        pending.complete(Result.success(second))
        advanceUntilIdle()
        assertEquals(second, viewModel.file.value)
    }

    @Test fun aSlowPreviousFetchCannotReplaceTheNewlySelectedPodcast() = runTest {
        val oldRequest = CompletableDeferred<Result<Format9File>>()
        coEvery { repository.getFormat9Data(football.sheetname) } coAnswers {
            // Simulate a repository finishing its fallback even after cancellation.
            withContext(NonCancellable) { oldRequest.await() }
        }
        coEvery { repository.getFormat9Data(second.sheetname) } returns Result.success(second)
        viewModel.load(football.sheetname)
        runCurrent()
        viewModel.load(second.sheetname)
        runCurrent()
        assertEquals(second, viewModel.file.value)

        oldRequest.complete(Result.success(football))
        advanceUntilIdle()
        assertEquals(second, viewModel.file.value)
        assertNull(viewModel.error.value)
    }
}
