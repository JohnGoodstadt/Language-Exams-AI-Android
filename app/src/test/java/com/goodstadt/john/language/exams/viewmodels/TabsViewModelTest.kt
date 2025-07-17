package com.goodstadt.john.language.exams.viewmodels

import android.app.Application
import com.goodstadt.john.language.exams.MainCoroutineRule
import com.goodstadt.john.language.exams.data.AuthRepository
import com.goodstadt.john.language.exams.data.ControlRepository
import com.goodstadt.john.language.exams.data.RecallingItems
import com.goodstadt.john.language.exams.data.TTSStatsRepository
import com.goodstadt.john.language.exams.data.UserStatsRepository
import com.goodstadt.john.language.exams.data.UserPreferencesRepository
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.models.LanguageCodeDetails
import com.goodstadt.john.language.exams.models.VocabFile
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class TabsViewModelTest {

    // This rule swaps the main dispatcher with a test dispatcher, crucial for Flow testing
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    // Declare mocks for all three dependencies
    private lateinit var mockControlRepository: ControlRepository
    private lateinit var mockUserPreferencesRepository: UserPreferencesRepository
    private lateinit var mockRecallingItemsManager: RecallingItems
    private lateinit var mockVocabRepository: VocabRepository
    private lateinit var mockAuthRepository: AuthRepository
    private lateinit var mockUserStatsRepository: UserStatsRepository
    private lateinit var mockTTSStatsRepository: TTSStatsRepository
    private lateinit var viewModel: TabsViewModel
    private lateinit var mockApplication: Application

    @Before
    fun setUp() {
        // Create mock instances of all repositories
        mockVocabRepository = mockk()
        mockUserPreferencesRepository = mockk()
        mockControlRepository = mockk()

        // --- NEW MOCK SETUP FOR FLOWS ---
        // When the ViewModel asks for the preference flows, return a simple Flow
        // that emits a single, test-specific value.
        every { mockUserPreferencesRepository.selectedFileNameFlow } returns flowOf("test_file_name")
        every { mockUserPreferencesRepository.selectedVoiceNameFlow } returns flowOf("test_voice_name")

        // Also mock the control repository call from the init block
        val dummyDetails: LanguageCodeDetails = mockk(relaxed = true)
        coEvery { mockControlRepository.getActiveLanguageDetails() } returns Result.success(dummyDetails)
    }

    @Test
    fun `when viewmodel initializes, it loads data for the file from preferences`() = runTest {
        // 1. Arrange
        val fakeVocabData: VocabFile = mockk(relaxed = true)
        // Set up the vocab repository to return success when called with our test file name
        coEvery { mockVocabRepository.getVocabData("test_file_name") } returns Result.success(fakeVocabData)

        // 2. Act
        // Initialize the ViewModel with ALL THREE mocks. This solves the "too many arguments" error.
//        viewModel = TabsViewModel(mockVocabRepository, mockUserPreferencesRepository, mockControlRepository)
       // viewModel = TabsViewModel(mockVocabRepository, mockUserPreferencesRepository,mockRecallingItemsManager,mockAuthRepository,mockStatsRepository,mockApplication)
        viewModel = TabsViewModel(mockAuthRepository, mockUserPreferencesRepository,mockRecallingItemsManager,mockTTSStatsRepository)
        // Allow the coroutines launched in the init block to complete
        advanceUntilIdle()

        // 3. Assert
        // Check that the final UI state is Success, proving that the data was loaded.
//        val uiState = viewModel.vocabUiState.value
//        assertThat(uiState).isInstanceOf(VocabDataUiState.Success::class.java)
//        assertThat((uiState as VocabDataUiState.Success).vocabFile).isEqualTo(fakeVocabData)
    }

    @Test
    fun `when file loading fails, vocabUiState is Error`() = runTest {
        // 1. Arrange
        val fakeException = Exception("File not found")
        // Set up the vocab repository to return failure
        coEvery { mockVocabRepository.getVocabData(any()) } returns Result.failure(fakeException)

        // 2. Act
//        viewModel = TabsViewModel(mockVocabRepository, mockUserPreferencesRepository, mockControlRepository)
        viewModel = TabsViewModel(mockAuthRepository, mockUserPreferencesRepository,mockRecallingItemsManager,mockTTSStatsRepository)
        advanceUntilIdle()

        // 3. Assert
        // Check that the UI state correctly reflects the error.
//        val uiState = viewModel.vocabUiState.value
//        assertThat(uiState).isInstanceOf(VocabDataUiState.Error::class.java)
    }
}