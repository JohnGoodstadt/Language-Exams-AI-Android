package com.goodstadt.john.language.exams.viewmodels

import com.goodstadt.john.language.exams.MainCoroutineRule
import com.goodstadt.john.language.exams.data.VocabRepository
import com.goodstadt.john.language.exams.models.VocabFile
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class TabsViewModelTest {

    // This rule swaps the main dispatcher with a test dispatcher
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    // Declare the mock object and the ViewModel
    private lateinit var mockVocabRepository: VocabRepository
    private lateinit var viewModel: TabsViewModel

    @Before
    fun setUp() {
        // Create a mock instance of the repository before each test
        mockVocabRepository = mockk()
    }

    @Test
    fun `when repository returns success, vocabUiState is Success`() {
        // 1. Arrange: Define what the mock should do and create a fake data object.
        val fakeVocabData: VocabFile = mockk() // A mock VocabFile is fine for this test
        coEvery { mockVocabRepository.getVocabData() } returns Result.success(fakeVocabData)

        // 2. Act: Initialize the ViewModel. The init block will run and call the repository.
        viewModel = TabsViewModel(mockVocabRepository)

        // 3. Assert: Check if the UI state is updated correctly.
        val uiState = viewModel.vocabUiState.value
        assertThat(uiState).isInstanceOf(VocabDataUiState.Success::class.java)
        assertThat((uiState as VocabDataUiState.Success).vocabFile).isEqualTo(fakeVocabData)
    }

    @Test
    fun `when repository returns failure, vocabUiState is Error`() {
        // 1. Arrange: Define the mock's behavior for a failure case.
        val fakeException = Exception("Failed to load file")
        coEvery { mockVocabRepository.getVocabData() } returns Result.failure(fakeException)

        // 2. Act: Initialize the ViewModel.
        viewModel = TabsViewModel(mockVocabRepository)

        // 3. Assert: Check if the UI state is an Error.
        val uiState = viewModel.vocabUiState.value
        assertThat(uiState).isInstanceOf(VocabDataUiState.Error::class.java)
        assertThat((uiState as VocabDataUiState.Error).message).isEqualTo(fakeException.localizedMessage)
    }
}